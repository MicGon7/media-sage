package com.mediasage.data.repository

import com.mediasage.data.local.dao.FigureDao
import com.mediasage.data.local.dao.QuoteDao
import com.mediasage.data.local.dao.SyncMetaDao
import com.mediasage.data.local.entity.FigureEntity
import com.mediasage.data.local.entity.QuoteEntity
import com.mediasage.data.local.entity.SyncMetaEntity
import com.mediasage.domain.model.UserSession
import com.mediasage.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val USER_ID = "user-1"

class QuoteRepositoryTest {

    private val augustine = FigureEntity(id = 1, name = "Augustine of Hippo", category = "church_father", century = "4th", serverId = 101)
    private val lewis = FigureEntity(id = 2, name = "C.S. Lewis", category = "theologian", century = "20th", serverId = 102)

    private fun repo(
        dao: FakeQuoteDao = FakeQuoteDao(),
        figureDao: FakeFigureDaoForMemorizedQuoteSync = FakeFigureDaoForMemorizedQuoteSync(listOf(augustine, lewis)),
        remote: FakeMemorizedQuoteRemoteDataSource? = FakeMemorizedQuoteRemoteDataSource(),
        syncMetaDao: FakeSyncMetaDaoForMemorizedQuoteSync = FakeSyncMetaDaoForMemorizedQuoteSync(),
        authRepository: FakeAuthRepositoryForMemorizedQuoteSync = FakeAuthRepositoryForMemorizedQuoteSync(USER_ID),
    ) = QuoteRepositoryImpl(dao, figureDao, remote, syncMetaDao, authRepository)

    @Test
    fun memorizeQuote_replacesAnyPreviouslyMemorizedQuote() = runTest {
        val dao = FakeQuoteDao(
            listOf(
                QuoteEntity(
                    id = 1, figureId = augustine.id, text = "Our heart is restless.",
                    source = "", themes = "", memorized = true, synced = true,
                ),
                QuoteEntity(id = 2, figureId = lewis.id, text = "You are never too old to dream.", source = "", themes = ""),
            )
        )

        repo(dao = dao, remote = null).memorizeQuote(lewis.id, "You are never too old to dream.")

        val memorized = dao.store.values.filter { it.memorized }
        assertEquals(1, memorized.size)
        assertEquals(lewis.id, memorized.first().figureId)
    }

    @Test
    fun memorizeQuote_stillMemorizesWhenNoCatalogRowExistsYetForThatQuote() = runTest {
        // Regression test: the Figure Detail quote sheet lists quotes from EncouragementRepository,
        // not this DAO's catalog, so a quote can be tapped there with no matching QuoteEntity row
        // (e.g. the best-effort saveQuote() at match time silently no-opped on a name-lookup miss).
        // memorize() alone only UPDATEs an existing row — without self-healing this, the previous
        // pin gets cleared and the new one silently fails to take, leaving nothing memorized.
        val dao = FakeQuoteDao()

        repo(dao = dao, remote = null).memorizeQuote(lewis.id, "You are never too old to dream.")

        val memorized = dao.store.values.single { it.memorized }
        assertEquals(lewis.id, memorized.figureId)
        assertEquals("You are never too old to dream.", memorized.text)
    }

    @Test
    fun memorizeQuote_replacesAPinFromADifferentFigureWithNoPriorCatalogRowForEither() = runTest {
        val dao = FakeQuoteDao()
        val repository = repo(dao = dao, remote = null)
        repository.memorizeQuote(augustine.id, "Our heart is restless.")

        repository.memorizeQuote(lewis.id, "You are never too old to dream.")

        val memorized = dao.store.values.single { it.memorized }
        assertEquals(lewis.id, memorized.figureId)
        assertEquals("You are never too old to dream.", memorized.text)
    }

    @Test
    fun memorizeQuote_pushesImmediatelyAndMarksSynced() = runTest {
        val dao = FakeQuoteDao(listOf(QuoteEntity(id = 1, figureId = lewis.id, text = "Quote", source = "", themes = "faith,hope")))
        val remote = FakeMemorizedQuoteRemoteDataSource()

        repo(dao = dao, remote = remote).memorizeQuote(lewis.id, "Quote")

        assertEquals(1, remote.pushedRows.size)
        assertEquals(lewis.serverId, remote.pushedRows.first().figureServerId)
        assertEquals(listOf("faith", "hope"), remote.pushedRows.first().themes)
        assertTrue(dao.getByFigureAndText(lewis.id, "Quote")!!.synced)
    }

