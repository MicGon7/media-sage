package com.mediasage.appserver.routes

import com.mediasage.appserver.repository.FigureRepository
import com.mediasage.appserver.repository.FiguresResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.koin.ktor.ext.inject

fun Route.figureRoutes() {
    val figureRepository: FigureRepository by inject()

    get("/api/figures") {
        val since = call.request.queryParameters["since"]?.toLongOrNull()
        val syncedAt = System.currentTimeMillis()
        val figures = figureRepository.getAllEnabled(since)
        call.respond(HttpStatusCode.OK, FiguresResponse(syncedAt = syncedAt, figures = figures))
    }
}
