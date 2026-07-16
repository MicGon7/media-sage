package com.mediasage.agentruntime

import com.mediasage.agentruntime.feedback.detector.DetectedPattern
import com.mediasage.agentruntime.feedback.detector.PatternDetector
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

private class FakePatternDetector(private val patterns: List<DetectedPattern>) : PatternDetector {
    override fun detectPatterns(windowDays: Int, minOccurrences: Int): List<DetectedPattern> = patterns
}

class JobCompletionNotifierTest {

    @Test
    fun postsFactsAndGateTrendForFailedRun() = runTest {
        var body: String? = null
        val engine = MockEngine { request ->
            body = (request.body as TextContent).text
            respond("ok", HttpStatusCode.OK)
        }
        val notifier = JobCompletionNotifier(
            slackClient = SlackApiClient(HttpClient(engine), "https://hooks.slack.com/x"),
            patternDetector = FakePatternDetector(
                listOf(DetectedPattern.GateFailure(gate = "tests", runCount = 3, windowDays = 7)),
            ),
            repoOwner = "michael-gonzalez-dev",
            repoName = "media-sage",
        )

        notifier.notifyCompletion(failureEvent())

        val msg = body!!
        assertTrue(msg.contains("MS-257"), "ticket key present")
        assertTrue(msg.contains("failure"), "status present")
        assertTrue(msg.contains("turns: 12"), "turn count present")
        assertTrue(msg.contains("0.0500"), "cost present")
        assertTrue(msg.contains("gate: tests"), "failed gate present")
        assertTrue(msg.contains("github.com/michael-gonzalez-dev/media-sage/pull/200"), "PR link present")
        assertTrue(msg.contains("failed in 3 runs over the last 7 days"), "gate trend line present")
    }

    @Test
    fun omitsGateTrendForCleanSuccess() = runTest {
        var body: String? = null
        val engine = MockEngine { request ->
            body = (request.body as TextContent).text
            respond("ok", HttpStatusCode.OK)
        }
        val notifier = JobCompletionNotifier(
            slackClient = SlackApiClient(HttpClient(engine), "https://hooks.slack.com/x"),
            patternDetector = FakePatternDetector(emptyList()),
            repoOwner = "michael-gonzalez-dev",
            repoName = "media-sage",
        )

        notifier.notifyCompletion(successEvent())

        val msg = body!!
        assertTrue(msg.contains("MS-300"), "ticket key present")
        assertTrue(msg.contains("success"), "status present")
        assertFalse(msg.contains("⚠️"), "no gate trend line for a clean success")
    }
}

private fun failureEvent() = JobCompletionEvent(
    ticketKey = "MS-257",
    executionName = "exec-1",
    status = "failure",
    prNumber = 200,
    failedGate = "tests",
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
