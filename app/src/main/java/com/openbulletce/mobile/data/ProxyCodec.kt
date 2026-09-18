package com.openbulletce.mobile.data

object ProxyCodec {
    data class ParseResult(
        val proxy: ProxyRecord? = null,
        val error: String? = null
    )

    fun displayMasked(proxy: ProxyRecord): String {
        if (proxy.password.isBlank()) return proxy.raw
        val credentialTail = ":${proxy.username}:${proxy.password}"
        return proxy.raw.replace(credentialTail, ":${proxy.username}:••••")
    }

    fun parse(rawInput: String): ParseResult {
        val original = rawInput.trim()
        if (original.isBlank()) return ParseResult(error = "Proxy is empty")

        val firstHop = original.substringBefore("->").trim()
        var current = firstHop
        var type = MobileProxyType.HTTP

        if (current.startsWith("(")) {
            val end = current.indexOf(')')
            if (end <= 1) return ParseResult(error = "Invalid proxy type prefix")
            val typeText = current.substring(1, end).trim()
            type = when (typeText.lowercase()) {
                "http", "https" -> MobileProxyType.HTTP
                "socks4" -> MobileProxyType.SOCKS4
                "socks4a" -> MobileProxyType.SOCKS4A
                "socks5" -> MobileProxyType.SOCKS5
                else -> return ParseResult(error = "Unknown proxy type: " + typeText)
            }
            current = current.substring(end + 1).trim()
        }

        val parts = current.split(':')
        if (parts.size !in 2..4) return ParseResult(error = "Use host:port or host:port:user:pass")

        val host = parts[0].trim()
        val port = parts[1].toIntOrNull() ?: return ParseResult(error = "Invalid port")
        if (host.isBlank() || port !in 1..65535) return ParseResult(error = "Invalid host/port")
        if (parts.size == 3) return ParseResult(error = "Username requires a password")

        return ParseResult(
            proxy = ProxyRecord(
                raw = original,
                type = type,
                username = parts.getOrNull(2).orEmpty(),
                password = parts.getOrNull(3).orEmpty()
            )
        )
    }
}
