package com.mediasage.server.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import kotlinx.serialization.Serializable

/** Standard error response returned by all endpoints. */
@Serializable
data class ErrorResponse(
    val status: Int,
    val message: String
)

/** Catches exceptions and returns structured JSON error responses instead of stack traces. */
fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(400, cause.message ?: "Bad request"))
        }
        exception<Throwable> { call, cause ->
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(500, cause.message ?: "Internal server error")
            )
        }
    }
}
