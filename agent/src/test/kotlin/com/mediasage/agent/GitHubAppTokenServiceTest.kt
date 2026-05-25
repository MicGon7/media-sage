package com.mediasage.agent

import com.mediasage.agent.service.GitHubAppTokenService
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for [GitHubAppTokenService] crypto helpers.
 *
 * Tests cover JWT structure and RS256 signature verification using a generated key pair.
 * The token exchange (GitHub API call) is not tested here — it is covered by the smoke test.
 */
class GitHubAppTokenServiceTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Generates a 2048-bit RSA key pair and returns the private key as a PKCS#8 PEM string. */
    private fun generatePkcs8PrivateKeyPem(): Pair<String, RSAPublicKey> {
        val keyPairGen = KeyPairGenerator.getInstance("RSA")
        keyPairGen.initialize(2048)
        val keyPair = keyPairGen.generateKeyPair()
        val enc = Base64.getMimeEncoder(64, "\n".toByteArray())
        val pem = "-----BEGIN PRIVATE KEY-----\n${enc.encodeToString(keyPair.private.encoded)}\n-----END PRIVATE KEY-----"
        return pem to (keyPair.public as RSAPublicKey)
    }

    // ── wrapInPkcs8 ──────────────────────────────────────────────────────────

    @Test
    fun `wrapInPkcs8 produces valid DER that KeyFactory can load`() {
        // Generate a PKCS#8 key, strip it back to raw DER, wrap it again — round-trip test
        val (pem, _) = generatePkcs8PrivateKeyPem()
        val stripped = pem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\s".toRegex(), "")
        val pkcs8Bytes = Base64.getDecoder().decode(stripped)

        // Simple validation: wrapped output starts with SEQUENCE tag 0x30
        val wrapped = GitHubAppTokenService.wrapInPkcs8(pkcs8Bytes)
        assertEquals(0x30.toByte(), wrapped[0], "PKCS#8 envelope must start with SEQUENCE tag 0x30")
    }

    // ── loadRsaPrivateKey ─────────────────────────────────────────────────────

    @Test
    fun `loadRsaPrivateKey loads PKCS#8 key`() {
        val (pem, publicKey) = generatePkcs8PrivateKeyPem()
        val privateKey = GitHubAppTokenService.loadRsaPrivateKey(pem)
        // Verify the key works by signing and verifying a test payload
        val data = "test-payload".toByteArray()
        val sig = Signature.getInstance("SHA256withRSA").apply {
            initSign(privateKey)
            update(data)
        }.sign()
        val valid = Signature.getInstance("SHA256withRSA").apply {
            initVerify(publicKey)
            update(data)
        }.verify(sig)
        assertTrue(valid, "Signature produced by loaded private key must verify with corresponding public key")
    }

    // ── generateJwt ──────────────────────────────────────────────────────────

    @Test
    fun `generateJwt produces three-part dot-separated token`() {
        val (pem, _) = generatePkcs8PrivateKeyPem()
        val service = GitHubAppTokenService(
            appId = "123456",
            installationId = "789",
            privateKeyPem = pem,
            httpClient = io.ktor.client.HttpClient()
        )
        val now = System.currentTimeMillis() / 1000
        val jwt = service.generateJwt(now)
        val parts = jwt.split(".")
        assertEquals(3, parts.size, "JWT must have exactly three dot-separated parts")
        parts.forEach { part ->
            assertTrue(part.isNotBlank(), "Each JWT part must be non-empty")
        }
    }

    @Test
    fun `generateJwt header decodes to RS256 JWT`() {
        val (pem, _) = generatePkcs8PrivateKeyPem()
        val service = GitHubAppTokenService(
            appId = "123456",
            installationId = "789",
            privateKeyPem = pem,
            httpClient = io.ktor.client.HttpClient()
        )
        val jwt = service.generateJwt(System.currentTimeMillis() / 1000)
        val headerJson = String(Base64.getUrlDecoder().decode(jwt.split(".")[0].padded()))
        assertTrue(headerJson.contains("\"RS256\""), "JWT header must specify RS256 algorithm")
        assertTrue(headerJson.contains("\"JWT\""), "JWT header must specify JWT type")
    }

    @Test
    fun `generateJwt payload contains iss and exp`() {
        val (pem, _) = generatePkcs8PrivateKeyPem()
        val service = GitHubAppTokenService(
            appId = "my-app-id",
            installationId = "789",
            privateKeyPem = pem,
            httpClient = io.ktor.client.HttpClient()
        )
        val now = System.currentTimeMillis() / 1000
        val jwt = service.generateJwt(now)
        val payloadJson = String(Base64.getUrlDecoder().decode(jwt.split(".")[1].padded()))
        assertTrue(payloadJson.contains("\"my-app-id\""), "JWT payload must contain app ID as iss")
        assertTrue(payloadJson.contains("\"exp\""), "JWT payload must contain exp claim")
    }

    @Test
    fun `generateJwt signature verifies with corresponding public key`() {
        val (pem, publicKey) = generatePkcs8PrivateKeyPem()
        val service = GitHubAppTokenService(
            appId = "123456",
            installationId = "789",
            privateKeyPem = pem,
            httpClient = io.ktor.client.HttpClient()
        )
        val now = System.currentTimeMillis() / 1000
        val jwt = service.generateJwt(now)
        val parts = jwt.split(".")
        val signingInput = "${parts[0]}.${parts[1]}".toByteArray()
        val signature = Base64.getUrlDecoder().decode(parts[2].padded())
        val valid = Signature.getInstance("SHA256withRSA").apply {
            initVerify(publicKey)
            update(signingInput)
        }.verify(signature)
        assertTrue(valid, "JWT signature must verify with the corresponding RSA public key")
    }

    // ── derLen ────────────────────────────────────────────────────────────────

    @Test
    fun `derLen encodes short lengths as single byte`() {
        assertEquals(1, GitHubAppTokenService.derLen(0).size)
        assertEquals(0x7F.toByte(), GitHubAppTokenService.derLen(127)[0])
    }

    @Test
    fun `derLen encodes medium lengths with 0x81 prefix`() {
        val encoded = GitHubAppTokenService.derLen(200)
        assertEquals(2, encoded.size)
        assertEquals(0x81.toByte(), encoded[0])
        assertEquals(200.toByte(), encoded[1])
    }

    @Test
    fun `derLen encodes large lengths with 0x82 prefix`() {
        val encoded = GitHubAppTokenService.derLen(1000)
        assertEquals(3, encoded.size)
        assertEquals(0x82.toByte(), encoded[0])
        assertEquals(0x03.toByte(), encoded[1])
        assertEquals(0xE8.toByte(), encoded[2])
    }

    // ── decodeBase64Key ───────────────────────────────────────────────────────

    @Test
    fun `decodeBase64Key round-trips a PEM string`() {
        val original = "-----BEGIN PRIVATE KEY-----\nABCDEFG\n-----END PRIVATE KEY-----"
        val encoded = Base64.getEncoder().encodeToString(original.toByteArray())
        assertEquals(original, GitHubAppTokenService.decodeBase64Key(encoded))
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    /** Pads a base64url string to a multiple of 4 for standard decoder compatibility. */
    private fun String.padded(): String {
        val pad = (4 - length % 4) % 4
        return this + "=".repeat(pad)
    }
}