    @Test
    fun memorizeQuote_leavesRowUnsyncedWhenPushFails() = runTest {
        val dao = FakeQuoteDao(listOf(QuoteEntity(id = 1, figureId = lewis.id, text = "Quote", source = "", themes = "")))
        val remote = FakeMemorizedQuoteRemoteDataSource(shouldThrowOnPush = true)

        repo(dao = dao, remote = remote).memorizeQuote(lewis.id, "Quote")

        assertFalse(dao.getByFigureAndText(lewis.id, "Quote")!!.synced)
    }

    @Test
    fun resolve_pullsExistingMemorizedQuoteForSignedInUser() = runTest {
        val dao = FakeQuoteDao()
        val remote = FakeMemorizedQuoteRemoteDataSource(
            initialRow = MemorizedQuoteRow(userId = USER_ID, figureServerId = lewis.serverId, quoteText = "Quote", themes = listOf("hope"))
        )

        repo(dao = dao, remote = remote).resolve(USER_ID)

        val entity = dao.store.values.first { it.memorized }
        assertEquals(lewis.id, entity.figureId)
        assertEquals("Quote", entity.text)
        assertTrue(entity.synced)
    }

    @Test
    fun resolve_doesNotClobberAnUnsyncedLocalMemorizeForSignedInUser() = runTest {
        val dao = FakeQuoteDao(
            listOf(
                QuoteEntity(id = 1, figureId = augustine.id, text = "Local pin", source = "", themes = "", memorized = true, synced = false)
            )
        )
        val remote = FakeMemorizedQuoteRemoteDataSource(
            initialRow = MemorizedQuoteRow(userId = USER_ID, figureServerId = lewis.serverId, quoteText = "Remote pin"),
            shouldThrowOnPush = true,
        )

        repo(dao = dao, remote = remote).resolve(USER_ID)

        val memorized = dao.store.values.single { it.memorized }
        assertEquals(augustine.id, memorized.figureId)
        assertEquals("Local pin", memorized.text)
    }

    @Test
    fun resolve_wipesLocalMemorizedQuoteWhenADifferentAccountSignsIn() = runTest {
        val dao = FakeQuoteDao(
            listOf(
                QuoteEntity(id = 1, figureId = augustine.id, text = "Old pin", source = "", themes = "", memorized = true, synced = true)
            )
        )
        val syncMetaDao = FakeSyncMetaDaoForMemorizedQuoteSync(SyncMetaEntity(lastMemorizedQuoteSyncUserId = "previous-user"))
        val remote = FakeMemorizedQuoteRemoteDataSource()

        repo(dao = dao, remote = remote, syncMetaDao = syncMetaDao).resolve(USER_ID)

        assertFalse(dao.store.values.any { it.memorized })
        assertEquals(USER_ID, syncMetaDao.get()?.lastMemorizedQuoteSyncUserId)
    }

    @Test
    fun resolve_isNoOpBeyondFlippingIsResolvedWhenRemoteDataSourceIsUnconfigured() = runTest {
        val dao = FakeQuoteDao()

        repo(dao = dao, remote = null).resolve(USER_ID)

        assertTrue(dao.store.isEmpty())
    }

    @Test
    fun isResolved_isFalseBeforeResolveIsCalled() = runTest {
        assertFalse(repo().isResolved.value)
    }

    @Test
    fun resolve_setsIsResolvedTrueEvenWhenNoRemoteRowExists() = runTest {
        val repository = repo(remote = FakeMemorizedQuoteRemoteDataSource())

        repository.resolve(USER_ID)

        assertTrue(repository.isResolved.value)
    }

    @Test
    fun observeMemorizedQuote_returnsNullWhenNothingMemorized() = runTest {
        val dao = FakeQuoteDao(listOf(QuoteEntity(id = 1, figureId = augustine.id, text = "Not memorized", source = "", themes = "")))

        val result = repo(dao = dao).observeMemorizedQuote().first()

        assertNull(result)
    }
}

