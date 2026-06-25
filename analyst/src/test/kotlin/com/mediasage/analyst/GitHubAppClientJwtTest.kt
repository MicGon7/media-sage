package com.mediasage.analyst

import com.mediasage.analyst.github.GitHubAppClient
import com.mediasage.analyst.github.loadPrivateKey
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import java.io.IOException
import java.security.KeyPairGenerator
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

    @Test
    fun installationTokenRetriesOnIoFailureThenSucceeds() = runTest {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val pem = "-----BEGIN PRIVATE KEY-----\n${Base64.getEncoder().encodeToString(keyPair.private.encoded)}\n-----END PRIVATE KEY-----"

        var tokenCallCount = 0
        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath.endsWith("/access_tokens")) {
                tokenCallCount++
                if (tokenCallCount < 3) throw IOException("simulated timeout on attempt $tokenCallCount")
                respond(
                    content = """{"token":"gh-token-ok","expires_at":"2030-01-01T00:00:00Z"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            } else {
                respond(content = "[]", status = HttpStatusCode.OK, headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
        val client = HttpClient(mockEngine) {
            install(HttpTimeout) { requestTimeoutMillis = 5_000 }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val githubClient = GitHubAppClient(client, appId = "1", privateKeyPem = pem, installationId = "42")
        // hasOpenAnalystPr calls installationToken (retried twice) then GET /pulls
        val result = githubClient.hasOpenAnalystPr("owner", "repo")
        assertEquals(false, result, "Empty PR list should return false")
        assertEquals(3, tokenCallCount, "Should have attempted installationToken 3 times (2 failures + 1 success)")
    }

    @Test
    fun installationTokenThrowsAfterAllRetriesExhausted() = runTest {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val pem = "-----BEGIN PRIVATE KEY-----\n${Base64.getEncoder().encodeToString(keyPair.private.encoded)}\n-----END PRIVATE KEY-----"

        val mockEngine = MockEngine { _ -> throw IOException("persistent timeout") }
        val client = HttpClient(mockEngine) {
            install(HttpTimeout) { requestTimeoutMillis = 5_000 }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val githubClient = GitHubAppClient(client, appId = "1", privateKeyPem = pem, installationId = "42")
        assertFailsWith<IllegalStateException> {
            githubClient.hasOpenAnalystPr("owner", "repo")
        }
    }
}
