package com.mediasage.appserver.plugins

import com.mediasage.appserver.service.ClaudeApiException
import com.mediasage.appserver.service.DailyLimitExceededException
import com.mediasage.appserver.service.NewsApiException
import com.mediasage.appserver.service.ScriptureApiException
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*

/** Catches exceptions and returns structured JSON error responses instead of stack traces. */
fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<ClaudeApiException> { call, cause ->
            call.respond(HttpStatusCode.fromValue(cause.statusCode), ErrorResponse(cause.statusCode, cause.message))
        }
        exception<NewsApiException> { call, cause ->
            call.respond(HttpStatusCode.fromValue(cause.statusCode), ErrorResponse(cause.statusCode, cause.message))
        }
        exception<ScriptureApiException> { call, cause ->
            call.respond(HttpStatusCode.fromValue(cause.statusCode), ErrorResponse(cause.statusCode, cause.message))
        }
        exception<DailyLimitExceededException> { call, cause ->
            call.respond(HttpStatusCode.TooManyRequests, ErrorResponse(429, cause.message))
        }
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(400, cause.message ?: "Bad request"))
        }
        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled exception", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(500, cause.message ?: "Internal server error")
            )
        }
    }
}
