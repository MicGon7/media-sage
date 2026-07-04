package com.mediasage.data.repository

import com.mediasage.data.local.dao.DailyReflectionDao
import com.mediasage.data.local.entity.DailyReflectionEntity
import com.mediasage.data.remote.DailyReflectionRequestDto
import com.mediasage.data.remote.MediaSageApi
import com.mediasage.domain.model.BriefingDay
import com.mediasage.domain.model.DailyReflection
import com.mediasage.domain.repository.DailyReflectionRepository
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
        tone: String,
        theme: String?
    ): DailyReflection {
        val resolvedTheme = theme?.uppercase() ?: "NEWS"
        val epochDay = currentTimeMillis() / 86400000L
        val cached = dao.get(figureId, epochDay, tone, resolvedTheme)
        if (cached != null) return cached.toDomain()

        val todaysEntries = dao.getAllForDay(figureId, epochDay)
        val previousReflections = todaysEntries.map { "${it.insight} ${it.implication} ${it.inspiration}" }
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
                previousReflections = previousReflections,
                theme = resolvedTheme.takeIf { it != "NEWS" }
            )
        )
        val entity = DailyReflectionEntity(
            id = "${figureId}_${epochDay}_${tone}_$resolvedTheme",
            figureId = figureId,
            epochDay = epochDay,
            tone = tone,
            theme = resolvedTheme,
            scriptureReference = response.scriptureReference,
            scriptureText = response.scriptureText,
            insight = response.insight,
            implication = response.implication,
            inspiration = response.inspiration,
            sources = response.sources
        )
        dao.upsert(entity)
        return entity.toDomain()
    }

    override fun observeByEpochDayRange(startEpochDay: Long, endEpochDay: Long): Flow<List<BriefingDay>> =
        dao.getByEpochDayRange(startEpochDay, endEpochDay).map { entities ->
            entities.groupBy { it.epochDay }.map { (day, list) -> BriefingDay(day, list.first().figureId) }
        }

    override suspend fun getForDay(epochDay: Long): DailyReflection? =
        dao.getFirstForDay(epochDay)?.toDomain()
}

private fun DailyReflectionEntity.toDomain() = DailyReflection(
    scriptureReference = scriptureReference,
    scriptureText = scriptureText,
    insight = insight,
    implication = implication,
    inspiration = inspiration,
    sources = sources,
    tone = tone
)
