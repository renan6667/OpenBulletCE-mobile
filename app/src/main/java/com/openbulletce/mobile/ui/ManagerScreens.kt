package com.openbulletce.mobile.ui

import android.content.Intent
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.openbulletce.mobile.data.*
import com.openbulletce.mobile.network.AuthorizedHttpClient
import com.openbulletce.mobile.network.SimpleRequest
import com.openbulletce.mobile.security.AuthorizedTargetPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@Composable
fun ProxiesScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember { ManagerStore(context) }
    val prefs = remember { AppPreferences(context) }
    val scope = rememberCoroutineScope()

    var records by remember { mutableStateOf(store.proxies()) }
    var raw by remember { mutableStateOf("") }
    var testUrl by remember { mutableStateOf(prefs.loadProxyTestUrl()) }
    var selectedProxyId by remember { mutableStateOf(prefs.loadRunnerDraft().selectedProxyId) }
    var testingId by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("") }

    fun refresh() { records = store.proxies() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Proxy Manager", fontWeight = FontWeight.Bold)
        Text(
            "Proxies are saved locally. Tests and Runner traffic still require the destination host to be explicitly authorized in Settings.",
            style = MaterialTheme.typography.bodySmall
        )

        OutlinedTextField(
            raw,
            { raw = it },
            label = { Text("(Http)host:port:user:pass") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            testUrl,
            {
                testUrl = it
                prefs.saveProxyTestUrl(it)
            },
            label = { Text("Authorized URL used to test proxies") },
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = {
                val parsed = ProxyCodec.parse(raw)
                if (parsed.proxy != null) {
                    store.putProxy(parsed.proxy)
                    raw = ""
                    status = "Proxy added and saved"
                    refresh()
                } else {
                    status = parsed.error.orEmpty()
                }
            }) { Text("Add") }

            OutlinedButton(
                enabled = records.isNotEmpty() && testingId == null,
                onClick = {
                    store.clearProxies()
                    selectedProxyId = ""
                    prefs.saveRunnerDraft(
                        prefs.loadRunnerDraft().copy(selectedProxyId = "")
                    )
                    refresh()
                    status = "Proxy list cleared"
                }
            ) { Text("Clear") }
        }

        if (status.isNotBlank()) Text(status)

        HorizontalDivider()
        Text("Stored: ${records.size}")

        records.forEach { proxy ->
            val issue = ProxyCodec.executionIssue(proxy)
            val selected = proxy.id == selectedProxyId

            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        ProxyCodec.displayMasked(proxy),
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "${proxy.type} • ${proxy.working}" +
                            if (proxy.pingMs > 0) " • ${proxy.pingMs} ms" else ""
                    )
                    if (selected) {
                        Text(
                            "Selected for Runner",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    issue?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }

                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            enabled = testingId == null && issue == null && testUrl.isNotBlank(),
                            onClick = {
                                testingId = proxy.id
                                status = "Testing proxy against authorized URL..."
                                prefs.saveProxyTestUrl(testUrl)

                                scope.launch {
                                    val policy = AuthorizedTargetPolicy(
                                        prefs.loadAuthorizedHosts().toSet()
                                    )
                                    val client = AuthorizedHttpClient(
                                        policy,
                                        prefs.loadTimeoutMs()
                                    )
                                    val started = System.nanoTime()
                                    val result = client.execute(
                                        SimpleRequest("GET", testUrl),
                                        proxy
                                    )
                                    val ping = ((System.nanoTime() - started) / 1_000_000L)
                                        .coerceAtMost(Int.MAX_VALUE.toLong())
                                        .toInt()

                                    val updated = if (result.isSuccess) {
                                        proxy.copy(working = "WORKING", pingMs = ping)
                                    } else {
                                        proxy.copy(working = "FAILED", pingMs = ping)
                                    }
                                    store.putProxy(updated)
                                    refresh()
                                    status = result.fold(
                                        onSuccess = {
                                            "Proxy working: HTTP ${it.statusCode} in ${ping} ms"
                                        },
                                        onFailure = {
                                            "Proxy test failed: ${it.message}"
                                        }
                                    )
                                    testingId = null
                                }
                            }
                        ) {
                            Text(if (testingId == proxy.id) "Testing..." else "Test")
                        }

                        Button(onClick = {
                            selectedProxyId = proxy.id
                            prefs.saveRunnerDraft(
                                prefs.loadRunnerDraft().copy(selectedProxyId = proxy.id)
                            )
                            status = "Proxy selected for Runner"
                        }) {
                            Text(if (selected) "Selected" else "Use in Runner")
                        }

                        if (selected) {
                            OutlinedButton(onClick = {
                                selectedProxyId = ""
                                prefs.saveRunnerDraft(
                                    prefs.loadRunnerDraft().copy(selectedProxyId = "")
                                )
                                status = "Runner will connect without a proxy"
                            }) {
                                Text("Disable")
                            }
                        }

                        TextButton(onClick = {
                            store.removeProxy(proxy.id)
                            if (selectedProxyId == proxy.id) {
                                selectedProxyId = ""
                                prefs.saveRunnerDraft(
                                    prefs.loadRunnerDraft().copy(selectedProxyId = "")
                                )
                            }
                            refresh()
                        }) { Text("Remove") }
                    }
                }
            }
        }
    }
}

