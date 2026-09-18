package com.openbulletce.mobile.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeRequestPresetTest {
    @Test
    fun parsesStaticDesktopRequest() {
        val script = """
REQUEST POST "https://example.test/login"
  CONTENT "a=1&b=2"
  CONTENTTYPE "application/x-www-form-urlencoded"
  HEADER "Accept: application/json"
  COOKIE "session: abc"
        """.trimIndent()

        val preset = SafeRequestPresetParser.fromScript(script)

        assertEquals("POST", preset.method)
        assertEquals("https://example.test/login", preset.url)
        assertEquals("a=1&b=2", preset.body)
        assertEquals("application/json", preset.headers["Accept"])
        assertEquals("application/x-www-form-urlencoded", preset.headers["Content-Type"])
        assertEquals("session= abc", preset.headers["Cookie"])
    }

    @Test
    fun rejectsDynamicRequestUrl() {
        val result = runCatching {
            SafeRequestPresetParser.fromScript(
                """REQUEST GET "https://example.test/<USER>""""
            )
        }

        assertTrue(result.isFailure)
    }

    @Test
    fun rejectsUnsupportedRawMode() {
        val result = runCatching {
            SafeRequestPresetParser.fromScript(
                """
REQUEST POST "https://example.test/upload" RAW
  RAWDATA "00FF"
                """.trimIndent()
            )
        }

        assertTrue(result.isFailure)
    }
}
