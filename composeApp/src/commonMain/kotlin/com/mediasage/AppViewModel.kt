package com.mediasage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediasage.data.ThemePreferencesRepository
import com.mediasage.domain.model.UserSession
import com.mediasage.domain.repository.AuthRepository
import com.mediasage.domain.repository.DailyReflectionRepository
import com.mediasage.domain.repository.DayAssignmentRepository
import com.mediasage.domain.repository.EncouragementRepository
import com.mediasage.domain.repository.FigureRepository
import com.mediasage.domain.repository.QuoteRepository
import com.mediasage.theme.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AuthUiState {
    data object Loading : AuthUiState
    data object Unauthenticated : AuthUiState
    data class Authenticated(val session: UserSession) : AuthUiState
}

class AppViewModel(
    private val figureRepository: FigureRepository,
    private val dayAssignmentRepository: DayAssignmentRepository,
    private val dailyReflectionRepository: DailyReflectionRepository,
    private val encouragementRepository: EncouragementRepository,
    private val quoteRepository: QuoteRepository,
    themePreferencesRepository: ThemePreferencesRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    val darkMode: StateFlow<Boolean?> = themePreferencesRepository.darkMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val appTheme: StateFlow<AppTheme> = themePreferencesRepository.appTheme
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppTheme.CLASSIC)

    private val _authBypass = MutableStateFlow(false)

    val authState: StateFlow<AuthUiState> = combine(
        authRepository.observeAuthState(),
        _authBypass
    ) { session, bypassed ->
        when {
            bypassed -> AuthUiState.Authenticated(UserSession("", null))
            session != null -> AuthUiState.Authenticated(session)
            else -> AuthUiState.Unauthenticated
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AuthUiState.Loading)

    fun bypassAuth() {
        _authBypass.value = true
    }

    fun resetBypass() {
        _authBypass.value = false
    }

    init {
        val figuresSynced = viewModelScope.launch {
            try {
                figureRepository.syncFigures()
            } catch (e: Exception) {
                // Sync failure is non-fatal — app works offline with cached figures
            }
        }

        // A single sequential collector — never run the local-only seed and the
        // authenticated remote sync concurrently, or the seed can race ahead, fill the
        // table with defaults, and get mistaken for a pending local edit that should
        // win over the real pulled schedule.
        //
        // Also waits on figuresSynced first: pullAndReconcile resolves each remote row's
        // figure by serverId, so on a fresh install where the figures table is still empty,
        // running ahead of figure sync makes every row fail to resolve and get silently
        // dropped, leaving day_assignment empty until the next distinct signed-in session.
        viewModelScope.launch {
            figuresSynced.join()
            authState
                .filter { it !is AuthUiState.Loading }
                .map { state -> (state as? AuthUiState.Authenticated)?.session?.userId?.takeIf { it.isNotBlank() } }
                .distinctUntilChanged()
                .collect { userId ->
                    dayAssignmentRepository.resolve(userId)
                    dailyReflectionRepository.resolve(userId)
                    encouragementRepository.resolve(userId)
                    quoteRepository.resolve(userId)
                }
        }
    }
}
