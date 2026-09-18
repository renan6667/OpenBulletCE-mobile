package com.openbulletce.mobile.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class MobileProxyType { HTTP, SOCKS4, SOCKS4A, SOCKS5 }

data class ProxyRecord(
    val id: String = UUID.randomUUID().toString(),
    val raw: String,
    val type: MobileProxyType = MobileProxyType.HTTP,
    val username: String = "",
    val password: String = "",
    val working: String = "UNTESTED",
    val pingMs: Int = 0,
    val country: String = ""
)

data class WordlistRecord(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val uri: String,
    val type: String = "Default",
    val purpose: String = "",
    val totalLines: Int = 0
)

data class CookieSetRecord(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val treeUri: String
)

data class HitRecord(
    val id: String = UUID.randomUUID().toString(),
    val data: String,
    val captured: String = "",
    val proxy: String = "",
    val dateEpochMs: Long = System.currentTimeMillis(),
    val type: String = "MANUAL",
    val configName: String = "",
    val wordlistName: String = ""
)

class ManagerStore(context: Context) {
    private val prefs = context.getSharedPreferences("obce_mobile_managers", Context.MODE_PRIVATE)

    fun proxies(): List<ProxyRecord> =
        readArray("proxies").mapNotNull { obj ->
            runCatching {
                ProxyRecord(
                    id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                    raw = obj.getString("raw"),
                    type = runCatching { MobileProxyType.valueOf(obj.optString("type", "HTTP")) }
                        .getOrDefault(MobileProxyType.HTTP),
                    username = obj.optString("username"),
                    password = obj.optString("password"),
                    working = obj.optString("working", "UNTESTED"),
                    pingMs = obj.optInt("pingMs"),
                    country = obj.optString("country")
                )
            }.getOrNull()
        }

    fun putProxy(record: ProxyRecord) {
        val next = proxies().filterNot { it.id == record.id } + record
        saveArray("proxies", next.map(::proxyJson))
    }

    fun removeProxy(id: String) =
        saveArray("proxies", proxies().filterNot { it.id == id }.map(::proxyJson))

    fun clearProxies() = saveArray("proxies", emptyList())

    fun wordlists(): List<WordlistRecord> =
        readArray("wordlists").mapNotNull { obj ->
            runCatching {
                WordlistRecord(
                    id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                    name = obj.getString("name"),
                    uri = obj.getString("uri"),
                    type = obj.optString("type", "Default"),
                    purpose = obj.optString("purpose"),
                    totalLines = obj.optInt("totalLines")
                )
            }.getOrNull()
        }

    fun putWordlist(record: WordlistRecord) {
        val next = wordlists().filterNot { it.uri == record.uri || it.id == record.id } + record
        saveArray("wordlists", next.map(::wordlistJson))
    }

    fun removeWordlist(id: String) =
        saveArray("wordlists", wordlists().filterNot { it.id == id }.map(::wordlistJson))

    fun cookieSets(): List<CookieSetRecord> =
        readArray("cookie_sets").mapNotNull { obj ->
            runCatching {
                CookieSetRecord(
                    id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                    name = obj.getString("name"),
                    treeUri = obj.getString("treeUri")
                )
            }.getOrNull()
        }

    fun putCookieSet(record: CookieSetRecord) {
        val next = cookieSets().filterNot { it.treeUri == record.treeUri || it.id == record.id } + record
        saveArray("cookie_sets", next.map(::cookieSetJson))
    }

    fun removeCookieSet(id: String) =
        saveArray("cookie_sets", cookieSets().filterNot { it.id == id }.map(::cookieSetJson))

    fun hits(): List<HitRecord> =
        readArray("hits").mapNotNull { obj ->
            runCatching {
                HitRecord(
                    id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                    data = obj.optString("data"),
                    captured = obj.optString("captured"),
                    proxy = obj.optString("proxy"),
                    dateEpochMs = obj.optLong("dateEpochMs", System.currentTimeMillis()),
                    type = obj.optString("type", "MANUAL"),
                    configName = obj.optString("configName"),
                    wordlistName = obj.optString("wordlistName")
                )
            }.getOrNull()
        }.sortedByDescending { it.dateEpochMs }

    fun putHit(record: HitRecord) {
        val next = (hits().filterNot { it.id == record.id } + record).takeLast(2000)
        saveArray("hits", next.map(::hitJson))
    }

    fun removeHit(id: String) =
        saveArray("hits", hits().filterNot { it.id == id }.map(::hitJson))

    fun clearHits() = saveArray("hits", emptyList())

    private fun readArray(key: String): List<JSONObject> {
        val raw = prefs.getString(key, "[]") ?: "[]"
        val array = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        return List(array.length()) { i -> array.optJSONObject(i) }.filterNotNull()
    }

    private fun saveArray(key: String, values: List<JSONObject>) {
        val array = JSONArray()
        values.forEach(array::put)
        prefs.edit().putString(key, array.toString()).apply()
    }

    private fun proxyJson(v: ProxyRecord) = JSONObject()
        .put("id", v.id).put("raw", v.raw).put("type", v.type.name)
        .put("username", v.username).put("password", v.password)
        .put("working", v.working).put("pingMs", v.pingMs).put("country", v.country)

    private fun wordlistJson(v: WordlistRecord) = JSONObject()
        .put("id", v.id).put("name", v.name).put("uri", v.uri)
        .put("type", v.type).put("purpose", v.purpose).put("totalLines", v.totalLines)

    private fun cookieSetJson(v: CookieSetRecord) = JSONObject()
        .put("id", v.id).put("name", v.name).put("treeUri", v.treeUri)

    private fun hitJson(v: HitRecord) = JSONObject()
        .put("id", v.id).put("data", v.data).put("captured", v.captured)
        .put("proxy", v.proxy).put("dateEpochMs", v.dateEpochMs).put("type", v.type)
        .put("configName", v.configName).put("wordlistName", v.wordlistName)
}
