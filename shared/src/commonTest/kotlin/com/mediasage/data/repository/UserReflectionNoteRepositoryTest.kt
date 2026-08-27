package com.mediasage.data.repository

import com.mediasage.data.crypto.ReflectionNoteCipher
import com.mediasage.data.local.dao.UserReflectionNoteDao
import com.mediasage.data.local.entity.UserReflectionNoteEntity
import com.mediasage.domain.model.UserSession
import com.mediasage.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val USER_A = "user-a"
private const val USER_B = "user-b"

class UserReflectionNoteRepositoryTest {

    private fun repo(
        dao: FakeUserReflectionNoteDao = FakeUserReflectionNoteDao(),
        cipher: ReflectionNoteCipher = FakeReflectionNoteCipher(),
        remote: FakeUserReflectionNoteRemoteDataSource? = FakeUserReflectionNoteRemoteDataSource(),
        authRepository: FakeAuthRepositoryForNoteSync = FakeAuthRepositoryForNoteSync(USER_A),
    ) = UserReflectionNoteRepositoryImpl(dao, cipher, remote, authRepository)

    @Test
    fun `saveNote stores ciphertext not plaintext`() = runTest {
        val dao = FakeUserReflectionNoteDao()
        val repository = repo(dao = dao)

        repository.saveNote("2026-08-27_encouraged_news", "Only I should be able to read this.")

        val stored = dao.get(USER_A, "2026-08-27_encouraged_news")
        assertEquals("ROT13:Bayl V fubhyq or noyr gb ernq guvf.", stored?.noteText)
    }

    @Test
    fun `getNote decrypts the stored ciphertext back to the original text`() = runTest {
        val repository = repo()
        repository.saveNote("2026-08-27_encouraged_news", "Round trip me.")

        val note = repository.getNote("2026-08-27_encouraged_news")

        assertEquals("Round trip me.", note)
    }

    @Test
    fun `getNote returns null when no note exists`() = runTest {
        assertNull(repo().getNote("missing"))
    }

    @Test
    fun `a note saved under one account is never visible to a different account on the same device`() = runTest {
        val dao = FakeUserReflectionNoteDao()
        repo(dao = dao, authRepository = FakeAuthRepositoryForNoteSync(USER_A))
            .saveNote("2026-08-27_morning_NEWS", "Account A's private note.")

        val noteForOtherAccount = repo(dao = dao, authRepository = FakeAuthRepositoryForNoteSync(USER_B))
            .getNote("2026-08-27_morning_NEWS")

        assertNull(noteForOtherAccount)
    }

    @Test
    fun `saveNote pushes the note immediately and marks it synced`() = runTest {
        val dao = FakeUserReflectionNoteDao()
        val remote = FakeUserReflectionNoteRemoteDataSource()

        repo(dao = dao, remote = remote).saveNote("2026-08-27_morning_NEWS", "Push me.")

        assertEquals(1, remote.pushedRows.size)
        assertTrue(dao.get(USER_A, "2026-08-27_morning_NEWS")!!.synced)
    }

    @Test
    fun `saveNote leaves the note unsynced when the push fails`() = runTest {
        val dao = FakeUserReflectionNoteDao()
        val remote = FakeUserReflectionNoteRemoteDataSource(shouldThrowOnPush = true)

        repo(dao = dao, remote = remote).saveNote("2026-08-27_morning_NEWS", "Push me.")

        assertTrue(!dao.get(USER_A, "2026-08-27_morning_NEWS")!!.synced)
    }

    @Test
    fun `resolve does nothing when signed out`() = runTest {
        val dao = FakeUserReflectionNoteDao()
        val remote = FakeUserReflectionNoteRemoteDataSource()

        repo(dao = dao, remote = remote).resolve(null)

        assertTrue(dao.upsertCalls.isEmpty())
        assertTrue(remote.pushedRows.isEmpty())
    }

    @Test
    fun `resolve pushes a pending local note for the signed-in user`() = runTest {
        val dao = FakeUserReflectionNoteDao()
        dao.upsert(note(userId = USER_A, id = "1_morning_NEWS", synced = false))
        val remote = FakeUserReflectionNoteRemoteDataSource()

        repo(dao = dao, remote = remote).resolve(USER_A)

        assertEquals(1, remote.pushedRows.size)
        assertTrue(dao.get(USER_A, "1_morning_NEWS")!!.synced)
    }

    @Test
    fun `resolve adopts a note saved from another device`() = runTest {
        val dao = FakeUserReflectionNoteDao()
        val remote = FakeUserReflectionNoteRemoteDataSource(
            initialRows = listOf(
                UserReflectionNoteRow(userId = USER_A, id = "2_morning_NEWS", noteText = "ROT13:sebz nabgure qrivpr", updatedAtMillis = 100L)
            )
        )

        repo(dao = dao, remote = remote).resolve(USER_A)

        val pulled = dao.get(USER_A, "2_morning_NEWS")
        assertEquals("ROT13:sebz nabgure qrivpr", pulled?.noteText)
        assertTrue(pulled!!.synced)
    }

