package com.mediasage.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.mediasage.feature.figures.FiguresScreen
import com.mediasage.feature.figures.FiguresViewModel
import com.mediasage.feature.home.HomeScreen
import com.mediasage.feature.home.HomeViewModel
import com.mediasage.feature.match.MatchScreen
import com.mediasage.feature.match.MatchViewModel
import org.jetbrains.compose.resources.stringResource

@Composable
fun MediaSageScaffold(
    appState: MediaSageAppState = rememberMediaSageAppState()
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (appState.showBottomBar) {
                MediaSageBottomBar(
                    destinations = TopLevelDestination.entries,
                    currentDestination = appState.currentDestination,
                    onNavigate = { appState.navigateToTopLevel(it) }
                )
            }
        }
    ) { padding ->
        NavDisplay(
            backStack = appState.backStack,
            modifier = Modifier.padding(padding),
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            popTransitionSpec = { fadeIn() togetherWith fadeOut() },
        ) { route ->
            when (route) {
                is Route.Home -> NavEntry(route) {
                    val vm = koinViewModel<HomeViewModel>()
                    val state by vm.state.collectAsState()
                    HomeScreen(
                        state = state,
                        onIntent = vm::onIntent,
                        onNavigateToDetail = { id -> appState.navigateToMatch(id) }
                    )
                }
                is Route.Match -> NavEntry(route) {
                    val vm = koinViewModel<MatchViewModel>(
                        key = "match-${route.headlineId}",
                        parameters = { parametersOf(route.headlineId) }
                    )
                    val state by vm.state.collectAsState()
                    MatchScreen(
                        headlineId = route.headlineId,
                        state = state,
                        onIntent = vm::onIntent,
                        onNavigateBack = { appState.navigateBack() }
                    )
                }
                is Route.Figures -> NavEntry(route) {
                    val vm = viewModel { FiguresViewModel() }
                    val state by vm.state.collectAsState()
                    FiguresScreen(
                        state = state,
                        onIntent = vm::onIntent
                    )
                }
                else -> NavEntry(route) {}
            }
        }
    }
}

@Composable
private fun MediaSageBottomBar(
    destinations: List<TopLevelDestination>,
    currentDestination: Any?,
    onNavigate: (TopLevelDestination) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        destinations.forEach { destination ->
            NavigationBarItem(
                selected = currentDestination == destination.route,
                onClick = { onNavigate(destination) },
                icon = { Icon(destination.icon, contentDescription = null) },
                label = { Text(stringResource(destination.labelRes)) }
            )
        }
    }
}
