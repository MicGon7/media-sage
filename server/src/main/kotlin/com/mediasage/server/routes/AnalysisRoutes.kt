package com.mediasage.server.routes

import io.ktor.server.routing.*

/** Quote matching endpoints — Claude AI matches headlines to quotes. Implemented in MS-6. */
fun Route.analysisRoutes() {
    route("/api/analysis") {
        // POST /api/analysis/match — match a headline with an encouraging quote (MS-6)
    }
}
