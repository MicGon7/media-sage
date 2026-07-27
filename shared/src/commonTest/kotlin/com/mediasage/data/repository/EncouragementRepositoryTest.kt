package com.mediasage.data.repository

import com.mediasage.data.local.entity.EncouragementEntity
import com.mediasage.data.local.entity.FigureEntity
import com.mediasage.data.local.entity.SyncMetaEntity
import com.mediasage.data.local.entity.VoiceFigureProjection
import com.mediasage.data.local.dao.EncouragementDao
import com.mediasage.data.local.dao.FigureDao
import com.mediasage.data.local.dao.SyncMetaDao
import com.mediasage.data.remote.DailyReflectionRequestDto
import com.mediasage.data.remote.DailyReflectionResponseDto
import com.mediasage.data.remote.EncourageRequestDto
import com.mediasage.data.remote.EncourageResultDto
import com.mediasage.data.remote.AssignmentDefaultDto
import com.mediasage.data.remote.FiguresResponse
import com.mediasage.data.remote.MatchRequestDto
import com.mediasage.data.remote.MatchResultDto
import com.mediasage.data.remote.MediaSageApi
import com.mediasage.data.remote.NewsArticleDto
import com.mediasage.data.remote.ScripturePassageDto
import com.mediasage.data.remote.ScriptureVerseDto
import com.mediasage.domain.model.UserSession
import com.mediasage.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val USER_ID = "user-1"

class EncouragementRepositoryTest {

    private val sampleResult = EncourageResultDto(
        summary = "A summary",
        quoteText = "Darkness cannot drive out darkness",
        figureName = "Martin Luther King Jr.",
        figureRole = "Civil Rights Leader",
        scriptureReference = "John 1:5",
        scriptureText = "The light shines in the darkness",
        explanation = "Both speak to overcoming darkness with light",
        connectionThemes = listOf("light", "hope"),
        matchTheme = "overcoming",
        tone = "hopeful"
    )

    private fun repo(
        dao: FakeEncouragementDao = FakeEncouragementDao(),
        figureDao: FakeFigureDao = FakeFigureDao(),
        api: FakeMediaSageApi = FakeMediaSageApi(result = sampleResult),
        remote: FakeSavedInsightRemoteDataSource? = FakeSavedInsightRemoteDataSource(),
        syncMetaDao: FakeSyncMetaDao = FakeSyncMetaDao(),
        authRepository: FakeAuthRepository = FakeAuthRepository(USER_ID),
    ) = EncouragementRepositoryImpl(api, dao, figureDao, remote, syncMetaDao, authRepository)

    @Test
    fun returnsCachedEncouragementWhenArticleUrlHit() = runTest {
        val cached = EncouragementEntity(
            articleUrl = "https://example.com/article",
            summary = "Cached summary",
            quoteText = "Cached quote",
            figureName = "Augustine",
            figureRole = "Bishop of Hippo",
            scriptureReference = "Psalm 23:1",
            scriptureText = "The Lord is my shepherd",
            explanation = "A cached explanation",
            connectionThemes = "peace,trust",
            matchTheme = "trust",
            tone = "peaceful",
            headlineTitle = "Cached headline"
        )
        val dao = FakeEncouragementDao(preloaded = listOf(cached))
        val api = FakeMediaSageApi(result = sampleResult)

        val result = repo(dao = dao, api = api).getEncouragement("Cached headline", articleUrl = "https://example.com/article")

        assertEquals("Cached quote", result.quoteText)
        assertEquals(0, api.encourageCallCount)
    }

    @Test
    fun callsApiAndSavesWhenNoCacheHit() = runTest {
        val dao = FakeEncouragementDao()
        val api = FakeMediaSageApi(result = sampleResult)

        val result = repo(dao = dao, api = api).getEncouragement("Breaking news", articleUrl = "https://example.com/news")

        assertEquals("Darkness cannot drive out darkness", result.quoteText)
        assertEquals(1, api.encourageCallCount)
        assertEquals(1, dao.insertCallCount)
        assertNotNull(dao.getByArticleUrl("https://example.com/news"))
    }

