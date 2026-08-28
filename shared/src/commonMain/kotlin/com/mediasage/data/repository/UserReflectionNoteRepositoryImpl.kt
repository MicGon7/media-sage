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

class UserReflectionNoteRepositoryImpl(
    private val dao: UserReflectionNoteDao,
    private val cipher: ReflectionNoteCipher,
    private val remote: UserReflectionNoteRemoteDataSource?,
    private val authRepository: AuthRepository,
    private val localAccountKeyDao: LocalAccountKeyDao,
    private val keyRemote: ReflectionNoteKeyRemoteDataSource?,
) : UserReflectionNoteRepository {

    override suspend fun getNote(reflectionId: String): String? = withContext(Dispatchers.Default) {
        try {
            val userId = currentUserId()
            val stored = dao.get(userId, reflectionId)?.noteText ?: return@withContext null
            decryptStored(userId, stored)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Keystore/Keychain or key-provisioning failure — treat as "no note" rather than crashing
            null
        }
    }

    override suspend fun saveNote(reflectionId: String, noteText: String) = withContext(Dispatchers.Default) {
        try {
            val userId = currentUserId()
            val key = getOrProvisionAccountKey(userId)
            val encrypted = if (key != null) SharedNoteCipher.encrypt(noteText, key) else cipher.encrypt(noteText)
            val entity = UserReflectionNoteEntity(
                userId = userId,
                id = reflectionId,
                noteText = encrypted,
                updatedAtMillis = epochMillis(),
                synced = false,
            )
            dao.upsert(entity)
            pushNote(entity)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Keystore/Keychain or key-provisioning failure — the note isn't saved, but must never crash
        }
    }

    override suspend fun resolve(userId: String?) {
        if (userId == null) return
        try {
            pushPending(userId)
            pullAndReconcile(userId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Failure is non-fatal — retried on next resolve pass
        }
    }

    /**
     * Notes saved under the shared account key (MS-740) decrypt here first. A note saved before
     * this change (or before a key was ever provisioned on this device) falls back to the old
     * per-device [cipher] — still readable on the exact device that wrote it, never on any other
     * device, until it's next saved and migrates forward onto the shared key.
     */
    private suspend fun decryptStored(userId: String, stored: String): String? {
        val key = getOrProvisionAccountKey(userId)
        if (key != null) {
            try {
                return SharedNoteCipher.decrypt(stored, key)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Not shared-key ciphertext — fall through to the legacy per-device format below
            }
        }
        return cipher.decrypt(stored)
    }

    private suspend fun getOrProvisionAccountKey(userId: String): ByteArray? {
        if (userId.isBlank()) return null
        localAccountKeyDao.get(userId)?.let { return unwrapLocalKey(it.wrappedKeyBase64) }
        return try {
            fetchOrClaimAccountKey(userId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null // network failure — caller falls back to the legacy per-device cipher
        }
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
