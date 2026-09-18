package com.openbulletce.mobile.config

import org.json.JSONArray
import org.json.JSONObject

data class CustomInputSpec(
    val id: Int,
    val description: String,
    val variableName: String,
    val original: JSONObject
)

data class DataRuleSpec(
    val id: Int,
    val sliceName: String,
    val ruleType: Int,
    val ruleString: String,
    val original: JSONObject
) {
    val ruleTypeLabel: String
        get() = when (ruleType) {
            0 -> "MustContain"
            1 -> "MustNotContain"
            2 -> "MinLength"
            3 -> "MaxLength"
            4 -> "MustMatchRegex"
            else -> "Unknown($ruleType)"
        }
}

object DesktopConfigCollections {
    fun customInputs(settings: JSONObject): List<CustomInputSpec> {
        val array = settings.optJSONArray("CustomInputs") ?: return emptyList()
        return List(array.length()) { index -> array.optJSONObject(index) }
            .filterNotNull()
            .mapIndexed { index, obj ->
                CustomInputSpec(
                    id = obj.optInt("Id", index),
                    description = obj.optString("Description"),
                    variableName = obj.optString("VariableName"),
                    original = JSONObject(obj.toString())
                )
            }
    }

    fun dataRules(settings: JSONObject): List<DataRuleSpec> {
        val array = settings.optJSONArray("DataRules") ?: return emptyList()
        return List(array.length()) { index -> array.optJSONObject(index) }
            .filterNotNull()
            .mapIndexed { index, obj ->
                DataRuleSpec(
                    id = obj.optInt("Id", index),
                    sliceName = obj.optString("SliceName"),
                    ruleType = readRuleType(obj.opt("RuleType")),
                    ruleString = obj.optString("RuleString", "Lowercase"),
                    original = JSONObject(obj.toString())
                )
            }
    }

    fun customInputsJson(values: List<CustomInputSpec>): JSONArray =
        JSONArray().also { array ->
            values.forEach { item ->
                val obj = JSONObject(item.original.toString())
                    .put("Id", item.id)
                    .put("Description", item.description)
                    .put("VariableName", item.variableName)
                array.put(obj)
            }
        }

    fun dataRulesJson(values: List<DataRuleSpec>): JSONArray =
        JSONArray().also { array ->
            values.forEach { item ->
                val obj = JSONObject(item.original.toString())
                    .put("Id", item.id)
                    .put("SliceName", item.sliceName)
                    .put("RuleType", item.ruleType.coerceIn(0, 4))
                    .put("RuleString", item.ruleString)
                array.put(obj)
            }
        }

    fun newCustomInput(existing: List<CustomInputSpec>): CustomInputSpec =
        CustomInputSpec(
            id = nextId(existing.map { it.id }),
            description = "",
            variableName = "",
            original = JSONObject()
        )

    fun newDataRule(existing: List<DataRuleSpec>): DataRuleSpec =
        DataRuleSpec(
            id = nextId(existing.map { it.id }),
            sliceName = "",
            ruleType = 0,
            ruleString = "Lowercase",
            original = JSONObject()
        )

    private fun readRuleType(value: Any?): Int = when (value) {
        is Number -> value.toInt().coerceIn(0, 4)
        is String -> when (value.lowercase()) {
            "mustcontain" -> 0
            "mustnotcontain" -> 1
            "minlength" -> 2
            "maxlength" -> 3
            "mustmatchregex" -> 4
            else -> value.toIntOrNull()?.coerceIn(0, 4) ?: 0
        }
        else -> 0
    }

    private fun nextId(ids: List<Int>): Int = (ids.maxOrNull() ?: -1) + 1
}