    @Test
    fun doesNotSaveDuplicateQuoteTextForSameFigure() = runTest {
        val dao = FakeEncouragementDao()
        val api = FakeMediaSageApi(result = sampleResult)
        val repository = repo(dao = dao, api = api)

        repository.getEncouragement("Article A", articleUrl = "https://example.com/a")
        repository.getEncouragement("Article B", articleUrl = "https://example.com/b")

        assertEquals(2, api.encourageCallCount)
        assertEquals(1, dao.insertCallCount)
    }

    @Test
    fun doesNotCallApiWhenArticleUrlIsNull() = runTest {
        val dao = FakeEncouragementDao()
        val api = FakeMediaSageApi(result = sampleResult)

        val result = repo(dao = dao, api = api).getEncouragement("Breaking news", articleUrl = null)

        assertEquals("Darkness cannot drive out darkness", result.quoteText)
        assertEquals(1, api.encourageCallCount)
        assertEquals(0, dao.insertCallCount)
    }

    @Test
    fun populatesFigureIdWhenFigureExistsOnCache() = runTest {
        val figure = FigureEntity(id = 42, name = "Martin Luther King Jr.", category = "social_justice", century = "20th")
        val dao = FakeEncouragementDao()
        val figureDao = FakeFigureDao(figures = listOf(figure))

        repo(dao = dao, figureDao = figureDao).getEncouragement("Article", articleUrl = "https://example.com/article")

        val saved = dao.getByArticleUrl("https://example.com/article")
        assertEquals(42L, saved?.figureId)
    }

    @Test
    fun getByFigureIdReturnsMappedEncouragements() = runTest {
        val entity = EncouragementEntity(
            articleUrl = "https://example.com/a",
            summary = null,
            quoteText = "Test quote",
            figureName = "Augustine",
            figureRole = "Bishop",
            scriptureReference = "Psalm 23",
            scriptureText = "The Lord is my shepherd",
            explanation = "Explanation",
            connectionThemes = "peace",
            matchTheme = "trust",
            tone = "hopeful",
            figureId = 7L
        )
        val dao = FakeEncouragementDao(preloaded = listOf(entity))

        val results = repo(dao = dao).observeByFigureId(7L).first()

        assertEquals(1, results.size)
        assertEquals("Test quote", results.first().quoteText)
    }

    @Test
    fun toggleBookmarkFlipsBookmarkedState() = runTest {
        val cached = EncouragementEntity(
            articleUrl = "https://example.com/article",
            summary = null,
            quoteText = "Test quote",
            figureName = "Augustine",
            figureRole = "Bishop",
            scriptureReference = "Psalm 23",
            scriptureText = "The Lord is my shepherd",
            explanation = "Explanation",
            connectionThemes = "peace",
            matchTheme = "trust",
            tone = "hopeful",
            bookmarked = false
        )
        val dao = FakeEncouragementDao(preloaded = listOf(cached))
        val repository = repo(dao = dao, remote = null)

        assertFalse(repository.observeIsBookmarked("https://example.com/article").first())

        repository.toggleBookmark("https://example.com/article")
        assertTrue(repository.observeIsBookmarked("https://example.com/article").first())

        repository.toggleBookmark("https://example.com/article")
        assertFalse(repository.observeIsBookmarked("https://example.com/article").first())
    }

    @Test
    fun observeIsBookmarkedReturnsFalseForUnknownUrl() = runTest {
        assertFalse(repo().observeIsBookmarked("https://example.com/unknown").first())
    }

    @Test
    fun toggleBookmarkOnPushesFullSnapshotAndMarksSynced() = runTest {
        val figure = FigureEntity(id = 1, name = "Augustine", category = "church_father", century = "4th", serverId = 101)
        val cached = EncouragementEntity(
            articleUrl = "https://example.com/article",
            summary = "A summary",
            quoteText = "Test quote",
            figureName = "Augustine",
            figureRole = "Bishop",
            scriptureReference = "Psalm 23",
            scriptureText = "The Lord is my shepherd",
            explanation = "Explanation",
            connectionThemes = "peace,trust",
            matchTheme = "trust",
            tone = "hopeful",
            figureId = 1L,
        )
        val dao = FakeEncouragementDao(preloaded = listOf(cached))
        val figureDao = FakeFigureDao(figures = listOf(figure))
        val remote = FakeSavedInsightRemoteDataSource()

        repo(dao = dao, figureDao = figureDao, remote = remote).toggleBookmark("https://example.com/article")

        assertEquals(1, remote.pushedRows.size)
        assertEquals(listOf("peace", "trust"), remote.pushedRows.first().connectionThemes)
        assertEquals(101L, remote.pushedRows.first().figureServerId)
        assertTrue(dao.getByArticleUrl("https://example.com/article")!!.synced)
    }

