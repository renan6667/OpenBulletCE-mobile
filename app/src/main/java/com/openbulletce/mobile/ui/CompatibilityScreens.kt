package com.openbulletce.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ToolsScreen() {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Tools", fontWeight = FontWeight.Bold)
        Text(
            "The desktop Tools section contains List Generator, Selenium Tools and Database maintenance.",
            style = MaterialTheme.typography.bodySmall
        )

        CompatibilityCard(
            title = "List Generator",
            status = "Compatibility placeholder",
            detail = "The desktop generator is identified in the port matrix. Android does not add automated credential-generation behavior."
        )
        CompatibilityCard(
            title = "Selenium Tools",
            status = "Desktop-only",
            detail = "Selenium/WebDriver tooling is not a direct Android equivalent. Related desktop config data remains preserved."
        )
        CompatibilityCard(
            title = "Database",
            status = "Adapted",
            detail = "The current Android build uses app-local preferences/document URIs instead of desktop LiteDB, so LiteDB Shrink has no Android operation to perform."
        )
    }
}

@Composable
fun PluginsScreen() {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Plugins", fontWeight = FontWeight.Bold)
        Text(
            "Cookie Edition desktop plugins are .NET assemblies loaded through its PluginFramework. Android cannot load those DLLs directly.",
            style = MaterialTheme.typography.bodySmall
        )
        CompatibilityCard(
            title = "PC plugin metadata",
            status = "Preserved",
            detail = "RequiredPlugins fields inside imported .lce configs are retained during PC ↔ Android round trips."
        )
        CompatibilityCard(
            title = ".NET plugin execution",
            status = "Desktop-only",
            detail = "DLL execution is intentionally not emulated. A future Android plugin API would need a separate, Android-native contract."
        )
    }
}

@Composable
private fun CompatibilityCard(
    title: String,
    status: String,
    detail: String
) {
    Card {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(status, style = MaterialTheme.typography.labelMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
    }
}
