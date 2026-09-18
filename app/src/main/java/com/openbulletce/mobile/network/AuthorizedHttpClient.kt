package com.openbulletce.mobile.network

import com.openbulletce.mobile.security.AuthorizedTargetPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Reader
import java.net.HttpURLConnection
import java.net.URL

data class SimpleRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String = ""
)

data class SimpleResponse(
    val statusCode: Int,
    val body: String,
    val headers: Map<String, List<String>>,
    val truncated: Boolean = false
)

class AuthorizedHttpClient(
    private val policy: AuthorizedTargetPolicy,
    private val timeoutMs: Int
) {
    suspend fun execute(request: SimpleRequest): Result<SimpleResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val decision = policy.check(request.url)
            require(decision.allowed) { decision.reason ?: "Target is not authorized" }

            val method = request.method.uppercase()
            require(method in setOf("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE")) {
                "Unsupported HTTP method"
            }

            val requestBytes = request.body.toByteArray(Charsets.UTF_8)
            require(requestBytes.size <= MAX_REQUEST_BODY_BYTES) {
                "Request body is larger than the mobile safety limit"
            }

            val connection = URL(request.url).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = method
                connection.connectTimeout = timeoutMs
                connection.readTimeout = timeoutMs
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("User-Agent", "OpenBulletCE-Mobile/0.2")

                request.headers.forEach { (name, value) ->
                    require('\n' !in name && '\r' !in name && '\n' !in value && '\r' !in value) {
                        "Header names and values cannot contain line breaks"
                    }
                    if (name.isNotBlank()) connection.setRequestProperty(name, value)
                }

                if (method in setOf("POST", "PUT", "PATCH") && requestBytes.isNotEmpty()) {
                    connection.doOutput = true
                    connection.outputStream.use { out ->
                        out.write(requestBytes)
                    }
                }

                val code = connection.responseCode
                val stream = if (code >= 400) connection.errorStream else connection.inputStream
                val limited = stream?.bufferedReader()?.use(::readLimited)
                    ?: LimitedText("", false)

                SimpleResponse(
                    statusCode = code,
                    body = limited.text,
                    headers = connection.headerFields.filterKeys { it != null },
                    truncated = limited.truncated
                )
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun readLimited(reader: Reader): LimitedText {
        val output = StringBuilder()
        val buffer = CharArray(8_192)
        var truncated = false

        while (output.length < MAX_RESPONSE_CHARS) {
            val remaining = MAX_RESPONSE_CHARS - output.length
            val count = reader.read(buffer, 0, minOf(buffer.size, remaining))
            if (count < 0) break
            output.append(buffer, 0, count)
        }

        if (output.length >= MAX_RESPONSE_CHARS) {
            truncated = reader.read() >= 0
        }

        return LimitedText(output.toString(), truncated)
    }

    private data class LimitedText(
        val text: String,
        val truncated: Boolean
    )

    private companion object {
        const val MAX_REQUEST_BODY_BYTES = 1_048_576
        const val MAX_RESPONSE_CHARS = 1_000_000
    }
}
