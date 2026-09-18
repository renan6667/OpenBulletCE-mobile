package com.openbulletce.mobile.config

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopConfigCollectionsTest {
    @Test
    fun customInputEditPreservesUnknownFields() {
        val settings = JSONObject()
            .put(
                "CustomInputs",
                JSONArray().put(
                    JSONObject()
                        .put("Id", 4)
                        .put("Description", "Before")
                        .put("VariableName", "TOKEN")
                        .put("FutureField", "keep-me")
                )
            )

        val parsed = DesktopConfigCollections.customInputs(settings)
        val edited = listOf(parsed.single().copy(description = "After"))
        val json = DesktopConfigCollections.customInputsJson(edited).getJSONObject(0)

        assertEquals(4, json.getInt("Id"))
        assertEquals("After", json.getString("Description"))
        assertEquals("TOKEN", json.getString("VariableName"))
        assertEquals("keep-me", json.getString("FutureField"))
    }

    @Test
    fun dataRuleSupportsNumericAndNamedDesktopEnumValues() {
        val numeric = JSONObject()
            .put("DataRules", JSONArray().put(
                JSONObject()
                    .put("Id", 1)
                    .put("SliceName", "DATA")
                    .put("RuleType", 4)
                    .put("RuleString", "^[a-z]+$")
            ))

        val named = JSONObject()
            .put("DataRules", JSONArray().put(
                JSONObject()
                    .put("Id", 2)
                    .put("SliceName", "DATA")
                    .put("RuleType", "MinLength")
                    .put("RuleString", "8")
            ))

        assertEquals(4, DesktopConfigCollections.dataRules(numeric).single().ruleType)
        assertEquals(2, DesktopConfigCollections.dataRules(named).single().ruleType)
    }

    @Test
    fun emptyMissingCollectionsStayEmptyUntilEdited() {
        val settings = JSONObject()

        assertTrue(DesktopConfigCollections.customInputs(settings).isEmpty())
        assertTrue(DesktopConfigCollections.dataRules(settings).isEmpty())
    }
}
