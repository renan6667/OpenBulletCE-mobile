package com.openbulletce.mobile.ui

import android.content.Intent
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.openbulletce.mobile.data.*
import java.text.DateFormat
import java.util.Date

@Composable
fun ProxiesScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember { ManagerStore(context) }
    var records by remember { mutableStateOf(store.proxies()) }
    var raw by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }

    fun refresh() { records = store.proxies() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Proxy Manager", fontWeight = FontWeight.Bold)
        Text(
            "Desktop-style proxy references are stored locally. Parsing supports (Http)/(Socks4)/(Socks4a)/(Socks5) and preserves chain text.",
            style = MaterialTheme.typography.bodySmall
        )
        OutlinedTextField(
            raw,
            { raw = it },
            label = { Text("(Http)host:port:user:pass") },
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val parsed = ProxyCodec.parse(raw)
                if (parsed.proxy != null) {
                    store.putProxy(parsed.proxy)
                    raw = ""
                    status = "Proxy added"
                    refresh()
                } else {
                    status = parsed.error.orEmpty()
                }
            }) { Text("Add") }

            OutlinedButton(
                enabled = records.isNotEmpty(),
                onClick = {
                    store.clearProxies()
                    refresh()
                    status = "Proxy list cleared"
                }
            ) { Text("Clear") }
        }
        if (status.isNotBlank()) Text(status)

        HorizontalDivider()
        Text("Stored: ${records.size}")
        records.forEach { proxy ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(proxy.raw, fontWeight = FontWeight.SemiBold)
                    Text("${proxy.type} • ${proxy.working}" + if (proxy.pingMs > 0) " • ${proxy.pingMs} ms" else "")
                    TextButton(onClick = {
                        store.removeProxy(proxy.id)
                        refresh()
                    }) { Text("Remove") }
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
    var records by remember { mutableStateOf(store.wordlists()) }
    var status by remember { mutableStateOf("") }

    fun refresh() { records = store.wordlists() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                var name = "wordlist"
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        name = cursor.getString(0) ?: name
                    }
                }
                val total = resolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                    var count = 0
                    while (reader.readLine() != null) count++
                    count
                } ?: 0

                store.putWordlist(
                    WordlistRecord(
                        name = name,
                        uri = uri.toString(),
                        totalLines = total
                    )
                )
                refresh()
                status = "Imported $name ($total lines)"
            }.onFailure {
                status = "Import error: ${it.message}"
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Wordlist Manager", fontWeight = FontWeight.Bold)
        Text(
            "Android keeps a persistent document URI instead of a Windows filesystem path.",
            style = MaterialTheme.typography.bodySmall
        )
        Button(onClick = { picker.launch(arrayOf("text/*", "application/octet-stream")) }) {
            Text("Import wordlist")
        }
        if (status.isNotBlank()) Text(status)
        HorizontalDivider()
        Text("Stored: ${records.size}")
        records.forEach { item ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(item.name, fontWeight = FontWeight.SemiBold)
                    Text("${item.type} • ${item.totalLines} lines")
                    if (item.purpose.isNotBlank()) Text(item.purpose)
                    TextButton(onClick = {
                        store.removeWordlist(item.id)
                        refresh()
                    }) { Text("Remove") }
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
    var status by remember { mutableStateOf("") }

    fun refresh() { records = store.cookieSets() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                val fallbackName = uri.lastPathSegment?.substringAfterLast(':')?.ifBlank { "Cookies" } ?: "Cookies"
                store.putCookieSet(CookieSetRecord(name = fallbackName, treeUri = uri.toString()))
                refresh()
                status = "Cookie folder added"
            }.onFailure {
                status = "Import error: ${it.message}"
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Cookie Manager", fontWeight = FontWeight.Bold)
        Text(
            "Like the PC Cookie Edition manager, this stores references to cookie-set folders. Android uses persisted folder URIs.",
            style = MaterialTheme.typography.bodySmall
        )
        Button(onClick = { picker.launch(null) }) { Text("Add cookie folder") }
        if (status.isNotBlank()) Text(status)
        HorizontalDivider()
        Text("Stored: ${records.size}")
        records.forEach { item ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(item.name, fontWeight = FontWeight.SemiBold)
                    Text(item.treeUri, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = {
                        store.removeCookieSet(item.id)
                        refresh()
                    }) { Text("Remove") }
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

    fun refresh() { records = store.hits() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Hits DB", fontWeight = FontWeight.Bold)
            TextButton(
                enabled = records.isNotEmpty(),
                onClick = { store.clearHits(); refresh() }
            ) { Text("Clear") }
        }
        Text(
            "Results are stored locally only when you explicitly save them from the authorized Runner.",
            style = MaterialTheme.typography.bodySmall
        )
        Text("Stored: ${records.size}")
        records.forEach { hit ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(hit.type, fontWeight = FontWeight.SemiBold)
                    Text(hit.data)
                    if (hit.captured.isNotBlank()) Text("Capture: ${hit.captured}")
                    if (hit.configName.isNotBlank()) Text("Config: ${hit.configName}")
                    if (hit.wordlistName.isNotBlank()) Text("Wordlist: ${hit.wordlistName}")
                    Text(DateFormat.getDateTimeInstance().format(Date(hit.dateEpochMs)))
                    TextButton(onClick = { store.removeHit(hit.id); refresh() }) { Text("Remove") }
                }
            }
        }
    }
}
