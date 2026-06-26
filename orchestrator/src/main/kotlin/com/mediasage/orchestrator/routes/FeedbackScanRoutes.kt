package com.mediasage.orchestrator.routes

import com.mediasage.orchestrator.feedback.pr.SkillPrService
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
 * it skips PR creation when an open [Analyst] PR already exists.
 *
 * When [skillPrService] is null (GitHub App env vars not configured), the route returns 200
 * without taking any action.
 */
fun Route.feedbackScanRoutes(skillPrService: SkillPrService?) {
    post("/webhook/feedback-scan") {
        if (skillPrService == null) {
            log.info("Feedback scan: auto-PR not configured — skipping")
            call.respond(HttpStatusCode.OK)
            return@post
        }
        log.info("Feedback scan triggered")
        skillPrService.maybeOpenPr()
        call.respond(HttpStatusCode.OK)
    }
}
