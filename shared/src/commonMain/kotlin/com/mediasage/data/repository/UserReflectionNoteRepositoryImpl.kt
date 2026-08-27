package com.mediasage.data.repository

import com.mediasage.data.crypto.ReflectionNoteCipher
import com.mediasage.data.local.dao.UserReflectionNoteDao
import com.mediasage.data.local.entity.UserReflectionNoteEntity
import com.mediasage.domain.repository.UserReflectionNoteRepository

class UserReflectionNoteRepositoryImpl(
    private val dao: UserReflectionNoteDao,
    private val cipher: ReflectionNoteCipher,
) : UserReflectionNoteRepository {
    override suspend fun getNote(reflectionId: String): String? =
        dao.get(reflectionId)?.noteText?.let(cipher::decrypt)

    override suspend fun saveNote(reflectionId: String, noteText: String) {
        val encrypted = cipher.encrypt(noteText)
        dao.upsert(UserReflectionNoteEntity(id = reflectionId, noteText = encrypted, updatedAtMillis = epochMillis()))
    }
}
