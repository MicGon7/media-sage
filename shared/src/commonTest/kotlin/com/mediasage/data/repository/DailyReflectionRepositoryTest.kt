package com.mediasage.data.repository

import com.mediasage.data.local.dao.DailyReflectionDao
import com.mediasage.data.local.dao.FigureDao
import com.mediasage.data.local.dao.SyncMetaDao
import com.mediasage.data.local.entity.DailyReflectionEntity
import com.mediasage.data.local.entity.FigureEntity
import com.mediasage.data.local.entity.SyncMetaEntity
import com.mediasage.data.remote.AssignmentDefaultDto
import com.mediasage.data.remote.DailyReflectionRequestDto
import com.mediasage.data.remote.DailyReflectionResponseDto
import com.mediasage.data.remote.EncourageRequestDto
import com.mediasage.data.remote.EncourageResultDto
import com.mediasage.data.remote.FiguresResponse
import com.mediasage.data.remote.MatchRequestDto
import com.mediasage.data.remote.MatchResultDto
import com.mediasage.data.remote.MediaSageApi
import com.mediasage.data.remote.NewsArticleDto
import com.mediasage.data.remote.ScripturePassageDto
import com.mediasage.data.remote.ScriptureVerseDto
import com.mediasage.domain.model.BriefingDay
import com.mediasage.domain.model.UserSession
import com.mediasage.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val USER_ID = "user-1"

class DailyReflectionRepositoryTest {

    private val augustine = FigureEntity(id = 1, name = "Augustine of Hippo", category = "church_father", century = "4th", serverId = 101)
    private val lewis = FigureEntity(id = 2, name = "C.S. Lewis", category = "theologian", century = "20th", serverId = 106)

    private val allFigures = listOf(augustine, lewis)

    private fun repo(
        dao: FakeDailyReflectionDao = FakeDailyReflectionDao(),
        api: FakeReflectionApi = FakeReflectionApi(),
        figureDao: FakeFigureDaoForReflectionSync = FakeFigureDaoForReflectionSync(allFigures),
        remote: FakeDailyReflectionRemoteDataSource? = FakeDailyReflectionRemoteDataSource(),
        syncMetaDao: FakeSyncMetaDao = FakeSyncMetaDao(),
        authRepository: FakeAuthRepository = FakeAuthRepository(USER_ID),
    ) = DailyReflectionRepositoryImpl(dao, api, figureDao, remote, syncMetaDao, authRepository)

    @Test
    fun getOrFetch_returnsCachedReflectionWithoutCallingApiOrPushing() = runTest {
        val dao = FakeDailyReflectionDao()
        val remote = FakeDailyReflectionRemoteDataSource()
        dao.upsert(
            reflection(figureId = augustine.id, epochDay = 100L, tone = "morning", synced = true)
        )
        val api = FakeReflectionApi()

        repo(dao = dao, api = api, remote = remote).getOrFetch(
            figureId = augustine.id, figureName = augustine.name, headlines = emptyList(), tone = "morning"
        )

        assertEquals(0, api.callCount)
        assertTrue(remote.pushedRows.isEmpty())
    }

    @Test
    fun getOrFetch_generatesAndPushesANewReflectionImmediately() = runTest {
        val dao = FakeDailyReflectionDao()
        val remote = FakeDailyReflectionRemoteDataSource()
        val api = FakeReflectionApi()

        repo(dao = dao, api = api, remote = remote).getOrFetch(
            figureId = augustine.id, figureName = augustine.name, headlines = emptyList(), tone = "morning"
        )

        assertEquals(1, api.callCount)
        assertEquals(1, remote.pushedRows.size)
        assertEquals(augustine.serverId, remote.pushedRows.first().figureServerId)
    }

