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
import com.openbulletce.mobile.data.MobileProxyStatus
import com.openbulletce.mobile.data.ProxyCodec
import com.openbulletce.mobile.data.RunnerDraft
import com.openbulletce.mobile.network.AuthorizedHttpClient
import com.openbulletce.mobile.network.SimpleRequest
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

    val initial = remember { prefs.loadRunnerDraft() }
    var configId by remember { mutableStateOf(initial.configId) }
    var wordlistId by remember { mutableStateOf(initial.wordlistId) }
    var selectedProxyId by remember { mutableStateOf(initial.selectedProxyId) }
    var proxyMode by remember { mutableStateOf(initial.proxyMode) }
    var botsAmount by remember { mutableStateOf(initial.botsAmount) }
    var startingPoint by remember { mutableStateOf(initial.startingPoint.toString()) }

    var configMenu by remember { mutableStateOf(false) }
    var wordlistMenu by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Idle") }
    var running by remember { mutableStateOf(false) }
    var progressCount by remember { mutableStateOf(0) }
    var lastResponse by remember { mutableStateOf("") }

    val configs = libraryStore.configs()
    val wordlists = managerStore.wordlists()
    val proxies = managerStore.proxies()
    val hits = managerStore.hits()

    val selectedConfigRecord = configs.firstOrNull { it.id == configId }
    val selectedWordlist = wordlists.firstOrNull { it.id == wordlistId }
    val selectedProxy = proxies.firstOrNull { it.id == selectedProxyId }

    val listSize = selectedWordlist?.totalLines ?: 0
    val startValue = startingPoint.toIntOrNull()?.coerceAtLeast(1) ?: 1
    val progressPercent = if (listSize <= 0) 0
    else ((progressCount.coerceAtMost(listSize) * 100f) / listSize).toInt()

    val configHits = hits.count {
        selectedConfigRecord != null && it.configName == selectedConfigRecord.name
    }

    LaunchedEffect(
        configId,
        wordlistId,
        selectedProxyId,
        proxyMode,
        botsAmount,
        startingPoint
    ) {
        delay(200)
        prefs.saveRunnerDraft(
            prefs.loadRunnerDraft().copy(
                configId = configId,
                wordlistId = wordlistId,
                selectedProxyId = selectedProxyId,
                proxyMode = proxyMode,
                botsAmount = botsAmount,
                startingPoint = startValue
            )
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Runner", fontWeight = FontWeight.Bold)

        Card(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Session", fontWeight = FontWeight.SemiBold)

                Text("Config")
                Box {
                    OutlinedButton(onClick = { configMenu = true }) {
                        Text(selectedConfigRecord?.name ?: "Select Config")
                    }
                    DropdownMenu(
                        expanded = configMenu,
                        onDismissRequest = { configMenu = false }
                    ) {
                        configs.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item.name) },
                                onClick = {
                                    configId = item.id
                                    prefs.saveActiveConfigId(item.id)
                                    val config = libraryStore.loadSnapshot(item)
                                    val suggested = config?.settings
                                        ?.optInt("SuggestedBots", botsAmount)
                                        ?.coerceIn(1, 200)
                                    if (suggested != null) botsAmount = suggested
                                    configMenu = false
                                    status = "Config loaded"
                                }
                            )
                        }
                    }
                }

                Text("Wordlist")
                Box {
                    OutlinedButton(onClick = { wordlistMenu = true }) {
                        Text(selectedWordlist?.name ?: "Select Wordlist")
                    }
                    DropdownMenu(
                        expanded = wordlistMenu,
                        onDismissRequest = { wordlistMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("None") },
                            onClick = {
                                wordlistId = ""
                                wordlistMenu = false
                            }
                        )
                        wordlists.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item.name) },
                                onClick = {
                                    wordlistId = item.id
                                    wordlistMenu = false
                                    status = "Wordlist selected"
                                }
                            )
                        }
                    }
                }

                Text("Bots: $botsAmount")
                Slider(
                    value = botsAmount.toFloat(),
                    onValueChange = { botsAmount = it.toInt().coerceIn(1, 200) },
                    valueRange = 1f..200f,
                    steps = 198
                )

                Text("Proxies")
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("DEFAULT" to "DEF", "ON" to "ON", "OFF" to "OFF")
                        .forEach { (value, label) ->
                            FilterChip(
                                selected = proxyMode == value,
                                onClick = { proxyMode = value },
                                label = { Text(label) }
                            )
                        }
                }

                val useProxy = selectedConfigRecord?.let { record ->
                    val cfg = libraryStore.loadSnapshot(record)
                    val needs = cfg?.settings?.optBoolean("NeedsProxies", false) ?: false
                    proxyMode == "ON" || (proxyMode == "DEFAULT" && needs)
                } ?: (proxyMode == "ON")

                if (useProxy) {
                    Text(
                        selectedProxy?.let {
                            "Proxy: " + ProxyCodec.displayMasked(it)
                        } ?: "Proxy: none selected — choose one in Proxy Manager",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                OutlinedTextField(
                    value = startingPoint,
                    onValueChange = {
                        startingPoint = it.filter(Char::isDigit).take(9)
                    },
                    label = { Text("Start") },
                    modifier = Modifier.fillMaxWidth()
                )

                LinearProgressIndicator(
                    progress = { progressPercent / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Prog: $progressCount / $listSize ($progressPercent%)",
                    style = MaterialTheme.typography.bodySmall
                )

                Button(
                    enabled = !running && selectedConfigRecord != null,
                    onClick = {
                        val record = selectedConfigRecord ?: return@Button
                        val config = libraryStore.loadSnapshot(record)
                            ?: runCatching {
                                if (record.uri.isBlank()) null
                                else documentStore.read(Uri.parse(record.uri))
                            }.getOrNull()

                        if (config == null) {
                            status = "Config could not be opened"
                            return@Button
                        }

                        val presetResult = SafeRequestPresetParser.fromConfig(config)
                        if (presetResult.isFailure) {
                            status = "This config uses blocks that are not ported yet: " +
                                (presetResult.exceptionOrNull()?.message ?: "unsupported script")
                            return@Button
                        }

                        val needsProxy = config.settings.optBoolean("NeedsProxies", false)
                        val useProxy = proxyMode == "ON" ||
                            (proxyMode == "DEFAULT" && needsProxy)

                        if (useProxy && selectedProxy == null) {
                            status = "Select a proxy in Proxy Manager first"
                            return@Button
                        }
                        if (useProxy && selectedProxy != null) {
                            val issue = ProxyCodec.executionIssue(selectedProxy)
                            if (issue != null) {
                                status = issue
                                return@Button
                            }

                            val maxProxyUses = config.settings
                                .optInt("MaxProxyUses", 0)
                                .coerceAtLeast(0)
                            if (maxProxyUses > 0 && selectedProxy.uses >= maxProxyUses) {
                                status = "MaxProxyUses reached for selected proxy"
                                return@Button
                            }
                        }

                        val preset = presetResult.getOrThrow()
                        running = true
                        status = "Running"
                        progressCount = if (listSize > 0) {
                            startValue.coerceAtMost(listSize)
                        } else {
                            0
                        }

                        scope.launch {
                            if (useProxy && selectedProxy != null) {
                                val busy = managerStore.markProxyBusy(selectedProxy.id)
                                if (busy?.status != MobileProxyStatus.BUSY) {
                                    status = "Selected proxy is no longer AVAILABLE"
                                    running = false
                                    return@launch
                                }
                            }

                            val client = AuthorizedHttpClient(prefs.loadTimeoutMs())
                            val result = client.execute(
                                SimpleRequest(
                                    method = preset.method,
                                    url = preset.url,
                                    headers = preset.headers,
                                    body = preset.body
                                ),
                                if (useProxy) selectedProxy else null
                            )

                            result
                                .onSuccess { response ->
                                    if (useProxy && selectedProxy != null) {
                                        managerStore.finishProxyUse(selectedProxy.id)
                                    }

                                    lastResponse = response.body.take(12_000)
                                    status = "Completed — HTTP ${response.statusCode}"
                                    if (listSize > 0) {
                                        progressCount = (progressCount + 1).coerceAtMost(listSize)
                                    }
                                    managerStore.putHit(
                                        HitRecord(
                                            data = selectedWordlist?.name.orEmpty(),
                                            captured = response.body.take(2_000),
                                            proxy = if (useProxy) {
                                                selectedProxy?.let(ProxyCodec::displayMasked).orEmpty()
                                            } else {
                                                ""
                                            },
                                            type = "CONFIG_REQUEST",
                                            configName = record.name,
                                            wordlistName = selectedWordlist?.name.orEmpty()
                                        )
                                    )
                                }
                                .onFailure { error ->
                                    lastResponse = ""
                                    val retries = prefs.incrementRunnerRetryCount()

                                    if (useProxy && selectedProxy != null) {
                                        managerStore.finishProxyUse(selectedProxy.id)
                                        managerStore.markProxyBad(
                                            selectedProxy.id,
                                            error.message ?: "Runner request error"
                                        )
                                        selectedProxyId = ""
                                        prefs.saveRunnerDraft(
                                            prefs.loadRunnerDraft().copy(
                                                selectedProxyId = ""
                                            )
                                        )
                                        status = "ERROR — proxy BAD • Retries: $retries"
                                    } else {
                                        status = "ERROR • Retries: $retries — ${error.message}"
                                    }
                                }

                            running = false
                        }
                    }
                ) {
                    Text(if (running) "RUNNING" else "START")
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                val proxyStats = managerStore.proxies()
                Text("STATUS: $status", fontWeight = FontWeight.SemiBold)
                Text("Hits: $configHits")
                Text("Custom: 0")
                Text("ToCheck: 0")
                Text("Retries: ${prefs.loadRunnerRetryCount()}")
                Text("Alive: ${proxyStats.count {
                    it.status == MobileProxyStatus.AVAILABLE ||
                        it.status == MobileProxyStatus.BUSY
                }}")
                Text("Banned: ${proxyStats.count {
                    it.status == MobileProxyStatus.BANNED
                }}")
                Text("Bad: ${proxyStats.count {
                    it.status == MobileProxyStatus.BAD
                }}")
                Text("CPM: 0")
            }
        }

        if (lastResponse.isNotBlank()) {
            Text("Last response", fontWeight = FontWeight.SemiBold)
            Text(
                lastResponse,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun ConfigScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember { ConfigDocumentStore(context.contentResolver) }
    val libraryStore = remember { ConfigLibraryStore(context) }
    val prefs = remember { AppPreferences(context) }

    var library by remember { mutableStateOf(libraryStore.configs()) }
    var loaded by remember { mutableStateOf<DesktopConfigCodec.DesktopConfig?>(null) }
    var currentRecordId by remember { mutableStateOf<String?>(null) }
    var activeConfigId by remember { mutableStateOf(prefs.loadActiveConfigId()) }
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

    fun loadForEditing(
        config: DesktopConfigCodec.DesktopConfig,
        message: String,
        recordId: String? = null
    ) {
        loaded = config
        currentRecordId = recordId
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

    fun rememberImportedConfig(
        uri: Uri,
        config: DesktopConfigCodec.DesktopConfig
    ): ConfigLibraryRecord {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        val record = libraryStore.upsertForUri(
            name = config.name,
            author = config.author,
            uri = uri.toString()
        )
        libraryStore.saveSnapshot(record, config)
        prefs.saveActiveConfigId(record.id)
        activeConfigId = record.id
        refreshLibrary()
        return record
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
                    val record = rememberImportedConfig(uri, it)
                    loadForEditing(
                        it,
                        "Imported, saved locally and selected for Runner: ${it.name}",
                        record.id
                    )
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

    LaunchedEffect(
        currentRecordId,
        name,
        author,
        version,
        additionalInfo,
        saveEmptyCaptures,
        continueOnCustom,
        ignoreResponseErrors,
        maxRedirects,
        allowedWordlist1,
        allowedWordlist2,
        encodeData,
        customInputs,
        dataRules,
        script
    ) {
        val id = currentRecordId ?: return@LaunchedEffect
        delay(350)
        val record = libraryStore.find(id) ?: return@LaunchedEffect
        val snapshot = workingConfig() ?: return@LaunchedEffect

        libraryStore.saveSnapshot(record, snapshot)

        if (record.name != snapshot.name || record.author != snapshot.author) {
            libraryStore.put(
                record.copy(
                    name = snapshot.name,
                    author = snapshot.author
                )
            )
            refreshLibrary()
        }
    }

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
                val record = ConfigLibraryRecord(
                    name = fresh.name,
                    author = fresh.author,
                    uri = ""
                )
                libraryStore.put(record)
                libraryStore.saveSnapshot(record, fresh)
                loadForEditing(
                    fresh,
                    "New config saved locally",
                    record.id
                )
                refreshLibrary()
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
                "Configs are copied into app-local storage, so they can be reopened even if the original document provider later changes.",
                style = MaterialTheme.typography.bodySmall
            )
            library.forEach { item ->
                val isActive = item.id == activeConfigId
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(item.name, fontWeight = FontWeight.SemiBold)
                        if (item.author.isNotBlank()) Text(item.author)
                        if (isActive) {
                            Text(
                                "Active in Runner",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }

                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextButton(onClick = {
                                runCatching {
                                    val config = libraryStore.loadSnapshot(item)
                                        ?: if (item.uri.isBlank()) {
                                            error("No local snapshot or source URI")
                                        } else {
                                            store.read(Uri.parse(item.uri)).also {
                                                libraryStore.saveSnapshot(item, it)
                                            }
                                        }

                                    loadForEditing(
                                        config,
                                        "Loaded: ${config.name}",
                                        item.id
                                    )
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

                            Button(onClick = {
                                prefs.saveActiveConfigId(item.id)
                                activeConfigId = item.id
                                status = "${item.name} selected for Runner"
                            }) {
                                Text(if (isActive) "Active" else "Use in Runner")
                            }

                            TextButton(onClick = {
                                libraryStore.remove(item.id)
                                if (activeConfigId == item.id) {
                                    prefs.saveActiveConfigId("")
                                    activeConfigId = ""
                                }
                                if (currentRecordId == item.id) {
                                    currentRecordId = null
                                    loaded = null
                                }
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
            Text("Full LoliScript execution: disabled")
            Text(
                "Runner can load the first static REQUEST from the active config; unsupported/dynamic blocks remain preserved.",
                style = MaterialTheme.typography.bodySmall
            )
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

    var timeout by remember { mutableStateOf(prefs.loadTimeoutMs().toString()) }
    var status by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Settings", fontWeight = FontWeight.Bold)
        Text(
            "Runner session, configs, wordlists, proxies, cookies and Hits are persisted automatically.",
            style = MaterialTheme.typography.bodySmall
        )

        OutlinedTextField(
            timeout,
            { timeout = it.filter(Char::isDigit) },
            label = { Text("Request timeout (ms)") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(onClick = {
            prefs.saveTimeoutMs(timeout.toIntOrNull() ?: 15_000)
            status = "Saved"
        }) {
            Text("Save")
        }

        if (status.isNotBlank()) Text(status)
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
