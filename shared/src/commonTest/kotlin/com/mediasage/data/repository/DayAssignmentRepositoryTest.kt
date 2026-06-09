package com.mediasage.data.repository

import com.mediasage.data.local.dao.DayAssignmentDao
import com.mediasage.data.local.dao.FigureDao
import com.mediasage.data.local.entity.DayAssignmentEntity
import com.mediasage.data.local.entity.FigureEntity
import com.mediasage.data.local.entity.VoiceFigureProjection
import com.mediasage.data.remote.AssignmentDefaultDto
import com.mediasage.data.remote.DailyReflectionRequestDto
import com.mediasage.data.remote.DailyReflectionResponseDto
import com.mediasage.data.remote.EncourageRequestDto
import com.mediasage.data.remote.EncourageResultDto
import com.mediasage.data.remote.FigureDto
import com.mediasage.data.remote.FiguresResponse
import com.mediasage.data.remote.MatchCandidateDto
import com.mediasage.data.remote.MatchRequestDto
import com.mediasage.data.remote.MatchResultDto
import com.mediasage.data.remote.MediaSageApi
import com.mediasage.data.remote.NewsArticleDto
import com.mediasage.data.remote.ScripturePassageDto
import com.mediasage.data.remote.ScriptureVerseDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DayAssignmentRepositoryTest {

    private val augustine = FigureEntity(id = 1, name = "Augustine of Hippo", category = "church_father", century = "4th")
    private val julian = FigureEntity(id = 2, name = "Julian of Norwich", category = "mystic", century = "14th")
    private val luther = FigureEntity(id = 3, name = "Martin Luther", category = "reformer", century = "16th")
    private val brother = FigureEntity(id = 4, name = "Brother Lawrence", category = "mystic", century = "17th")
    private val corrie = FigureEntity(id = 5, name = "Corrie ten Boom", category = "social_justice", century = "20th")
    private val lewis = FigureEntity(id = 6, name = "C.S. Lewis", category = "theologian", century = "20th")
    private val teresa = FigureEntity(id = 7, name = "Mother Teresa", category = "missionary", century = "20th")

    private val allFigures = listOf(augustine, julian, luther, brother, corrie, lewis, teresa)

    @Test
    fun seedDefaultsIfEmpty_seeds7AssignmentsWhenTableEmpty() = runTest {
        val dao = FakeDayAssignmentDao(initialCount = 0)
        val figureDao = FakeFigureDaoForSeeding(allFigures)
        val api = FakeAssignmentApi(
            defaults = listOf(
                AssignmentDefaultDto(0, "Augustine of Hippo"),
                AssignmentDefaultDto(1, "Julian of Norwich"),
                AssignmentDefaultDto(2, "Martin Luther"),
                AssignmentDefaultDto(3, "Brother Lawrence"),
                AssignmentDefaultDto(4, "Corrie ten Boom"),
                AssignmentDefaultDto(5, "C.S. Lewis"),
                AssignmentDefaultDto(6, "Mother Teresa"),
            )
        )
        val repo = DayAssignmentRepositoryImpl(dao, figureDao, api)

        repo.seedDefaultsIfEmpty()

        assertEquals(7, dao.upsertCalls.size)
    }

    @Test
    fun seedDefaultsIfEmpty_skipsWhenTableNonEmpty() = runTest {
        val dao = FakeDayAssignmentDao(initialCount = 3)
        val figureDao = FakeFigureDaoForSeeding(allFigures)
        val api = FakeAssignmentApi()
        val repo = DayAssignmentRepositoryImpl(dao, figureDao, api)

        repo.seedDefaultsIfEmpty()

        assertTrue(dao.upsertCalls.isEmpty())
        assertEquals(0, api.callCount)
    }

    @Test
    fun seedDefaultsIfEmpty_usesFallbackOnNetworkFailure() = runTest {
        val dao = FakeDayAssignmentDao(initialCount = 0)
        val figureDao = FakeFigureDaoForSeeding(allFigures)
        val api = FakeAssignmentApi(shouldThrow = true)
        val repo = DayAssignmentRepositoryImpl(dao, figureDao, api)

        repo.seedDefaultsIfEmpty()

        assertEquals(7, dao.upsertCalls.size)
    }

    @Test
    fun seedDefaultsIfEmpty_gracefullySkipsNamesNotInLocalDb() = runTest {
        val dao = FakeDayAssignmentDao(initialCount = 0)
        val figureDao = FakeFigureDaoForSeeding(listOf(augustine))
        val api = FakeAssignmentApi(
            defaults = listOf(
                AssignmentDefaultDto(0, "Augustine of Hippo"),
                AssignmentDefaultDto(1, "Unknown Figure"),
            )
        )
        val repo = DayAssignmentRepositoryImpl(dao, figureDao, api)

        repo.seedDefaultsIfEmpty()

        assertEquals(1, dao.upsertCalls.size)
        assertEquals(DayAssignmentEntity(dayOfWeek = 0, figureId = 1L), dao.upsertCalls.first())
    }

    @Test
    fun seedDefaultsIfEmpty_isCaseInsensitiveForFigureNameLookup() = runTest {
        val dao = FakeDayAssignmentDao(initialCount = 0)
        val figureDao = FakeFigureDaoForSeeding(listOf(augustine))
        val api = FakeAssignmentApi(
            defaults = listOf(
                AssignmentDefaultDto(0, "augustine of hippo"),
            )
        )
        val repo = DayAssignmentRepositoryImpl(dao, figureDao, api)

        repo.seedDefaultsIfEmpty()

        assertEquals(1, dao.upsertCalls.size)
        assertEquals(DayAssignmentEntity(dayOfWeek = 0, figureId = 1L), dao.upsertCalls.first())
    }

    @Test
    fun seedDefaultsIfEmpty_fallbackContains7Entries() = runTest {
        assertEquals(7, DayAssignmentRepositoryImpl.FALLBACK_DEFAULTS.size)
    }
}

