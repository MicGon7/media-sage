package com.mediasage.appserver.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*

/** Configures CORS to allow the mobile app and local development tools to reach the server. */
fun Application.configureCORS() {
    install(CORS) {
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowHeader(HttpHeaders.ContentType)
        anyHost()   // Permissive for local dev — lock down before deployment
    }
}
