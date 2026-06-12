package com.mediasage.orchestrator

import com.mediasage.orchestrator.plugins.configureContentNegotiation
import com.mediasage.orchestrator.plugins.configureStatusPages
import com.mediasage.orchestrator.routes.githubWebhookRoutes
import com.mediasage.orchestrator.service.AgentLauncher
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals

private const val TEST_SECRET = "test-webhook-secret"
private const val BOT_LOGIN = "media-sage-worker[bot]"
private const val HUMAN_LOGIN = "human-reviewer"

class GitHubWebhookRouteTest {

    @Test
    fun missingEventHeaderReturns400() = testGitHubApp {
        val body = prReviewPayload()
        val response = client.post("/webhook/github") {
            contentType(ContentType.Application.Json)
            header("X-Hub-Signature-256", validSignature(TEST_SECRET, body))
            setBody(body)
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun missingSignatureReturns401() = testGitHubApp {
        val response = client.post("/webhook/github") {
            contentType(ContentType.Application.Json)
            header("X-GitHub-Event", "pull_request_review")
            setBody(prReviewPayload())
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun invalidSignatureReturns401() = testGitHubApp {
        val response = client.post("/webhook/github") {
            contentType(ContentType.Application.Json)
            header("X-GitHub-Event", "pull_request_review")
            header("X-Hub-Signature-256", "sha256=badsignature")
            setBody(prReviewPayload())
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun irrelevantEventReturns200() = testGitHubApp {
        val body = prReviewPayload()
        val response = client.post("/webhook/github") {
            contentType(ContentType.Application.Json)
            header("X-GitHub-Event", "push")
            header("X-Hub-Signature-256", validSignature(TEST_SECRET, body))
            setBody(body)
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun agentCommentPrefixReturns200WithoutFiring() = testGitHubApp {
        val body = prReviewPayload(reviewBody = "🤖 **Agent:** Already addressed this.")
        val response = client.post("/webhook/github") {
            contentType(ContentType.Application.Json)
            header("X-GitHub-Event", "pull_request_review")
            header("X-Hub-Signature-256", validSignature(TEST_SECRET, body))
            setBody(body)
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun approvedReviewReturns200WithoutFiring() = testGitHubApp {
        val body = prReviewPayload(state = "approved", reviewBody = "LGTM")
        val response = client.post("/webhook/github") {
            contentType(ContentType.Application.Json)
            header("X-GitHub-Event", "pull_request_review")
            header("X-Hub-Signature-256", validSignature(TEST_SECRET, body))
            setBody(body)
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun botAuthoredPrChangesRequestedReturns200() = testGitHubApp {
        val body = prReviewPayload(prAuthorLogin = BOT_LOGIN, state = "changes_requested", reviewBody = "Please extract this to a helper.")
        val response = client.post("/webhook/github") {
            contentType(ContentType.Application.Json)
            header("X-GitHub-Event", "pull_request_review")
            header("X-Hub-Signature-256", validSignature(TEST_SECRET, body))
            setBody(body)
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun uppercaseChangesRequestedFiresAgent() {
        val tracking = FakeAgentLauncher()
        testGitHubApp(agentService = tracking) {
            val body = prReviewPayload(prAuthorLogin = BOT_LOGIN, state = "CHANGES_REQUESTED", reviewBody = "Please fix this.")
            val response = client.post("/webhook/github") {
                contentType(ContentType.Application.Json)
                header("X-GitHub-Event", "pull_request_review")
                header("X-Hub-Signature-256", validSignature(TEST_SECRET, body))
                setBody(body)
            }
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(1, tracking.agentLaunches, "Agent must fire for CHANGES_REQUESTED (uppercase)")
        }
    }

    @Test
    fun changesRequestedPassesReviewerLogin() {
        val tracking = FakeAgentLauncher()
        testGitHubApp(agentService = tracking) {
            val body = prReviewPayload(
                prAuthorLogin = BOT_LOGIN,
                state = "changes_requested",
                senderLogin = "jane-reviewer",
                reviewBody = "Please fix this."
            )
            val response = client.post("/webhook/github") {
                contentType(ContentType.Application.Json)
                header("X-GitHub-Event", "pull_request_review")
                header("X-Hub-Signature-256", validSignature(TEST_SECRET, body))
                setBody(body)
            }
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("jane-reviewer", tracking.lastReviewerLogin, "reviewerLogin must be passed from sender.login")
        }
    }

    @Test
    fun commentedReviewFiresCommentAgent() {
        val tracking = FakeAgentLauncher()
        testGitHubApp(agentService = tracking) {
            val body = prReviewPayload(prAuthorLogin = BOT_LOGIN, state = "commented", reviewBody = "What does this function do?")
            val response = client.post("/webhook/github") {
                contentType(ContentType.Application.Json)
                header("X-GitHub-Event", "pull_request_review")
                header("X-Hub-Signature-256", validSignature(TEST_SECRET, body))
                setBody(body)
            }
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(0, tracking.agentLaunches, "Full agent must NOT fire for comment review")
            assertEquals(1, tracking.commentReviewLaunches, "Comment agent must fire for commented review")
        }
    }

    @Test
    fun humanAuthoredPrReturns200WithoutFiring() {
        val tracking = FakeAgentLauncher()
        testGitHubApp(agentService = tracking) {
            val body = prReviewPayload(
                prAuthorLogin = HUMAN_LOGIN,
                state = "changes_requested",
                reviewBody = "Please extract this to a helper."
            )
            val response = client.post("/webhook/github") {
                contentType(ContentType.Application.Json)
                header("X-GitHub-Event", "pull_request_review")
                header("X-Hub-Signature-256", validSignature(TEST_SECRET, body))
                setBody(body)
            }
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(0, tracking.agentLaunches, "Agent must NOT fire for human-authored PR")
        }
    }

    @Test
    fun botSubmittedReviewIsIgnoredToPreventFeedbackLoop() {
        val tracking = FakeAgentLauncher()
        testGitHubApp(agentService = tracking) {
            val body = prReviewPayload(
                prAuthorLogin = BOT_LOGIN,
                state = "commented",
                senderLogin = BOT_LOGIN,
                reviewBody = "🤖 **Agent:** Judge verdict for MS-42"
            )
            val response = client.post("/webhook/github") {
                contentType(ContentType.Application.Json)
                header("X-GitHub-Event", "pull_request_review")
                header("X-Hub-Signature-256", validSignature(TEST_SECRET, body))
                setBody(body)
            }
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(0, tracking.agentLaunches, "Agent must NOT fire when bot submitted the review")
            assertEquals(0, tracking.commentReviewLaunches, "Comment agent must NOT fire when bot submitted the review")
        }
    }

    @Test
    fun inlineCommentEditedIsIgnored() = testGitHubApp {
        val body = reviewCommentPayload(action = "edited", commentBody = "Updated: please rename this variable.")
        val response = client.post("/webhook/github") {
            contentType(ContentType.Application.Json)
            header("X-GitHub-Event", "pull_request_review_comment")
            header("X-Hub-Signature-256", validSignature(TEST_SECRET, body))
            setBody(body)
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun branchWithNoTicketKeyReturns200WithoutFiring() = testGitHubApp {
        val body = prReviewPayload(prAuthorLogin = BOT_LOGIN, branchRef = "main", state = "changes_requested", reviewBody = "Fix this.")
        val response = client.post("/webhook/github") {
            contentType(ContentType.Application.Json)
            header("X-GitHub-Event", "pull_request_review")
            header("X-Hub-Signature-256", validSignature(TEST_SECRET, body))
            setBody(body)
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun mergeConflictDequeueFiresConflictResolver() {
        val tracking = FakeAgentLauncher()
        testGitHubApp(agentService = tracking) {
            val body = dequeuePayload(prAuthorLogin = BOT_LOGIN, reason = "merge_conflict", baseBranch = "release/1.0")
            val response = client.post("/webhook/github") {
                contentType(ContentType.Application.Json)
                header("X-GitHub-Event", "pull_request")
                header("X-Hub-Signature-256", validSignature(TEST_SECRET, body))
                setBody(body)
            }
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(1, tracking.conflictResolutionLaunches,
                "Conflict resolver must fire for bot-authored PR with merge_conflict dequeue")
            assertEquals("release/1.0", tracking.lastBaseBranch,
                "Base branch from payload must be passed to launchForConflictResolution")
        }
    }

    @Test
    fun ciFailureDequeueIsIgnored() {
        val tracking = FakeAgentLauncher()
        testGitHubApp(agentService = tracking) {
            val body = dequeuePayload(prAuthorLogin = BOT_LOGIN, reason = "checks_failed")
            val response = client.post("/webhook/github") {
                contentType(ContentType.Application.Json)
                header("X-GitHub-Event", "pull_request")
                header("X-Hub-Signature-256", validSignature(TEST_SECRET, body))
                setBody(body)
            }
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(0, tracking.conflictResolutionLaunches, "Conflict resolver must NOT fire for CI failure dequeue")
        }
    }

    @Test
    fun humanAuthoredPrDequeueIsIgnored() {
        val tracking = FakeAgentLauncher()
        testGitHubApp(agentService = tracking) {
            val body = dequeuePayload(prAuthorLogin = HUMAN_LOGIN, reason = "merge_conflict")
            val response = client.post("/webhook/github") {
                contentType(ContentType.Application.Json)
                header("X-GitHub-Event", "pull_request")
                header("X-Hub-Signature-256", validSignature(TEST_SECRET, body))
                setBody(body)
            }
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(0, tracking.conflictResolutionLaunches, "Conflict resolver must NOT fire for human-authored PR")
        }
    }

    @Test
    fun dequeueWithNoTicketKeyIsIgnored() {
        val tracking = FakeAgentLauncher()
        testGitHubApp(agentService = tracking) {
            val body = dequeuePayload(prAuthorLogin = BOT_LOGIN, reason = "merge_conflict", branchRef = "hotfix/no-ticket-here")
            val response = client.post("/webhook/github") {
                contentType(ContentType.Application.Json)
                header("X-GitHub-Event", "pull_request")
                header("X-Hub-Signature-256", validSignature(TEST_SECRET, body))
                setBody(body)
            }
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(0, tracking.conflictResolutionLaunches, "Conflict resolver must NOT fire when branch has no ticket key")
        }
    }
}

// ---- Payload builders ----

private fun dequeuePayload(
    prAuthorLogin: String = BOT_LOGIN,
    reason: String = "merge_conflict",
    branchRef: String = "feature/MS-42-some-feature",
    baseBranch: String = "main",
    prNumber: Int = 42,
    senderLogin: String = "github-merge-queue[bot]"
) = """
{
  "action": "dequeued",
  "reason": "$reason",
  "sender": { "login": "$senderLogin" },
  "pull_request": {
    "number": $prNumber,
    "head": { "ref": "$branchRef" },
    "base": { "ref": "$baseBranch" },
    "user": { "login": "$prAuthorLogin" }
  }
}
""".trimIndent()

private fun prReviewPayload(
    prAuthorLogin: String = BOT_LOGIN,
    action: String = "submitted",
    senderLogin: String = HUMAN_LOGIN,
    branchRef: String = "feature/MS-42-some-feature",
    prNumber: Int = 42,
    state: String = "commented",
    reviewBody: String? = "Looks good, but consider renaming."
) = """
{
  "action": "$action",
  "sender": { "login": "$senderLogin" },
  "pull_request": {
    "number": $prNumber,
    "head": { "ref": "$branchRef" },
    "user": { "login": "$prAuthorLogin" }
  },
  "review": {
    "state": "$state",
    "body": ${if (reviewBody != null) "\"$reviewBody\"" else "null"}
  }
}
""".trimIndent()

private fun reviewCommentPayload(
    action: String = "created",
    senderLogin: String = HUMAN_LOGIN,
    prAuthorLogin: String = BOT_LOGIN,
    branchRef: String = "feature/MS-42-some-feature",
    prNumber: Int = 42,
    commentId: Long = 1001L,
    commentBody: String = "Consider extracting this."
) = """
{
  "action": "$action",
  "sender": { "login": "$senderLogin" },
  "pull_request": {
    "number": $prNumber,
    "head": { "ref": "$branchRef" },
    "user": { "login": "$prAuthorLogin" }
  },
  "comment": {
    "id": $commentId,
    "body": "$commentBody"
  }
}
""".trimIndent()

// ---- Test helpers ----

private fun validSignature(secret: String, body: String): String {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
    val hex = mac.doFinal(body.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    return "sha256=$hex"
}

private fun testGitHubApp(
    botLogin: String = BOT_LOGIN,
    agentService: AgentLauncher = FakeAgentLauncher(),
    block: suspend ApplicationTestBuilder.() -> Unit
) = testApplication {
    application {
        install(Koin) {
            modules(module {
                single<AgentLauncher> { agentService }
            })
        }
        configureContentNegotiation()
        configureStatusPages()
        routing { githubWebhookRoutes(TEST_SECRET, botLogin) }
    }
    block()
}

private class FakeAgentLauncher : AgentLauncher {
    var agentLaunches = 0
    var commentReviewLaunches = 0
    var conflictResolutionLaunches = 0
    var lastReviewerLogin: String? = null
    var lastBaseBranch: String? = null

    override fun launch(ticketKey: String, ticketContent: String?, dryRun: Boolean) = false

    override fun launchForPrReview(
        ticketKey: String, prNumber: Int, branchRef: String, commentBody: String, reviewerLogin: String
    ): Boolean {
        agentLaunches++
        lastReviewerLogin = reviewerLogin
        return true
    }

    override fun launchForCommentReview(
        ticketKey: String, prNumber: Int, branchRef: String, commentBody: String
    ): Boolean {
        commentReviewLaunches++
        return true
    }

    override fun launchForConflictResolution(ticketKey: String, prNumber: Int, branchRef: String, baseBranch: String): Boolean {
        conflictResolutionLaunches++
        lastBaseBranch = baseBranch
        return true
    }

    override fun launchForJudge(ticketKey: String, prNumber: Int?) = false
}