    @Test
    fun getOrFetch_adoptsAnAlreadyGeneratedRemoteReflectionInsteadOfGeneratingADuplicate() = runTest {
        // Regression test: resolve() only pulls the remote once per session/sign-in. If another
        // device generated and pushed today's reflection since that pull (or before this
        // session's pull even ran), a plain local-cache-miss check would otherwise still fall
        // through to generating an independent duplicate. getOrFetch must check the remote
        // directly at the exact moment it decides whether to generate.
        val todayEpochDay = localEpochDay(epochMillis())
        val dao = FakeDailyReflectionDao()
        val api = FakeReflectionApi()
        val remote = FakeDailyReflectionRemoteDataSource(
            initialRows = listOf(
                DailyReflectionRow(
                    userId = USER_ID, epochDay = todayEpochDay, tone = "morning", theme = "NEWS",
                    figureServerId = augustine.serverId, scriptureReference = "Remote Ref",
                    scriptureText = "text", insight = "i", implication = "im", inspiration = "in",
                    sources = emptyList(),
                )
            )
        )

        val result = repo(dao = dao, api = api, remote = remote).getOrFetch(
            figureId = augustine.id, figureName = augustine.name, headlines = emptyList(), tone = "morning"
        )

        assertEquals(0, api.callCount)
        assertTrue(remote.pushedRows.isEmpty())
        assertEquals("Remote Ref", result.scriptureReference)
    }

    @Test
    fun isResolved_isFalseBeforeResolveIsCalled() = runTest {
        assertFalse(repo().isResolved.value)
    }

    @Test
    fun resolve_setsIsResolvedTrueWithNoUser() = runTest {
        val repository = repo()

        repository.resolve(null)

        assertTrue(repository.isResolved.value)
    }

    @Test
    fun resolve_doesNothingLocallyWhenSignedOut() = runTest {
        val dao = FakeDailyReflectionDao()
        val remote = FakeDailyReflectionRemoteDataSource()

        repo(dao = dao, remote = remote).resolve(null)

        assertTrue(dao.upsertCalls.isEmpty())
        assertTrue(remote.pushedRows.isEmpty())
    }

    @Test
    fun resolve_pushesPendingLocalReflectionForSignedInUser() = runTest {
        val dao = FakeDailyReflectionDao()
        dao.upsert(reflection(figureId = augustine.id, epochDay = 100L, tone = "morning", synced = false))
        val remote = FakeDailyReflectionRemoteDataSource()

        repo(dao = dao, remote = remote).resolve(USER_ID)

        assertEquals(1, remote.pushedRows.size)
        assertTrue(dao.getRawById("100_morning_NEWS")!!.synced)
    }

    @Test
    fun resolve_leavesRowUnsyncedWhenPushFails() = runTest {
        val dao = FakeDailyReflectionDao()
        dao.upsert(reflection(figureId = augustine.id, epochDay = 100L, tone = "morning", synced = false))
        val remote = FakeDailyReflectionRemoteDataSource(shouldThrowOnPush = true)

        repo(dao = dao, remote = remote).resolve(USER_ID)

        assertFalse(dao.getRawById("100_morning_NEWS")!!.synced)
    }

    @Test
    fun resolve_pullsAReflectionGeneratedOnAnotherDevice() = runTest {
        val dao = FakeDailyReflectionDao()
        val remote = FakeDailyReflectionRemoteDataSource(
            initialRows = listOf(
                DailyReflectionRow(
                    userId = USER_ID, epochDay = 200L, tone = "morning", theme = "NEWS",
                    figureServerId = lewis.serverId, scriptureReference = "John 3:16",
                    scriptureText = "text", insight = "i", implication = "im", inspiration = "in",
                    sources = emptyList(),
                )
            )
        )

        repo(dao = dao, remote = remote).resolve(USER_ID)

        val pulled = dao.getRawById("200_morning_NEWS")
        assertNotNull(pulled)
        assertEquals(lewis.id, pulled.figureId)
        assertTrue(pulled.synced)
    }

    @Test
    fun resolve_neverOverwritesAnExistingLocalReflectionWithAPulledDuplicate() = runTest {
        // Regression guard for the union-by-key reconciliation: reflections are create-once and
        // immutable, so a pull must never clobber content already generated locally for the same
        // epochDay/tone/theme key — insertIfAbsent (not upsert) is what makes this safe.
        val dao = FakeDailyReflectionDao()
        dao.upsert(
            reflection(
                figureId = augustine.id, epochDay = 300L, tone = "morning",
                scriptureReference = "Local Ref", synced = true,
            )
        )
        val remote = FakeDailyReflectionRemoteDataSource(
            initialRows = listOf(
                DailyReflectionRow(
                    userId = USER_ID, epochDay = 300L, tone = "morning", theme = "NEWS",
                    figureServerId = lewis.serverId, scriptureReference = "Remote Ref",
                    scriptureText = "text", insight = "i", implication = "im", inspiration = "in",
                    sources = emptyList(),
                )
            )
        )

        repo(dao = dao, remote = remote).resolve(USER_ID)

        assertEquals("Local Ref", dao.getRawById("300_morning_NEWS")!!.scriptureReference)
    }

