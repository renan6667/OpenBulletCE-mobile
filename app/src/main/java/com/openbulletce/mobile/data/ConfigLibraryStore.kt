package com.openbulletce.mobile.data

import android.content.Context
import com.openbulletce.mobile.config.DesktopConfigCodec
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class ConfigLibraryRecord(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val author: String = "",
    val uri: String,
    val lastOpenedEpochMs: Long = System.currentTimeMillis()
)

class ConfigLibraryStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(
        "obce_mobile_config_library",
        Context.MODE_PRIVATE
    )
    private val snapshotDir = File(appContext.filesDir, "configs").apply { mkdirs() }

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
                        uri = obj.optString("uri"),
                        lastOpenedEpochMs = obj.optLong(
                            "lastOpenedEpochMs",
                            System.currentTimeMillis()
                        )
                    )
                }.getOrNull()
            }
            .sortedByDescending { it.lastOpenedEpochMs }
    }

    fun find(id: String): ConfigLibraryRecord? = configs().firstOrNull { it.id == id }

    fun upsertForUri(name: String, author: String, uri: String): ConfigLibraryRecord {
        val existing = configs().firstOrNull { it.uri.isNotBlank() && it.uri == uri }
        val record = if (existing == null) {
            ConfigLibraryRecord(name = name, author = author, uri = uri)
        } else {
            existing.copy(
                name = name,
                author = author,
                lastOpenedEpochMs = System.currentTimeMillis()
            )
        }
        put(record)
        return record
    }

    fun put(record: ConfigLibraryRecord) {
        val next = configs()
            .filterNot { it.id == record.id || (record.uri.isNotBlank() && it.uri == record.uri) }
            .plus(record)
            .sortedByDescending { it.lastOpenedEpochMs }
            .take(MAX_CONFIGS)

        save(next)
    }

    fun touch(record: ConfigLibraryRecord) {
        put(record.copy(lastOpenedEpochMs = System.currentTimeMillis()))
    }

    fun saveSnapshot(record: ConfigLibraryRecord, config: DesktopConfigCodec.DesktopConfig) {
        snapshotFile(record.id).writeText(
            DesktopConfigCodec.encode(config),
            Charsets.UTF_8
        )
    }

    fun loadSnapshot(record: ConfigLibraryRecord): DesktopConfigCodec.DesktopConfig? =
        runCatching {
            val file = snapshotFile(record.id)
            if (!file.isFile) return@runCatching null
            DesktopConfigCodec.decode(file.readText(Charsets.UTF_8))
        }.getOrNull()

    fun remove(id: String) {
        snapshotFile(id).delete()
        save(configs().filterNot { it.id == id })
    }

    private fun snapshotFile(id: String): File =
        File(snapshotDir, id.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".lce")

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
