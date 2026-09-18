package com.openbulletce.mobile.data

object ProxyCodec {
    data class ParseResult(
        val proxy: ProxyRecord? = null,
        val error: String? = null
    )

    data class Endpoint(
        val host: String,
        val port: Int
    )

    fun displayMasked(proxy: ProxyRecord): String {
        if (proxy.password.isBlank()) return proxy.raw
        val credentialTail = ":${proxy.username}:${proxy.password}"
        return proxy.raw.replace(
            credentialTail,
            ":${proxy.username}:••••"
        )
    }

    fun executionIssue(proxy: ProxyRecord): String? {
        if (proxy.status != MobileProxyStatus.AVAILABLE) {
            return when (proxy.status) {
                MobileProxyStatus.BUSY -> "Proxy is busy."
                MobileProxyStatus.BAD -> proxy.banReason.ifBlank { "Proxy is marked BAD." }
                MobileProxyStatus.BANNED -> proxy.banReason.ifBlank { "Proxy is BANNED." }
                MobileProxyStatus.AVAILABLE -> null
            }
        }
        return transportIssue(proxy)
    }

    fun transportIssue(proxy: ProxyRecord): String? {
        if ("->" in proxy.raw) {
            return "Proxy chains are stored for compatibility but are not executable on Android yet."
        }
        if (proxy.type == MobileProxyType.SOCKS4 || proxy.type == MobileProxyType.SOCKS4A) {
            return "SOCKS4/SOCKS4A are stored for compatibility. Android execution currently supports HTTP and SOCKS5."
        }
        if (proxy.type == MobileProxyType.SOCKS5 && proxy.username.isNotBlank()) {
            return "Authenticated SOCKS5 is stored, but authenticated SOCKS execution is not supported yet."
        }
        return null
    }

    fun endpoint(proxy: ProxyRecord): Result<Endpoint> = runCatching {
        executionIssue(proxy)?.let { error(it) }

        var current = proxy.raw.substringBefore("->").trim()
        if (current.startsWith("(")) {
            val end = current.indexOf(')')
            require(end > 1) { "Invalid proxy type prefix" }
            current = current.substring(end + 1).trim()
        }

        val parts = current.split(':')
        require(parts.size in 2..4) { "Invalid proxy format" }
        val host = parts[0].trim()
        val port = parts[1].toIntOrNull() ?: error("Invalid proxy port")
        require(host.isNotBlank()) { "Proxy host is empty" }
        require(port in 1..65535) { "Proxy port is out of range" }
        Endpoint(host, port)
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
