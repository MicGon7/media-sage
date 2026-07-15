package com.mediasage.agentruntime.feedback

import com.mediasage.agentruntime.feedback.detector.DetectedPattern
import com.mediasage.agentruntime.feedback.detector.PatternDetector
import com.mediasage.agentruntime.feedback.github.FileContents
import com.mediasage.agentruntime.feedback.github.GitHubApiClient
import com.mediasage.agentruntime.feedback.pr.FeedbackPrService
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FeedbackPrServiceTest {

    @Test
    fun skipsWhenNoPatternsDetected() = runTest {
        val github = FakeGitHubApiClient()
        val service = buildService(detector = FakePatternDetector(), githubClient = github)
        service.proposePatch()
        assertTrue(github.createPrCalls.isEmpty(), "No PR should be opened when no patterns are detected")
    }

    @Test
    fun skipsWhenGitHubCheckThrows() = runTest {
        val github = FakeGitHubApiClient(throwOnHasOpenPr = true)
        val service = buildService(
            detector = FakePatternDetector(listOf(DetectedPattern.GateFailure("tests", 3, 7))),
            githubClient = github,
        )
        service.proposePatch() // must not throw
        assertTrue(github.createPrCalls.isEmpty(), "No PR should be opened when the GitHub check throws")
    }

    @Test
    fun skipsWhenOpenFeedbackPrAlreadyExists() = runTest {
        val github = FakeGitHubApiClient(openPrExists = true)
        val service = buildService(
            detector = FakePatternDetector(listOf(DetectedPattern.GateFailure("tests", 3, 7))),
            githubClient = github,
        )
        service.proposePatch()
        assertTrue(github.createPrCalls.isEmpty(), "PR must not be opened when an open feedback PR already exists")
    }

    @Test
    fun opensPrForGateFailurePattern() = runTest {
        val github = FakeGitHubApiClient()
        val service = buildService(
            detector = FakePatternDetector(listOf(DetectedPattern.GateFailure("detekt", 4, 7))),
            githubClient = github,
            proposedContent = "updated skill content",
        )
        service.proposePatch()
        assertEquals(1, github.createPrCalls.size, "Exactly one PR must be opened")
        val (title, head, base) = github.createPrCalls.first()
        assertTrue(title.startsWith("[Feedback]"), "PR title must be prefixed with [Feedback]")
        assertTrue(title.contains("detekt"), "PR title must reference the failing gate")
        assertTrue(head.startsWith("feedback/scan-"), "Branch must follow feedback/scan-YYYY-MM-DD convention")
        assertEquals("main", base)
    }

    @Test
    fun opensPrForLowRubricScorePattern() = runTest {
        val github = FakeGitHubApiClient()
        val service = buildService(
            detector = FakePatternDetector(listOf(DetectedPattern.LowRubricScore("retry_recovery", 2.1, 3, 7))),
            githubClient = github,
            proposedContent = "updated skill content",
        )
        service.proposePatch()
        assertEquals(1, github.createPrCalls.size)
        val (title, _, _) = github.createPrCalls.first()
        assertTrue(title.contains("retry recovery"), "PR title must reference the failing criterion (underscores → spaces)")
    }

    @Test
    fun onlyFirstPatternProducesAPr() = runTest {
        val github = FakeGitHubApiClient()
        val service = buildService(
            detector = FakePatternDetector(
                listOf(
                    DetectedPattern.GateFailure("tests", 5, 7),
                    DetectedPattern.GateFailure("detekt", 3, 7),
                )
            ),
            githubClient = github,
            proposedContent = "updated",
        )
        service.proposePatch()
        assertEquals(1, github.createPrCalls.size, "Only the highest-signal pattern should produce a PR per invocation")
    }

    @Test
    fun proposedContentIsWrittenToSkillFile() = runTest {
        val github = FakeGitHubApiClient()
        val service = buildService(
            detector = FakePatternDetector(listOf(DetectedPattern.GateFailure("compile", 3, 7))),
            githubClient = github,
            proposedContent = "new skill content from feedback scanner",
        )
        service.proposePatch()
        assertNotNull(github.lastUpdatedContent)
        assertEquals("new skill content from feedback scanner", github.lastUpdatedContent)
    }

    // ---- Helpers ----

    private fun buildService(
        detector: PatternDetector,
        githubClient: GitHubApiClient,
        proposedContent: String = "patched content",
    ): FeedbackPrService {
        val mockResponse = """
            {"content":[{"type":"tool_use","input":{"proposed_content":"$proposedContent"}}]}
        """.trimIndent()
        val mockEngine = MockEngine {
            respond(
                content = mockResponse,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        return FeedbackPrService(
            detector = detector,
            githubClient = githubClient,
            httpClient = mockClient,
            authToken = "test-token",
            claudeBaseUrl = "https://api.anthropic.com",
            repoOwner = "test-owner",
            repoName = "test-repo",
            model = "claude-sonnet-4-6",
        )
    }
}

// ---- Fakes ----

private class FakePatternDetector(
    private val patterns: List<DetectedPattern> = emptyList(),
) : PatternDetector {
    override fun detectPatterns(windowDays: Int, minOccurrences: Int): List<DetectedPattern> = patterns
}

private class FakeGitHubApiClient(
    private val openPrExists: Boolean = false,
    private val throwOnHasOpenPr: Boolean = false,
    private val fileContents: FileContents = FileContents("original skill content", "sha-abc"),
    private val prUrl: String = "https://github.com/test-owner/test-repo/pull/1",
) : GitHubApiClient {
    val createPrCalls = mutableListOf<Triple<String, String, String>>()
    var lastUpdatedContent: String? = null

    override suspend fun hasOpenFeedbackPr(owner: String, repo: String): Boolean {
        if (throwOnHasOpenPr) error("GitHub installationToken failed after 3 attempts: simulated timeout")
        return openPrExists
    }
    override suspend fun getFileContents(owner: String, repo: String, path: String) = fileContents
    override suspend fun getBranchSha(owner: String, repo: String, branch: String) = "sha-of-main"
    override suspend fun createBranch(owner: String, repo: String, name: String, sha: String) = Unit
    override suspend fun updateFile(
        owner: String,
        repo: String,
        path: String,
        branch: String,
        content: String,
        currentSha: String,
    ) {
        lastUpdatedContent = content
    }
    override suspend fun createPr(
        owner: String,
        repo: String,
        title: String,
        body: String,
        head: String,
        base: String,
    ): String {
        createPrCalls.add(Triple(title, head, base))
        return prUrl
    }
    override suspend fun getPrDetails(owner: String, repo: String, prNumber: Int) =
        com.mediasage.agentruntime.feedback.github.PrDetails("", "", "", "")
    override suspend fun getPrDiff(owner: String, repo: String, prNumber: Int) = ""
}
