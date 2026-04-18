package com.mediasage.server.routes

import io.ktor.server.routing.*

/** News headline endpoints — implemented in MS-7. */
fun Route.newsRoutes() {
    route("/api/news") {
        // GET /api/news/headlines — fetch top headlines (MS-7)
    }
}
