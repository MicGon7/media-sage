package com.mediasage.analyst.routes

import com.mediasage.analyst.plugins.ErrorResponse
import com.mediasage.analyst.stats.PipelineStatsReader
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/** Default trailing window when the caller omits `?days=`. */
private const val DEFAULT_WINDOW_DAYS = 7

/**
 * Exposes `GET /stats[?days=N]` — a cross-run health summary over the last N days (default 7).
 *
 * The window is parameterised from the start so downstream consumers can ask for the slice they
 * need: a daily digest will request `?days=1`, a weekly review `?days=7`.
 *
 * Invalid `days` (non-numeric or non-positive) returns 400 rather than silently falling back to a
 * default, so a malformed automation request surfaces as an error instead of wrong data.
 */
fun Route.statsRoutes(statsReader: PipelineStatsReader) {
    get("/stats") {
        val daysParam = call.request.queryParameters["days"]
        val windowDays = if (daysParam == null) DEFAULT_WINDOW_DAYS else (daysParam.toIntOrNull() ?: 0)
        if (windowDays <= 0) {
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(400, "days must be a positive integer"))
            return@get
        }
        call.respond(statsReader.stats(windowDays))
    }
}
