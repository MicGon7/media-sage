package com.mediasage.domain.repository

import com.mediasage.domain.model.UserSession
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    fun observeAuthState(): Flow<UserSession?>
    fun currentSession(): UserSession?
    suspend fun signInWithEmail(email: String, password: String)
    suspend fun signOut()
}