    @Test
    fun toggleBookmarkOffPushesDeleteAndClearsBookmarkButKeepsCacheContent() = runTest {
        val cached = EncouragementEntity(
            articleUrl = "https://example.com/article",
            summary = null,
            quoteText = "Test quote",
            figureName = "Augustine",
            figureRole = "Bishop",
            scriptureReference = "Psalm 23",
            scriptureText = "The Lord is my shepherd",
            explanation = "Explanation",
            connectionThemes = "peace",
            matchTheme = "trust",
            tone = "hopeful",
            bookmarked = true,
            synced = true,
        )
        val dao = FakeEncouragementDao(preloaded = listOf(cached))
        val remote = FakeSavedInsightRemoteDataSource()

        repo(dao = dao, remote = remote).toggleBookmark("https://example.com/article")

        assertEquals(listOf("https://example.com/article"), remote.deletedArticleUrls)
        val row = dao.getByArticleUrl("https://example.com/article")
        assertNotNull(row)
        assertFalse(row!!.bookmarked)
        assertEquals("Test quote", row.quoteText) // cache content survives an unbookmark
    }

    @Test
    fun toggleBookmarkOffKeepsTombstoneWhenDeletePushFails() = runTest {
        val cached = EncouragementEntity(
            articleUrl = "https://example.com/article",
            summary = null,
            quoteText = "Test quote",
            figureName = "Augustine",
            figureRole = "Bishop",
            scriptureReference = "Psalm 23",
            scriptureText = "The Lord is my shepherd",
            explanation = "Explanation",
            connectionThemes = "peace",
            matchTheme = "trust",
            tone = "hopeful",
            bookmarked = true,
            synced = true,
        )
        val dao = FakeEncouragementDao(preloaded = listOf(cached))
        val remote = FakeSavedInsightRemoteDataSource(shouldThrowOnDelete = true)

        repo(dao = dao, remote = remote).toggleBookmark("https://example.com/article")

        val row = dao.getByArticleUrl("https://example.com/article")
        assertTrue(row!!.pendingDelete)
        assertFalse(row.synced)
    }

    @Test
    fun resolvePullsRemoteContentIntoLocalDaoForUncachedDevice() = runTest {
        val figure = FigureEntity(id = 1, name = "Julian of Norwich", category = "mystic", century = "14th", serverId = 202)
        val figureDao = FakeFigureDao(figures = listOf(figure))
        val dao = FakeEncouragementDao()
        val remote = FakeSavedInsightRemoteDataSource(
            initialRows = listOf(
                SavedInsightRow(
                    userId = USER_ID,
                    articleUrl = "https://example.com/never-cached",
                    figureServerId = 202,
                    quoteText = "All shall be well",
                    figureName = "Julian of Norwich",
                    figureRole = "Anchoress",
                    scriptureReference = "Romans 8:28",
                    scriptureText = "All things work together for good",
                    explanation = "Hope in adversity",
                    connectionThemes = listOf("hope"),
                    matchTheme = "hope",
                    tone = "comforting",
                )
            )
        )

        repo(dao = dao, figureDao = figureDao, remote = remote).resolve(USER_ID)

        val local = dao.getByArticleUrl("https://example.com/never-cached")
        assertNotNull(local)
        assertTrue(local!!.bookmarked)
        assertTrue(local.synced)
        assertEquals("All shall be well", local.quoteText)
        assertEquals(1L, local.figureId)
    }

