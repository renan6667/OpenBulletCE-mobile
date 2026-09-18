package com.openbulletce.mobile.config

data class SafeRequestPreset(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String = ""
)

object SafeRequestPresetParser {
    private val requestRegex = Regex(
        """(?i)(?:^|\s)REQUEST\s+(GET|HEAD|POST|PUT|PATCH|DELETE)\s+"((?:\\.|[^"])*)"""
    )
    private val literalRegex = Regex(
        """(?i)^\s*(CONTENT|CONTENTTYPE|HEADER|COOKIE)\s+"((?:\\.|[^"])*)""""
    )
    private val unsupportedModeRegex = Regex(
        """(?i)\b(BASICAUTH|MULTIPART|RAWDATA|RAW)\b"""
    )

    fun fromConfig(config: DesktopConfigCodec.DesktopConfig): Result<SafeRequestPreset> =
        runCatching { fromScript(config.script) }

    fun fromScript(script: String): SafeRequestPreset {
        val lines = script.lines()
        val requestIndex = lines.indexOfFirst { requestRegex.containsMatchIn(it) }
        require(requestIndex >= 0) { "No REQUEST block found in this config" }

        val firstLine = lines[requestIndex]
        require(!unsupportedModeRegex.containsMatchIn(firstLine)) {
            "This REQUEST mode is preserved but not supported by the safe mobile preset loader"
        }

        val match = requestRegex.find(firstLine)
            ?: error("Unable to parse REQUEST line")
        val method = match.groupValues[1].uppercase()
        val url = unescapeLiteral(match.groupValues[2])

        require(isStatic(url)) {
            "The first REQUEST URL contains variables/functions. It cannot be loaded as a static mobile preset."
        }

        val headers = linkedMapOf<String, String>()
        val cookies = mutableListOf<String>()
        var body = ""
        var contentType = ""

        for (index in requestIndex + 1 until lines.size) {
            val line = lines[index]
            if (line.isBlank()) continue
            if (!line.first().isWhitespace()) break

            if (unsupportedModeRegex.containsMatchIn(line)) {
                error("This REQUEST mode is preserved but not supported by the safe mobile preset loader")
            }

            val token = literalRegex.find(line) ?: continue
            val key = token.groupValues[1].uppercase()
            val value = unescapeLiteral(token.groupValues[2])
            require(isStatic(value)) {
                "The REQUEST contains dynamic values. It cannot be loaded as a static mobile preset."
            }

            when (key) {
                "CONTENT" -> body = value
                "CONTENTTYPE" -> contentType = value
                "HEADER" -> {
                    val split = value.indexOf(':')
                    if (split > 0) {
                        headers[value.substring(0, split).trim()] =
                            value.substring(split + 1).trim()
                    }
                }
                "COOKIE" -> cookies += value.replaceFirst(":", "=")
            }
        }

        if (contentType.isNotBlank()) {
            headers.putIfAbsent("Content-Type", contentType)
        }
        if (cookies.isNotEmpty()) {
            headers["Cookie"] = cookies.joinToString("; ")
        }

        return SafeRequestPreset(
            method = method,
            url = url,
            headers = headers,
            body = body
        )
    }

    private fun isStatic(value: String): Boolean =
        !value.contains('<') &&
            !value.contains('>') &&
            !value.contains("{{") &&
            !value.contains("}}")

    private fun unescapeLiteral(value: String): String =
        value
            .replace("\\\"", "\"")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\\", "\\")
}
