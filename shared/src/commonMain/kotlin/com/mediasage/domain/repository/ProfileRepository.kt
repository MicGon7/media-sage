package com.mediasage.domain.repository

interface ProfileRepository {
    suspend fun createProfile(userId: String, displayName: String)
}
