package com.mediasage.server.plugins

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import kotlinx.serialization.json.Json

/** Installs ContentNegotiation so Ktor automatically serializes/deserializes JSON request and response bodies. */
fun Application.configureContentNegotiation() {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true          // Readable JSON in responses (helpful during development)
            isLenient = false           // Strict JSON parsing — reject malformed input
            ignoreUnknownKeys = true    // Don't fail if the JSON has extra fields we don't model
        })
    }
}
