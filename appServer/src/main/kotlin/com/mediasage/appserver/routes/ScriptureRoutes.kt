package com.mediasage.appserver.routes

import com.mediasage.appserver.service.ScriptureApiClient
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

/** Scripture API endpoints — search and lookup Bible verses. */
fun Route.scriptureRoutes() {
    val scriptureClient by inject<ScriptureApiClient>()

    route("/api/scripture") {
        get("/search") {
            val query = call.parameters["query"]
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "query parameter is required")
                )
            val limit = call.parameters["limit"]?.toIntOrNull() ?: 10

            val verses = scriptureClient.searchVerses(query = query, limit = limit)
            call.respond(verses)
        }

        get("/passage/{passageId}") {
            val passageId = call.parameters["passageId"]
                ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "passageId path parameter is required")
                )

            val passage = scriptureClient.getPassage(passageId)
            call.respond(passage)
        }
    }
}
