package com.mediasage.navigation

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.mediasage.feature.figures.FiguresContract
import com.mediasage.feature.home.HomeContract
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.mediasage.feature.bookmarks.BookmarksScreen
import com.mediasage.feature.bookmarks.BookmarksViewModel
import com.mediasage.feature.figures.FigureDetailScreen
import com.mediasage.feature.figures.FigureDetailViewModel
import com.mediasage.feature.figures.FiguresScreen
import com.mediasage.feature.figures.FiguresViewModel
import com.mediasage.feature.history.HistoryScreen
import com.mediasage.feature.history.HistoryViewModel
import com.mediasage.feature.home.HomeScreen
import com.mediasage.feature.home.HomeViewModel
import com.mediasage.feature.headlinedetail.HeadlineDetailScreen
import com.mediasage.feature.headlinedetail.HeadlineDetailViewModel
import com.mediasage.feature.settings.SettingsContract
import com.mediasage.feature.settings.SettingsScreen
import com.mediasage.feature.settings.SettingsViewModel
import com.mediasage.feature.you.YouScreen
import com.mediasage.feature.you.YouViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun MediaSageScaffold(
    onSignedOut: () -> Unit = {},
    appState: MediaSageAppState = rememberMediaSageAppState()
) {
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
        ) { route ->
            when (route) {
                is Route.Home -> NavEntry(route) {
                    val vm = koinViewModel<HomeViewModel>()
                    val state by vm.state.collectAsState()
                    LaunchedEffect(vm) {
                        vm.sideEffects.collect { effect ->
                            when (effect) {
                                is HomeContract.SideEffect.ShowError ->
                                    snackbarHostState.showSnackbar(effect.message)
                                is HomeContract.SideEffect.NavigateToDetail ->
                                    appState.navigateToHeadlineDetail(effect.articleUrl)
                            }
                        }
                    }
                    HomeScreen(
                        state = state,
                        onIntent = vm::onIntent,
                        onNavigateToDetail = { url -> appState.navigateToHeadlineDetail(url) },
                        onNavigateToFigureDetail = { id -> appState.navigateToFigureDetail(id) }
                    )
                }
                is Route.HeadlineDetail -> NavEntry(route) {
                    val vm = koinViewModel<HeadlineDetailViewModel>(
                        key = "headline-detail-${route.articleUrl}",
                        parameters = { parametersOf(route.articleUrl) }
                    )
                    val state by vm.state.collectAsState()
                    HeadlineDetailScreen(
                        state = state,
                        onIntent = vm::onIntent,
                        onNavigateBack = { appState.navigateBack() }
                    )
                }
                is Route.Figures -> NavEntry(route) {
                    val vm = koinViewModel<FiguresViewModel>()
                    val state by vm.state.collectAsState()
                    LaunchedEffect(vm) {
                        vm.sideEffects.collect { effect ->
                            when (effect) {
                                is FiguresContract.SideEffect.ShowError ->
                                    snackbarHostState.showSnackbar(effect.message)
                            }
                        }
                    }
                    FiguresScreen(
                        state = state,
                        onIntent = vm::onIntent,
                        onNavigateToFigureDetail = { id -> appState.navigateToFigureDetail(id) }
                    )
                }
                is Route.FigureDetail -> NavEntry(route) {
                    val vm = koinViewModel<FigureDetailViewModel>(
                        key = "figure-${route.figureId}",
                        parameters = { parametersOf(route.figureId) }
                    )
                    val state by vm.state.collectAsState()
                    FigureDetailScreen(
                        state = state,
                        onIntent = vm::onIntent,
                        onNavigateBack = { appState.navigateBack() }
                    )
                }
                is Route.You -> NavEntry(route) {
                    val vm = koinViewModel<YouViewModel>()
                    val state by vm.state.collectAsState()
                    YouScreen(
                        state = state,
                        onIntent = vm::onIntent,
                        onNavigateToBookmarks = { appState.navigateToBookmarks() },
                        onNavigateToHistory = { appState.navigateToHistory() },
                        onNavigateToSettings = { appState.navigateToSettings() }
                    )
                }
                is Route.Bookmarks -> NavEntry(route) {
                    val vm = koinViewModel<BookmarksViewModel>()
                    val state by vm.state.collectAsState()
                    BookmarksScreen(
                        state = state,
                        onIntent = vm::onIntent,
                        onNavigateBack = { appState.navigateBack() },
                        onNavigateToDetail = { url -> appState.navigateToHeadlineDetail(url) }
                    )
                }
                is Route.History -> NavEntry(route) {
                    val vm = koinViewModel<HistoryViewModel>()
                    val state by vm.state.collectAsState()
                    HistoryScreen(
                        state = state,
                        onIntent = vm::onIntent,
                        onNavigateBack = { appState.navigateBack() },
                        onNavigateToDetail = { url -> appState.navigateToHeadlineDetail(url) }
                    )
                }
                is Route.Settings -> NavEntry(route) {
                    val vm = koinViewModel<SettingsViewModel>()
                    val state by vm.state.collectAsState()
                    LaunchedEffect(vm) {
                        vm.sideEffects.collect { effect ->
                            when (effect) {
                                is SettingsContract.SideEffect.SignedOut -> onSignedOut()
                            }
                        }
                    }
                    SettingsScreen(
                        state = state,
                        onIntent = vm::onIntent,
                        onNavigateBack = { appState.navigateBack() }
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
            val selected = currentDestination == destination.route
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(destination) },
                icon = {
                    Icon(
                        imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                        contentDescription = null
                    )
                },
                label = { Text(stringResource(destination.labelRes)) },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = Color.Transparent,
                    selectedIconColor = MaterialTheme.colorScheme.onSurface,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            )
        }
    }
}
