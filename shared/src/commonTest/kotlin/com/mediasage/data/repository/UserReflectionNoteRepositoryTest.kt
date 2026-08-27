package com.mediasage.data.repository

import com.mediasage.data.crypto.ReflectionNoteCipher
import com.mediasage.data.local.dao.UserReflectionNoteDao
import com.mediasage.data.local.entity.UserReflectionNoteEntity
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UserReflectionNoteRepositoryTest {

    @Test
    fun `saveNote stores ciphertext not plaintext`() = runTest {
        val dao = FakeUserReflectionNoteDao()
        val repository = UserReflectionNoteRepositoryImpl(dao, FakeReflectionNoteCipher())

        repository.saveNote("2026-08-27_encouraged_news", "Only I should be able to read this.")

        val stored = dao.get("2026-08-27_encouraged_news")
        assertEquals("ROT13:Bayl V fubhyq or noyr gb ernq guvf.", stored?.noteText)
    }

    @Test
    fun `getNote decrypts the stored ciphertext back to the original text`() = runTest {
        val dao = FakeUserReflectionNoteDao()
        val repository = UserReflectionNoteRepositoryImpl(dao, FakeReflectionNoteCipher())
        repository.saveNote("2026-08-27_encouraged_news", "Round trip me.")

        val note = repository.getNote("2026-08-27_encouraged_news")

        assertEquals("Round trip me.", note)
    }

    @Test
    fun `getNote returns null when no note exists`() = runTest {
        val repository = UserReflectionNoteRepositoryImpl(FakeUserReflectionNoteDao(), FakeReflectionNoteCipher())

        assertNull(repository.getNote("missing"))
    }
}

private class FakeUserReflectionNoteDao : UserReflectionNoteDao {
    private val notesById = mutableMapOf<String, UserReflectionNoteEntity>()

    override suspend fun get(id: String): UserReflectionNoteEntity? = notesById[id]

    override suspend fun upsert(entity: UserReflectionNoteEntity) {
        notesById[entity.id] = entity
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
