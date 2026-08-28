package com.mediasage.data.repository

import com.mediasage.data.crypto.ReflectionNoteCipher
import com.mediasage.data.local.dao.LocalAccountKeyDao
import com.mediasage.data.local.dao.UserReflectionNoteDao
import com.mediasage.data.local.entity.LocalAccountKeyEntity
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
        localAccountKeyDao: FakeLocalAccountKeyDao = FakeLocalAccountKeyDao(),
        keyRemote: FakeReflectionNoteKeyRemoteDataSource? = FakeReflectionNoteKeyRemoteDataSource(),
    ) = UserReflectionNoteRepositoryImpl(dao, cipher, remote, authRepository, localAccountKeyDao, keyRemote)

    @Test
    fun `saveNote stores ciphertext not plaintext`() = runTest {
        val dao = FakeUserReflectionNoteDao()
        val repository = repo(dao = dao)

        repository.saveNote("2026-08-27_encouraged_news", "Only I should be able to read this.")

        val stored = dao.get(USER_A, "2026-08-27_encouraged_news")
        assertTrue(stored != null && stored.noteText != "Only I should be able to read this.")
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
    fun `getNote returns null instead of crashing when the local cipher throws wrapping the account key`() = runTest {
        // Regression test (MS-739): Keychain/Keystore failure must never crash the app — still
        // true now that the local cipher's job is wrapping the cached shared key, not the note.
        val dao = FakeUserReflectionNoteDao()
        dao.upsert(note(userId = USER_A, id = "5_morning_NEWS", text = "undecryptable"))

        val note = repo(dao = dao, cipher = ThrowingCipher(), keyRemote = null).getNote("5_morning_NEWS")

        assertNull(note)
    }

    @Test
    fun `saveNote does not crash when the local cipher throws`() = runTest {
        repo(cipher = ThrowingCipher(), keyRemote = null).saveNote("6_morning_NEWS", "won't be saved")
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

    @Test
    fun `a note saved on one device is readable on a second device signed into the same account`() = runTest {
        // The actual MS-740 regression test: two independent local caches (separate note DAO and
        // separate local-account-key DAO — standing in for two physical devices) sharing only the
        // remote note table and the remote key table, exercising the real AES-GCM SharedNoteCipher.
        val sharedNotesRemote = FakeUserReflectionNoteRemoteDataSource()
        val sharedKeyRemote = FakeReflectionNoteKeyRemoteDataSource()
        val deviceA = repo(remote = sharedNotesRemote, keyRemote = sharedKeyRemote)
        val deviceB = repo(remote = sharedNotesRemote, keyRemote = sharedKeyRemote)

        deviceA.saveNote("7_morning_NEWS", "Written on device A")
        deviceB.resolve(USER_A)

        assertEquals("Written on device A", deviceB.getNote("7_morning_NEWS"))
    }

    @Test
    fun `two devices racing to provision the account key converge on one winner and can read each other's notes`() = runTest {
        val sharedNotesRemote = FakeUserReflectionNoteRemoteDataSource()
        // Both devices' first key fetch races ahead of either device's write and sees nothing —
        // a real unique-constraint conflict then decides exactly one winner.
        val sharedKeyRemote = FakeReflectionNoteKeyRemoteDataSource(forceFetchNullForFirstNCalls = 2)
        val deviceA = repo(remote = sharedNotesRemote, keyRemote = sharedKeyRemote)
        val deviceB = repo(remote = sharedNotesRemote, keyRemote = sharedKeyRemote)

        deviceA.saveNote("8_morning_NEWS", "From device A")
        deviceB.saveNote("9_morning_NEWS", "From device B")

        assertEquals(1, sharedKeyRemote.pushedKeys.size)

        deviceB.resolve(USER_A)
        assertEquals("From device A", deviceB.getNote("8_morning_NEWS"))
        deviceA.resolve(USER_A)
        assertEquals("From device B", deviceA.getNote("9_morning_NEWS"))
    }

    @Test
    fun `a note encrypted under the old per-device cipher is still readable and migrates onto the shared key on next save`() = runTest {
        val dao = FakeUserReflectionNoteDao()
        val legacyCipher = FakeReflectionNoteCipher()
        dao.upsert(note(userId = USER_A, id = "legacy_note", text = legacyCipher.encrypt("Before MS-740")))
        val sharedKeyRemote = FakeReflectionNoteKeyRemoteDataSource()
        val repository = repo(dao = dao, cipher = legacyCipher, keyRemote = sharedKeyRemote)

        assertEquals("Before MS-740", repository.getNote("legacy_note"))

        repository.saveNote("legacy_note", "After MS-740")

        // A second device with its own local cipher (never having seen the legacy ciphertext at
        // all) must still read the re-saved note purely via the shared account key.
        val deviceB = repo(
            dao = dao,
            cipher = FakeReflectionNoteCipher(),
            keyRemote = sharedKeyRemote,
            authRepository = FakeAuthRepositoryForNoteSync(USER_A),
        )
        assertEquals("After MS-740", deviceB.getNote("legacy_note"))
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

private class FakeLocalAccountKeyDao : LocalAccountKeyDao {
    private val entries = mutableMapOf<String, LocalAccountKeyEntity>()

    override suspend fun get(userId: String): LocalAccountKeyEntity? = entries[userId]

    override suspend fun upsert(entity: LocalAccountKeyEntity) {
        entries[entity.userId] = entity
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

private class ThrowingCipher : ReflectionNoteCipher {
    override fun encrypt(plaintext: String): String = throw IllegalStateException("Keychain/Keystore failure")
    override fun decrypt(ciphertext: String): String = throw IllegalStateException("Keychain/Keystore failure")
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

private class FakeReflectionNoteKeyRemoteDataSource(
    private val forceFetchNullForFirstNCalls: Int = 0,
) : ReflectionNoteKeyRemoteDataSource {
    private val keysByUser = mutableMapOf<String, String>()
    private var fetchCallCount = 0
    val pushedKeys = mutableListOf<Pair<String, String>>()

    override suspend fun push(userId: String, keyMaterialBase64: String) {
        // A plain insert with userId as the primary key — a second push for the same user is a
        // real unique-constraint violation, exactly like the Supabase table this fakes.
        if (keysByUser.containsKey(userId)) throw RuntimeException("duplicate key value violates unique constraint")
        keysByUser[userId] = keyMaterialBase64
        pushedKeys.add(userId to keyMaterialBase64)
    }

    override suspend fun fetch(userId: String): String? {
        fetchCallCount++
        if (fetchCallCount <= forceFetchNullForFirstNCalls) return null
        return keysByUser[userId]
    }
}
