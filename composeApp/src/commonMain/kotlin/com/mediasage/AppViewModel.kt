package com.mediasage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediasage.data.ThemePreferencesRepository
import com.mediasage.domain.model.UserSession
import com.mediasage.domain.repository.AuthRepository
import com.mediasage.domain.repository.DayAssignmentRepository
import com.mediasage.domain.repository.FigureRepository
import com.mediasage.theme.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
        viewModelScope.launch {
            try {
                figureRepository.syncFigures()
            } catch (e: Exception) {
                // Sync failure is non-fatal — app works offline with cached figures
            }
            try {
                dayAssignmentRepository.seedDefaultsIfEmpty()
            } catch (e: Exception) {
                // Seeding failure is non-fatal — briefing will be empty until next launch
            }
        }
    }
}
