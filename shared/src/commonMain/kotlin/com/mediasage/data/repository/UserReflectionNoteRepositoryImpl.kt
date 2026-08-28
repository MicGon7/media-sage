package com.mediasage.data.repository

import com.mediasage.data.crypto.ReflectionNoteCipher
import com.mediasage.data.local.dao.UserReflectionNoteDao
import com.mediasage.data.local.entity.UserReflectionNoteEntity
import com.mediasage.domain.repository.AuthRepository
import com.mediasage.domain.repository.UserReflectionNoteRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UserReflectionNoteRepositoryImpl(
    private val dao: UserReflectionNoteDao,
    private val cipher: ReflectionNoteCipher,
    private val remote: UserReflectionNoteRemoteDataSource?,
    private val authRepository: AuthRepository,
) : UserReflectionNoteRepository {

    override suspend fun getNote(reflectionId: String): String? = withContext(Dispatchers.IO) {
        try {
            dao.get(currentUserId(), reflectionId)?.noteText?.let(cipher::decrypt)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Keystore/Keychain decrypt failure — treat as "no note" rather than crashing
            null
        }
    }

    override suspend fun saveNote(reflectionId: String, noteText: String) = withContext(Dispatchers.IO) {
        try {
            val entity = UserReflectionNoteEntity(
                userId = currentUserId(),
                id = reflectionId,
                noteText = cipher.encrypt(noteText),
                updatedAtMillis = epochMillis(),
                synced = false,
            )
            dao.upsert(entity)
            pushNote(entity)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Keystore/Keychain encrypt failure — the note isn't saved, but must never crash the app
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
