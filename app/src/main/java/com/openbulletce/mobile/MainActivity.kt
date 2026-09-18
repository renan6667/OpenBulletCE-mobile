package com.openbulletce.mobile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.openbulletce.mobile.config.ConfigCompatibility
import com.openbulletce.mobile.config.ConfigDocumentStore
import com.openbulletce.mobile.config.CustomInputSpec
import com.openbulletce.mobile.config.DataRuleSpec
import com.openbulletce.mobile.config.DesktopConfigCollections
import com.openbulletce.mobile.config.DesktopConfigCodec
import com.openbulletce.mobile.config.LoliScriptInspector
import com.openbulletce.mobile.config.SafeRequestPresetParser
import com.openbulletce.mobile.data.AppPreferences
import com.openbulletce.mobile.data.ConfigLibraryRecord
import com.openbulletce.mobile.data.ConfigLibraryStore
import com.openbulletce.mobile.data.HitRecord
import com.openbulletce.mobile.data.ManagerStore
import com.openbulletce.mobile.data.ProxyCodec
import com.openbulletce.mobile.data.RunnerDraft
import com.openbulletce.mobile.network.AuthorizedHttpClient
import com.openbulletce.mobile.network.SimpleRequest
import com.openbulletce.mobile.security.AuthorizedTargetPolicy
import com.openbulletce.mobile.ui.CookiesScreen
import com.openbulletce.mobile.ui.CustomInputsEditor
import com.openbulletce.mobile.ui.DataRulesEditor
import com.openbulletce.mobile.ui.HitsScreen
import com.openbulletce.mobile.ui.PluginsScreen
import com.openbulletce.mobile.ui.ToolsScreen
import com.openbulletce.mobile.ui.ProxiesScreen
import com.openbulletce.mobile.ui.WordlistsScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { OpenBulletMobileApp() }
    }
}

private val ObBg = Color(0xFF101216)
private val ObPanel = Color(0xFF181B20)
private val ObAccent = Color(0xFF4DA3FF)

private enum class Section(val label: String) {
    RUNNER("Runner"),
    PROXIES("Proxies"),
    WORDLISTS("Wordlists"),
    COOKIES("Cookies"),
    CONFIGS("Configs"),
    HITS("Hits DB"),
    TOOLS("Tools"),
    PLUGINS("Plugins"),
    SETTINGS("Settings"),
    ABOUT("About")
}

