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
import kotlin.test.assertTrue

class ClaudeAgentServiceTest {

    @Test
    fun `posts inline comment for each code observation in verdict`() = runTest {
        val verdict = buildVerdict(
            observations = listOf(
                "src/main/Foo.kt:42 — hardcoded string literal should be a constant",
                "src/main/Bar.kt:10 — layer boundary violation visible in imports",
            )
        )
        val github = RecordingGitHubApiClient()
        buildService(verdict = verdict, github = github).evaluate("MS-100", 1)

        assertEquals(2, github.inlineComments.size)
        assertEquals("src/main/Foo.kt", github.inlineComments[0].path)
        assertEquals(42, github.inlineComments[0].line)
        assertEquals("hardcoded string literal should be a constant", github.inlineComments[0].body)
        assertEquals("src/main/Bar.kt", github.inlineComments[1].path)
        assertEquals(10, github.inlineComments[1].line)
    }

    @Test
    fun `skips inline comments when verdict has no code observations section`() = runTest {
        val verdict = buildVerdict(observations = emptyList())
        val github = RecordingGitHubApiClient()
        buildService(verdict = verdict, github = github).evaluate("MS-100", 1)

        assertTrue(github.inlineComments.isEmpty())
    }

    @Test
    fun `top-level comment is still posted when inline comment call fails`() = runTest {
        val verdict = buildVerdict(
            observations = listOf("src/main/Foo.kt:42 — some observation")
        )
        val github = RecordingGitHubApiClient(throwOnInlineComment = true)
        buildService(verdict = verdict, github = github).evaluate("MS-100", 1)

        assertEquals(1, github.prComments.size, "Top-level PR comment must still be posted on inline failure")
        assertTrue(github.inlineComments.isEmpty(), "Failed inline comment must not be recorded")
    }

    @Test
    fun `top-level comment and jira comment are unchanged from verdict text`() = runTest {
        val verdict = buildVerdict(
            observations = listOf("src/main/Foo.kt:42 — some observation")
        )
        val github = RecordingGitHubApiClient()
        val jira = RecordingJiraApiClient()
        buildService(verdict = verdict, github = github, jira = jira).evaluate("MS-100", 1)

        assertTrue(github.prComments.first().contains("Overall: PASS"))
        assertTrue(jira.comments.first().contains("Overall: PASS"))
    }

    // ---- Helpers ----

    private fun buildVerdict(observations: List<String>): String {
        val obsSection = if (observations.isEmpty()) "" else buildString {
            appendLine()
            appendLine("Code observations:")
            observations.forEach { appendLine("- $it") }
        }
        return """
            🤖 Agent: Judge verdict for MS-100

            Task: Judge verdict on PR #1

            AC compliance:
            ✅ Feature implemented — src/Main.kt

            Overall: PASS$obsSection
        """.trimIndent()
    }

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

private data class InlineComment(val path: String, val line: Int, val body: String)

private class RecordingGitHubApiClient(
    private val throwOnInlineComment: Boolean = false,
) : GitHubApiClient {
    val prComments = mutableListOf<String>()
    val inlineComments = mutableListOf<InlineComment>()

    override suspend fun hasOpenFeedbackPr(owner: String, repo: String) = false
    override suspend fun getFileContents(owner: String, repo: String, path: String) = FileContents("", "")
    override suspend fun getBranchSha(owner: String, repo: String, branch: String) = ""
    override suspend fun createBranch(owner: String, repo: String, name: String, sha: String) = Unit
    override suspend fun updateFile(
        owner: String, repo: String, path: String, branch: String, content: String, currentSha: String,
    ) = Unit
    override suspend fun createPr(owner: String, repo: String, title: String, body: String, head: String, base: String) = ""
    override suspend fun getPrDetails(owner: String, repo: String, prNumber: Int) = PrDetails("", "", "", "")
    override suspend fun getPrDiff(owner: String, repo: String, prNumber: Int) = ""
    override suspend fun postPrComment(owner: String, repo: String, prNumber: Int, body: String) { prComments.add(body) }
    override suspend fun postInlineReviewComment(
        owner: String, repo: String, prNumber: Int, path: String, line: Int, body: String,
    ) {
        check(!throwOnInlineComment) { "Simulated inline comment failure" }
        inlineComments.add(InlineComment(path, line, body))
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
