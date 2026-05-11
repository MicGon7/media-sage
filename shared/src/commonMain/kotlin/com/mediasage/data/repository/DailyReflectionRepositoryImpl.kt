package com.mediasage.data.repository

import com.mediasage.data.local.dao.DailyReflectionDao
import com.mediasage.data.local.entity.DailyReflectionEntity
import com.mediasage.data.remote.DailyReflectionRequestDto
import com.mediasage.data.remote.MediaSageApi
import com.mediasage.domain.model.DailyReflection
import com.mediasage.domain.repository.DailyReflectionRepository
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class DailyReflectionRepositoryImpl(
    private val dao: DailyReflectionDao,
    private val api: MediaSageApi
) : DailyReflectionRepository {

    override suspend fun getOrFetch(
        figureId: Long,
        figureName: String,
        headlines: List<String>,
        tone: String
    ): DailyReflection {
        val epochDay = currentTimeMillis() / 86400000L
        val cached = dao.get(figureId, epochDay, tone)
        if (cached != null) return cached.toDomain()

        val todaysEntries = dao.getAllForDay(figureId, epochDay)
        val previousReflections = todaysEntries.map { it.reflection }
        val previousScriptures = (
            dao.getAllScripturesForDay(epochDay) +
            dao.getRecentScripturesForFigure(figureId, fromDay = epochDay - 7, today = epochDay)
        ).distinct()
        val dayOfWeek = Instant.fromEpochMilliseconds(currentTimeMillis())
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }

        val response = api.getDailyReflection(
            DailyReflectionRequestDto(
                figureId = figureId,
                figureName = figureName,
                headlines = headlines,
                tone = tone,
                dayOfWeek = dayOfWeek,
                previousScriptures = previousScriptures,
                previousReflections = previousReflections
            )
        )
        val entity = DailyReflectionEntity(
            id = "${figureId}_${epochDay}_$tone",
            figureId = figureId,
            epochDay = epochDay,
            tone = tone,
            scriptureReference = response.scriptureReference,
            scriptureText = response.scriptureText,
            reflection = response.reflection,
            sources = response.sources
        )
        dao.upsert(entity)
        return entity.toDomain()
    }
}

private fun DailyReflectionEntity.toDomain() = DailyReflection(
    scriptureReference = scriptureReference,
    scriptureText = scriptureText,
    reflection = reflection,
    sources = sources,
    tone = tone
)
