package com.mediasage.orchestrator.feedback

import com.mediasage.orchestrator.feedback.github.loadPrivateKey
import java.security.KeyPairGenerator
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class GitHubAppClientJwtTest {

    @Test
    fun loadPrivateKeyAcceptsPkcs8Pem() {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val b64 = Base64.getEncoder().encodeToString(keyPair.private.encoded)
        val pem = "-----BEGIN PRIVATE KEY-----\n$b64\n-----END PRIVATE KEY-----"
        val key = loadPrivateKey(pem)
        assertNotNull(key)
        assertEquals("RSA", key.algorithm)
    }

    @Test
    fun loadPrivateKeyAcceptsBase64EncodedPem() {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val b64 = Base64.getEncoder().encodeToString(keyPair.private.encoded)
        val pem = "-----BEGIN PRIVATE KEY-----\n$b64\n-----END PRIVATE KEY-----"
        val base64Pem = Base64.getEncoder().encodeToString(pem.toByteArray())
        val key = loadPrivateKey(base64Pem)
        assertNotNull(key)
        assertEquals("RSA", key.algorithm)
    }
}
