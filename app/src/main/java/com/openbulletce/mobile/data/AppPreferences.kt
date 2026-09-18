package com.openbulletce.mobile.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class RunnerDraft(
    val method: String = "GET",
    val url: String = "",
    val headersText: String = "",
    val body: String = "",
    val selectedProxyId: String = "",
    val configId: String = "",
    val wordlistId: String = "",
    val proxyMode: String = "DEFAULT",
    val botsAmount: Int = 1,
    val startingPoint: Int = 1
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
        val raw = prefs.getString("runner_draft", null) ?: return RunnerDraft(
            configId = loadActiveConfigId()
        )
        return runCatching {
            val obj = JSONObject(raw)
            RunnerDraft(
                method = obj.optString("method", "GET"),
                url = obj.optString("url"),
                headersText = obj.optString("headersText"),
                body = obj.optString("body"),
                selectedProxyId = obj.optString("selectedProxyId"),
                configId = obj.optString("configId", loadActiveConfigId()),
                wordlistId = obj.optString("wordlistId"),
                proxyMode = obj.optString("proxyMode", "DEFAULT"),
                botsAmount = obj.optInt("botsAmount", 1).coerceIn(1, 200),
                startingPoint = obj.optInt("startingPoint", 1).coerceAtLeast(1)
            )
        }.getOrDefault(RunnerDraft(configId = loadActiveConfigId()))
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
                .put("configId", draft.configId)
                .put("wordlistId", draft.wordlistId)
                .put("proxyMode", draft.proxyMode)
                .put("botsAmount", draft.botsAmount.coerceIn(1, 200))
                .put("startingPoint", draft.startingPoint.coerceAtLeast(1))
                .toString()
        ).apply()

        prefs.edit().putString("active_config_id", draft.configId).apply()
    }

    fun loadActiveConfigId(): String = prefs.getString("active_config_id", "").orEmpty()

    fun saveActiveConfigId(id: String) {
        prefs.edit().putString("active_config_id", id).apply()
        val current = loadRunnerDraft()
        if (current.configId != id) {
            saveRunnerDraft(current.copy(configId = id))
        }
    }

    fun loadProxyTestUrl(): String =
        prefs.getString("proxy_test_url", "https://example.com/").orEmpty()
            .ifBlank { "https://example.com/" }

    fun saveProxyTestUrl(url: String) {
        prefs.edit().putString("proxy_test_url", url.trim()).apply()
    }

    fun loadProxySuccessKey(): String =
        prefs.getString("proxy_success_key", "").orEmpty()

    fun saveProxySuccessKey(value: String) {
        prefs.edit().putString("proxy_success_key", value).apply()
    }

    fun loadProxyCheckerBots(): Int =
        prefs.getInt("proxy_checker_bots", 1).coerceIn(1, 200)

    fun saveProxyCheckerBots(value: Int) {
        prefs.edit().putInt("proxy_checker_bots", value.coerceIn(1, 200)).apply()
    }

    fun loadLastSection(): String = prefs.getString("last_section", "RUNNER").orEmpty()

    fun saveLastSection(section: String) {
        prefs.edit().putString("last_section", section).apply()
    }
}
