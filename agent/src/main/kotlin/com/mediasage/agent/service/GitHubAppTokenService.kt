package com.mediasage.agent.service

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.util.Base64

/**
 * Generates GitHub App installation tokens for authenticating as [appId] installed as
 * [installationId].
 *
 * **Auth flow:**
 * 1. Signs a short-lived JWT (9-minute window) with the App's RSA private key.
 * 2. Exchanges the JWT for an installation token (valid 1 hour) via the GitHub API.
 *
 * Tokens are cached and refreshed 10 minutes before expiry to guard against clock-skew
 * failures during the handoff window.
 *
 * **No external libraries required.** JWT generation uses JDK built-ins (`java.security`).
 * GitHub App private keys are PKCS#1 DER — [loadRsaPrivateKey] wraps them in a PKCS#8 envelope
 * so Java's `KeyFactory` can load them without BouncyCastle.
 */
class GitHubAppTokenService(
    private val appId: String,
    private val installationId: String,
    privateKeyPem: String,
    private val httpClient: HttpClient
) {
    private val log = LoggerFactory.getLogger(GitHubAppTokenService::class.java)
    private val privateKey: PrivateKey = loadRsaPrivateKey(privateKeyPem)

    @Volatile private var cachedToken: String? = null
    @Volatile private var tokenExpiresAt: Long = 0L // epoch seconds

    /**
     * Returns a valid GitHub App installation token, fetching a fresh one when
     * fewer than 10 minutes remain before the cached token expires.
     */
    suspend fun getToken(): String {
        val now = System.currentTimeMillis() / 1000
        val cached = cachedToken
        if (cached != null && now < tokenExpiresAt - 600) return cached

        log.info("Fetching fresh GitHub App installation token (appId=$appId)")
        val jwt = generateJwt(now)
        val (token, expiresAt) = exchangeForInstallationToken(jwt)
        cachedToken = token
        tokenExpiresAt = expiresAt
        log.info("GitHub App installation token refreshed (expires in ${expiresAt - now}s)")
        return token
    }

    /**
     * Produces a RS256 JWT signed with [privateKey] for authenticating as [appId].
     * Expiry is set to 9 minutes — GitHub allows up to 10; leaving a 1-minute buffer.
     */
    internal fun generateJwt(nowEpochSeconds: Long): String {
        val enc = Base64.getUrlEncoder().withoutPadding()
        val header = enc.encodeToString("""{"alg":"RS256","typ":"JWT"}""".toByteArray())
        val payload = enc.encodeToString(
            """{"iss":"$appId","iat":$nowEpochSeconds,"exp":${nowEpochSeconds + 540}}""".toByteArray()
        )
        val signingInput = "$header.$payload"
        val sig = Signature.getInstance("SHA256withRSA").apply {
            initSign(privateKey)
            update(signingInput.toByteArray())
        }.sign()
        return "$signingInput.${enc.encodeToString(sig)}"
    }

    private suspend fun exchangeForInstallationToken(jwt: String): Pair<String, Long> {
        val response = httpClient.post(
            "https://api.github.com/app/installations/$installationId/access_tokens"
        ) {
            headers {
                append(HttpHeaders.Authorization, "Bearer $jwt")
                append(HttpHeaders.Accept, "application/vnd.github+json")
                append("X-GitHub-Api-Version", "2022-11-28")
            }
        }
        check(response.status.isSuccess()) {
            "GitHub App token exchange failed: ${response.status}"
        }
        val json = response.body<JsonObject>()
        val token = json["token"]?.jsonPrimitive?.content
            ?: error("GitHub App token response missing 'token' field")
        val expiresAt = json["expires_at"]?.jsonPrimitive?.content
            ?.let { Instant.parse(it).epochSecond }
            ?: (System.currentTimeMillis() / 1000 + 3600)
        return token to expiresAt
    }

    companion object {
        /**
         * Loads an RSA private key from PEM-encoded text.
         *
         * Handles both PKCS#8 (`BEGIN PRIVATE KEY`) and PKCS#1 (`BEGIN RSA PRIVATE KEY`).
         * GitHub generates App private keys in PKCS#1 format; [wrapInPkcs8] converts them
         * to PKCS#8 DER so Java's `KeyFactory` can load them without BouncyCastle.
         */
        fun loadRsaPrivateKey(pem: String): PrivateKey {
            val isPkcs8 = pem.contains("BEGIN PRIVATE KEY")
            val stripped = pem
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("\\s".toRegex(), "")
            val keyBytes = Base64.getDecoder().decode(stripped)
            val pkcs8Bytes = if (isPkcs8) keyBytes else wrapInPkcs8(keyBytes)
            return KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(pkcs8Bytes))
        }

        /**
         * Decodes a base64-encoded PEM private key string (as stored in Cloud Run env vars).
         * This reverses the base64 encoding applied when the key was stored to avoid
         * multiline env var issues.
         */
        fun decodeBase64Key(base64EncodedPem: String): String =
            String(Base64.getDecoder().decode(base64EncodedPem))

        /**
         * Wraps raw PKCS#1 DER bytes in a minimal PKCS#8 DER envelope.
         *
         * PKCS#8 structure:
         * ```
         * SEQUENCE {
         *   INTEGER 0                            -- version
         *   SEQUENCE { OID rsaEncryption NULL }  -- algorithm identifier
         *   OCTET STRING { [PKCS#1 DER] }        -- private key
         * }
         * ```
         */
        internal fun wrapInPkcs8(pkcs1: ByteArray): ByteArray {
            // OID 1.2.840.113549.1.1.1 (rsaEncryption) + NULL
            val algId = byteArrayOf(
                0x30, 0x0D,
                0x06, 0x09, 0x2A, 0x86.toByte(), 0x48, 0x86.toByte(),
                0xF7.toByte(), 0x0D, 0x01, 0x01, 0x01,
                0x05, 0x00
            )
            val version = byteArrayOf(0x02, 0x01, 0x00)
            val octetStr = byteArrayOf(0x04) + derLen(pkcs1.size) + pkcs1
            val inner = version + algId + octetStr
            return byteArrayOf(0x30) + derLen(inner.size) + inner
        }

        /** Encodes [n] as a DER length field (definite short or long form). */
        internal fun derLen(n: Int): ByteArray = when {
            n < 0x80 -> byteArrayOf(n.toByte())
            n < 0x100 -> byteArrayOf(0x81.toByte(), n.toByte())
            else -> byteArrayOf(0x82.toByte(), (n shr 8).toByte(), (n and 0xFF).toByte())
        }
    }
}
