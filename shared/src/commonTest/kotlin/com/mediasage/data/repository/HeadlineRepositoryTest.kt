package com.mediasage.data.repository

import com.mediasage.data.local.dao.HeadlineDao
import com.mediasage.data.local.entity.HeadlineEntity
import com.mediasage.data.local.entity.ReadHeadlineEntity
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
import com.mediasage.domain.model.UserSession
import com.mediasage.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HeadlineRepositoryTest {

    private val headline = HeadlineEntity(
        id = 1, title = "Test headline", source = "BBC",
        url = "https://example.com/a", publishedAt = 1000L, fetchedAt = 2000L
    )

    private fun repo(
        dao: FakeHeadlineDaoForUserScopedRead = FakeHeadlineDaoForUserScopedRead(listOf(headline)),
        authRepository: FakeAuthRepositoryForHeadlineSync = FakeAuthRepositoryForHeadlineSync(USER_A),
        api: MediaSageApi = FakeMediaSageApiForHeadlineSync()
    ) = HeadlineRepositoryImpl(dao, api, authRepository)

    @Test
    fun markAsReadDoesNotLeakToADifferentUser() = runTest {
        val dao = FakeHeadlineDaoForUserScopedRead(listOf(headline))
        val authRepository = FakeAuthRepositoryForHeadlineSync(USER_A)
        val repository = repo(dao = dao, authRepository = authRepository)

        repository.markAsRead(headline.url)
        assertTrue(repository.observeHeadlines().first().single().isRead)

        authRepository.setUser(USER_B)
        assertFalse(repository.observeHeadlines().first().single().isRead)
    }

    @Test
    fun sameUserSeesTheirOwnReadStateAfterSwitchingAwayAndBack() = runTest {
        val dao = FakeHeadlineDaoForUserScopedRead(listOf(headline))
        val authRepository = FakeAuthRepositoryForHeadlineSync(USER_A)
        val repository = repo(dao = dao, authRepository = authRepository)

        repository.markAsRead(headline.url)
        authRepository.setUser(USER_B)
        authRepository.setUser(USER_A)

        assertTrue(repository.observeHeadlines().first().single().isRead)
    }

    @Test
    fun markAsReadFlagsTheArticleForTheCurrentUser() = runTest {
        val dao = FakeHeadlineDaoForUserScopedRead(listOf(headline))
        val repository = repo(dao = dao)

        assertFalse(repository.observeHeadlines().first().single().isRead)
        repository.markAsRead(headline.url)
        assertTrue(repository.observeHeadlines().first().single().isRead)
    }

    @Test
    fun readStateSurvivesAHeadlineCacheRefresh() = runTest {
        val dao = FakeHeadlineDaoForUserScopedRead(listOf(headline))
        val api = FakeMediaSageApiForHeadlineSync(
            headlines = listOf(NewsArticleDto(uuid = "1", title = headline.title, url = headline.url))
        )
        val repository = repo(dao = dao, api = api)

        repository.markAsRead(headline.url)
        repository.refreshHeadlines()

        assertTrue(repository.observeHeadlines().first().single().isRead)
    }

    private companion object {
        const val USER_A = "user-a"
        const val USER_B = "user-b"
    }
}

private class FakeHeadlineDaoForUserScopedRead(initial: List<HeadlineEntity>) : HeadlineDao {
    private val headlines = MutableStateFlow(initial)
    private val readHeadlines = MutableStateFlow<List<ReadHeadlineEntity>>(emptyList())

    override suspend fun insert(headline: HeadlineEntity): Long {
        headlines.value = headlines.value + headline
        return headline.id
    }

    override suspend fun insertAll(headlines: List<HeadlineEntity>) {
        this.headlines.value = this.headlines.value + headlines
    }

    override fun observeAll(): Flow<List<HeadlineEntity>> = headlines

    override suspend fun getById(id: Long): HeadlineEntity? = headlines.value.find { it.id == id }

    override suspend fun getIdByUrl(url: String): Long? = headlines.value.find { it.url == url }?.id

    override suspend fun getByUrl(url: String): HeadlineEntity? = headlines.value.find { it.url == url }

    override suspend fun deleteOlderThan(olderThan: Long) {
        headlines.value = headlines.value.filterNot { it.fetchedAt < olderThan }
    }

    override suspend fun deleteAll() {
        headlines.value = emptyList()
    }

    override suspend fun markRead(readHeadline: ReadHeadlineEntity) {
        readHeadlines.value = readHeadlines.value.filterNot {
            it.userId == readHeadline.userId && it.url == readHeadline.url
        } + readHeadline
    }

    override fun observeReadUrls(userId: String): Flow<List<String>> =
        readHeadlines.map { rows -> rows.filter { it.userId == userId }.map { it.url } }

    override suspend fun isRead(userId: String, url: String): Boolean =
        readHeadlines.value.any { it.userId == userId && it.url == url }
}

private class FakeAuthRepositoryForHeadlineSync(initialUserId: String?) : AuthRepository {
    private val session = MutableStateFlow(initialUserId?.let { UserSession(it, null) })

    fun setUser(userId: String?) {
        session.value = userId?.let { UserSession(it, null) }
    }

    override fun observeAuthState(): Flow<UserSession?> = session
    override fun currentSession(): UserSession? = session.value
    override suspend fun signInWithEmail(email: String, password: String) = Unit
    override suspend fun signUp(email: String, password: String, displayName: String) = Unit
    override suspend fun verifySignUpOtp(email: String, token: String) = Unit
    override suspend fun signOut() = Unit
}

private class FakeMediaSageApiForHeadlineSync(private val headlines: List<NewsArticleDto> = emptyList()) : MediaSageApi {
    override suspend fun getFigures(since: Long?): FiguresResponse = error("not used in this test")
    override suspend fun getHeadlines(locale: String, limit: Int): List<NewsArticleDto> = headlines
    override suspend fun searchNews(query: String, limit: Int): List<NewsArticleDto> = error("not used in this test")
    override suspend fun encourage(request: EncourageRequestDto): EncourageResultDto = error("not used in this test")
    override suspend fun matchQuote(request: MatchRequestDto): MatchResultDto = error("not used in this test")
    override suspend fun searchScripture(query: String, limit: Int): List<ScriptureVerseDto> =
        error("not used in this test")
    override suspend fun getPassage(passageId: String): ScripturePassageDto = error("not used in this test")
    override suspend fun getDailyReflection(request: DailyReflectionRequestDto): DailyReflectionResponseDto =
        error("not used in this test")
    override suspend fun getAssignmentDefaults(): List<AssignmentDefaultDto> = error("not used in this test")
}
