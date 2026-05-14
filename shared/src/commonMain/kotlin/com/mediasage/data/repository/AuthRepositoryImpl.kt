package com.mediasage.data.repository

import com.mediasage.domain.model.UserSession
import com.mediasage.domain.repository.AuthRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

class AuthRepositoryImpl(
    private val supabaseClient: SupabaseClient?
) : AuthRepository {

    override fun observeAuthState(): Flow<UserSession?> {
        val client = supabaseClient ?: return flowOf(null)
        return client.auth.sessionStatus
            .filter { it !is SessionStatus.Initializing }
            .map { status ->
                when (status) {
                    is SessionStatus.Authenticated -> UserSession(
                        userId = status.session.user?.id ?: "",
                        email = status.session.user?.email,
                        displayName = status.session.user?.userMetadata?.get("full_name")?.jsonPrimitive?.contentOrNull,
                    )
                    else -> null // NotAuthenticated or RefreshFailure
                }
            }
    }

    override fun currentSession(): UserSession? {
        val status = supabaseClient?.auth?.sessionStatus?.value
        return (status as? SessionStatus.Authenticated)?.let {
            UserSession(
                userId = it.session.user?.id ?: "",
                email = it.session.user?.email,
                displayName = it.session.user?.userMetadata?.get("full_name")?.jsonPrimitive?.contentOrNull,
            )
        }
    }

    override suspend fun signInWithEmail(email: String, password: String) {
        val client = supabaseClient ?: error("Supabase not configured")
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    override suspend fun signOut() {
        supabaseClient?.auth?.signOut()
    }
}
