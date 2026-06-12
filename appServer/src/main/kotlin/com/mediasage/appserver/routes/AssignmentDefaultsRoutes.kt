package com.mediasage.appserver.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

private val defaultWeekAssignments = listOf(
    AssignmentDefaultResponse(dayOrdinal = 0, figureName = "Augustine of Hippo"),
    AssignmentDefaultResponse(dayOrdinal = 1, figureName = "Julian of Norwich"),
    AssignmentDefaultResponse(dayOrdinal = 2, figureName = "Martin Luther"),
    AssignmentDefaultResponse(dayOrdinal = 3, figureName = "Brother Lawrence"),
    AssignmentDefaultResponse(dayOrdinal = 4, figureName = "Corrie ten Boom"),
    AssignmentDefaultResponse(dayOrdinal = 5, figureName = "C.S. Lewis"),
    AssignmentDefaultResponse(dayOrdinal = 6, figureName = "Mother Teresa"),
)

fun Route.assignmentDefaultsRoutes() {
    get("/api/assignments/defaults") {
        call.respond(HttpStatusCode.OK, defaultWeekAssignments)
    }
}
