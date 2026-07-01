package com.mediasage.agentruntime.routes

import com.mediasage.agentruntime.feedback.pr.FeedbackPrService
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("FeedbackScanRoutes")

/**
 * Triggered by Cloud Scheduler on a fixed schedule (e.g. daily).
 *
 * Detects recurring failure patterns across recent pipeline runs and proposes a targeted
 * skill-file edit as a GitHub PR when actionable patterns are found. The route is idempotent —
 * it skips PR creation when an open feedback PR already exists.
 *
 * When [feedbackPrService] is null (GitHub App env vars not configured), the route returns 200
 * without taking any action.
 */
fun Route.feedbackScanRoutes(feedbackPrService: FeedbackPrService?) {
    post("/webhook/feedback-scan") {
        if (feedbackPrService == null) {
            log.info("Feedback scan: auto-PR not configured — skipping")
            call.respond(HttpStatusCode.OK)
            return@post
        }
        log.info("Feedback scan triggered")
        feedbackPrService.proposePatch()
        call.respond(HttpStatusCode.OK)
    }
}
