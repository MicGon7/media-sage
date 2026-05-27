package com.mediasage.agent

import com.mediasage.agent.plugins.configureContentNegotiation
import com.mediasage.agent.plugins.configureStatusPages
import com.mediasage.agent.routes.githubWebhookRoutes
import com.mediasage.agent.service.AgentLauncher
import com.mediasage.agent.service.JiraLabelChecker
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
    fun autonomousTicketChangesRequestedReturns200() = testGitHubApp(jiraAutonomous = true) {
        val body = prReviewPayload(state = "changes_requested", reviewBody = "Please extract this to a helper.")
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
        testGitHubApp(jiraAutonomous = true, agentService = tracking) {
            val body = prReviewPayload(state = "CHANGES_REQUESTED", reviewBody = "Please fix this.")
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
        testGitHubApp(jiraAutonomous = true, agentService = tracking) {
            val body = prReviewPayload(state = "changes_requested", senderLogin = "jane-reviewer", reviewBody = "Please fix this.")
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
        testGitHubApp(jiraAutonomous = true, agentService = tracking) {
            val body = prReviewPayload(state = "commented", reviewBody = "What does this function do?")
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
    fun nonAutonomousTicketReturns200WithoutFiring() = testGitHubApp(jiraAutonomous = false) {
        val body = prReviewPayload(state = "changes_requested", reviewBody = "Please extract this to a helper.")
        val response = client.post("/webhook/github") {
            contentType(ContentType.Application.Json)
            header("X-GitHub-Event", "pull_request_review")
            header("X-Hub-Signature-256", validSignature(TEST_SECRET, body))
            setBody(body)
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun inlineCommentPostsQuickReplyNotAgent() {
        val tracking = FakeAgentLauncher()
        testGitHubApp(agentService = tracking) {
            val body = reviewCommentPayload(commentBody = "Rename this variable for clarity.")
            val response = client.post("/webhook/github") {
                contentType(ContentType.Application.Json)
                header("X-GitHub-Event", "pull_request_review_comment")
                header("X-Hub-Signature-256", validSignature(TEST_SECRET, body))
                setBody(body)
            }
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(0, tracking.agentLaunches, "Full agent must NOT fire for inline comments")
            assertEquals(1, tracking.inlineReplies, "Quick reply must fire for inline comments")
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
        val body = prReviewPayload(branchRef = "main", state = "changes_requested", reviewBody = "Fix this.")
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
        testGitHubApp(jiraAutonomous = true, agentService = tracking) {
            val body = dequeuePayload(reason = "merge_conflict")
            val response = client.post("/webhook/github") {
                contentType(ContentType.Application.Json)
                header("X-GitHub-Event", "pull_request")
                header("X-Hub-Signature-256", validSignature(TEST_SECRET, body))
                setBody(body)
            }
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(1, tracking.conflictResolutionLaunches, "Conflict resolver must fire for merge_conflict dequeue")
        }
    }

    @Test
    fun ciFailureDequeueIsIgnored() {
        val tracking = FakeAgentLauncher()
        testGitHubApp(jiraAutonomous = true, agentService = tracking) {
            val body = dequeuePayload(reason = "checks_failed")
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
    fun nonAutonomousTicketDequeueIsIgnored() {
        val tracking = FakeAgentLauncher()
        testGitHubApp(jiraAutonomous = false, agentService = tracking) {
            val body = dequeuePayload(reason = "merge_conflict")
            val response = client.post("/webhook/github") {
                contentType(ContentType.Application.Json)
                header("X-GitHub-Event", "pull_request")
                header("X-Hub-Signature-256", validSignature(TEST_SECRET, body))
                setBody(body)
            }
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(0, tracking.conflictResolutionLaunches, "Conflict resolver must NOT fire for non-autonomous ticket")
        }
    }

    @Test
    fun dequeueWithNoTicketKeyIsIgnored() {
        val tracking = FakeAgentLauncher()
        testGitHubApp(jiraAutonomous = true, agentService = tracking) {
            val body = dequeuePayload(reason = "merge_conflict", branchRef = "hotfix/no-ticket-here")
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
    reason: String = "merge_conflict",
    branchRef: String = "feature/MS-42-some-feature",
    prNumber: Int = 42,
    senderLogin: String = "github-merge-queue[bot]"
) = """
{
  "action": "dequeued",
  "reason": "$reason",
  "sender": { "login": "$senderLogin" },
  "pull_request": {
    "number": $prNumber,
    "head": { "ref": "$branchRef" }
  }
}
""".trimIndent()

private fun prReviewPayload(
    action: String = "submitted",
    senderLogin: String = "human-reviewer",
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
    "head": { "ref": "$branchRef" }
  },
  "review": {
    "state": "$state",
    "body": ${if (reviewBody != null) "\"$reviewBody\"" else "null"}
  }
}
""".trimIndent()

private fun reviewCommentPayload(
    action: String = "created",
    senderLogin: String = "human-reviewer",
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
    "head": { "ref": "$branchRef" }
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
    jiraAutonomous: Boolean = true,
    agentService: AgentLauncher = FakeAgentLauncher(),
    block: suspend ApplicationTestBuilder.() -> Unit
) = testApplication {
    application {
        install(Koin) {
            modules(module {
                single<AgentLauncher> { agentService }
                single<JiraLabelChecker> { FakeJiraLabelChecker(jiraAutonomous) }
            })
        }
        configureContentNegotiation()
        configureStatusPages()
        routing { githubWebhookRoutes(TEST_SECRET) }
    }
    block()
}

private class FakeJiraLabelChecker(private val autonomous: Boolean) : JiraLabelChecker {
    override suspend fun isAutonomous(ticketKey: String) = autonomous
}

private class FakeAgentLauncher : AgentLauncher {
    var agentLaunches = 0
    var commentReviewLaunches = 0
    var conflictResolutionLaunches = 0
    var inlineReplies = 0
    var lastReviewerLogin: String? = null

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

    override fun launchForConflictResolution(ticketKey: String, prNumber: Int, branchRef: String): Boolean {
        conflictResolutionLaunches++
        return true
    }

    override fun postInlineCommentReply(prNumber: Int) {
        inlineReplies++
    }
}