    @Test
    fun resolve_skipsPulledRowWhenFigureNotYetSyncedLocally() = runTest {
        val dao = FakeDailyReflectionDao()
        val figureDao = FakeFigureDaoForReflectionSync(listOf(augustine)) // lewis missing
        val remote = FakeDailyReflectionRemoteDataSource(
            initialRows = listOf(
                DailyReflectionRow(
                    userId = USER_ID, epochDay = 400L, tone = "morning", theme = "NEWS",
                    figureServerId = lewis.serverId, scriptureReference = "ref",
                    scriptureText = "text", insight = "i", implication = "im", inspiration = "in",
                    sources = emptyList(),
                )
            )
        )

        repo(dao = dao, figureDao = figureDao, remote = remote).resolve(USER_ID)

        assertNull(dao.getRawById("400_morning_NEWS"))
    }

    @Test
    fun resolve_wipesLocalDataWhenADifferentAccountSignsIn() = runTest {
        val dao = FakeDailyReflectionDao()
        dao.upsert(reflection(figureId = augustine.id, epochDay = 500L, tone = "morning", synced = true))
        val syncMetaDao = FakeSyncMetaDao(SyncMetaEntity(lastDailyReflectionSyncUserId = "previous-user"))
        val remote = FakeDailyReflectionRemoteDataSource()

        repo(dao = dao, remote = remote, syncMetaDao = syncMetaDao).resolve(USER_ID)

        assertNull(dao.getRawById("500_morning_NEWS"))
        assertEquals(USER_ID, syncMetaDao.get()?.lastDailyReflectionSyncUserId)
    }

    @Test
    fun resolve_isNoOpBeyondFlippingIsResolvedWhenRemoteDataSourceIsUnconfigured() = runTest {
        val dao = FakeDailyReflectionDao()
        dao.upsert(reflection(figureId = augustine.id, epochDay = 600L, tone = "morning", synced = false))

        repo(dao = dao, remote = null).resolve(USER_ID)

        assertFalse(dao.getRawById("600_morning_NEWS")!!.synced)
    }

    @Test
    fun resolve_setsIsResolvedTrueEvenWhenSyncThrows() = runTest {
        val remote = FakeDailyReflectionRemoteDataSource(shouldThrowOnFetch = true)
        val repository = repo(remote = remote)

        repository.resolve(USER_ID)

        assertTrue(repository.isResolved.value)
    }

    private fun reflection(
        figureId: Long,
        epochDay: Long,
        tone: String,
        theme: String = "NEWS",
        scriptureReference: String = "ref",
        synced: Boolean = false,
    ) = DailyReflectionEntity(
        id = "${epochDay}_${tone}_$theme",
        figureId = figureId,
        epochDay = epochDay,
        tone = tone,
        theme = theme,
        scriptureReference = scriptureReference,
        scriptureText = "text",
        insight = "insight",
        implication = "implication",
        inspiration = "inspiration",
        sources = emptyList(),
        synced = synced,
    )
}

private class FakeDailyReflectionDao : DailyReflectionDao {
    val upsertCalls = mutableListOf<DailyReflectionEntity>()
    private val store = mutableMapOf<String, DailyReflectionEntity>()

    override suspend fun get(figureId: Long, epochDay: Long, tone: String, theme: String): DailyReflectionEntity? =
        store.values.find { it.figureId == figureId && it.epochDay == epochDay && it.tone == tone && it.theme == theme }

    override suspend fun getRawById(id: String): DailyReflectionEntity? = store[id]

    override suspend fun getAllForDay(figureId: Long, epochDay: Long): List<DailyReflectionEntity> =
        store.values.filter { it.figureId == figureId && it.epochDay == epochDay }

    override suspend fun getAllScripturesForDay(epochDay: Long): List<String> =
        store.values.filter { it.epochDay == epochDay }.map { it.scriptureReference }.distinct()

    override suspend fun getRecentScripturesForFigure(figureId: Long, fromDay: Long, today: Long): List<String> =
        store.values.filter { it.figureId == figureId && it.epochDay in fromDay until today }
            .map { it.scriptureReference }.distinct()