private class FakeDayAssignmentDao(private val initialCount: Int = 0) : DayAssignmentDao {
    val upsertCalls = mutableListOf<DayAssignmentEntity>()

    override fun observeAll(): Flow<List<DayAssignmentEntity>> = flowOf(emptyList())

    override suspend fun upsert(entity: DayAssignmentEntity) {
        upsertCalls.add(entity)
    }

    override suspend fun delete(dayOfWeek: Int) {}

    override suspend fun countAll(): Int = initialCount
}

private class FakeFigureDaoForSeeding(figures: List<FigureEntity> = emptyList()) : FigureDao {
    private val store = figures.associateBy { it.id }.toMutableMap()

    override suspend fun insert(figure: FigureEntity): Long {
        store[figure.id] = figure
        return figure.id
    }

    override suspend fun insertAll(figures: List<FigureEntity>) {
        figures.forEach { store[it.id] = it }
    }

    override fun observeAll(): Flow<List<FigureEntity>> = flowOf(store.values.toList())

    override suspend fun getById(id: Long): FigureEntity? = store[id]

    override fun observeByCategory(category: String): Flow<List<FigureEntity>> =
        flowOf(store.values.filter { it.category == category })

    override suspend fun getByName(name: String): FigureEntity? =
        store.values.find { it.name == name }

    override suspend fun getByNameIgnoreCase(name: String): FigureEntity? =
        store.values.find { it.name.lowercase() == name.lowercase() }

    override suspend fun deleteById(id: Long) { store.remove(id) }

    override suspend fun deleteAll() { store.clear() }
}

private class FakeAssignmentApi(
    private val defaults: List<AssignmentDefaultDto> = emptyList(),
    private val shouldThrow: Boolean = false,
) : MediaSageApi {
    var callCount = 0

    override suspend fun getAssignmentDefaults(): List<AssignmentDefaultDto> {
        callCount++
        if (shouldThrow) throw RuntimeException("Network failure")
        return defaults
    }

    override suspend fun getFigures(since: Long?): FiguresResponse =
        FiguresResponse(syncedAt = 0L, figures = emptyList())

    override suspend fun getHeadlines(locale: String, limit: Int): List<NewsArticleDto> = emptyList()

    override suspend fun searchNews(query: String, limit: Int): List<NewsArticleDto> = emptyList()

    override suspend fun encourage(request: EncourageRequestDto): EncourageResultDto =
        EncourageResultDto(
            quoteText = "", figureName = "", figureRole = "", scriptureReference = "",
            scriptureText = "", explanation = "", connectionThemes = emptyList(),
            matchTheme = "", tone = ""
        )

    @Suppress("DEPRECATION")
    override suspend fun matchQuote(request: MatchRequestDto): MatchResultDto =
        MatchResultDto(selectedQuoteId = 0, confidence = 0f, explanation = "", connectionThemes = emptyList())

    override suspend fun searchScripture(query: String, limit: Int): List<ScriptureVerseDto> = emptyList()

    override suspend fun getPassage(passageId: String): ScripturePassageDto =
        ScripturePassageDto(id = "", reference = "", content = "")

    override suspend fun getDailyReflection(request: DailyReflectionRequestDto): DailyReflectionResponseDto =
        DailyReflectionResponseDto(
            scriptureReference = "", scriptureText = "", insight = "",
            implication = "", inspiration = "", sources = emptyList(), tone = "morning"
        )
}
