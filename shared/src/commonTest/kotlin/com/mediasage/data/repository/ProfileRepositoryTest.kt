package com.mediasage.data.repository

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ProfileRepositoryTest {

    @Test
    fun createProfilePushesRowWithUserIdAndDisplayName() = runTest {
        val remote = FakeProfileRemoteDataSource()
        val repository = ProfileRepositoryImpl(remote)

        repository.createProfile(userId = "user-1", displayName = "Ada Lovelace")

        assertEquals(ProfileRow(userId = "user-1", displayName = "Ada Lovelace"), remote.pushedRow)
    }

    @Test
    fun createProfileIsNoOpWhenRemoteIsNull() = runTest {
        val repository = ProfileRepositoryImpl(remote = null)

        // Supabase not configured (local/offline build) — must not throw.
        repository.createProfile(userId = "user-1", displayName = "Ada Lovelace")
    }
}

private class FakeProfileRemoteDataSource : ProfileRemoteDataSource {
    var pushedRow: ProfileRow? = null
        private set

    override suspend fun push(row: ProfileRow) {
        pushedRow = row
    }
}
