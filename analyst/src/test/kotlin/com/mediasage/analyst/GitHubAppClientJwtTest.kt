package com.mediasage.analyst

import com.mediasage.analyst.github.GitHubAppClient
import com.mediasage.analyst.github.loadPrivateKey
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.*
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GitHubAppClientJwtTest {

    private lateinit var pkcs8Pem: String
    private lateinit var client: GitHubAppClient

    @BeforeTest
    fun setUp() {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val b64 = Base64.getEncoder().encodeToString(keyPair.private.encoded)
        pkcs8Pem = "-----BEGIN PRIVATE KEY-----\n$b64\n-----END PRIVATE KEY-----"
        client = GitHubAppClient(
            httpClient = HttpClient(MockEngine {
                respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }),
            appId = "99999",
            privateKeyPem = pkcs8Pem,
            installationId = "12345",
        )
    }

    @Test
    fun jwtHasThreeDotSeparatedSegments() {
        val jwt = client.buildJwt()
        assertEquals(3, jwt.split(".").size, "JWT must be header.payload.signature")
    }

    @Test
    fun jwtHeaderIsRs256() {
        val jwt = client.buildJwt()
        val headerJson = String(Base64.getUrlDecoder().decode(jwt.split(".")[0]))
        val header = Json.parseToJsonElement(headerJson).jsonObject
        assertEquals("RS256", header["alg"]!!.jsonPrimitive.content)
        assertEquals("JWT", header["typ"]!!.jsonPrimitive.content)
    }

    @Test
    fun jwtPayloadContainsIssMatchingAppId() {
        val jwt = client.buildJwt()
        val payloadJson = String(Base64.getUrlDecoder().decode(jwt.split(".")[1]))
        val payload = Json.parseToJsonElement(payloadJson).jsonObject
        assertEquals("99999", payload["iss"]!!.jsonPrimitive.content)
    }

    @Test
    fun jwtSignatureVerifiesWithMatchingPublicKey() {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val b64 = Base64.getEncoder().encodeToString(keyPair.private.encoded)
        val pem = "-----BEGIN PRIVATE KEY-----\n$b64\n-----END PRIVATE KEY-----"
        val testClient = GitHubAppClient(
            httpClient = HttpClient(MockEngine {
                respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }),
            appId = "42",
            privateKeyPem = pem,
            installationId = "1",
        )
        val jwt = testClient.buildJwt()
        val parts = jwt.split(".")
        val signingInput = "${parts[0]}.${parts[1]}"
        val signature = Base64.getUrlDecoder().decode(parts[2])
        val verifier = Signature.getInstance("SHA256withRSA").apply {
            initVerify(keyPair.public)
            update(signingInput.toByteArray())
        }
        assertTrue(verifier.verify(signature), "JWT signature must verify with the matching public key")
    }

    @Test
    fun loadPrivateKeyAcceptsPkcs8Pem() {
        val key = loadPrivateKey(pkcs8Pem)
        assertNotNull(key)
        assertEquals("RSA", key.algorithm)
    }
}
