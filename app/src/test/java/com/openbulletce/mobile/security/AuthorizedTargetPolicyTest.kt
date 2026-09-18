package com.openbulletce.mobile.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthorizedTargetPolicyTest {
    @Test
    fun exactHostDoesNotImplicitlyAuthorizeSubdomains() {
        val policy = AuthorizedTargetPolicy(setOf("example.test"))

        assertTrue(policy.check("https://example.test/").allowed)
        assertFalse(policy.check("https://api.example.test/").allowed)
    }

    @Test
    fun explicitWildcardAuthorizesOnlySubdomains() {
        val policy = AuthorizedTargetPolicy(setOf("*.example.test"))

        assertTrue(policy.check("https://api.example.test/").allowed)
        assertTrue(policy.check("https://deep.api.example.test/").allowed)
        assertFalse(policy.check("https://example.test/").allowed)
    }

    @Test
    fun broadSingleLabelWildcardIsIgnored() {
        val policy = AuthorizedTargetPolicy(setOf("*.com"))

        assertFalse(policy.check("https://example.com/").allowed)
    }

    @Test
    fun nonHttpSchemesAreRejected() {
        val policy = AuthorizedTargetPolicy(setOf("localhost"))

        assertFalse(policy.check("file:///tmp/test").allowed)
        assertFalse(policy.check("javascript:alert(1)").allowed)
    }
}
