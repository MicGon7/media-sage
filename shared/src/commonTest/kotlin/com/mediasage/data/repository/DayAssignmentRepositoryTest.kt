@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.mediasage.data.repository

import com.mediasage.data.local.dao.DayAssignmentDao
import com.mediasage.data.local.dao.FigureDao
import com.mediasage.data.local.dao.SyncMetaDao
import com.mediasage.data.local.entity.DayAssignmentEntity
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
import com.mediasage.domain.model.DailyReflection
import com.mediasage.domain.model.UserSession
import com.mediasage.domain.repository.AuthRepository
import com.mediasage.domain.repository.DailyReflectionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val USER_ID = "user-1"

class DayAssignmentRepositoryTest {

    private val augustine = FigureEntity(id = 1, name = "Augustine of Hippo", category = "church_father", century = "4th", serverId = 101)
    private val julian = FigureEntity(id = 2, name = "Julian of Norwich", category = "mystic", century = "14th", serverId = 102)
    private val luther = FigureEntity(id = 3, name = "Martin Luther", category = "reformer", century = "16th", serverId = 103)
    private val brother = FigureEntity(id = 4, name = "Brother Lawrence", category = "mystic", century = "17th", serverId = 104)
    private val corrie = FigureEntity(id = 5, name = "Corrie ten Boom", category = "social_justice", century = "20th", serverId = 105)
    private val lewis = FigureEntity(id = 6, name = "C.S. Lewis", category = "theologian", century = "20th", serverId = 106)
    private val teresa = FigureEntity(id = 7, name = "Mother Teresa", category = "missionary", century = "20th", serverId = 107)

    private val allFigures = listOf(augustine, julian, luther, brother, corrie, lewis, teresa)

    private fun repo(
        dao: FakeDayAssignmentDao = FakeDayAssignmentDao(),
        figureDao: FakeFigureDaoForSeeding = FakeFigureDaoForSeeding(allFigures),
        api: FakeAssignmentApi = FakeAssignmentApi(),
        dailyReflectionRepository: FakeDailyReflectionRepository = FakeDailyReflectionRepository(),
        remote: FakeDayAssignmentRemoteDataSource? = FakeDayAssignmentRemoteDataSource(),
        syncMetaDao: FakeSyncMetaDao = FakeSyncMetaDao(),
        authRepository: FakeAuthRepository = FakeAuthRepository(USER_ID),
    ) = DayAssignmentRepositoryImpl(dao, figureDao, api, dailyReflectionRepository, remote, syncMetaDao, authRepository)

    @Test
    fun resolve_seeds7AssignmentsWhenTableEmptyAndNoUser() = runTest {
        val dao = FakeDayAssignmentDao(initialCount = 0)
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

        repo(dao = dao, api = api).resolve(null)

        assertEquals(7, dao.upsertCalls.size)
    }

    @Test
    fun resolve_skipsSeedingWhenTableNonEmptyAndNoUser() = runTest {
        val dao = FakeDayAssignmentDao(initialCount = 3)
        val api = FakeAssignmentApi()

        repo(dao = dao, api = api).resolve(null)

        assertTrue(dao.upsertCalls.isEmpty())
        assertEquals(0, api.callCount)
    }

    @Test
    fun resolve_usesFallbackDefaultsOnNetworkFailureWhenNoUser() = runTest {
        val dao = FakeDayAssignmentDao(initialCount = 0)
        val api = FakeAssignmentApi(shouldThrow = true)

        repo(dao = dao, api = api).resolve(null)

        assertEquals(7, dao.upsertCalls.size)
    }

    @Test
    fun resolve_gracefullySkipsNamesNotInLocalDbWhenNoUser() = runTest {
        val dao = FakeDayAssignmentDao(initialCount = 0)
        val figureDao = FakeFigureDaoForSeeding(listOf(augustine))
        val api = FakeAssignmentApi(
            defaults = listOf(
                AssignmentDefaultDto(0, "Augustine of Hippo"),
                AssignmentDefaultDto(1, "Unknown Figure"),
            )
        )

        repo(dao = dao, figureDao = figureDao, api = api).resolve(null)

        assertEquals(1, dao.upsertCalls.size)
        assertEquals(1L, dao.upsertCalls.first().figureId)
    }

