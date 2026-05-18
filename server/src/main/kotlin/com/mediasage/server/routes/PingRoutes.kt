package com.mediasage.server.routes

import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.pingRoutes() {
    get("/ping") {
        call.respondText("pong")
    }
}
