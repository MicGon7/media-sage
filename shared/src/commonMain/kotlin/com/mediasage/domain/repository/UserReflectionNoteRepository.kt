package com.mediasage.domain.repository

interface UserReflectionNoteRepository {
    suspend fun getNote(reflectionId: String): String?

    suspend fun saveNote(reflectionId: String, noteText: String)

    /**
     * Pushes any locally-saved, not-yet-synced notes up for [userId] (a no-op when signed out),
     * then pulls and reconciles any notes saved on another device — last-write-wins by
     * `updatedAtMillis`, since unlike daily reflections a note can be edited more than once.
     */
    suspend fun resolve(userId: String?)
}
