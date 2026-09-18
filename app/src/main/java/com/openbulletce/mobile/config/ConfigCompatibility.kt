package com.openbulletce.mobile.config

data class ConfigCompatibilityIssue(
    val keyword: String,
    val message: String
)

data class ConfigCompatibilityReport(
    val roundTripPreserved: Boolean,
    val executionSupported: Boolean,
    val issues: List<ConfigCompatibilityIssue>
)

object ConfigCompatibility {
    private val preservedDesktopOnly = linkedMapOf(
        "NAVIGATE" to "Desktop Selenium/browser automation is preserved but not executed on Android.",
        "BROWSERACTION" to "Desktop Selenium/browser automation is preserved but not executed on Android.",
        "ELEMENTACTION" to "Desktop Selenium/browser automation is preserved but not executed on Android.",
        "EXECUTEJS" to "Desktop browser JavaScript execution is preserved but not executed on Android.",
        "MOUSEACTION" to "Desktop mouse automation has no Android runtime equivalent.",
        "CAPTCHA" to "Captcha-solving blocks are preserved but not executed by the Android port.",
        "RECAPTCHA" to "Captcha-solving blocks are preserved but not executed by the Android port.",
        "BYPASSCF" to "Cloudflare-bypass blocks are preserved but intentionally not executed on Android.",
        "BEGIN SCRIPT" to "Embedded arbitrary scripts are preserved as text and never executed on Android."
    )

    fun analyze(config: DesktopConfigCodec.DesktopConfig): ConfigCompatibilityReport {
        val upper = config.script.uppercase()
        val issues = preservedDesktopOnly.mapNotNull { (keyword, message) ->
            if (upper.contains(keyword)) ConfigCompatibilityIssue(keyword, message) else null
        }

        return ConfigCompatibilityReport(
            roundTripPreserved = true,
            executionSupported = false,
            issues = issues
        )
    }
}