@Composable
private fun OpenBulletMobileApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { AppPreferences(context) }
    val initialSection = remember {
        runCatching { Section.valueOf(prefs.loadLastSection()) }
            .getOrDefault(Section.RUNNER)
    }
    var section by remember { mutableStateOf(initialSection) }

    LaunchedEffect(section) {
        prefs.saveLastSection(section.name)
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = ObBg,
            surface = ObPanel,
            primary = ObAccent
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = ObBg) {
            Column {
                Text(
                    "OpenBullet CE Mobile",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    Section.entries.forEach { item ->
                        TextButton(onClick = { section = item }) {
                            Text(
                                item.label,
                                color = if (item == section) ObAccent else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    when (section) {
                        Section.RUNNER -> RunnerScreen()
                        Section.PROXIES -> ProxiesScreen()
                        Section.WORDLISTS -> WordlistsScreen()
                        Section.COOKIES -> CookiesScreen()
                        Section.CONFIGS -> ConfigScreen()
                        Section.HITS -> HitsScreen()
                        Section.TOOLS -> ToolsScreen()
                        Section.PLUGINS -> PluginsScreen()
                        Section.SETTINGS -> SettingsScreen()
                        Section.ABOUT -> AboutScreen()
                    }
                }
            }
        }
    }
}

@Composable
private fun RunnerScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { AppPreferences(context) }
    val managerStore = remember { ManagerStore(context) }
    val libraryStore = remember { ConfigLibraryStore(context) }
    val documentStore = remember { ConfigDocumentStore(context.contentResolver) }
    val scope = rememberCoroutineScope()

    val initialDraft = remember { prefs.loadRunnerDraft() }
    var method by remember { mutableStateOf(initialDraft.method) }
    var url by remember { mutableStateOf(initialDraft.url) }
    var headersText by remember { mutableStateOf(initialDraft.headersText) }
    var body by remember { mutableStateOf(initialDraft.body) }
    var selectedProxyId by remember { mutableStateOf(initialDraft.selectedProxyId) }
    var proxyMenuExpanded by remember { mutableStateOf(false) }
    var output by remember { mutableStateOf("Ready") }
    var lastResponse by remember { mutableStateOf<String?>(null) }
    var running by remember { mutableStateOf(false) }

    val proxies = remember { managerStore.proxies() }
    val selectedProxy = proxies.firstOrNull { it.id == selectedProxyId }
    val activeConfigId = prefs.loadActiveConfigId()
    val activeConfigRecord = remember(activeConfigId) { libraryStore.find(activeConfigId) }

    LaunchedEffect(method, url, headersText, body, selectedProxyId) {
        delay(250)
        prefs.saveRunnerDraft(
            RunnerDraft(
                method = method,
                url = url,
                headersText = headersText,
                body = body,
                selectedProxyId = selectedProxyId
            )
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Authorized request runner", fontWeight = FontWeight.Bold)
        Text(
            "Runner fields, selected proxy and active config are restored when the app opens again. Requests still require an explicitly authorized destination host.",
            style = MaterialTheme.typography.bodySmall
        )

        activeConfigRecord?.let { record ->
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Active config: ${record.name}", fontWeight = FontWeight.SemiBold)
                    if (record.author.isNotBlank()) Text(record.author)

                    OutlinedButton(
                        enabled = !running,
                        onClick = {
                            val config = libraryStore.loadSnapshot(record)
                                ?: runCatching {
                                    if (record.uri.isBlank()) null
                                    else documentStore.read(Uri.parse(record.uri))
                                }.getOrNull()

                            if (config == null) {
                                output = "ERROR: active config could not be reopened."
                            } else {
                                SafeRequestPresetParser.fromConfig(config)
                                    .onSuccess { preset ->
                                        method = preset.method
                                        url = preset.url
                                        headersText = preset.headers.entries
                                            .joinToString("\n") { "${it.key}: ${it.value}" }
                                        body = preset.body
                                        output = "Loaded the first static REQUEST from ${record.name}. Full LoliScript was not executed."
                                    }
                                    .onFailure {
                                        output = "Config is active, but its REQUEST cannot be loaded as a safe static preset: ${it.message}"
                                    }
                            }
                        }
                    ) {
                        Text("Load REQUEST into Runner")
                    }
                }
            }
        }

        OutlinedTextField(
            method,
            { method = it.uppercase().take(10) },
            label = { Text("Method") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            url,
            { url = it },
            label = { Text("URL") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            headersText,
            { headersText = it },
            label = { Text("Headers — one Name: value per line") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )
        OutlinedTextField(
            body,
            { body = it },
            label = { Text("Body") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )

        Text("Proxy", fontWeight = FontWeight.SemiBold)
        Box {
            OutlinedButton(onClick = { proxyMenuExpanded = true }) {
                Text(
                    selectedProxy?.let {
                        ProxyCodec.displayMasked(it) + " • " + it.working
                    } ?: "No proxy"
                )
            }
            DropdownMenu(
                expanded = proxyMenuExpanded,
                onDismissRequest = { proxyMenuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("No proxy") },
                    onClick = {
                        selectedProxyId = ""
                        proxyMenuExpanded = false
                    }
                )
                proxies.forEach { proxy ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                ProxyCodec.displayMasked(proxy) +
                                    " • " + proxy.type + " • " + proxy.working
                            )
                        },
                        onClick = {
                            selectedProxyId = proxy.id
                            proxyMenuExpanded = false
                        }
                    )
                }
            }
        }

        selectedProxy?.let { proxy ->
            ProxyCodec.executionIssue(proxy)?.let { issue ->
                Text(issue, style = MaterialTheme.typography.bodySmall)
            }
        }

        Button(
            enabled = !running && (selectedProxy == null || ProxyCodec.executionIssue(selectedProxy) == null),
            onClick = {
                running = true
                output = "Running..."
                val headers = headersText.lineSequence()
                    .mapNotNull { line ->
                        val split = line.indexOf(':')
                        if (split <= 0) null
                        else line.substring(0, split).trim() to line.substring(split + 1).trim()
                    }
                    .toMap()

                val policy = AuthorizedTargetPolicy(prefs.loadAuthorizedHosts().toSet())
                val client = AuthorizedHttpClient(policy, prefs.loadTimeoutMs())

                scope.launch {
                    val result = client.execute(
                        SimpleRequest(method, url, headers, body),
                        selectedProxy
                    )
                    output = result.fold(
                        onSuccess = {
                            lastResponse = it.body.take(12_000)
                            val suffix = if (it.truncated) {
                                "\n\n[Response truncated by mobile safety limit]"
                            } else {
                                ""
                            }
                            val proxyLine = selectedProxy?.let { p ->
                                "\nProxy: ${ProxyCodec.displayMasked(p)}"
                            }.orEmpty()
                            "HTTP ${it.statusCode}$proxyLine\n\n${it.body.take(12_000)}$suffix"
                        },
                        onFailure = {
                            lastResponse = null
                            "ERROR: ${it.message}"
                        }
                    )
                    running = false
                }
            }
        ) {
            Text(if (running) "Running" else "START")
        }

        OutlinedButton(
            enabled = lastResponse != null && !running,
            onClick = {
                managerStore.putHit(
                    HitRecord(
                        data = url,
                        captured = lastResponse.orEmpty().take(2_000),
                        proxy = selectedProxy?.let(ProxyCodec::displayMasked).orEmpty(),
                        type = "MANUAL_HTTP",
                        configName = activeConfigRecord?.name.orEmpty()
                    )
                )
                output += "\n\nSaved to Hits DB."
            }
        ) {
            Text("Save result to Hits DB")
        }

        HorizontalDivider()
        Text(output)
    }
}

