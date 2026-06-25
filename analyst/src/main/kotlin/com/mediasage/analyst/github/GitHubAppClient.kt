package com.mediasage.analyst.github

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

private val log = LoggerFactory.getLogger(GitHubAppClient::class.java)
private val json = Json { ignoreUnknownKeys = true }
private const val GITHUB_API = "https://api.github.com"
private const val GH_API_VERSION = "2022-11-28"
private const val GH_ACCEPT = "application/vnd.github+json"

class GitHubAppClient(
    private val httpClient: HttpClient,
    private val appId: String,
    private val privateKeyPem: String,
    private val installationId: String,
) : GitHubApiClient {

    override suspend fun hasOpenAnalystPr(owner: String, repo: String): Boolean {
        val token = installationToken()
        val response = httpClient.get("$GITHUB_API/repos/$owner/$repo/pulls?state=open&per_page=100") {
            ghAuth(token)
        }
        if (!response.status.isSuccess()) return false
        val prs = json.decodeFromString<List<PrItem>>(response.bodyAsText())
        return prs.any { it.title.startsWith("[Analyst]") }
    }

    override suspend fun getFileContents(owner: String, repo: String, path: String): FileContents {
        val token = installationToken()
        val response = httpClient.get("$GITHUB_API/repos/$owner/$repo/contents/$path") {
            ghAuth(token)
        }
        check(response.status.isSuccess()) {
            "GitHub getFileContents failed (${response.status}): ${response.bodyAsText()}"
        }
        val obj = json.decodeFromString<FileContentsResponse>(response.bodyAsText())
        val decoded = String(Base64.getMimeDecoder().decode(obj.content))
        return FileContents(content = decoded, sha = obj.sha)
    }

    override suspend fun getBranchSha(owner: String, repo: String, branch: String): String {
        val token = installationToken()
        val response = httpClient.get("$GITHUB_API/repos/$owner/$repo/git/ref/heads/$branch") {
            ghAuth(token)
        }
        check(response.status.isSuccess()) {
            "GitHub getBranchSha failed (${response.status}): ${response.bodyAsText()}"
        }
        return json.parseToJsonElement(response.bodyAsText())
            .jsonObject["object"]!!.jsonObject["sha"]!!.jsonPrimitive.content
    }

    override suspend fun createBranch(owner: String, repo: String, name: String, sha: String) {
        val token = installationToken()
        val body = buildJsonObject {
            put("ref", "refs/heads/$name")
            put("sha", sha)
        }
        val response = httpClient.post("$GITHUB_API/repos/$owner/$repo/git/refs") {
            ghAuth(token)
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
        if (response.status == HttpStatusCode.UnprocessableEntity) {
            log.info("Branch {} already exists — skipping creation", name)
            return
        }
        check(response.status.isSuccess()) {
            "GitHub createBranch failed (${response.status}): ${response.bodyAsText()}"
        }
    }

    override suspend fun updateFile(
        owner: String,
        repo: String,
        path: String,
        branch: String,
        content: String,
        currentSha: String,
    ) {
        val token = installationToken()
        val encoded = Base64.getEncoder().encodeToString(content.toByteArray())
        val body = buildJsonObject {
            put("message", "analyst: propose skill improvement")
            put("content", encoded)
            put("sha", currentSha)
            put("branch", branch)
        }
        val response = httpClient.put("$GITHUB_API/repos/$owner/$repo/contents/$path") {
            ghAuth(token)
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
        check(response.status.isSuccess()) {
            "GitHub updateFile failed (${response.status}): ${response.bodyAsText()}"
        }
    }

    override suspend fun createPr(
        owner: String,
        repo: String,
        title: String,
        body: String,
        head: String,
        base: String,
    ): String {
        val token = installationToken()
        val reqBody = buildJsonObject {
            put("title", title)
            put("body", body)
            put("head", head)
            put("base", base)
        }
        val response = httpClient.post("$GITHUB_API/repos/$owner/$repo/pulls") {
            ghAuth(token)
            contentType(ContentType.Application.Json)
            setBody(reqBody.toString())
        }
        check(response.status.isSuccess()) {
            "GitHub createPr failed (${response.status}): ${response.bodyAsText()}"
        }
        return json.parseToJsonElement(response.bodyAsText())
            .jsonObject["html_url"]!!.jsonPrimitive.content
    }

    // Minted fresh per call — 10-min JWT expiry is plenty for a single PR flow
    private suspend fun installationToken(): String {
        val jwt = buildJwt()
        val response = httpClient.post("$GITHUB_API/app/installations/$installationId/access_tokens") {
            header("Authorization", "Bearer $jwt")
            header("Accept", GH_ACCEPT)
            header("X-GitHub-Api-Version", GH_API_VERSION)
        }
        check(response.status.isSuccess()) {
            "GitHub installationToken failed (${response.status}): ${response.bodyAsText()}"
        }
        return json.parseToJsonElement(response.bodyAsText())
            .jsonObject["token"]!!.jsonPrimitive.content
    }

    internal fun buildJwt(): String {
        val nowSecs = System.currentTimeMillis() / 1000
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("""{"alg":"RS256","typ":"JWT"}""".toByteArray())
        val payload = encoder.encodeToString(
            """{"iat":${nowSecs - 60},"exp":${nowSecs + 540},"iss":"$appId"}""".toByteArray()
        )
        val signingInput = "$header.$payload"
        val privateKey = loadPrivateKey(privateKeyPem)
        val sig = Signature.getInstance("SHA256withRSA").apply {
            initSign(privateKey)
            update(signingInput.toByteArray())
        }.sign()
        return "$signingInput.${encoder.encodeToString(sig)}"
    }

    private fun io.ktor.client.request.HttpRequestBuilder.ghAuth(token: String) {
        header("Authorization", "Bearer $token")
        header("Accept", GH_ACCEPT)
        header("X-GitHub-Api-Version", GH_API_VERSION)
    }
}

internal fun loadPrivateKey(pem: String): java.security.PrivateKey {
    val clean = pem.lines().filter { !it.startsWith("-----") }.joinToString("")
    val bytes = Base64.getDecoder().decode(clean)
    val pkcs8Bytes = if (pem.contains("BEGIN RSA PRIVATE KEY")) wrapPkcs1(bytes) else bytes
    return KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(pkcs8Bytes))
}

// Wraps a PKCS#1 RSAPrivateKey DER blob in a PKCS#8 PrivateKeyInfo envelope so
// Java's KeyFactory can consume it. No Bouncy Castle needed — pure DER encoding.
private fun wrapPkcs1(pkcs1: ByteArray): ByteArray {
    // AlgorithmIdentifier for RSA: SEQUENCE { OID 1.2.840.113549.1.1.1, NULL }
    val algId = byteArrayOf(
        0x30, 0x0d,
        0x06, 0x09, 0x2a, 0x86.toByte(), 0x48, 0x86.toByte(), 0xf7.toByte(), 0x0d, 0x01, 0x01, 0x01,
        0x05, 0x00,
    )
    val version = byteArrayOf(0x02, 0x01, 0x00) // INTEGER 0
    val inner = version + algId + derTlv(0x04, pkcs1)
    return derTlv(0x30, inner)
}

private fun derTlv(tag: Int, data: ByteArray): ByteArray {
    val len = when {
        data.size < 0x80 -> byteArrayOf(data.size.toByte())
        data.size < 0x100 -> byteArrayOf(0x81.toByte(), data.size.toByte())
        else -> byteArrayOf(0x82.toByte(), (data.size shr 8).toByte(), (data.size and 0xff).toByte())
    }
    return byteArrayOf(tag.toByte()) + len + data
}

@Serializable
private data class PrItem(val title: String)

@Serializable
private data class FileContentsResponse(val content: String, val sha: String)
