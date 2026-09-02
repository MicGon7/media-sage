package com.mediasage.data.repository

import com.mediasage.data.crypto.ReflectionNoteCipher
import com.mediasage.data.crypto.SharedNoteCipher
import com.mediasage.data.local.dao.LocalAccountKeyDao
import com.mediasage.data.local.dao.UserReflectionNoteDao
import com.mediasage.data.local.entity.LocalAccountKeyEntity
import com.mediasage.data.local.entity.UserReflectionNoteEntity
import com.mediasage.domain.repository.AuthRepository
import com.mediasage.domain.repository.UserReflectionNoteRepository
import kotlin.io.encoding.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Marks ciphertext written under the shared account key (MS-740) — see [decryptStored]. */
private const val SHARED_KEY_PREFIX = "v2:"

/**
 * Runs [block], returning `null` on any non-cancellation failure — the "Keystore/Keychain or
 * key-provisioning failure, never crash, but don't guess a wrong result either" contract this
 * repository applies at every read/decrypt boundary. Cancellation always propagates: swallowing
 * it would leave a coroutine that thinks it finished normally after being cancelled.
 */
private suspend inline fun <T> nullOnFailure(block: suspend () -> T): T? =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

/** As [nullOnFailure], for call sites with nothing meaningful to return on failure. */
private suspend inline fun ignoreFailure(block: suspend () -> Unit) {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Non-fatal — the caller's own doc comment explains why for that specific operation.
    }
}