@Composable
private fun ConfigScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember { ConfigDocumentStore(context.contentResolver) }
    val libraryStore = remember { ConfigLibraryStore(context) }

    var library by remember { mutableStateOf(libraryStore.configs()) }
    var loaded by remember { mutableStateOf<DesktopConfigCodec.DesktopConfig?>(null) }
    var name by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var version by remember { mutableStateOf("1.2.2") }
    var additionalInfo by remember { mutableStateOf("") }
    var saveEmptyCaptures by remember { mutableStateOf(false) }
    var continueOnCustom by remember { mutableStateOf(false) }
    var ignoreResponseErrors by remember { mutableStateOf(false) }
    var maxRedirects by remember { mutableStateOf("8") }
    var allowedWordlist1 by remember { mutableStateOf("") }
    var allowedWordlist2 by remember { mutableStateOf("") }
    var encodeData by remember { mutableStateOf(false) }
    var customInputs by remember { mutableStateOf<List<CustomInputSpec>>(emptyList()) }
    var dataRules by remember { mutableStateOf<List<DataRuleSpec>>(emptyList()) }
    var script by remember { mutableStateOf("") }
    var baselineName by remember { mutableStateOf("") }
    var baselineAuthor by remember { mutableStateOf("") }
    var baselineVersion by remember { mutableStateOf("1.2.2") }
    var baselineAdditionalInfo by remember { mutableStateOf("") }
    var baselineSaveEmptyCaptures by remember { mutableStateOf(false) }
    var baselineContinueOnCustom by remember { mutableStateOf(false) }
    var baselineIgnoreResponseErrors by remember { mutableStateOf(false) }
    var baselineMaxRedirects by remember { mutableStateOf("8") }
    var baselineAllowedWordlist1 by remember { mutableStateOf("") }
    var baselineAllowedWordlist2 by remember { mutableStateOf("") }
    var baselineEncodeData by remember { mutableStateOf(false) }
    var baselineCustomInputs by remember { mutableStateOf<List<CustomInputSpec>>(emptyList()) }
    var baselineDataRules by remember { mutableStateOf<List<DataRuleSpec>>(emptyList()) }
    var baselineScript by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("No desktop config loaded") }

    fun refreshLibrary() {
        library = libraryStore.configs()
    }

    fun loadForEditing(config: DesktopConfigCodec.DesktopConfig, message: String) {
        loaded = config
        name = config.name
        author = config.author
        version = config.settings.optString("Version", "1.2.2")
        additionalInfo = config.settings.optString("AdditionalInfo")
        saveEmptyCaptures = config.settings.optBoolean("SaveEmptyCaptures", false)
        continueOnCustom = config.settings.optBoolean("ContinueOnCustom", false)
        ignoreResponseErrors = config.settings.optBoolean("IgnoreResponseErrors", false)
        maxRedirects = config.settings.optInt("MaxRedirects", 8).toString()
        allowedWordlist1 = config.settings.optString("AllowedWordlist1")
        allowedWordlist2 = config.settings.optString("AllowedWordlist2")
        encodeData = config.settings.optBoolean("EncodeData", false)
        customInputs = DesktopConfigCollections.customInputs(config.settings)
        dataRules = DesktopConfigCollections.dataRules(config.settings)
        script = config.script

        baselineName = name
        baselineAuthor = author
        baselineVersion = version
        baselineAdditionalInfo = additionalInfo
        baselineSaveEmptyCaptures = saveEmptyCaptures
        baselineContinueOnCustom = continueOnCustom
        baselineIgnoreResponseErrors = ignoreResponseErrors
        baselineMaxRedirects = maxRedirects
        baselineAllowedWordlist1 = allowedWordlist1
        baselineAllowedWordlist2 = allowedWordlist2
        baselineEncodeData = encodeData
        baselineCustomInputs = customInputs
        baselineDataRules = dataRules
        baselineScript = script
        status = message
    }

    fun rememberImportedConfig(uri: Uri, config: DesktopConfigCodec.DesktopConfig) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        libraryStore.put(
            ConfigLibraryRecord(
                name = config.name,
                author = config.author,
                uri = uri.toString()
            )
        )
        refreshLibrary()
    }

    fun workingConfig(): DesktopConfigCodec.DesktopConfig? {
        val base = loaded ?: return null
        val changes = mutableMapOf<String, Any?>()

        if (name != baselineName) changes["Name"] = name
        if (author != baselineAuthor) changes["Author"] = author
        if (version != baselineVersion) changes["Version"] = version
        if (additionalInfo != baselineAdditionalInfo) changes["AdditionalInfo"] = additionalInfo
        if (saveEmptyCaptures != baselineSaveEmptyCaptures) {
            changes["SaveEmptyCaptures"] = saveEmptyCaptures
        }
        if (continueOnCustom != baselineContinueOnCustom) {
            changes["ContinueOnCustom"] = continueOnCustom
        }
        if (ignoreResponseErrors != baselineIgnoreResponseErrors) {
            changes["IgnoreResponseErrors"] = ignoreResponseErrors
        }
        if (maxRedirects != baselineMaxRedirects) {
            changes["MaxRedirects"] = (maxRedirects.toIntOrNull() ?: 8).coerceIn(0, 100)
        }
        if (allowedWordlist1 != baselineAllowedWordlist1) {
            changes["AllowedWordlist1"] = allowedWordlist1
        }
        if (allowedWordlist2 != baselineAllowedWordlist2) {
            changes["AllowedWordlist2"] = allowedWordlist2
        }
        if (encodeData != baselineEncodeData) changes["EncodeData"] = encodeData
        if (customInputs != baselineCustomInputs) {
            changes["CustomInputs"] = DesktopConfigCollections.customInputsJson(customInputs)
        }
        if (dataRules != baselineDataRules) {
            changes["DataRules"] = DesktopConfigCollections.dataRulesJson(dataRules)
        }

        val withSettings = if (changes.isEmpty()) {
            base
        } else {
            DesktopConfigCodec.withSettings(base, changes)
        }

        return if (script == baselineScript) {
            withSettings
        } else {
            withSettings.copy(script = script)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching { store.read(uri) }
                .onSuccess {
                    loadForEditing(it, "Loaded: ${it.name}")
                    rememberImportedConfig(uri, it)
                }
                .onFailure { status = "Import error: ${it.message}" }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val config = workingConfig()
        if (uri != null && config != null) {
            runCatching { store.write(uri, config) }
                .onSuccess { status = "Exported as desktop-compatible .lce" }
                .onFailure { status = "Export error: ${it.message}" }
        }
    }

    val working = workingConfig()
    val compatibility = working?.let { ConfigCompatibility.analyze(it) }
    val scriptReport = remember(script) { LoliScriptInspector.inspect(script) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("PC config compatibility", fontWeight = FontWeight.Bold)
        Text(
            "Imports and exports Cookie Edition [SETTINGS] + [SCRIPT] configs. Unknown desktop settings stay preserved.",
            style = MaterialTheme.typography.bodySmall
        )

        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                Text("Import PC config")
            }
            OutlinedButton(onClick = {
                val fresh = DesktopConfigCodec.DesktopConfig(
                    settings = JSONObject()
                        .put("Name", "New Config")
                        .put("Author", "")
                        .put("Version", "1.2.2"),
                    script = ""
                )
                loadForEditing(fresh, "New desktop-compatible config")
            }) {
                Text("New")
            }
            Button(
                enabled = working != null,
                onClick = {
                    val safeName = name
                        .replace(Regex("[^A-Za-z0-9._-]"), "_")
                        .ifBlank { "config" }
                        .take(80)
                    exportLauncher.launch("$safeName.lce")
                }
            ) {
                Text("Export .lce")
            }
        }

        Text(status)

        if (library.isNotEmpty()) {
            Text("Config library", fontWeight = FontWeight.Bold)
            Text(
                "Imported .lce documents kept through Android document permissions.",
                style = MaterialTheme.typography.bodySmall
            )
            library.forEach { item ->
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(item.name, fontWeight = FontWeight.SemiBold)
                        if (item.author.isNotBlank()) Text(item.author)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                runCatching {
                                    val uri = Uri.parse(item.uri)
                                    val config = store.read(uri)
                                    loadForEditing(config, "Loaded: ${config.name}")
                                    libraryStore.touch(
                                        item.copy(
                                            name = config.name,
                                            author = config.author
                                        )
                                    )
                                    refreshLibrary()
                                }.onFailure {
                                    status = "Open error: ${it.message}"
                                }
                            }) {
                                Text("Open")
                            }
                            TextButton(onClick = {
                                libraryStore.remove(item.id)
                                refreshLibrary()
                            }) {
                                Text("Forget")
                            }
                        }
                    }
                }
            }
        }

        working?.let { config ->
            HorizontalDivider()

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = author,
                onValueChange = { author = it },
                label = { Text("Author") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = version,
                onValueChange = { version = it },
                label = { Text("RuriLib version") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = additionalInfo,
                onValueChange = { additionalInfo = it },
                label = { Text("Additional information") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Checkbox(
                    checked = saveEmptyCaptures,
                    onCheckedChange = { saveEmptyCaptures = it }
                )
                Text("Save empty captures")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Checkbox(
                    checked = continueOnCustom,
                    onCheckedChange = { continueOnCustom = it }
                )
                Text("Continue after Custom status (desktop setting)")
            }

            Text("Request settings", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Checkbox(
                    checked = ignoreResponseErrors,
                    onCheckedChange = { ignoreResponseErrors = it }
                )
                Text("Ignore response errors")
            }
            OutlinedTextField(
                value = maxRedirects,
                onValueChange = { maxRedirects = it.filter(Char::isDigit).take(3) },
                label = { Text("Maximum redirects (0–100)") },
                modifier = Modifier.fillMaxWidth()
            )

            Text("Data settings", fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = allowedWordlist1,
                onValueChange = { allowedWordlist1 = it },
                label = { Text("Allowed Wordlist Type 1") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = allowedWordlist2,
                onValueChange = { allowedWordlist2 = it },
                label = { Text("Allowed Wordlist Type 2") },
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Checkbox(
                    checked = encodeData,
                    onCheckedChange = { encodeData = it }
                )
                Text("URL-encode data after slicing")
            }

            Text(
                "SuggestedBots, MaxCPM, proxy and Selenium fields are preserved from PC but are not used to enable automated behavior on Android.",
                style = MaterialTheme.typography.bodySmall
            )

            CustomInputsEditor(
                values = customInputs,
                onChange = { customInputs = it }
            )

            DataRulesEditor(
                values = dataRules,
                onChange = { dataRules = it }
            )

            OutlinedTextField(
                value = script,
                onValueChange = { script = it },
                label = { Text("[SCRIPT] LoliScript") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 12
            )

            val requiredPlugins = config.settings.optJSONArray("RequiredPlugins")
            if (requiredPlugins != null && requiredPlugins.length() > 0) {
                Text("Required desktop plugins", fontWeight = FontWeight.Bold)
                for (index in 0 until requiredPlugins.length()) {
                    Text(
                        "• ${requiredPlugins.optString(index)} (preserved; desktop plugin)",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Text("Preserved [SETTINGS] JSON", fontWeight = FontWeight.Bold)
            Text(config.settings.toString(2), style = MaterialTheme.typography.bodySmall)

            Text("Stacker / script inspection", fontWeight = FontWeight.Bold)
            Text(
                "Detected blocks: " +
                    if (scriptReport.blockCounts.isEmpty()) "(none)"
                    else scriptReport.blockCounts.entries.joinToString { "${it.key}=${it.value}" },
                style = MaterialTheme.typography.bodySmall
            )
            if (scriptReport.unknownLines.isNotEmpty()) {
                Text(
                    "Unrecognized lines: ${scriptReport.unknownLines.size} (preserved, not executed)",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (scriptReport.embeddedScriptPresent) {
                Text(
                    "Embedded script detected: preserved as text; execution disabled.",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Text("Compatibility report", fontWeight = FontWeight.Bold)
            Text("PC ↔ Android round-trip: preserved")
            Text("Imported LoliScript execution on Android: disabled")
            compatibility?.issues?.forEach { issue ->
                Text(
                    "• ${issue.keyword}: ${issue.message}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun SettingsScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { AppPreferences(context) }

    var hosts by remember { mutableStateOf(prefs.loadAuthorizedHosts().joinToString("\n")) }
    var timeout by remember { mutableStateOf(prefs.loadTimeoutMs().toString()) }
    var status by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Safety / authorized targets", fontWeight = FontWeight.Bold)
        Text(
            "Use an exact host (api.example.com). To authorize subdomains, use an explicit wildcard such as *.example.com.",
            style = MaterialTheme.typography.bodySmall
        )
        OutlinedTextField(
            hosts,
            { hosts = it },
            label = { Text("Authorized hosts — one per line") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 5
        )
        OutlinedTextField(
            timeout,
            { timeout = it.filter(Char::isDigit) },
            label = { Text("Timeout (ms)") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = {
            prefs.saveAuthorizedHosts(hosts.lines())
            prefs.saveTimeoutMs(timeout.toIntOrNull() ?: 15_000)
            status = "Saved"
        }) {
            Text("Save")
        }
        Text(status)
    }
}

@Composable
private fun PortPlaceholder(name: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(name, fontWeight = FontWeight.Bold)
        Text("Module retained in the mobile navigation. Android implementation is being ported in isolated components.")
    }
}

@Composable
private fun AboutScreen() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("OpenBullet CE Mobile", fontWeight = FontWeight.Bold)
        Text("Android port focused on local and explicitly authorized testing.")
        Text("Desktop config round-trip compatibility is a core requirement.")
    }
}