    override fun getByEpochDayRange(start: Long, end: Long): Flow<List<DailyReflectionEntity>> =
        flowOf(store.values.filter { it.epochDay in start..end })

    override suspend fun getEarliestEpochDay(): Long? = store.values.minOfOrNull { it.epochDay }

    override suspend fun getForDayAndTone(epochDay: Long, tone: String): DailyReflectionEntity? =
        store.values.find { it.epochDay == epochDay && it.tone == tone }

    override suspend fun getFigureIdForDay(epochDay: Long): Long? =
        store.values.find { it.epochDay == epochDay }?.figureId

    override suspend fun getPendingSync(): List<DailyReflectionEntity> = store.values.filterNot { it.synced }

    override suspend fun markSynced(id: String) {
        store[id]?.let { store[id] = it.copy(synced = true) }
    }

    override suspend fun clearAll() { store.clear() }

    override suspend fun upsert(entity: DailyReflectionEntity) {
        upsertCalls.add(entity)
        store[entity.id] = entity
    }

    override suspend fun insertIfAbsent(entity: DailyReflectionEntity) {
        store.putIfAbsent(entity.id, entity)
    }
}

private class FakeFigureDaoForReflectionSync(figures: List<FigureEntity> = emptyList()) : FigureDao {
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

    override suspend fun getByServerId(serverId: Long): FigureEntity? =
        store.values.find { it.serverId == serverId }

    override fun observeByCategory(category: String): Flow<List<FigureEntity>> =
        flowOf(store.values.filter { it.category == category })

    override suspend fun getByName(name: String): FigureEntity? =
        store.values.find { it.name == name }

    override suspend fun getByNameIgnoreCase(name: String): FigureEntity? =
        store.values.find { it.name.lowercase() == name.lowercase() }

    override suspend fun deleteById(id: Long) { store.remove(id) }

    override suspend fun deleteAll() { store.clear() }
}

private class FakeSyncMetaDao(private var meta: SyncMetaEntity? = null) : SyncMetaDao {
    override suspend fun get(): SyncMetaEntity? = meta
    override suspend fun upsert(meta: SyncMetaEntity) { this.meta = meta }
}

private class FakeAuthRepository(private val userId: String?) : AuthRepository {
    override fun observeAuthState(): Flow<UserSession?> =
        MutableStateFlow(userId?.let { UserSession(it, null) })

    override fun currentSession(): UserSession? = userId?.let { UserSession(it, null) }
    override suspend fun signInWithEmail(email: String, password: String) = Unit
    override suspend fun signOut() = Unit
}

private class FakeDailyReflectionRemoteDataSource(
    initialRows: List<DailyReflectionRow> = emptyList(),
    private val shouldThrowOnPush: Boolean = false,
    private val shouldThrowOnFetch: Boolean = false,
) : DailyReflectionRemoteDataSource {
    private val rows = initialRows.associateBy { Triple(it.epochDay, it.tone, it.theme) }.toMutableMap()
    val pushedRows = mutableListOf<DailyReflectionRow>()

    override suspend fun push(row: DailyReflectionRow) {
        if (shouldThrowOnPush) throw RuntimeException("Push failed")
        pushedRows.add(row)
        rows[Triple(row.epochDay, row.tone, row.theme)] = row
    }

    override suspend fun fetchAll(userId: String): List<DailyReflectionRow> {
        if (shouldThrowOnFetch) throw RuntimeException("Fetch failed")
        return rows.values.filter { it.userId == userId }
    }

    override suspend fun fetchOne(userId: String, epochDay: Long, tone: String, theme: String): DailyReflectionRow? {
        if (shouldThrowOnFetch) throw RuntimeException("Fetch failed")
        return rows[Triple(epochDay, tone, theme)]?.takeIf { it.userId == userId }
    }
}

private class FakeReflectionApi(
    private val response: DailyReflectionResponseDto = DailyReflectionResponseDto(
        scriptureReference = "ref", scriptureText = "text", insight = "insight",
        implication = "implication", inspiration = "inspiration", sources = emptyList(), tone = "morning"
    ),
) : MediaSageApi {
    var callCount = 0

    override suspend fun getAssignmentDefaults(): List<AssignmentDefaultDto> = emptyList()

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

    override suspend fun getDailyReflection(request: DailyReflectionRequestDto): DailyReflectionResponseDto {
        callCount++
        return response
    }
}
