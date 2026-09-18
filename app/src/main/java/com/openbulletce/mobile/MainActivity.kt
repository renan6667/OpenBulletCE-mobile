package com.openbulletce.mobile

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
import com.openbulletce.mobile.config.ConfigDocumentStore
import com.openbulletce.mobile.config.DesktopConfigCodec
import com.openbulletce.mobile.data.AppPreferences
import com.openbulletce.mobile.network.AuthorizedHttpClient
import com.openbulletce.mobile.network.SimpleRequest
import com.openbulletce.mobile.security.AuthorizedTargetPolicy
import kotlinx.coroutines.launch

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
    var section by remember { mutableStateOf(Section.RUNNER) }

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
                        Section.CONFIGS -> ConfigScreen()
                        Section.SETTINGS -> SettingsScreen()
                        Section.ABOUT -> AboutScreen()
                        else -> PortPlaceholder(section.label)
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
    val scope = rememberCoroutineScope()

    var method by remember { mutableStateOf("GET") }
    var url by remember { mutableStateOf("http://127.0.0.1:8080/") }
    var headersText by remember { mutableStateOf("Accept: */*") }
    var body by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("Ready") }
    var running by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Authorized request runner", fontWeight = FontWeight.Bold)
        Text(
            "Requests only run when the host is present in Settings > Authorized hosts.",
            style = MaterialTheme.typography.bodySmall
        )
        OutlinedTextField(method, { method = it.uppercase() }, label = { Text("Method") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(url, { url = it }, label = { Text("URL") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            headersText,
            { headersText = it },
            label = { Text("Headers — one Name: value per line") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )
        OutlinedTextField(body, { body = it }, label = { Text("Body") }, modifier = Modifier.fillMaxWidth(), minLines = 3)

        Button(
            enabled = !running,
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
                    val result = client.execute(SimpleRequest(method, url, headers, body))
                    output = result.fold(
                        onSuccess = { "HTTP ${it.statusCode}\n\n${it.body.take(12_000)}" },
                        onFailure = { "ERROR: ${it.message}" }
                    )
                    running = false
                }
            }
        ) {
            Text(if (running) "Running" else "START")
        }

        HorizontalDivider()
        Text(output)
    }
}

@Composable
private fun ConfigScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember { ConfigDocumentStore(context.contentResolver) }

    var loaded by remember { mutableStateOf<DesktopConfigCodec.DesktopConfig?>(null) }
    var status by remember { mutableStateOf("No desktop config loaded") }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching { store.read(uri) }
                .onSuccess {
                    loaded = it
                    status = "Loaded: ${it.name}"
                }
                .onFailure { status = "Import error: ${it.message}" }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        val config = loaded
        if (uri != null && config != null) {
            runCatching { store.write(uri, config) }
                .onSuccess { status = "Exported in desktop-compatible format" }
                .onFailure { status = "Export error: ${it.message}" }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("PC config compatibility", fontWeight = FontWeight.Bold)
        Text(
            "Imports and exports the desktop [SETTINGS] + [SCRIPT] container without dropping unknown settings.",
            style = MaterialTheme.typography.bodySmall
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                Text("Import PC config")
            }
            Button(
                enabled = loaded != null,
                onClick = {
                    val name = loaded?.name
                        ?.replace(Regex("[^A-Za-z0-9._-]"), "_")
                        ?.ifBlank { "config" }
                        ?: "config"
                    exportLauncher.launch("$name.loli")
                }
            ) {
                Text("Export")
            }
        }

        Text(status)

        loaded?.let { config ->
            HorizontalDivider()
            Text("Name: ${config.name}")
            if (config.author.isNotBlank()) Text("Author: ${config.author}")
            Text("Settings JSON", fontWeight = FontWeight.Bold)
            Text(config.settings.toString(2))
            Text("LoliScript", fontWeight = FontWeight.Bold)
            Text(config.script.ifBlank { "(empty)" })
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
