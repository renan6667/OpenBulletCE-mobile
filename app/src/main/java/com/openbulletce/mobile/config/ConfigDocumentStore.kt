package com.openbulletce.mobile.config

import android.content.ContentResolver
import android.net.Uri

class ConfigDocumentStore(
    private val resolver: ContentResolver
) {
    fun read(uri: Uri): DesktopConfigCodec.DesktopConfig {
        val text = resolver.openInputStream(uri)
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("Unable to open config")
        return DesktopConfigCodec.decode(text)
    }

    fun write(uri: Uri, config: DesktopConfigCodec.DesktopConfig) {
        resolver.openOutputStream(uri, "wt")
            ?.bufferedWriter()
            ?.use { it.write(DesktopConfigCodec.encode(config)) }
            ?: error("Unable to save config")
    }
}
