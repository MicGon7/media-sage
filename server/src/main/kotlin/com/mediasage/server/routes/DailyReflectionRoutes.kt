package com.mediasage.server.routes

import com.mediasage.server.prompts.ReflectionTheme
import com.mediasage.server.service.DailyReflectionResult
import com.mediasage.server.service.DailyReflectionService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

fun Route.dailyReflectionRoutes() {
    val service: DailyReflectionService by inject()

    post("/api/analysis/daily-reflection") {
        val request = call.receive<DailyReflectionRequest>()
        if (request.figureId <= 0 || request.figureName.isBlank()) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "figureId and figureName are required"))
            return@post
        }
        val result = service.generate(
            DailyReflectionService.DailyReflectionRequest(
                figureId = request.figureId,
                figureName = request.figureName,
                headlines = request.headlines,
                tone = request.tone.ifBlank { "morning" },
                dayOfWeek = request.dayOfWeek,
                previousScriptures = request.previousScriptures,
                previousReflections = request.previousReflections,
                theme = request.theme?.let { runCatching { ReflectionTheme.valueOf(it.uppercase()) }.getOrNull() }
            )
        )
        call.respond(HttpStatusCode.OK, result.toResponse())
    }
}

@Serializable
data class DailyReflectionRequest(
    val figureId: Long,
    val figureName: String,
    val headlines: List<String> = emptyList(),
    val tone: String = "morning",
    val dayOfWeek: String = "",
    val previousScriptures: List<String> = emptyList(),
    val previousReflections: List<String> = emptyList(),
    val theme: String? = null
)

@Serializable
data class DailyReflectionResponse(
    val scriptureReference: String,
    val scriptureText: String,
    val insight: String,
    val implication: String,
    val inspiration: String,
    val sources: List<String>,
    val tone: String
)

private fun DailyReflectionResult.toResponse() = DailyReflectionResponse(
    scriptureReference = scriptureReference,
    scriptureText = scriptureText,
    insight = insight,
    implication = implication,
    inspiration = inspiration,
    sources = sources,
    tone = tone
)
