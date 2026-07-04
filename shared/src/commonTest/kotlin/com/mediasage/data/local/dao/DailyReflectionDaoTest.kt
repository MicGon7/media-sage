package com.mediasage.data.local.dao

import com.mediasage.data.local.entity.DailyReflectionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DailyReflectionDaoTest {

    private fun entity(id: String, epochDay: Long) = DailyReflectionEntity(
        id = id,
        figureId = 1L,
        epochDay = epochDay,
        tone = "morning",
        scriptureReference = "John 3:16",
        scriptureText = "For God so loved the world",
        insight = "insight",
        implication = "implication",
        inspiration = "inspiration",
        sources = emptyList()
    )

    @Test
    fun getByEpochDayRange_returnsEmptyWhenNoMatchInRange() = runTest {
        val dao = FakeDailyReflectionDao(listOf(entity("a", 10L), entity("b", 20L)))

        val result = dao.getByEpochDayRange(start = 50L, end = 60L).first()

        assertTrue(result.isEmpty())
    }

    @Test
    fun getByEpochDayRange_returnsSingleDayEntities() = runTest {
        val target = entity("a", 5L)
        val dao = FakeDailyReflectionDao(listOf(target, entity("b", 6L), entity("c", 4L)))

        val result = dao.getByEpochDayRange(start = 5L, end = 5L).first()

        assertEquals(1, result.size)
        assertEquals(target, result.first())
    }

    @Test
    fun getByEpochDayRange_returnsMultiDayEntities() = runTest {
        val day1 = entity("a", 1L)
        val day2 = entity("b", 2L)
        val day3 = entity("c", 3L)
        val outside = entity("d", 4L)
        val dao = FakeDailyReflectionDao(listOf(day1, day2, day3, outside))

        val result = dao.getByEpochDayRange(start = 1L, end = 3L).first()

        assertEquals(3, result.size)
        assertEquals(listOf(day1, day2, day3), result)
    }
}

private class FakeDailyReflectionDao(private val store: List<DailyReflectionEntity> = emptyList()) : DailyReflectionDao {

    override suspend fun get(figureId: Long, epochDay: Long, tone: String, theme: String): DailyReflectionEntity? =
        store.find { it.figureId == figureId && it.epochDay == epochDay && it.tone == tone && it.theme == theme }

    override suspend fun getAllForDay(figureId: Long, epochDay: Long): List<DailyReflectionEntity> =
        store.filter { it.figureId == figureId && it.epochDay == epochDay }

    override suspend fun getAllScripturesForDay(epochDay: Long): List<String> =
        store.filter { it.epochDay == epochDay }.map { it.scriptureReference }.distinct()

    override suspend fun getRecentScripturesForFigure(figureId: Long, fromDay: Long, today: Long): List<String> =
        store.filter { it.figureId == figureId && it.epochDay >= fromDay && it.epochDay < today }
            .map { it.scriptureReference }.distinct()

    override fun getByEpochDayRange(start: Long, end: Long): Flow<List<DailyReflectionEntity>> =
        flowOf(store.filter { it.epochDay in start..end }.sortedBy { it.epochDay })

    override suspend fun upsert(entity: DailyReflectionEntity) {}
}
