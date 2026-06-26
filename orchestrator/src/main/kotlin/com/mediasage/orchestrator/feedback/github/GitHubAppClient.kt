package com.mediasage.orchestrator.feedback.github

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
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
import java.security.interfaces.RSAPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.Date

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

    // Global requestTimeoutMillis is 60s, but this call fires after ~4–5 min of DB work on a cold
    // instance. Override to 15s — the endpoint normally responds in <1s.
    private suspend fun installationToken(): String {
        val jwt = buildJwt()
        val response = httpClient.post("$GITHUB_API/app/installations/$installationId/access_tokens") {
            header("Authorization", "Bearer $jwt")
            header("Accept", GH_ACCEPT)
            header("X-GitHub-Api-Version", GH_API_VERSION)
            timeout { requestTimeoutMillis = 15_000 }
        }
        check(response.status.isSuccess()) {
            "GitHub installationToken failed (${response.status}): ${response.bodyAsText()}"
        }
        return json.parseToJsonElement(response.bodyAsText())
            .jsonObject["token"]!!.jsonPrimitive.content
    }

    private fun buildJwt(): String {
        val nowMs = System.currentTimeMillis()
        return JWT.create()
            .withIssuedAt(Date(nowMs - 60_000))
            .withExpiresAt(Date(nowMs + 540_000))
            .withIssuer(appId)
            .sign(Algorithm.RSA256(null, loadPrivateKey(privateKeyPem)))
    }

    private fun io.ktor.client.request.HttpRequestBuilder.ghAuth(token: String) {
        header("Authorization", "Bearer $token")
        header("Accept", GH_ACCEPT)
        header("X-GitHub-Api-Version", GH_API_VERSION)
    }
}

// Workers store the key as base64(PEM). If the input has no PEM header, decode it first.
internal fun loadPrivateKey(pem: String): RSAPrivateKey {
    val pemStr = if (pem.startsWith("-----")) pem else String(Base64.getDecoder().decode(pem))
    val clean = pemStr.lines().filter { !it.startsWith("-----") }.joinToString("")
    val bytes = Base64.getDecoder().decode(clean)
    return KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(bytes)) as RSAPrivateKey
}

@Serializable
private data class PrItem(val title: String)

@Serializable
private data class FileContentsResponse(val content: String, val sha: String)