    @Test
    fun resolve_isCaseInsensitiveForFigureNameLookupWhenNoUser() = runTest {
        val dao = FakeDayAssignmentDao(initialCount = 0)
        val figureDao = FakeFigureDaoForSeeding(listOf(augustine))
        val api = FakeAssignmentApi(defaults = listOf(AssignmentDefaultDto(0, "augustine of hippo")))

        repo(dao = dao, figureDao = figureDao, api = api).resolve(null)

        assertEquals(1, dao.upsertCalls.size)
        assertEquals(1L, dao.upsertCalls.first().figureId)
    }

    @Test
    fun resolve_fallbackDefaultsContains7Entries() = runTest {
        assertEquals(7, DayAssignmentRepositoryImpl.FALLBACK_DEFAULTS.size)
    }

    @Test
    fun resolveReporter_returnsDayAssignmentFigureId() = runTest {
        val dao = FakeDayAssignmentDao()
        dao.upsert(DayAssignmentEntity(dayOfWeek = 3, figureId = 42L))

        val result = repo(dao = dao).resolveReporter(epochDay = 20001L, dayOfWeek = 3)

        assertEquals(42L, result)
    }

    @Test
    fun resolveReporter_returnsNullWhenNoAssignmentExists() = runTest {
        val result = repo().resolveReporter(epochDay = 20002L, dayOfWeek = 5)

        assertEquals(null, result)
    }

    @Test
    fun resolveReporter_prefersLockedFigureOverCurrentAssignment() = runTest {
        val dao = FakeDayAssignmentDao()
        dao.upsert(DayAssignmentEntity(dayOfWeek = 3, figureId = 99L))
        val reflectionRepo = FakeDailyReflectionRepository(lockedFigureIdsByEpochDay = mapOf(20001L to 42L))

        val result = repo(dao = dao, dailyReflectionRepository = reflectionRepo)
            .resolveReporter(epochDay = 20001L, dayOfWeek = 3)

        assertEquals(42L, result)
    }

    @Test
    fun resolveReporter_fallsBackToAssignmentWhenDayNotLocked() = runTest {
        val dao = FakeDayAssignmentDao()
        dao.upsert(DayAssignmentEntity(dayOfWeek = 3, figureId = 99L))
        val reflectionRepo = FakeDailyReflectionRepository(lockedFigureIdsByEpochDay = mapOf(30000L to 42L))

        val result = repo(dao = dao, dailyReflectionRepository = reflectionRepo)
            .resolveReporter(epochDay = 20001L, dayOfWeek = 3)

        assertEquals(99L, result)
    }

    @Test
    fun assign_pushesImmediatelyAndMarksSynced() = runTest {
        val dao = FakeDayAssignmentDao()
        val remote = FakeDayAssignmentRemoteDataSource()

        repo(dao = dao, remote = remote).assign(dayOfWeek = 2, figureId = luther.id)

        assertEquals(1, remote.pushedRows.size)
        assertEquals(luther.serverId, remote.pushedRows.first().figureServerId)
        assertTrue(dao.getByDayOfWeek(2)!!.synced)
    }

    @Test
    fun assign_leavesRowUnsyncedWhenPushFails() = runTest {
        val dao = FakeDayAssignmentDao()
        val remote = FakeDayAssignmentRemoteDataSource(shouldThrowOnPush = true)

        repo(dao = dao, remote = remote).assign(dayOfWeek = 2, figureId = luther.id)

        assertTrue(dao.getRawByDayOfWeek(2)!!.synced.not())
    }

    @Test
    fun clear_pushesDeleteAndPurgesRowOnSuccess() = runTest {
        val dao = FakeDayAssignmentDao()
        dao.upsert(DayAssignmentEntity(dayOfWeek = 1, figureId = julian.id, synced = true))
        val remote = FakeDayAssignmentRemoteDataSource()

        repo(dao = dao, remote = remote).clear(1)

        assertEquals(listOf(1), remote.deletedDays)
        assertNull(dao.getRawByDayOfWeek(1))
    }

