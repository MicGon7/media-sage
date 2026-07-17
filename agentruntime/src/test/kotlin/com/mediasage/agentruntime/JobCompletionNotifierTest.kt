package com.mediasage.agentruntime

import com.mediasage.agentruntime.service.JobCompletionNotifier
import com.mediasage.agentruntime.service.SlackApiClient
import com.mediasage.pipeline.core.JobCompletionEvent
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JobCompletionNotifierTest {

    @Test
    fun postsFactsForFailedRun() = runTest {
        var body: String? = null
        val engine = MockEngine { request ->
            body = (request.body as TextContent).text
            respond("ok", HttpStatusCode.OK)
        }
        val notifier = JobCompletionNotifier(
            slackClient = SlackApiClient(HttpClient(engine), "https://hooks.slack.com/x"),
            repoOwner = "michael-gonzalez-dev",
            repoName = "media-sage",
        )

        notifier.notifyCompletion(failureEvent())

        val msg = body!!
        assertTrue(msg.contains("MS-257"), "ticket key present")
        assertTrue(msg.contains("failure"), "status present")
        assertTrue(msg.contains("turns: 12"), "turn count present")
        assertTrue(msg.contains("0.0500"), "cost present")
        assertTrue(msg.contains("github.com/michael-gonzalez-dev/media-sage/pull/200"), "PR link present")
        assertFalse(msg.contains("gate"), "no gate line remains")
    }

    @Test
    fun namesJobTypeInHeaderForTicketWork() = runTest {
        val msg = render(successEvent().copy(jobType = "ticket-work"))
        assertTrue(msg.contains("*MS-300* — ticket-work — success"), "job type named in header")
    }

    @Test
    fun rendersPrLinkAndCleanSignalForQualityReviewWithNoComments() = runTest {
        val msg = render(
            successEvent().copy(jobType = "pr-quality-work", prNumber = 314, reviewCommentCount = 0),
        )
        assertTrue(msg.contains("pr-quality-work"), "job type named")
        assertTrue(msg.contains("review: clean"), "clean signal when no comments")
        assertTrue(msg.contains("github.com/michael-gonzalez-dev/media-sage/pull/314"), "PR link present")
    }

    @Test
    fun rendersCommentCountSignalForQualityReviewWithComments() = runTest {
        val single = render(successEvent().copy(jobType = "pr-quality-work", reviewCommentCount = 1))
        assertTrue(single.contains("review: 1 comment"), "singular comment signal")
        assertFalse(single.contains("1 comments"), "no plural for a single comment")

        val many = render(successEvent().copy(jobType = "pr-quality-work", reviewCommentCount = 3))
        assertTrue(many.contains("review: 3 comments"), "plural comment count signal")
    }

    @Test
    fun omitsReviewSignalForNonReviewJobAndMissingJobType() = runTest {
        val ticketWork = render(successEvent().copy(jobType = "ticket-work", reviewCommentCount = 5))
        assertFalse(ticketWork.contains("review:"), "no review signal for non-review job type")

        // Null jobType (older worker / recovery path) still produces a valid message.
        val legacy = render(successEvent())
        assertTrue(legacy.contains("*MS-300* — success"), "header renders without a job type")
        assertFalse(legacy.contains("review:"), "no review signal without a job type")
    }

    private suspend fun render(event: JobCompletionEvent): String {
        var body: String? = null
        val engine = MockEngine { request ->
            body = (request.body as TextContent).text
            respond("ok", HttpStatusCode.OK)
        }
        JobCompletionNotifier(
            slackClient = SlackApiClient(HttpClient(engine), "https://hooks.slack.com/x"),
            repoOwner = "michael-gonzalez-dev",
            repoName = "media-sage",
        ).notifyCompletion(event)
        return body!!
    }
}

private fun failureEvent() = JobCompletionEvent(
    ticketKey = "MS-257",
    executionName = "exec-1",
    status = "failure",
    prNumber = 200,
    numTurns = 12,
    totalCostUsd = 0.05,
    durationMs = 192_000,
)

private fun successEvent() = JobCompletionEvent(
    ticketKey = "MS-300",
    executionName = "exec-2",
    status = "success",
    prNumber = 201,
    numTurns = 8,
    totalCostUsd = 0.02,
    durationMs = 60_000,
)
