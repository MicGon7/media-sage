package com.mediasage.data.repository

import com.mediasage.data.local.dao.UserReflectionNoteDao
import com.mediasage.data.local.entity.UserReflectionNoteEntity
import com.mediasage.domain.repository.UserReflectionNoteRepository

class UserReflectionNoteRepositoryImpl(
    private val dao: UserReflectionNoteDao,
) : UserReflectionNoteRepository {
    override suspend fun getNote(reflectionId: String): String? = dao.get(reflectionId)?.noteText

    override suspend fun saveNote(reflectionId: String, noteText: String) {
        dao.upsert(UserReflectionNoteEntity(id = reflectionId, noteText = noteText, updatedAtMillis = epochMillis()))
    }
}