@Composable
fun WordlistsScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val resolver = context.contentResolver
    val store = remember { ManagerStore(context) }
    val scope = rememberCoroutineScope()

    var records by remember { mutableStateOf(store.wordlists()) }
    var search by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var importing by remember { mutableStateOf(false) }

    fun refresh() { records = store.wordlists() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            importing = true
            status = "Reading wordlist..."
            scope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        runCatching {
                            resolver.takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        }

                        var name = "wordlist"
                        resolver.query(
                            uri,
                            arrayOf(OpenableColumns.DISPLAY_NAME),
                            null,
                            null,
                            null
                        )?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                name = cursor.getString(0) ?: name
                            }
                        }

                        val total = resolver.openInputStream(uri)
                            ?.bufferedReader()
                            ?.use { reader ->
                                var count = 0
                                while (count < Int.MAX_VALUE && reader.readLine() != null) {
                                    count++
                                }
                                count
                            }
                            ?: 0

                        WordlistRecord(
                            name = name.substringBeforeLast('.', name),
                            uri = uri.toString(),
                            totalLines = total
                        )
                    }
                }

                result
                    .onSuccess { item ->
                        store.putWordlist(item)
                        refresh()
                        status = "Imported ${item.name} (${item.totalLines} lines)"
                    }
                    .onFailure {
                        status = "Import error: ${it.message}"
                    }

                importing = false
            }
        }
    }

    val filtered = remember(records, search) {
        if (search.isBlank()) records
        else records.filter {
            it.name.contains(search, ignoreCase = true) ||
                it.type.contains(search, ignoreCase = true) ||
                it.purpose.contains(search, ignoreCase = true)
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Wordlist Manager", fontWeight = FontWeight.Bold)
        Text(
            "Android keeps a persistent document URI instead of a Windows filesystem path. Type and Purpose mirror the desktop metadata.",
            style = MaterialTheme.typography.bodySmall
        )

        Button(
            enabled = !importing,
            onClick = { picker.launch(arrayOf("text/*", "application/octet-stream")) }
        ) {
            Text(if (importing) "Importing..." else "Import wordlist")
        }

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            label = { Text("Search wordlists") },
            modifier = Modifier.fillMaxWidth()
        )

        if (status.isNotBlank()) Text(status)
        HorizontalDivider()
        Text("Showing: ${filtered.size} / ${records.size}")

        filtered.forEach { item ->
            WordlistCard(
                item = item,
                onSave = { updated ->
                    store.putWordlist(updated)
                    refresh()
                    status = "Updated ${updated.name}"
                },
                onRemove = {
                    store.removeWordlist(item.id)
                    refresh()
                }
            )
        }
    }
}

@Composable
private fun WordlistCard(
    item: WordlistRecord,
    onSave: (WordlistRecord) -> Unit,
    onRemove: () -> Unit
) {
    var name by remember(item.id, item.name) { mutableStateOf(item.name) }
    var type by remember(item.id, item.type) { mutableStateOf(item.type) }
    var purpose by remember(item.id, item.purpose) { mutableStateOf(item.purpose) }

    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = type,
                onValueChange = { type = it },
                label = { Text("Type") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = purpose,
                onValueChange = { purpose = it },
                label = { Text("Purpose") },
                modifier = Modifier.fillMaxWidth()
            )
            Text("${item.totalLines} lines", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    onSave(
                        item.copy(
                            name = name.trim().ifBlank { item.name },
                            type = type.trim().ifBlank { "Default" },
                            purpose = purpose.trim()
                        )
                    )
                }) {
                    Text("Save")
                }
                TextButton(onClick = onRemove) {
                    Text("Remove")
                }
            }
        }
    }
}