class UserReflectionNoteRepositoryImpl(
    private val dao: UserReflectionNoteDao,
    private val cipher: ReflectionNoteCipher,
    private val remote: UserReflectionNoteRemoteDataSource?,
    private val authRepository: AuthRepository,
    private val localAccountKeyDao: LocalAccountKeyDao,
    private val keyRemote: ReflectionNoteKeyRemoteDataSource?,
) : UserReflectionNoteRepository {

    override suspend fun getNote(reflectionId: String): String? = withContext(Dispatchers.Default) {
        // Corrupted ciphertext, or the shared account key genuinely unreachable right now — treat
        // as "no note" rather than crashing. Never masks a format-detection guess: which cipher
        // applies is read off SHARED_KEY_PREFIX, not inferred from a caught exception.
        nullOnFailure {
            val userId = currentUserId()
            val stored = dao.get(userId, reflectionId)?.noteText ?: return@nullOnFailure null
            decryptStored(userId, stored)
        }
    }

    override suspend fun saveNote(reflectionId: String, noteText: String) = withContext(Dispatchers.Default) {
        // Keystore/Keychain or key-provisioning failure — the note isn't saved, but must never crash.
        ignoreFailure {
            val userId = currentUserId()
            val key = getOrProvisionAccountKey(userId)
            val encrypted = if (key != null) {
                SHARED_KEY_PREFIX + SharedNoteCipher.encrypt(noteText, key)
            } else {
                cipher.encrypt(noteText)
            }
            val entity = UserReflectionNoteEntity(
                userId = userId,
                id = reflectionId,
                noteText = encrypted,
                updatedAtMillis = epochMillis(),
                synced = false,
            )
            dao.upsert(entity)
            pushNote(entity)
        }
    }

    override suspend fun resolve(userId: String?) {
        if (userId == null) return
        // Failure is non-fatal — retried on next resolve pass.
        ignoreFailure {
            pushPending(userId)
            pullAndReconcile(userId)
        }
    }

    /**
     * [SHARED_KEY_PREFIX] on [stored] is written by [saveNote] itself from MS-741 onward, so which
     * cipher to use is read off the ciphertext for any note saved from now on — never guessed by
     * trying one and catching a failure.
     *
     * Untagged ciphertext is genuinely ambiguous and still requires one such guess: the shared
     * account key (MS-740) shipped before this tag existed, so a note saved in that window is
     * untagged but *is* [SharedNoteCipher] ciphertext, indistinguishable by format alone from a
     * genuinely pre-MS-740 note under the old per-device [cipher]. This is a one-time back-compat
     * cost for data that already exists, not a design choice repeated going forward — every note
     * saved by this version tags deterministically, and each untagged note migrates onto the tag
     * (and out of this ambiguous bucket for good) the next time it's saved.
     */
    private suspend fun decryptStored(userId: String, stored: String): String {
        if (stored.startsWith(SHARED_KEY_PREFIX)) {
            val key = getOrProvisionAccountKey(userId)
                ?: error("Reflection note account key unavailable for user $userId")
            return SharedNoteCipher.decrypt(stored.removePrefix(SHARED_KEY_PREFIX), key)
        }
        val key = getOrProvisionAccountKey(userId)
        if (key != null) {
            // A failure here means this is genuinely pre-MS-740 per-device ciphertext, not an
            // untagged shared-key note — fall through to the legacy cipher below.
            nullOnFailure { SharedNoteCipher.decrypt(stored, key) }?.let { return it }
        }
        return cipher.decrypt(stored)
    }

    /**
     * Returns `null` when [userId] is blank, no [keyRemote] is configured at all, or reaching the
     * shared key fails just for this attempt (e.g. a transient Supabase network error) — in every
     * `null` case the caller falls back to the legacy per-device [cipher] for *this* save/read.
     * That fallback is never a guess: [saveNote] tags what it writes deterministically (no
     * [SHARED_KEY_PREFIX] means "couldn't reach the shared key when this was written"), and it
     * self-heals — the next save that does reach the key migrates the note forward, per
     * `shared/CLAUDE.md`'s documented back-compat story.
     *
     * A cached wrap that fails to unwrap (e.g. the device's local wrapping key was replaced,
     * which is possible even now that MS-741 fixed the Keychain persistence bug that made it
     * happen every launch) is treated the same as a cache miss — re-provisioned from [keyRemote]
     * — rather than a permanent failure every read/write hits from then on.
     */
    private suspend fun getOrProvisionAccountKey(userId: String): ByteArray? {
        if (userId.isBlank()) return null
        val cached = localAccountKeyDao.get(userId)
        if (cached != null) {
            // A failed unwrap here falls through and re-provisions below, instead of failing
            // every read/write from now on.
            nullOnFailure { unwrapLocalKey(cached.wrappedKeyBase64) }?.let { return it }
        }
        return nullOnFailure { fetchOrClaimAccountKey(userId) }
    }

    private suspend fun fetchOrClaimAccountKey(userId: String): ByteArray {
        val keyRemote = keyRemote
            // No Supabase configured — a device-only key still lets the feature work single-device.
            ?: return SharedNoteCipher.generateKey().also { cacheLocalKey(userId, it) }
        keyRemote.fetch(userId)?.let { existing ->
            val bytes = Base64.decode(existing)
            cacheLocalKey(userId, bytes)
            return bytes
        }
        return claimNewAccountKey(userId, keyRemote)
    }

    private suspend fun claimNewAccountKey(userId: String, keyRemote: ReflectionNoteKeyRemoteDataSource): ByteArray {
        val generated = SharedNoteCipher.generateKey()
        try {
            keyRemote.push(userId, Base64.encode(generated))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Lost the provisioning race — another device's insert won; adopt theirs instead.
            val winner = keyRemote.fetch(userId)?.let { Base64.decode(it) } ?: generated
            cacheLocalKey(userId, winner)
            return winner
        }
        cacheLocalKey(userId, generated)
        return generated
    }

    private suspend fun cacheLocalKey(userId: String, rawKey: ByteArray) {
        val wrapped = cipher.encrypt(Base64.encode(rawKey))
        localAccountKeyDao.upsert(LocalAccountKeyEntity(userId, wrapped))
    }

    private suspend fun unwrapLocalKey(wrappedBase64: String): ByteArray =
        Base64.decode(cipher.decrypt(wrappedBase64))

    private suspend fun pushNote(entity: UserReflectionNoteEntity) {
        val remote = remote ?: return
        if (entity.userId.isBlank()) return
        try {
            remote.push(entity.toRow())
            dao.markSynced(entity.userId, entity.id)
        } catch (e: Exception) {
            // stays unsynced — retried by the next resolve pass
        }
    }

    private suspend fun pushPending(userId: String) {
        val remote = remote ?: return
        for (entity in dao.getPendingSync(userId)) pushNote(entity)
    }

    private suspend fun pullAndReconcile(userId: String) {
        val remote = remote ?: return
        remote.fetchAll(userId).forEach { row ->
            val local = dao.get(userId, row.id)
            // Notes can be edited more than once, unlike daily_reflection's create-once rows —
            // last-write-wins by updatedAtMillis instead of a plain insert-if-absent union.
            if (local == null || row.updatedAtMillis > local.updatedAtMillis) {
                dao.upsert(
                    UserReflectionNoteEntity(
                        userId = userId,
                        id = row.id,
                        noteText = row.noteText,
                        updatedAtMillis = row.updatedAtMillis,
                        synced = true,
                    )
                )
            }
        }
    }

    private suspend fun currentUserId(): String =
        authRepository.currentSession()?.userId?.takeIf { it.isNotBlank() } ?: ""
}

private fun UserReflectionNoteEntity.toRow() = UserReflectionNoteRow(
    userId = userId,
    id = id,
    noteText = noteText,
    updatedAtMillis = updatedAtMillis,
)
