package com.mediasage.data.repository

import com.mediasage.data.local.entity.EncouragementEntity
import com.mediasage.data.local.entity.VoiceFigureProjection
import com.mediasage.data.local.dao.EncouragementDao
import com.mediasage.data.remote.EncourageRequestDto
import com.mediasage.data.remote.EncourageResultDto
import com.mediasage.data.remote.MatchCandidateDto
import com.mediasage.data.remote.MatchRequestDto
import com.mediasage.data.remote.MatchResultDto
import com.mediasage.data.remote.MediaSageApi
import com.mediasage.data.remote.NewsArticleDto
import com.mediasage.data.remote.ScripturePassageDto
import com.mediasage.data.remote.ScriptureVerseDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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
        val repo = EncouragementRepositoryImpl(api, dao)

        val result = repo.getEncouragement("Cached headline", articleUrl = "https://example.com/article")

        assertEquals("Cached quote", result.quoteText)
        assertEquals(0, api.encourageCallCount)
    }

    @Test
    fun callsApiAndSavesWhenNoCacheHit() = runTest {
        val dao = FakeEncouragementDao()
        val api = FakeMediaSageApi(result = sampleResult)
        val repo = EncouragementRepositoryImpl(api, dao)

        val result = repo.getEncouragement("Breaking news", articleUrl = "https://example.com/news")

        assertEquals("Darkness cannot drive out darkness", result.quoteText)
        assertEquals(1, api.encourageCallCount)
        assertEquals(1, dao.insertCallCount)
        assertNotNull(dao.getByArticleUrl("https://example.com/news"))
    }

    @Test
    fun doesNotSaveDuplicateQuoteTextForSameFigure() = runTest {
        val dao = FakeEncouragementDao()
        val api = FakeMediaSageApi(result = sampleResult)
        val repo = EncouragementRepositoryImpl(api, dao)

        repo.getEncouragement("Article A", articleUrl = "https://example.com/a")
        repo.getEncouragement("Article B", articleUrl = "https://example.com/b")

        assertEquals(2, api.encourageCallCount)
        assertEquals(1, dao.insertCallCount)
    }

    @Test
    fun passesRecentFiguresToApiOnSecondRequest() = runTest {
        val dao = FakeEncouragementDao()
        val api = FakeMediaSageApi(result = sampleResult)
        val repo = EncouragementRepositoryImpl(api, dao)

        repo.getEncouragement("First headline", articleUrl = "https://example.com/first")
        repo.getEncouragement("Second headline", articleUrl = "https://example.com/second")

        assertEquals(listOf("Martin Luther King Jr."), api.lastRequest?.recentFigures)
    }

    @Test
    fun doesNotCallApiWhenArticleUrlIsNull() = runTest {
        val dao = FakeEncouragementDao()
        val api = FakeMediaSageApi(result = sampleResult)
        val repo = EncouragementRepositoryImpl(api, dao)

        val result = repo.getEncouragement("Breaking news", articleUrl = null)

        assertEquals("Darkness cannot drive out darkness", result.quoteText)
        assertEquals(1, api.encourageCallCount)
        assertEquals(0, dao.insertCallCount)
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
        val api = FakeMediaSageApi(result = sampleResult)
        val repo = EncouragementRepositoryImpl(api, dao)

        assertFalse(repo.observeIsBookmarked("https://example.com/article").first())

        repo.toggleBookmark("https://example.com/article")
        assertTrue(repo.observeIsBookmarked("https://example.com/article").first())

        repo.toggleBookmark("https://example.com/article")
        assertFalse(repo.observeIsBookmarked("https://example.com/article").first())
    }

    @Test
    fun observeIsBookmarkedReturnsFalseForUnknownUrl() = runTest {
        val dao = FakeEncouragementDao()
        val api = FakeMediaSageApi(result = sampleResult)
        val repo = EncouragementRepositoryImpl(api, dao)

        assertFalse(repo.observeIsBookmarked("https://example.com/unknown").first())
    }
}

private class FakeEncouragementDao(preloaded: List<EncouragementEntity> = emptyList()) : EncouragementDao {
    private val store = preloaded.associateBy { it.articleUrl }.toMutableMap()
    var insertCallCount = 0

    override suspend fun insert(encouragement: EncouragementEntity) {
        val isDuplicate = store.values.any {
            it.figureName == encouragement.figureName && it.quoteText == encouragement.quoteText
        }
        if (!isDuplicate) {
            store[encouragement.articleUrl] = encouragement
            insertCallCount++
        }
    }

    override suspend fun getByArticleUrl(articleUrl: String): EncouragementEntity? = store[articleUrl]

    override fun getDistinctFigures(): Flow<List<VoiceFigureProjection>> = emptyFlow()

    override fun getByFigureName(figureName: String): Flow<List<EncouragementEntity>> =
        flowOf(store.values.filter { it.figureName == figureName })

    override suspend fun getRecentFigureNames(limit: Int): List<String> =
        store.values
            .sortedByDescending { it.cachedAt }
            .map { it.figureName }
            .distinct()
            .take(limit)

    override fun getAll(): Flow<List<EncouragementEntity>> = flowOf(store.values.toList())

    override fun getBookmarked(): Flow<List<EncouragementEntity>> =
        flowOf(store.values.filter { it.bookmarked })

    override fun observeBookmarkState(articleUrl: String): Flow<Boolean> =
        flowOf(store[articleUrl]?.bookmarked ?: false)

    override suspend fun toggleBookmark(articleUrl: String) {
        store[articleUrl]?.let { store[articleUrl] = it.copy(bookmarked = !it.bookmarked) }
    }

    override suspend fun deleteAll() { store.clear() }
}

private class FakeMediaSageApi(private val result: EncourageResultDto) : MediaSageApi {
    var encourageCallCount = 0
    var lastRequest: EncourageRequestDto? = null

    override suspend fun encourage(request: EncourageRequestDto): EncourageResultDto {
        encourageCallCount++
        lastRequest = request
        return result
    }

    override suspend fun getHeadlines(locale: String, limit: Int): List<NewsArticleDto> = emptyList()
    override suspend fun searchNews(query: String, limit: Int): List<NewsArticleDto> = emptyList()

    @Suppress("DEPRECATION")
    override suspend fun matchQuote(request: MatchRequestDto): MatchResultDto =
        MatchResultDto(selectedQuoteId = 0, confidence = 0f, explanation = "", connectionThemes = emptyList())

    override suspend fun searchScripture(query: String, limit: Int): List<ScriptureVerseDto> = emptyList()
    override suspend fun getPassage(passageId: String): ScripturePassageDto =
        ScripturePassageDto(id = "", reference = "", content = "")
}