    @Test
    fun resolveDoesNotClobberAnUnsyncedLocalBookmarkToggle() = runTest {
        val figure = FigureEntity(id = 1, name = "Augustine", category = "church_father", century = "4th", serverId = 101)
        val figureDao = FakeFigureDao(figures = listOf(figure))
        val local = EncouragementEntity(
            articleUrl = "https://example.com/article",
            summary = null,
            quoteText = "Local pending quote",
            figureName = "Augustine",
            figureRole = "Bishop",
            scriptureReference = "Psalm 23",
            scriptureText = "The Lord is my shepherd",
            explanation = "Explanation",
            connectionThemes = "peace",
            matchTheme = "trust",
            tone = "hopeful",
            bookmarked = true,
            figureId = 1L,
            synced = false,
        )
        val dao = FakeEncouragementDao(preloaded = listOf(local))
        val remote = FakeSavedInsightRemoteDataSource(
            initialRows = listOf(
                SavedInsightRow(
                    userId = USER_ID,
                    articleUrl = "https://example.com/article",
                    figureServerId = 101,
                    quoteText = "Remote quote",
                    figureName = "Augustine",
                    figureRole = "Bishop",
                    scriptureReference = "Psalm 23",
                    scriptureText = "The Lord is my shepherd",
                    explanation = "Explanation",
                    connectionThemes = listOf("peace"),
                    matchTheme = "trust",
                    tone = "hopeful",
                )
            ),
            shouldThrowOnPush = true,
        )

        repo(dao = dao, figureDao = figureDao, remote = remote).resolve(USER_ID)

        assertEquals("Local pending quote", dao.getByArticleUrl("https://example.com/article")?.quoteText)
    }

    @Test
    fun resolveUnbookmarksLocallyWhenRemovedRemotelyButKeepsCacheContent() = runTest {
        val local = EncouragementEntity(
            articleUrl = "https://example.com/article",
            summary = null,
            quoteText = "Test quote",
            figureName = "Augustine",
            figureRole = "Bishop",
            scriptureReference = "Psalm 23",
            scriptureText = "The Lord is my shepherd",
            explanation = "Explanation",
            connectionThemes = "peace",
            matchTheme = "trust",
            tone = "hopeful",
            bookmarked = true,
            synced = true,
        )
        val dao = FakeEncouragementDao(preloaded = listOf(local))
        val remote = FakeSavedInsightRemoteDataSource() // empty — unbookmarked on another device

        repo(dao = dao, remote = remote).resolve(USER_ID)

        val row = dao.getByArticleUrl("https://example.com/article")
        assertNotNull(row)
        assertFalse(row!!.bookmarked)
        assertEquals("Test quote", row.quoteText)
    }

    @Test
    fun resolveResetsOnlyBookmarkStateOnAccountSwitchNotSharedCacheContent() = runTest {
        val bookmarked = EncouragementEntity(
            articleUrl = "https://example.com/bookmarked",
            summary = null,
            quoteText = "Bookmarked quote",
            figureName = "Augustine",
            figureRole = "Bishop",
            scriptureReference = "Psalm 23",
            scriptureText = "The Lord is my shepherd",
            explanation = "Explanation",
            connectionThemes = "peace",
            matchTheme = "trust",
            tone = "hopeful",
            bookmarked = true,
            synced = true,
        )
        val cached = EncouragementEntity(
            articleUrl = "https://example.com/cached",
            summary = null,
            quoteText = "Unrelated cached quote",
            figureName = "Luther",
            figureRole = "Reformer",
            scriptureReference = "Romans 1",
            scriptureText = "text",
            explanation = "explanation",
            connectionThemes = "faith",
            matchTheme = "faith",
            tone = "bold",
            bookmarked = false,
        )
        val dao = FakeEncouragementDao(preloaded = listOf(bookmarked, cached))
        val syncMetaDao = FakeSyncMetaDao(SyncMetaEntity(lastSavedInsightSyncUserId = "previous-user"))

        repo(dao = dao, syncMetaDao = syncMetaDao).resolve(USER_ID)

        assertFalse(dao.getByArticleUrl("https://example.com/bookmarked")!!.bookmarked)
        assertEquals("Unrelated cached quote", dao.getByArticleUrl("https://example.com/cached")?.quoteText)
        assertEquals(USER_ID, syncMetaDao.get()?.lastSavedInsightSyncUserId)
    }

