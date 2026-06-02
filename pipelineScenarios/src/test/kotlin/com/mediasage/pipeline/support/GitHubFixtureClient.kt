package com.mediasage.pipeline.support

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64

/**
 * Lightweight GitHub API client for E2E scenario fixture setup and teardown.
 *
 * Manages the full lifecycle of test branches and PRs against [repo]:
 * - Syncs [E2E_BASE_BRANCH] with main before each scenario run
 * - Creates short-lived feature branches off [E2E_BASE_BRANCH]
 * - Pushes scratch commits to introduce real merge conflicts or trivial changes
 * - Opens and closes PRs targeting [E2E_BASE_BRANCH]
 *
 * Only ever touches files under `e2e-scratch/` — real code is never affected.
 * Authenticates via a personal [token] with repo write access.
 */
class GitHubFixtureClient(
    private val httpClient: HttpClient,
    private val token: String,
    private val owner: String = "michael-gonzalez-dev",
    private val repo: String = "media-sage"
) {
    private val baseUrl = "https://api.github.com"
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Fast-forwards [branch] to main's current HEAD.
     * Creates the branch if it does not exist yet (e.g. first run against a fresh repo).
     * Uses force-update so test commits from previous runs don't block the sync.
     * Call this at the start of every full pipeline scenario before creating fixture branches.
     */
    suspend fun syncBranchWithMain(branch: String) {
        val mainSha = getBranchSha("main")
        if (branchExists(branch)) {
            patch("$baseUrl/repos/$owner/$repo/git/refs/heads/$branch") {
                """{"sha":"$mainSha","force":true}"""
            }
        } else {
            post("$baseUrl/repos/$owner/$repo/git/refs") {
                """{"ref":"refs/heads/$branch","sha":"$mainSha"}"""
            }
        }
    }

    /**
     * Creates a branch named [name] off [fromRef] (default: [E2E_BASE_BRANCH]).
     */
    suspend fun createBranch(name: String, fromRef: String = E2E_BASE_BRANCH) {
        val sha = getBranchSha(fromRef)
        post("$baseUrl/repos/$owner/$repo/git/refs") {
            """{"ref":"refs/heads/$name","sha":"$sha"}"""
        }
    }

    /**
     * Creates or updates [path] on [branch] with [content], using [message] as the commit message.
     * Handles both new files and updates to existing files transparently.
     */
    suspend fun pushCommit(branch: String, path: String, content: String, message: String) {
        val encoded = Base64.getEncoder().encodeToString(content.toByteArray())
        val existingSha = getFileSha(branch, path)
        val body = buildString {
            append("""{"message":${json.encodeToString(kotlinx.serialization.json.JsonPrimitive(message))}""")
            append(""","content":"$encoded"""")
            append(""","branch":"$branch"""")
            if (existingSha != null) append(""","sha":"$existingSha"""")
            append("}")
        }
        put("$baseUrl/repos/$owner/$repo/contents/$path") { body }
    }

    /**
     * Opens a pull request from [branch] targeting [base] (default: [E2E_BASE_BRANCH]).
     * Returns the PR number for later use in the scenario and teardown.
     */
    suspend fun openPullRequest(
        branch: String,
        title: String,
        body: String = "",
        base: String = E2E_BASE_BRANCH
    ): Int {
        val escapedTitle = title.replace("\"", "\\\"")
        val escapedBody = body.replace("\"", "\\\"")
        val response = post("$baseUrl/repos/$owner/$repo/pulls") {
            """{"title":"$escapedTitle","head":"$branch","base":"$base","body":"$escapedBody"}"""
        }
        return json.parseToJsonElement(response).jsonObject["number"]!!.jsonPrimitive.int
    }

    /**
     * Closes the PR with [prNumber] without merging.
     * Safe to call in @AfterEach even if the PR was already closed.
     */
    suspend fun closePullRequest(prNumber: Int) {
        runCatching {
            patch("$baseUrl/repos/$owner/$repo/pulls/$prNumber") {
                """{"state":"closed"}"""
            }
        }
    }

    /**
     * Deletes the branch named [name].
     * Safe to call in @AfterEach even if the branch was already deleted.
     */
    suspend fun deleteBranch(name: String) {
        runCatching {
            httpClient.delete("$baseUrl/repos/$owner/$repo/git/refs/heads/$name") {
                header("Authorization", "Bearer $token")
                header("Accept", "application/vnd.github+json")
                header("X-GitHub-Api-Version", "2022-11-28")
            }
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private suspend fun branchExists(branch: String): Boolean {
        val response = httpClient.get("$baseUrl/repos/$owner/$repo/git/refs/heads/$branch") {
            header("Authorization", "Bearer $token")
            header("Accept", "application/vnd.github+json")
            header("X-GitHub-Api-Version", "2022-11-28")
        }
        return response.status != HttpStatusCode.NotFound && response.status.isSuccess()
    }

    private suspend fun getBranchSha(branch: String): String {
        val response = httpClient.get("$baseUrl/repos/$owner/$repo/git/refs/heads/$branch") {
            header("Authorization", "Bearer $token")
            header("Accept", "application/vnd.github+json")
            header("X-GitHub-Api-Version", "2022-11-28")
        }
        val body = response.bodyAsText()
        val element = json.parseToJsonElement(body)
        return element.jsonObject["object"]?.jsonObject?.get("sha")?.jsonPrimitive?.content
            ?: error("getBranchSha($branch) failed — status ${response.status}, body: $body")
    }

    private suspend fun getFileSha(branch: String, path: String): String? {
        val response = httpClient.get(
            "$baseUrl/repos/$owner/$repo/contents/$path?ref=$branch"
        ) {
            header("Authorization", "Bearer $token")
            header("Accept", "application/vnd.github+json")
            header("X-GitHub-Api-Version", "2022-11-28")
        }
        if (response.status == HttpStatusCode.NotFound) return null
        return json.parseToJsonElement(response.bodyAsText())
            .jsonObject["sha"]?.jsonPrimitive?.content
    }

    private suspend fun post(url: String, body: () -> String): String {
        return httpClient.post(url) {
            header("Authorization", "Bearer $token")
            header("Accept", "application/vnd.github+json")
            header("X-GitHub-Api-Version", "2022-11-28")
            setBody(TextContent(body(), ContentType.Application.Json))
        }.bodyAsText()
    }

    private suspend fun put(url: String, body: () -> String) {
        httpClient.put(url) {
            header("Authorization", "Bearer $token")
            header("Accept", "application/vnd.github+json")
            header("X-GitHub-Api-Version", "2022-11-28")
            setBody(TextContent(body(), ContentType.Application.Json))
        }
    }

    private suspend fun patch(url: String, body: () -> String) {
        httpClient.patch(url) {
            header("Authorization", "Bearer $token")
            header("Accept", "application/vnd.github+json")
            header("X-GitHub-Api-Version", "2022-11-28")
            setBody(TextContent(body(), ContentType.Application.Json))
        }
    }

    companion object {
        const val E2E_BASE_BRANCH = "e2e-base"
    }
}
