package com.openbulletce.mobile.data

import android.content.Context
import org.json.JSONArray

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
}