    @Test
    fun isResolvedIsFalseBeforeResolveIsCalled() = runTest {
        assertFalse(repo().isResolved.value)
    }

    @Test
    fun resolveSetsIsResolvedTrueEvenWhenSyncThrows() = runTest {
        val remote = FakeSavedInsightRemoteDataSource(shouldThrowOnFetch = true)
        val repository = repo(remote = remote)

        repository.resolve(USER_ID)

        assertTrue(repository.isResolved.value)
    }

    @Test
    fun resolveIsNoOpBeyondFlippingIsResolvedWhenRemoteDataSourceIsUnconfigured() = runTest {
        val dao = FakeEncouragementDao()

        repo(dao = dao, remote = null).resolve(USER_ID)

        assertEquals(0, dao.upsertCallCount)
    }
}

private class FakeEncouragementDao(preloaded: List<EncouragementEntity> = emptyList()) : EncouragementDao {
    private val store = preloaded.associateBy { it.articleUrl }.toMutableMap()
    var insertCallCount = 0
    var upsertCallCount = 0

    override suspend fun insert(encouragement: EncouragementEntity) {
        val isDuplicate = store.values.any {
            it.figureName == encouragement.figureName && it.quoteText == encouragement.quoteText
        }
        if (!isDuplicate) {
            store[encouragement.articleUrl] = encouragement
            insertCallCount++
        }
    }

    override suspend fun upsert(encouragement: EncouragementEntity) {
        store[encouragement.articleUrl] = encouragement
        upsertCallCount++
    }

    override suspend fun getByArticleUrl(articleUrl: String): EncouragementEntity? = store[articleUrl]

    override fun observeDistinctFigures(): Flow<List<VoiceFigureProjection>> = emptyFlow()

    override fun observeByFigureName(figureName: String): Flow<List<EncouragementEntity>> =
        flowOf(store.values.filter { it.figureName == figureName })

    override fun observeByFigureId(figureId: Long): Flow<List<EncouragementEntity>> =
        flowOf(store.values.filter { it.figureId == figureId })

    override fun observeCountByFigureName(): Flow<Map<String, Int>> =
        flowOf(store.values.groupBy { it.figureName }.mapValues { (_, v) -> v.size })

    override suspend fun getRecentFigureNames(limit: Int): List<String> =
        store.values
            .sortedByDescending { it.cachedAt }
            .map { it.figureName }
            .distinct()
            .take(limit)

    override fun observeAll(): Flow<List<EncouragementEntity>> = flowOf(store.values.toList())

    override fun observeBookmarked(): Flow<List<EncouragementEntity>> =
        flowOf(store.values.filter { it.bookmarked })

    override fun observeBookmarkState(articleUrl: String): Flow<Boolean> =
        flowOf(store[articleUrl]?.bookmarked ?: false)

    override suspend fun toggleBookmark(articleUrl: String) {
        store[articleUrl]?.let {
            val newBookmarked = !it.bookmarked
            store[articleUrl] = it.copy(
                bookmarked = newBookmarked,
                synced = false,
                pendingDelete = it.bookmarked, // old value — mirrors the SQL CASE WHEN bookmarked
            )
        }
    }

    override suspend fun getPendingSync(): List<EncouragementEntity> =
        store.values.filter { it.pendingDelete || (it.bookmarked && !it.synced) }

    override suspend fun markSynced(articleUrl: String) {
        store[articleUrl]?.let { store[articleUrl] = it.copy(synced = true) }
    }

    override suspend fun clearBookmarkState(articleUrl: String) {
        store[articleUrl]?.let { store[articleUrl] = it.copy(bookmarked = false, synced = true, pendingDelete = false) }
    }

    override suspend fun resetBookmarkStateForAccountSwitch() {
        store.replaceAll { _, v -> if (v.bookmarked) v.copy(bookmarked = false, synced = true, pendingDelete = false) else v }
    }

    override suspend fun getSyncedBookmarkedArticleUrls(): List<String> =
        store.values.filter { it.bookmarked && it.synced }.map { it.articleUrl }

    override fun observeByDateRange(startMillis: Long, endMillis: Long): Flow<List<EncouragementEntity>> =
        flowOf(store.values.filter { it.cachedAt >= startMillis && it.cachedAt < endMillis })

