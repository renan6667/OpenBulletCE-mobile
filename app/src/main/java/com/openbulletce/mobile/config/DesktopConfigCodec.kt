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
        val originalText: String? = null,
        val originalSettingsText: String? = null,
        val originalScript: String? = null
    ) {
        val name: String
            get() = settings.optString("Name").ifBlank { "Unnamed config" }

        val author: String
            get() = settings.optString("Author")
    }

    fun decode(text: String): DesktopConfig {
        val parseText = text.removePrefix("\uFEFF")
        val settingsAt = parseText.indexOf(SETTINGS)
        require(settingsAt >= 0) { "Missing [SETTINGS] section" }

        val scriptAt = parseText.indexOf(
            SCRIPT,
            startIndex = settingsAt + SETTINGS.length
        )
        require(scriptAt > settingsAt) { "Missing or invalid [SCRIPT] section" }

        val settingsText = parseText
            .substring(settingsAt + SETTINGS.length, scriptAt)
            .trim('\r', '\n', ' ', '\t')

        val scriptText = parseText
            .substring(scriptAt + SCRIPT.length)
            .trimStart('\r', '\n')

        require(settingsText.isNotBlank()) { "Empty settings JSON" }

        return DesktopConfig(
            settings = JSONObject(settingsText),
            script = scriptText,
            originalText = text,
            originalSettingsText = settingsText,
            originalScript = scriptText
        )
    }

    fun encode(config: DesktopConfig): String {
        val untouched = config.originalText != null &&
            config.originalSettingsText != null &&
            config.originalScript != null &&
            config.script == config.originalScript &&
            settingsMatch(config.originalSettingsText, config.settings)

        if (untouched) {
            return config.originalText!!
        }

        return buildString {
            appendLine(SETTINGS)
            appendLine(config.settings.toString(2))
            appendLine()
            appendLine(SCRIPT)
            append(config.script)
        }
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

    private fun settingsMatch(originalSettingsText: String, current: JSONObject): Boolean =
        runCatching {
            JSONObject(originalSettingsText).toString() == current.toString()
        }.getOrDefault(false)
}
