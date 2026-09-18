package com.openbulletce.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyCodecTest {
    @Test
    fun parsesHttpProxyWithCredentials() {
        val parsed = ProxyCodec.parse("(Http)proxy.example:8080:user:pass")
        val proxy = parsed.proxy

        assertNotNull(proxy)
        assertEquals(MobileProxyType.HTTP, proxy!!.type)
        assertEquals("user", proxy.username)
        assertEquals("pass", proxy.password)
        assertEquals("proxy.example", ProxyCodec.endpoint(proxy).getOrThrow().host)
        assertEquals(8080, ProxyCodec.endpoint(proxy).getOrThrow().port)
    }

    @Test
    fun supportsUnauthenticatedSocks5() {
        val proxy = ProxyCodec.parse("(Socks5)127.0.0.1:1080").proxy!!
        assertNull(ProxyCodec.executionIssue(proxy))
    }

    @Test
    fun doesNotPretendUnsupportedProxyModesWork() {
        val socks4 = ProxyCodec.parse("(Socks4)127.0.0.1:1080").proxy!!
        val chain = ProxyCodec.parse("(Http)127.0.0.1:8080 -> (Http)127.0.0.1:8081").proxy!!

        assertTrue(ProxyCodec.executionIssue(socks4)!!.contains("SOCKS4"))
        assertTrue(ProxyCodec.executionIssue(chain)!!.contains("chains"))
    }

    @Test
    fun checkerFailureDoesNotBecomeRunnerBadOrBanned() {
        val proxy = ProxyCodec.parse("(Http)127.0.0.1:8080").proxy!!
            .copy(
                working = "FAILED",
                status = MobileProxyStatus.AVAILABLE
            )

        assertNull(ProxyCodec.executionIssue(proxy))
    }

    @Test
    fun runnerBadAndBannedStatesBlockExecution() {
        val base = ProxyCodec.parse("(Http)127.0.0.1:8080").proxy!!
        val bad = base.copy(
            status = MobileProxyStatus.BAD,
            banReason = "Connection error"
        )
        val banned = base.copy(
            status = MobileProxyStatus.BANNED,
            banReason = "BAN status"
        )

        assertTrue(ProxyCodec.executionIssue(bad)!!.contains("Connection error"))
        assertTrue(ProxyCodec.executionIssue(banned)!!.contains("BAN status"))
        assertNull(ProxyCodec.transportIssue(bad))
        assertNull(ProxyCodec.transportIssue(banned))
    }
}