    override fun observeActiveEpochDays(): Flow<List<Long>> =
        flowOf(store.values.filter { it.cachedAt > 0 }.map { it.cachedAt / 86400000 }.distinct())

    override suspend fun deleteAll() { store.clear() }
}

private class FakeFigureDao(figures: List<FigureEntity> = emptyList()) : FigureDao {
    private val store = figures.associateBy { it.name }.toMutableMap()

    override suspend fun insert(figure: FigureEntity): Long {
        store[figure.name] = figure
        return figure.id
    }

    override suspend fun insertAll(figures: List<FigureEntity>) {
        figures.forEach { store[it.name] = it }
    }

    override fun observeAll(): Flow<List<FigureEntity>> = flowOf(store.values.toList())

    override suspend fun getById(id: Long): FigureEntity? = store.values.find { it.id == id }

    override suspend fun getByServerId(serverId: Long): FigureEntity? =
        store.values.find { it.serverId == serverId }

    override fun observeByCategory(category: String): Flow<List<FigureEntity>> =
        flowOf(store.values.filter { it.category == category })

    override suspend fun getByName(name: String): FigureEntity? = store[name]

    override suspend fun getByNameIgnoreCase(name: String): FigureEntity? =
        store.values.find { it.name.lowercase() == name.lowercase() }

    override suspend fun deleteById(id: Long) { store.entries.removeAll { it.value.id == id } }

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

private class FakeSavedInsightRemoteDataSource(
    initialRows: List<SavedInsightRow> = emptyList(),
    private val shouldThrowOnPush: Boolean = false,
    private val shouldThrowOnDelete: Boolean = false,
    private val shouldThrowOnFetch: Boolean = false,
) : SavedInsightRemoteDataSource {
    private val rows = initialRows.associateBy { it.articleUrl }.toMutableMap()
    val pushedRows = mutableListOf<SavedInsightRow>()
    val deletedArticleUrls = mutableListOf<String>()

    override suspend fun push(row: SavedInsightRow) {
        if (shouldThrowOnPush) throw RuntimeException("Push failed")
        pushedRows.add(row)
        rows[row.articleUrl] = row
    }

    override suspend fun delete(userId: String, articleUrl: String) {
        if (shouldThrowOnDelete) throw RuntimeException("Delete failed")
        deletedArticleUrls.add(articleUrl)
        rows.remove(articleUrl)
    }

    override suspend fun fetchAll(userId: String): List<SavedInsightRow> {
        if (shouldThrowOnFetch) throw RuntimeException("Fetch failed")
        return rows.values.filter { it.userId == userId }
    }
}

private class FakeMediaSageApi(private val result: EncourageResultDto) : MediaSageApi {
    var encourageCallCount = 0
    var lastRequest: EncourageRequestDto? = null

    override suspend fun encourage(request: EncourageRequestDto): EncourageResultDto {
        encourageCallCount++
        lastRequest = request
        return result
    }

    override suspend fun getFigures(since: Long?): FiguresResponse = FiguresResponse(syncedAt = 0L, figures = emptyList())
    override suspend fun getHeadlines(locale: String, limit: Int): List<NewsArticleDto> = emptyList()
    override suspend fun searchNews(query: String, limit: Int): List<NewsArticleDto> = emptyList()

    @Suppress("DEPRECATION")
    override suspend fun matchQuote(request: MatchRequestDto): MatchResultDto =
        MatchResultDto(selectedQuoteId = 0, confidence = 0f, explanation = "", connectionThemes = emptyList())

    override suspend fun searchScripture(query: String, limit: Int): List<ScriptureVerseDto> = emptyList()
    override suspend fun getPassage(passageId: String): ScripturePassageDto =
        ScripturePassageDto(id = "", reference = "", content = "")
    override suspend fun getDailyReflection(request: DailyReflectionRequestDto): DailyReflectionResponseDto =
        DailyReflectionResponseDto(scriptureReference = "", scriptureText = "", insight = "", implication = "", inspiration = "", sources = emptyList(), tone = "morning")

    override suspend fun getAssignmentDefaults(): List<AssignmentDefaultDto> = emptyList()
}
