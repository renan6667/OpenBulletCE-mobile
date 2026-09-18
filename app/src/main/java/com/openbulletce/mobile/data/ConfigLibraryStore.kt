package com.openbulletce.mobile.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ConfigLibraryRecord(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val author: String = "",
    val uri: String,
    val lastOpenedEpochMs: Long = System.currentTimeMillis()
)

class ConfigLibraryStore(context: Context) {
    private val prefs = context.getSharedPreferences(
        "obce_mobile_config_library",
        Context.MODE_PRIVATE
    )

    fun configs(): List<ConfigLibraryRecord> {
        val raw = prefs.getString(KEY, "[]") ?: "[]"
        val array = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }

        return List(array.length()) { index -> array.optJSONObject(index) }
            .filterNotNull()
            .mapNotNull { obj ->
                runCatching {
                    ConfigLibraryRecord(
                        id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                        name = obj.optString("name").ifBlank { "Unnamed config" },
                        author = obj.optString("author"),
                        uri = obj.getString("uri"),
                        lastOpenedEpochMs = obj.optLong(
                            "lastOpenedEpochMs",
                            System.currentTimeMillis()
                        )
                    )
                }.getOrNull()
            }
            .sortedByDescending { it.lastOpenedEpochMs }
    }

    fun put(record: ConfigLibraryRecord) {
        val next = configs()
            .filterNot { it.id == record.id || it.uri == record.uri }
            .plus(record)
            .sortedByDescending { it.lastOpenedEpochMs }
            .take(MAX_CONFIGS)

        save(next)
    }

    fun touch(record: ConfigLibraryRecord) {
        put(record.copy(lastOpenedEpochMs = System.currentTimeMillis()))
    }

    fun remove(id: String) {
        save(configs().filterNot { it.id == id })
    }

    private fun save(records: List<ConfigLibraryRecord>) {
        val array = JSONArray()
        records.forEach { record ->
            array.put(
                JSONObject()
                    .put("id", record.id)
                    .put("name", record.name)
                    .put("author", record.author)
                    .put("uri", record.uri)
                    .put("lastOpenedEpochMs", record.lastOpenedEpochMs)
            )
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private companion object {
        const val KEY = "configs"
        const val MAX_CONFIGS = 250
    }
}
