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

        val normalized = allowedHosts
            .map { it.lowercase().trim().trimEnd('.') }
            .filter { it.isNotBlank() }

        val allowed = normalized.any { candidate ->
            host == candidate || host.endsWith(".$candidate")
        }

        return if (allowed) {
            Decision(true)
        } else {
            Decision(false, "Host '$host' is not in the authorized-host list")
        }
    }
}