private class FakeQuoteDao(initial: List<QuoteEntity> = emptyList()) : QuoteDao {
    val store = initial.associateBy { it.id }.toMutableMap()
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1

    override suspend fun insert(quote: QuoteEntity): Long {
        val id = if (quote.id != 0L) quote.id else nextId++
        store[id] = quote.copy(id = id)
        return id
    }

    override suspend fun insertAll(quotes: List<QuoteEntity>) {
        quotes.forEach { insert(it) }
    }

    override suspend fun insertIgnore(quote: QuoteEntity): Long {
        val existing = store.values.find { it.figureId == quote.figureId && it.text == quote.text }
        if (existing != null) return -1
        return insert(quote)
    }

    override fun observeByFigure(figureId: Long): Flow<List<QuoteEntity>> =
        flowOf(store.values.filter { it.figureId == figureId })

    override suspend fun getLatestByFigure(figureId: Long): QuoteEntity? =
        store.values.filter { it.figureId == figureId }.maxByOrNull { it.id }

    override suspend fun getById(id: Long): QuoteEntity? = store[id]

    override suspend fun getByFigureAndText(figureId: Long, text: String): QuoteEntity? =
        store.values.find { it.figureId == figureId && it.text == text }

    override fun observeAll(): Flow<List<QuoteEntity>> = flowOf(store.values.toList())

    override fun observeMemorizedQuote(): Flow<QuoteEntity?> = flowOf(store.values.find { it.memorized })

    override suspend fun clearMemorized() {
        store.keys.toList().forEach { key ->
            store[key]?.let { if (it.memorized) store[key] = it.copy(memorized = false) }
        }
    }

    override suspend fun setMemorized(figureId: Long, text: String, synced: Boolean) {
        val entity = store.values.find { it.figureId == figureId && it.text == text } ?: return
        store[entity.id] = entity.copy(memorized = true, synced = synced)
    }

    override suspend fun memorize(figureId: Long, text: String) {
        clearMemorized()
        setMemorized(figureId, text, synced = false)
    }

    override suspend fun getPendingSync(): List<QuoteEntity> =
        store.values.filter { it.memorized && !it.synced }

    override suspend fun markSynced(figureId: Long, text: String) {
        val entity = store.values.find { it.figureId == figureId && it.text == text } ?: return
        store[entity.id] = entity.copy(synced = true)
    }

    override suspend fun deleteById(id: Long) { store.remove(id) }
}

private class FakeFigureDaoForMemorizedQuoteSync(figures: List<FigureEntity> = emptyList()) : FigureDao {
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

private class FakeSyncMetaDaoForMemorizedQuoteSync(private var meta: SyncMetaEntity? = null) : SyncMetaDao {
    override suspend fun get(): SyncMetaEntity? = meta
    override suspend fun upsert(meta: SyncMetaEntity) { this.meta = meta }
}

private class FakeAuthRepositoryForMemorizedQuoteSync(private val userId: String?) : AuthRepository {
    override fun observeAuthState(): Flow<UserSession?> =
        MutableStateFlow(userId?.let { UserSession(it, null) })

    override fun currentSession(): UserSession? = userId?.let { UserSession(it, null) }
    override suspend fun signInWithEmail(email: String, password: String) = Unit
    override suspend fun signUp(email: String, password: String, displayName: String) = Unit
    override suspend fun verifySignUpOtp(email: String, token: String) = Unit
    override suspend fun signOut() = Unit
}

private class FakeMemorizedQuoteRemoteDataSource(
    initialRow: MemorizedQuoteRow? = null,
    private val shouldThrowOnPush: Boolean = false,
) : MemorizedQuoteRemoteDataSource {
    private var row: MemorizedQuoteRow? = initialRow
    val pushedRows = mutableListOf<MemorizedQuoteRow>()

    override suspend fun push(row: MemorizedQuoteRow) {
        if (shouldThrowOnPush) throw RuntimeException("Push failed")
        pushedRows.add(row)
        this.row = row
    }

    override suspend fun fetch(userId: String): MemorizedQuoteRow? = row?.takeIf { it.userId == userId }
}
