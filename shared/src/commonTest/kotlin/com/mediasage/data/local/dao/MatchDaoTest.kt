package com.mediasage.data.local.dao

import com.mediasage.data.local.entity.MatchEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MatchDaoTest {

    private fun match(id: Long, createdAt: Long) = MatchEntity(
        id = id,
        headlineId = id,
        quoteId = id,
        explanation = "explanation",
        confidence = 0.9f,
        connectionThemes = "hope,faith",
        createdAt = createdAt
    )

    @Test
    fun getByCreatedAtRange_returnsEmptyWhenNoMatchInRange() = runTest {
        val dao = FakeMatchDao(listOf(match(1L, 1000L), match(2L, 2000L)))

        val result = dao.getByCreatedAtRange(start = 5000L, end = 6000L).first()

        assertTrue(result.isEmpty())
    }

    @Test
    fun getByCreatedAtRange_returnsSingleDayEntities() = runTest {
        val target = match(1L, 1000L)
        val dao = FakeMatchDao(listOf(target, match(2L, 2000L), match(3L, 500L)))

        val result = dao.getByCreatedAtRange(start = 1000L, end = 1000L).first()

        assertEquals(1, result.size)
        assertEquals(target, result.first())
    }

    @Test
    fun getByCreatedAtRange_returnsMultiDayEntities() = runTest {
        val early = match(1L, 1000L)
        val mid = match(2L, 2000L)
        val late = match(3L, 3000L)
        val outside = match(4L, 4000L)
        val dao = FakeMatchDao(listOf(early, mid, late, outside))

        val result = dao.getByCreatedAtRange(start = 1000L, end = 3000L).first()

        assertEquals(3, result.size)
        assertEquals(listOf(late, mid, early), result)
    }
}

private class FakeMatchDao(private val store: List<MatchEntity> = emptyList()) : MatchDao {

    override suspend fun insert(match: MatchEntity): Long = match.id

    override suspend fun getByHeadline(headlineId: Long): MatchEntity? =
        store.find { it.headlineId == headlineId }

    override fun observeAll(): Flow<List<MatchEntity>> =
        flowOf(store.sortedByDescending { it.createdAt })

    override fun getByCreatedAtRange(start: Long, end: Long): Flow<List<MatchEntity>> =
        flowOf(store.filter { it.createdAt in start..end }.sortedByDescending { it.createdAt })

    override suspend fun deleteById(id: Long) {}
}
