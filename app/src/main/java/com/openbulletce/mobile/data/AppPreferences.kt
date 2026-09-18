package com.openbulletce.mobile.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class RunnerDraft(
    val method: String = "GET",
    val url: String = "http://127.0.0.1:8080/",
    val headersText: String = "Accept: */*",
    val body: String = "",
    val selectedProxyId: String = ""
)

class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("obce_mobile", Context.MODE_PRIVATE)

    fun loadAuthorizedHosts(): List<String> {
        val fallback = listOf("localhost", "127.0.0.1", "10.0.2.2")
        val raw = prefs.getString("authorized_hosts", null) ?: return fallback
        return try {
            val json = JSONArray(raw)
            List(json.length()) { json.getString(it) }
        } catch (_: Exception) {
            fallback
        }
    }

    fun saveAuthorizedHosts(hosts: List<String>) {
        val clean = hosts.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        prefs.edit().putString("authorized_hosts", JSONArray(clean).toString()).apply()
    }

    fun loadTimeoutMs(): Int = prefs.getInt("timeout_ms", 15_000)

    fun saveTimeoutMs(value: Int) {
        prefs.edit().putInt("timeout_ms", value.coerceIn(1_000, 120_000)).apply()
    }

    fun loadRunnerDraft(): RunnerDraft {
        val raw = prefs.getString("runner_draft", null) ?: return RunnerDraft()
        return runCatching {
            val obj = JSONObject(raw)
            RunnerDraft(
                method = obj.optString("method", "GET"),
                url = obj.optString("url", "http://127.0.0.1:8080/"),
                headersText = obj.optString("headersText", "Accept: */*"),
                body = obj.optString("body"),
                selectedProxyId = obj.optString("selectedProxyId")
            )
        }.getOrDefault(RunnerDraft())
    }

    fun saveRunnerDraft(draft: RunnerDraft) {
        prefs.edit().putString(
            "runner_draft",
            JSONObject()
                .put("method", draft.method)
                .put("url", draft.url)
                .put("headersText", draft.headersText)
                .put("body", draft.body)
                .put("selectedProxyId", draft.selectedProxyId)
                .toString()
        ).apply()
    }

    fun loadActiveConfigId(): String = prefs.getString("active_config_id", "").orEmpty()

    fun saveActiveConfigId(id: String) {
        prefs.edit().putString("active_config_id", id).apply()
    }

    fun loadProxyTestUrl(): String =
        prefs.getString("proxy_test_url", "http://127.0.0.1:8080/").orEmpty()
            .ifBlank { "http://127.0.0.1:8080/" }

    fun saveProxyTestUrl(url: String) {
        prefs.edit().putString("proxy_test_url", url.trim()).apply()
    }

    fun loadLastSection(): String = prefs.getString("last_section", "RUNNER").orEmpty()

    fun saveLastSection(section: String) {
        prefs.edit().putString("last_section", section).apply()
    }
}
