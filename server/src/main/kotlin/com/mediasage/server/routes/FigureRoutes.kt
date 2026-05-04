package com.mediasage.server.routes

import com.mediasage.server.repository.FigureRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.koin.ktor.ext.inject

fun Route.figureRoutes() {
    val figureRepository: FigureRepository by inject()

    get("/api/figures") {
        val since = call.request.queryParameters["since"]?.toLongOrNull()
        val figures = figureRepository.getAllEnabled(since)
        call.respond(HttpStatusCode.OK, figures)
    }
}
