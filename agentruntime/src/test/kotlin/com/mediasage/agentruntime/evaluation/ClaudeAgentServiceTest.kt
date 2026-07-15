package com.mediasage.agentruntime.evaluation

import com.mediasage.agentruntime.AnthropicClient
import com.mediasage.agentruntime.feedback.github.FileContents
import com.mediasage.agentruntime.feedback.github.GitHubApiClient
import com.mediasage.agentruntime.feedback.github.PrDetails
import com.mediasage.agentruntime.service.JiraApiClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class ClaudeAgentServiceTest {

    @Test
    fun `posts the verdict to jira unchanged`() = runTest {
        val verdict = buildVerdict()
        val jira = RecordingJiraApiClient()
        buildService(verdict = verdict, jira = jira).evaluate("MS-100", 1)

        assertEquals(1, jira.comments.size)
        assertEquals(verdict, jira.comments.first())
    }

    @Test
    fun `reads the pr but posts no comment to the github pr`() = runTest {
        val github = RecordingGitHubApiClient()
        buildService(verdict = buildVerdict(), github = github).evaluate("MS-100", 1)

        // The GitHubApiClient has no PR-comment surface at all — the judge only reads.
        assertEquals(listOf("getPrDetails", "getPrDiff"), github.calls)
    }

    // ---- Helpers ----

    private fun buildVerdict(): String = """
        🤖 Agent: Judge verdict for MS-100

        Task: Judge verdict on PR #1

        AC compliance:
        ✅ Feature implemented — src/Main.kt

        Overall: PASS
    """.trimIndent()

    private fun buildService(
        verdict: String,
        github: GitHubApiClient = RecordingGitHubApiClient(),
        jira: JiraApiClient = RecordingJiraApiClient(),
    ): ClaudeAgentService {
        val escapedVerdict = Json.encodeToString(verdict)
        val claudeJson = """{"content":[{"type":"text","text":$escapedVerdict}]}"""
        val mockEngine = MockEngine {
            respond(
                content = claudeJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        return ClaudeAgentService(
            anthropicClient = AnthropicClient(HttpClient(mockEngine), "test-token", "https://api.anthropic.com"),
            githubApiClient = github,
            jiraApiClient = jira,
            model = "claude-sonnet-4-6",
            repoOwner = "test-owner",
            repoName = "test-repo",
        )
    }
}

// ---- Fakes ----

private class RecordingGitHubApiClient : GitHubApiClient {
    val calls = mutableListOf<String>()

    override suspend fun hasOpenFeedbackPr(owner: String, repo: String) = false
    override suspend fun getFileContents(owner: String, repo: String, path: String) = FileContents("", "")
    override suspend fun getBranchSha(owner: String, repo: String, branch: String) = ""
    override suspend fun createBranch(owner: String, repo: String, name: String, sha: String) = Unit
    override suspend fun updateFile(
        owner: String, repo: String, path: String, branch: String, content: String, currentSha: String,
    ) = Unit
    override suspend fun createPr(owner: String, repo: String, title: String, body: String, head: String, base: String) = ""
    override suspend fun getPrDetails(owner: String, repo: String, prNumber: Int): PrDetails {
        calls.add("getPrDetails")
        return PrDetails("", "", "", "")
    }
    override suspend fun getPrDiff(owner: String, repo: String, prNumber: Int): String {
        calls.add("getPrDiff")
        return ""
    }
}

private class RecordingJiraApiClient : JiraApiClient(
    httpClient = HttpClient(MockEngine { respond("", HttpStatusCode.OK) }),
    cloudId = "",
    email = "",
    apiToken = "",
) {
    val comments = mutableListOf<String>()

    override suspend fun getTicketContent(ticketKey: String): String? = null
    override suspend fun addComment(ticketKey: String, body: String) { comments.add(body) }
}
