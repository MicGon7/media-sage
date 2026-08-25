package com.mediasage.domain.repository

interface UserReflectionNoteRepository {
    suspend fun getNote(reflectionId: String): String?

    suspend fun saveNote(reflectionId: String, noteText: String)
}