    @Test
    fun clear_keepsTombstoneWhenDeletePushFails() = runTest {
        val dao = FakeDayAssignmentDao()
        dao.upsert(DayAssignmentEntity(dayOfWeek = 1, figureId = julian.id, synced = true))
        val remote = FakeDayAssignmentRemoteDataSource(shouldThrowOnDelete = true)

        repo(dao = dao, remote = remote).clear(1)

        val raw = dao.getRawByDayOfWeek(1)
        assertTrue(raw!!.pendingDelete)
        assertTrue(raw.synced.not())
    }

    @Test
    fun resolve_doesNotClobberExistingRemoteScheduleWithDefaultsSeededWhileSignedOut() = runTest {
        // Regression test for a real-device bug found while validating MS-663 AC 3: on a
        // fresh install, authState genuinely passes through Unauthenticated before a real
        // sign-in completes (a device with an already-persisted session skips straight to
        // Authenticated, which is why this only reproduced on fresh installs). resolve(null)
        // fires first and seeds local fallback defaults; moments later resolve(userId) fires
        // for the real sign-in. Those seeded rows must never look like a pending edit that
        // pushPending() pushes up and overwrites the account's real existing schedule with,
        // before pullAndReconcile() ever gets to pull the real schedule down.
        val dao = FakeDayAssignmentDao(initialCount = 0)
        val figureDao = FakeFigureDaoForSeeding(allFigures)
        val api = FakeAssignmentApi(defaults = listOf(AssignmentDefaultDto(0, "Augustine of Hippo")))
        val remote = FakeDayAssignmentRemoteDataSource(
            initialRows = listOf(DayAssignmentRow(userId = USER_ID, dayOfWeek = 0, figureServerId = brother.serverId))
        )
        val repository = repo(dao = dao, figureDao = figureDao, api = api, remote = remote)

        repository.resolve(null) // briefly signed-out at cold start on a fresh install
        repository.resolve(USER_ID) // the real sign-in completes moments later

        assertEquals(brother.id, dao.getByDayOfWeek(0)?.figureId)
        assertTrue(remote.pushedRows.isEmpty())
    }

    @Test
    fun resolve_bootstrapsBrandNewUserFromDefaultsAndPushesThemUp() = runTest {
        val dao = FakeDayAssignmentDao(initialCount = 0)
        val remote = FakeDayAssignmentRemoteDataSource()
        val api = FakeAssignmentApi(
            defaults = listOf(AssignmentDefaultDto(0, "Augustine of Hippo"))
        )

        repo(dao = dao, api = api, remote = remote).resolve(USER_ID)

        assertEquals(1, remote.pushedRows.size)
        assertTrue(dao.getByDayOfWeek(0)!!.synced)
    }

    @Test
    fun resolve_pullsExistingScheduleInsteadOfDefaultsForSignedInUser() = runTest {
        val dao = FakeDayAssignmentDao(initialCount = 0)
        val remote = FakeDayAssignmentRemoteDataSource(
            initialRows = listOf(
                DayAssignmentRow(userId = USER_ID, dayOfWeek = 2, figureServerId = luther.serverId)
            )
        )
        val api = FakeAssignmentApi(defaults = listOf(AssignmentDefaultDto(0, "Augustine of Hippo")))

        repo(dao = dao, api = api, remote = remote).resolve(USER_ID)

        assertEquals(luther.id, dao.getByDayOfWeek(2)?.figureId)
        assertTrue(dao.getByDayOfWeek(2)!!.synced)
        assertNull(dao.getByDayOfWeek(0))
    }

    @Test
    fun resolve_doesNotClobberAnUnsyncedLocalEditForSignedInUser() = runTest {
        val dao = FakeDayAssignmentDao(initialCount = 0)
        dao.upsert(DayAssignmentEntity(dayOfWeek = 2, figureId = brother.id, synced = false))
        val remote = FakeDayAssignmentRemoteDataSource(
            initialRows = listOf(
                DayAssignmentRow(userId = USER_ID, dayOfWeek = 2, figureServerId = luther.serverId)
            ),
            shouldThrowOnPush = true,
        )

        repo(dao = dao, remote = remote).resolve(USER_ID)

        assertEquals(brother.id, dao.getByDayOfWeek(2)?.figureId)
    }