@Composable
fun CookiesScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember { ManagerStore(context) }
    var records by remember { mutableStateOf(store.cookieSets()) }
    var search by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }

    fun refresh() { records = store.cookieSets() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                val fallbackName = uri.lastPathSegment
                    ?.substringAfterLast(':')
                    ?.ifBlank { "Cookies" }
                    ?: "Cookies"

                store.putCookieSet(
                    CookieSetRecord(
                        name = fallbackName,
                        treeUri = uri.toString()
                    )
                )
                refresh()
                status = "Cookie folder added"
            }.onFailure {
                status = "Import error: ${it.message}"
            }
        }
    }

    val filtered = remember(records, search) {
        if (search.isBlank()) records
        else records.filter { it.name.contains(search, ignoreCase = true) }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Cookie Manager", fontWeight = FontWeight.Bold)
        Text(
            "Stores local folder references like the desktop manager. The Android port does not automatically validate third-party sessions or cookie files.",
            style = MaterialTheme.typography.bodySmall
        )
        Button(onClick = { picker.launch(null) }) { Text("Add cookie folder") }
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            label = { Text("Search cookie folders") },
            modifier = Modifier.fillMaxWidth()
        )
        if (status.isNotBlank()) Text(status)
        HorizontalDivider()
        Text("Showing: ${filtered.size} / ${records.size}")

        filtered.forEach { item ->
            CookieSetCard(
                item = item,
                onSave = { updated ->
                    store.putCookieSet(updated)
                    refresh()
                    status = "Updated ${updated.name}"
                },
                onRemove = {
                    store.removeCookieSet(item.id)
                    refresh()
                }
            )
        }
    }
}

@Composable
private fun CookieSetCard(
    item: CookieSetRecord,
    onSave: (CookieSetRecord) -> Unit,
    onRemove: () -> Unit
) {
    var name by remember(item.id, item.name) { mutableStateOf(item.name) }

    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth()
            )
            Text(item.treeUri, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    onSave(item.copy(name = name.trim().ifBlank { item.name }))
                }) {
                    Text("Save")
                }
                TextButton(onClick = onRemove) {
                    Text("Remove")
                }
            }
        }
    }
}

@Composable
fun HitsScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember { ManagerStore(context) }
    var records by remember { mutableStateOf(store.hits()) }
    var search by remember { mutableStateOf("") }
    var configFilter by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }

    fun refresh() { records = store.hits() }

    val filtered = remember(records, search, configFilter, typeFilter) {
        records.filter { hit ->
            val matchesSearch = search.isBlank() ||
                hit.data.contains(search, ignoreCase = true) ||
                hit.captured.contains(search, ignoreCase = true) ||
                hit.proxy.contains(search, ignoreCase = true)

            val matchesConfig = configFilter.isBlank() ||
                hit.configName.equals(configFilter, ignoreCase = true)

            val matchesType = typeFilter.isBlank() ||
                hit.type.equals(typeFilter, ignoreCase = true)

            matchesSearch && matchesConfig && matchesType
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Hits DB", fontWeight = FontWeight.Bold)
        Text(
            "Results are stored locally only when you explicitly save them from the authorized Runner.",
            style = MaterialTheme.typography.bodySmall
        )

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            label = { Text("Search data / capture / proxy") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = configFilter,
            onValueChange = { configFilter = it },
            label = { Text("Config filter (blank = all)") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = typeFilter,
            onValueChange = { typeFilter = it },
            label = { Text("Type filter (blank = all)") },
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                enabled = records.isNotEmpty(),
                onClick = {
                    val removed = store.removeDuplicateHits()
                    refresh()
                    status = if (removed == 0) "No duplicates found" else "Removed $removed duplicate hit(s)"
                }
            ) {
                Text("Del. dupes")
            }
            OutlinedButton(
                enabled = records.isNotEmpty(),
                onClick = {
                    store.clearHits()
                    refresh()
                    status = "Hits DB cleared"
                }
            ) {
                Text("Purge")
            }
        }

        if (status.isNotBlank()) Text(status)
        Text("Showing: ${filtered.size} / ${records.size}")

        filtered.forEach { hit ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(hit.type, fontWeight = FontWeight.SemiBold)
                    Text(hit.data)
                    if (hit.captured.isNotBlank()) Text("Capture: ${hit.captured}")
                    if (hit.configName.isNotBlank()) Text("Config: ${hit.configName}")
                    if (hit.wordlistName.isNotBlank()) Text("Wordlist: ${hit.wordlistName}")
                    if (hit.proxy.isNotBlank()) Text("Proxy: ${hit.proxy}")
                    Text(DateFormat.getDateTimeInstance().format(Date(hit.dateEpochMs)))
                    TextButton(onClick = {
                        store.removeHit(hit.id)
                        refresh()
                    }) {
                        Text("Remove")
                    }
                }
            }
        }
    }
}
