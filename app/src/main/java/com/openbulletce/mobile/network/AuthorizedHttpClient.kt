package com.openbulletce.mobile.network

import com.openbulletce.mobile.security.AuthorizedTargetPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    val headers: Map<String, List<String>>
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

            val connection = URL(request.url).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = method
                connection.connectTimeout = timeoutMs
                connection.readTimeout = timeoutMs
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("User-Agent", "OpenBulletCE-Mobile/0.2")

                request.headers.forEach { (name, value) ->
                    if (name.isNotBlank()) connection.setRequestProperty(name, value)
                }

                if (method in setOf("POST", "PUT", "PATCH") && request.body.isNotEmpty()) {
                    connection.doOutput = true
                    connection.outputStream.use { out ->
                        out.write(request.body.toByteArray(Charsets.UTF_8))
                    }
                }

                val code = connection.responseCode
                val stream = if (code >= 400) connection.errorStream else connection.inputStream
                val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

                SimpleResponse(
                    statusCode = code,
                    body = text,
                    headers = connection.headerFields.filterKeys { it != null }
                )
            } finally {
                connection.disconnect()
            }
        }
    }
}