    @Test
    fun resolve_purgesLocalRowRemovedRemotelyFromAnotherDevice() = runTest {
        val dao = FakeDayAssignmentDao(initialCount = 0)
        dao.upsert(DayAssignmentEntity(dayOfWeek = 3, figureId = luther.id, synced = true))
        val remote = FakeDayAssignmentRemoteDataSource(
            initialRows = listOf(
                DayAssignmentRow(userId = USER_ID, dayOfWeek = 0, figureServerId = augustine.serverId)
            )
        )

        repo(dao = dao, remote = remote).resolve(USER_ID)

        assertNull(dao.getByDayOfWeek(3))
    }

    @Test
    fun resolve_wipesLocalDataWhenADifferentAccountSignsIn() = runTest {
        val dao = FakeDayAssignmentDao(initialCount = 0)
        dao.upsert(DayAssignmentEntity(dayOfWeek = 4, figureId = corrie.id, synced = true))
        val syncMetaDao = FakeSyncMetaDao(SyncMetaEntity(lastDayAssignmentSyncUserId = "previous-user"))
        val remote = FakeDayAssignmentRemoteDataSource()

        repo(dao = dao, remote = remote, syncMetaDao = syncMetaDao).resolve(USER_ID)

        assertNull(dao.getRawByDayOfWeek(4))
        assertEquals(USER_ID, syncMetaDao.get()?.lastDayAssignmentSyncUserId)
    }

    @Test
    fun resolve_isNoOpBeyondFlippingIsResolvedWhenRemoteDataSourceIsUnconfigured() = runTest {
        val dao = FakeDayAssignmentDao(initialCount = 3)

        repo(dao = dao, remote = null).resolve(USER_ID)

        assertTrue(dao.upsertCalls.isEmpty())
    }

    @Test
    fun resolve_pushesCurrentRoomStateNotAStaleSnapshotWhenAssignRacesPushPending() = runTest {
        // Regression test for a real-device bug found while validating MS-663: pushPending()
        // used to push the figureId captured in its getPendingSync() snapshot even if a
        // concurrent assign() had already updated Room to a different figure by the time the
        // loop reached that row — silently overwriting the server with stale data (with a
        // fresh updated_at) while Room, and every other device pulling from Room, already had
        // the correct newer value.
        lateinit var dao: FakeDayAssignmentDao
        dao = FakeDayAssignmentDao(
            onGetPendingSync = {
                // Simulates assign() landing on this row right after pushPending's snapshot is taken.
                dao.upsert(DayAssignmentEntity(dayOfWeek = 3, figureId = brother.id, synced = false))
            }
        )
        dao.upsert(DayAssignmentEntity(dayOfWeek = 3, figureId = augustine.id, synced = false))
        val remote = FakeDayAssignmentRemoteDataSource()

        repo(dao = dao, remote = remote).resolve(USER_ID)

        assertEquals(brother.serverId, remote.pushedRows.single { it.dayOfWeek == 3 }.figureServerId)
    }

    @Test
    fun isResolved_isFalseBeforeResolveIsCalled() = runTest {
        assertFalse(repo().isResolved.value)
    }

    @Test
    fun resolve_setsIsResolvedTrueAfterSeedingWithNoUser() = runTest {
        val repository = repo(dao = FakeDayAssignmentDao(initialCount = 0))

        repository.resolve(null)

        assertTrue(repository.isResolved.value)
    }

    @Test
    fun resolve_setsIsResolvedTrueAfterSyncingForSignedInUser() = runTest {
        val repository = repo()

        repository.resolve(USER_ID)

        assertTrue(repository.isResolved.value)
    }

    @Test
    fun resolve_setsIsResolvedTrueEvenWhenSyncThrows() = runTest {
        val remote = FakeDayAssignmentRemoteDataSource(shouldThrowOnFetch = true)
        val repository = repo(remote = remote)

        repository.resolve(USER_ID)

        assertTrue(repository.isResolved.value)
    }
}

