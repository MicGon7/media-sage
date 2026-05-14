package com.mediasage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import com.mediasage.feature.login.LoginContract
import com.mediasage.feature.login.LoginScreen
import com.mediasage.feature.login.LoginViewModel
import com.mediasage.navigation.MediaSageScaffold
import com.mediasage.theme.AppTheme
import com.mediasage.theme.MediaSageTheme
import org.koin.compose.viewmodel.koinViewModel

@Composable
@Preview
fun App(isDebugBuild: Boolean = false) {
    val appViewModel = koinViewModel<AppViewModel>()
    val darkMode by appViewModel.darkMode.collectAsState()
    val authState by appViewModel.authState.collectAsState()

    val appTheme = AppTheme.CLASSIC // change this to switch themes until Settings wires it up

    CompositionLocalProvider(LocalIsDebugBuild provides isDebugBuild) {
        MediaSageTheme(theme = appTheme, darkTheme = darkMode ?: false) {
            when (authState) {
                is AuthUiState.Loading -> Unit
                is AuthUiState.Unauthenticated -> {
                    val loginVm = koinViewModel<LoginViewModel>()
                    val loginState by loginVm.state.collectAsState()
                    LaunchedEffect(loginVm) {
                        loginVm.sideEffects.collect { effect ->
                            when (effect) {
                                is LoginContract.SideEffect.NavigateToHome -> appViewModel.bypassAuth()
                                is LoginContract.SideEffect.ShowError -> Unit
                            }
                        }
                    }
                    LoginScreen(state = loginState, onIntent = loginVm::onIntent)
                }
                is AuthUiState.Authenticated -> MediaSageScaffold(
                    onSignedOut = { appViewModel.resetBypass() }
                )
            }
        }
    }
}
