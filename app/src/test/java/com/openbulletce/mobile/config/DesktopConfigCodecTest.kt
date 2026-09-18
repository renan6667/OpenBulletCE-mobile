package com.openbulletce.mobile.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopConfigCodecTest {
    @Test
    fun untouchedDesktopConfigRoundTripsExactly() {
        val original = "\uFEFF[SETTINGS]\r\n{\r\n  \"Name\": \"PC Config\",\r\n  \"Author\": \"tester\",\r\n  \"UnknownFutureField\": {\"x\": 7}\r\n}\r\n\r\n[SCRIPT]\r\n#login REQUEST GET \"http://127.0.0.1/\"\r\n## keep this comment\r\n"

        val decoded = DesktopConfigCodec.decode(original)

        assertEquals("PC Config", decoded.name)
        assertEquals("tester", decoded.author)
        assertEquals(original, DesktopConfigCodec.encode(decoded))
    }

    @Test
    fun editingKnownSettingsPreservesUnknownDesktopFieldsAndScript() {
        val original = """
            [SETTINGS]
            {
              "Name": "Before",
              "Author": "PC",
              "UnknownFutureField": {
                "nested": true
              }
            }

            [SCRIPT]
            PRINT "hello"
        """.trimIndent()

        val decoded = DesktopConfigCodec.decode(original)
        val edited = DesktopConfigCodec.withSettings(
            decoded,
            mapOf("Name" to "After")
        )

        val exported = DesktopConfigCodec.encode(edited)
        val reparsed = DesktopConfigCodec.decode(exported)

        assertEquals("After", reparsed.name)
        assertTrue(reparsed.settings.getJSONObject("UnknownFutureField").getBoolean("nested"))
        assertEquals(decoded.script, reparsed.script)
    }

    @Test
    fun inspectorRecognizesUpstreamLabelsCommentsAndEmbeddedScriptsWithoutExecution() {
        val script = """
            ## comment
            #first REQUEST GET "http://127.0.0.1/"
            #setValue SET <A> "x"
            !PARSE "x" LR "a" "b" -> VAR "C"
            BEGIN SCRIPT JavaScript
            somethingArbitrary()
            END SCRIPT
        """.trimIndent()

        val report = LoliScriptInspector.inspect(script)

        assertEquals(1, report.blockCounts["REQUEST"])
        assertEquals(1, report.blockCounts["PARSE"])
        assertTrue(report.embeddedScriptPresent)
        assertFalse(report.lines.any { it.original.contains("somethingArbitrary") && it.kind == LoliLineKind.BLOCK })
    }

    @Test(expected = IllegalArgumentException::class)
    fun missingScriptSectionIsRejected() {
        DesktopConfigCodec.decode("[SETTINGS]\n{}")
    }
}
