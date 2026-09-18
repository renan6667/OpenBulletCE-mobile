package com.openbulletce.mobile.security

import java.net.URI

class AuthorizedTargetPolicy(
    private val allowedHosts: Set<String>
) {
    data class Decision(
        val allowed: Boolean,
        val reason: String? = null
    )

    fun check(rawUrl: String): Decision {
        val uri = try {
            URI(rawUrl)
        } catch (_: Exception) {
            return Decision(false, "Invalid URL")
        }

        if (uri.scheme !in setOf("http", "https")) {
            return Decision(false, "Only HTTP/HTTPS are supported")
        }

        val host = uri.host?.lowercase()?.trimEnd('.')
            ?: return Decision(false, "URL has no valid host")

        val rules = allowedHosts
            .mapNotNull(::normalizeRule)

        val allowed = rules.any { rule ->
            if (rule.wildcard) {
                host.endsWith(".${rule.host}") && host != rule.host
            } else {
                host == rule.host
            }
        }

        return if (allowed) {
            Decision(true)
        } else {
            Decision(
                false,
                "Host '$host' is not explicitly authorized. Add the exact host or an explicit *.domain rule."
            )
        }
    }

    private fun normalizeRule(raw: String): HostRule? {
        val text = raw.lowercase().trim().trimEnd('.')
        if (text.isBlank()) return null

        val wildcard = text.startsWith("*.")
        val host = if (wildcard) text.removePrefix("*.") else text

        if (host.isBlank() || host.contains('/') || host.contains(':') || host.contains('*')) {
            return null
        }

        // Avoid an accidental wildcard such as *.com / *.net.
        if (wildcard && !host.contains('.')) {
            return null
        }

        return HostRule(host = host, wildcard = wildcard)
    }

    private data class HostRule(
        val host: String,
        val wildcard: Boolean
    )
}
