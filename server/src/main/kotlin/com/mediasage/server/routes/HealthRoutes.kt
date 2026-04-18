package com.mediasage.server.routes

import io.ktor.server.response.*
import io.ktor.server.routing.*

/** Health check endpoint — proves the server is alive. Used by CI and monitoring. */
fun Route.healthRoutes() {
    get("/health") {
        call.respondText("OK")
    }
}
