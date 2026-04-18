package com.mediasage.server

import io.ktor.server.application.Application
import io.ktor.server.netty.EngineMain
import io.ktor.server.routing.routing
import com.mediasage.server.plugins.*
import com.mediasage.server.routes.*

fun main(args: Array<String>) {
    EngineMain.main(args)
}

/** Application module referenced in application.conf. Installs plugins and routes. */
fun Application.module() {
    configureContentNegotiation()
    configureCORS()
    configureCallLogging()
    configureStatusPages()
    configureRouting()
}

fun Application.configureRouting() {
    routing {
        healthRoutes()
        newsRoutes()
        analysisRoutes()
    }
}