    @Test
    fun `resolve keeps the newer local edit instead of an older remote row`() = runTest {
        val dao = FakeUserReflectionNoteDao()
        dao.upsert(note(userId = USER_A, id = "3_morning_NEWS", text = "Newer local edit", updatedAtMillis = 200L, synced = true))
        val remote = FakeUserReflectionNoteRemoteDataSource(
            initialRows = listOf(
                UserReflectionNoteRow(userId = USER_A, id = "3_morning_NEWS", noteText = "Older remote edit", updatedAtMillis = 100L)
            )
        )

        repo(dao = dao, remote = remote).resolve(USER_A)

        assertEquals("Newer local edit", dao.get(USER_A, "3_morning_NEWS")?.noteText)
    }

    @Test
    fun `resolve adopts a newer remote edit over an older local copy`() = runTest {
        val dao = FakeUserReflectionNoteDao()
        dao.upsert(note(userId = USER_A, id = "4_morning_NEWS", text = "Older local edit", updatedAtMillis = 100L, synced = true))
        val remote = FakeUserReflectionNoteRemoteDataSource(
            initialRows = listOf(
                UserReflectionNoteRow(userId = USER_A, id = "4_morning_NEWS", noteText = "Newer remote edit", updatedAtMillis = 200L)
            )
        )

        repo(dao = dao, remote = remote).resolve(USER_A)

        assertEquals("Newer remote edit", dao.get(USER_A, "4_morning_NEWS")?.noteText)
    }

    private fun note(
        userId: String,
        id: String,
        text: String = "note",
        updatedAtMillis: Long = 0L,
        synced: Boolean = false,
    ) = UserReflectionNoteEntity(userId = userId, id = id, noteText = text, updatedAtMillis = updatedAtMillis, synced = synced)
}

private class FakeUserReflectionNoteDao : UserReflectionNoteDao {
    val upsertCalls = mutableListOf<UserReflectionNoteEntity>()
    private val notesByKey = mutableMapOf<Pair<String, String>, UserReflectionNoteEntity>()

    override suspend fun get(userId: String, id: String): UserReflectionNoteEntity? = notesByKey[userId to id]

    override suspend fun upsert(entity: UserReflectionNoteEntity) {
        upsertCalls.add(entity)
        notesByKey[entity.userId to entity.id] = entity
    }

    override suspend fun getPendingSync(userId: String): List<UserReflectionNoteEntity> =
        notesByKey.values.filter { it.userId == userId && !it.synced }

    override suspend fun markSynced(userId: String, id: String) {
        notesByKey[userId to id]?.let { notesByKey[userId to id] = it.copy(synced = true) }
    }
}

private class FakeReflectionNoteCipher : ReflectionNoteCipher {
    override fun encrypt(plaintext: String): String = "ROT13:${plaintext.rot13()}"

    override fun decrypt(ciphertext: String): String = ciphertext.removePrefix("ROT13:").rot13()

    private fun String.rot13(): String = map { char ->
        when (char) {
            in 'a'..'z' -> 'a' + (char - 'a' + 13) % 26
            in 'A'..'Z' -> 'A' + (char - 'A' + 13) % 26
            else -> char
        }
    }.joinToString("")
}

private class FakeAuthRepositoryForNoteSync(private val userId: String?) : AuthRepository {
    override fun observeAuthState(): Flow<UserSession?> =
        MutableStateFlow(userId?.let { UserSession(it, null) })

    override fun currentSession(): UserSession? = userId?.let { UserSession(it, null) }
    override suspend fun signInWithEmail(email: String, password: String) = Unit
    override suspend fun signUp(email: String, password: String, displayName: String) = Unit
    override suspend fun verifySignUpOtp(email: String, token: String) = Unit
    override suspend fun signOut() = Unit
}

private class FakeUserReflectionNoteRemoteDataSource(
    initialRows: List<UserReflectionNoteRow> = emptyList(),
    private val shouldThrowOnPush: Boolean = false,
) : UserReflectionNoteRemoteDataSource {
    private val rows = initialRows.associateBy { it.userId to it.id }.toMutableMap()
    val pushedRows = mutableListOf<UserReflectionNoteRow>()

    override suspend fun push(row: UserReflectionNoteRow) {
        if (shouldThrowOnPush) throw RuntimeException("Push failed")
        pushedRows.add(row)
        rows[row.userId to row.id] = row
    }

    override suspend fun fetchAll(userId: String): List<UserReflectionNoteRow> =
        rows.values.filter { it.userId == userId }
}
