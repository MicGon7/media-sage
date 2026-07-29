package com.mediasage.data.repository

import com.mediasage.domain.repository.ProfileRepository

class ProfileRepositoryImpl(
    private val remote: ProfileRemoteDataSource?
) : ProfileRepository {

    override suspend fun createProfile(userId: String, displayName: String) {
        remote?.push(ProfileRow(userId = userId, displayName = displayName))
    }
}
