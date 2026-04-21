package com.mediasage.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.mediasage.feature.figures.FiguresScreen
import com.mediasage.feature.figures.FiguresViewModel
import com.mediasage.feature.home.HomeScreen
import com.mediasage.feature.home.HomeViewModel
import com.mediasage.feature.match.MatchContract
import com.mediasage.feature.match.MatchScreen
import com.mediasage.feature.match.MatchViewModel
import mediasage.composeapp.generated.resources.Res
import mediasage.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaSageScaffold(
    appState: MediaSageAppState = rememberMediaSageAppState()
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (appState.showTopBar) {
                TopAppBar(
                    title = { Text(stringResource(appState.titleRes)) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    navigationIcon = {
                        if (!appState.isTopLevel) {
                            IconButton(onClick = { appState.navigateBack() }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(Res.string.nav_back)
                                )
                            }
                        }
                    }
                )
            }
        },
        bottomBar = {
            MediaSageBottomBar(
                destinations = TopLevelDestination.entries,
                currentDestination = appState.currentDestination,
                onNavigate = { appState.navigateToTopLevel(it) }
            )
        }
    ) { padding ->
        NavDisplay(
            backStack = appState.backStack,
            modifier = Modifier.padding(padding)
        ) { route ->
            when (route) {
                is Route.Home -> NavEntry(route) {
                    val vm = viewModel { HomeViewModel() }
                    val state by vm.state.collectAsState()
                    HomeScreen(
                        state = state,
                        onIntent = vm::onIntent,
                        onNavigateToDetail = { id -> appState.navigateToMatch(id) }
                    )
                }
                is Route.Match -> NavEntry(route) {
                    val vm = viewModel { MatchViewModel() }
                    val state by vm.state.collectAsState()
                    LaunchedEffect(route.headlineId) {
                        vm.onIntent(MatchContract.Intent.LoadMatch(route.headlineId))
                    }
                    MatchScreen(
                        headlineId = route.headlineId,
                        state = state,
                        onIntent = vm::onIntent
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
