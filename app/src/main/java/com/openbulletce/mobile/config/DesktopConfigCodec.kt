package com.openbulletce.mobile.config

import org.json.JSONObject

/**
 * Codec for the legacy OpenBullet Cookie Edition desktop config container.
 *
 * Desktop format:
 * [SETTINGS]
 * { JSON }
 *
 * [SCRIPT]
 * <LoliScript>
 *
 * Unknown JSON fields are intentionally preserved so a PC -> Android -> PC
 * round-trip does not silently destroy settings that Android does not yet use.
 */
object DesktopConfigCodec {
    private const val SETTINGS = "[SETTINGS]"
    private const val SCRIPT = "[SCRIPT]"

    data class DesktopConfig(
        val settings: JSONObject,
        val script: String,
        val originalText: String? = null
    ) {
        val name: String
            get() = settings.optString("Name").ifBlank { "Unnamed config" }

        val author: String
            get() = settings.optString("Author")
    }

    fun decode(text: String): DesktopConfig {
        val normalized = text.replace("\r\n", "\n")
        val settingsAt = normalized.indexOf(SETTINGS)
        val scriptAt = normalized.indexOf(SCRIPT)

        require(settingsAt >= 0) { "Missing [SETTINGS] section" }
        require(scriptAt > settingsAt) { "Missing or invalid [SCRIPT] section" }

        val settingsText = normalized
            .substring(settingsAt + SETTINGS.length, scriptAt)
            .trim()

        val scriptText = normalized
            .substring(scriptAt + SCRIPT.length)
            .trimStart('\n')

        require(settingsText.isNotBlank()) { "Empty settings JSON" }

        return DesktopConfig(
            settings = JSONObject(settingsText),
            script = scriptText,
            originalText = text
        )
    }

    fun encode(config: DesktopConfig): String = buildString {
        appendLine(SETTINGS)
        appendLine(config.settings.toString())
        appendLine()
        appendLine(SCRIPT)
        append(config.script)
    }

    /**
     * Non-destructive edit: only requested keys are changed.
     * All unknown desktop settings stay in the JSON object.
     */
    fun withSettings(
        config: DesktopConfig,
        changes: Map<String, Any?>
    ): DesktopConfig {
        val copy = JSONObject(config.settings.toString())
        for ((key, value) in changes) {
            if (value == null) copy.remove(key) else copy.put(key, value)
        }
        return config.copy(settings = copy)
    }
}