private class FakeDailyReflectionRepository(
    private val lockedFigureIdsByEpochDay: Map<Long, Long> = emptyMap(),
) : DailyReflectionRepository {
    override suspend fun getOrFetch(
        figureId: Long,
        figureName: String,
        headlines: List<String>,
        tone: String,
        theme: String?,
    ): DailyReflection = throw UnsupportedOperationException()

    override fun observeByEpochDayRange(startEpochDay: Long, endEpochDay: Long): Flow<List<BriefingDay>> =
        MutableStateFlow(emptyList())

    override suspend fun getForDay(epochDay: Long, tone: String): DailyReflection? = null

    override suspend fun getEarliestBriefingEpochDay(): Long? = null

    override suspend fun getLockedFigureId(epochDay: Long): Long? = lockedFigureIdsByEpochDay[epochDay]
}

private class FakeDayAssignmentDao(
    initialCount: Int = 0,
    private val onGetPendingSync: suspend () -> Unit = {},
) : DayAssignmentDao {
    val upsertCalls = mutableListOf<DayAssignmentEntity>()
    private val store = (0 until initialCount)
        .associateWith { DayAssignmentEntity(dayOfWeek = it, figureId = 0L, synced = true) }
        .toMutableMap()

    override fun observeAll(): Flow<List<DayAssignmentEntity>> =
        flowOf(store.values.filterNot { it.pendingDelete })

    override suspend fun upsert(entity: DayAssignmentEntity) {
        upsertCalls.add(entity)
        store[entity.dayOfWeek] = entity
    }

    override suspend fun markPendingDelete(dayOfWeek: Int) {
        store[dayOfWeek]?.let { store[dayOfWeek] = it.copy(pendingDelete = true, synced = false) }
    }

    override suspend fun purge(dayOfWeek: Int) { store.remove(dayOfWeek) }

    override suspend fun clearAll() { store.clear() }

    override suspend fun countAll(): Int = store.values.count { !it.pendingDelete }

    override suspend fun getByDayOfWeek(dayOfWeek: Int): DayAssignmentEntity? =
        store[dayOfWeek]?.takeUnless { it.pendingDelete }

    override suspend fun getRawByDayOfWeek(dayOfWeek: Int): DayAssignmentEntity? = store[dayOfWeek]

    override suspend fun getPendingSync(): List<DayAssignmentEntity> {
        val snapshot = store.values.filterNot { it.synced }
        onGetPendingSync()
        return snapshot
    }

    override suspend fun markSynced(dayOfWeek: Int) {
        store[dayOfWeek]?.let { store[dayOfWeek] = it.copy(synced = true) }
    }
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

private class FakeDayAssignmentRemoteDataSource(
    initialRows: List<DayAssignmentRow> = emptyList(),
    private val shouldThrowOnPush: Boolean = false,
    private val shouldThrowOnDelete: Boolean = false,
    private val shouldThrowOnFetch: Boolean = false,
) : DayAssignmentRemoteDataSource {
    private val rows = initialRows.associateBy { it.dayOfWeek }.toMutableMap()
    val pushedRows = mutableListOf<DayAssignmentRow>()
    val deletedDays = mutableListOf<Int>()

    override suspend fun push(userId: String, dayOfWeek: Int, figureServerId: Long, lens: String?) {
        if (shouldThrowOnPush) throw RuntimeException("Push failed")
        val row = DayAssignmentRow(userId = userId, dayOfWeek = dayOfWeek, figureServerId = figureServerId, lens = lens)
        pushedRows.add(row)
        rows[dayOfWeek] = row
    }

    override suspend fun delete(userId: String, dayOfWeek: Int) {
        if (shouldThrowOnDelete) throw RuntimeException("Delete failed")
        deletedDays.add(dayOfWeek)
        rows.remove(dayOfWeek)
    }

    override suspend fun fetchAll(userId: String): List<DayAssignmentRow> {
        if (shouldThrowOnFetch) throw RuntimeException("Fetch failed")
        return rows.values.filter { it.userId == userId }
    }
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
