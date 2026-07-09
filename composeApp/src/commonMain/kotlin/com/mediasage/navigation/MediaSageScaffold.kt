package com.mediasage.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
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
import com.mediasage.theme.MediaSageTheme
import com.mediasage.feature.briefing.BriefingContract
import com.mediasage.feature.briefing.BriefingScreen
import com.mediasage.feature.briefing.BriefingViewModel
import com.mediasage.feature.figures.FiguresContract
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.mediasage.feature.bookmarks.BookmarksScreen
import com.mediasage.feature.bookmarks.BookmarksViewModel
import com.mediasage.feature.figures.FigureDetailScreen
import com.mediasage.feature.figures.FigureDetailViewModel
import com.mediasage.feature.figures.FiguresScreen
import com.mediasage.feature.figures.FiguresViewModel
import com.mediasage.feature.headlines.HeadlinesContract
import com.mediasage.feature.headlines.HeadlinesScreen
import com.mediasage.feature.headlines.HeadlinesViewModel
import com.mediasage.feature.you.HistoryScreen as YouHistoryScreen
import com.mediasage.feature.you.HistoryViewModel as YouHistoryViewModel
import com.mediasage.feature.headlinedetail.HeadlineDetailScreen
import com.mediasage.feature.headlinedetail.HeadlineDetailViewModel
import com.mediasage.feature.settings.SettingsContract
import com.mediasage.feature.settings.SettingsScreen
import com.mediasage.feature.settings.SettingsViewModel
import com.mediasage.feature.you.ReaderScreen
import com.mediasage.feature.you.ReaderViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun MediaSageScaffold(
    onSignedOut: () -> Unit = {},
    appState: MediaSageAppState = rememberMediaSageAppState()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val backgroundBrush = MediaSageTheme.colors.backgroundBrush

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (backgroundBrush != null) Modifier.background(backgroundBrush)
                else Modifier
            )
    ) {
    Scaffold(
        containerColor = if (backgroundBrush != null) Color.Transparent else MaterialTheme.colorScheme.surface,
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
                is Route.Briefing -> NavEntry(route) {
                    val vm = koinViewModel<BriefingViewModel>()
                    val state by vm.state.collectAsState()
                    LaunchedEffect(vm) {
                        vm.sideEffects.collect { effect ->
                            when (effect) {
                                is BriefingContract.SideEffect.ShowError ->
                                    snackbarHostState.showSnackbar(effect.message)
                            }
                        }
                    }
                    BriefingScreen(
                        state = state,
                        onIntent = vm::onIntent,
                        onNavigateToFigureDetail = { id -> appState.navigateToFigureDetail(id) }
                    )
                }
                is Route.Home -> NavEntry(route) {
                    val vm = koinViewModel<HeadlinesViewModel>()
                    val state by vm.state.collectAsState()
                    LaunchedEffect(vm) {
                        vm.sideEffects.collect { effect ->
                            when (effect) {
                                is HeadlinesContract.SideEffect.ShowError ->
                                    snackbarHostState.showSnackbar(effect.message)
                                is HeadlinesContract.SideEffect.NavigateToDetail ->
                                    appState.navigateToHeadlineDetail(effect.articleUrl)
                            }
                        }
                    }
                    HeadlinesScreen(
                        state = state,
                        onIntent = vm::onIntent,
                        onNavigateToDetail = { url -> appState.navigateToHeadlineDetail(url) }
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
                    val vm = koinViewModel<ReaderViewModel>()
                    val state by vm.state.collectAsState()
                    ReaderScreen(
                        state = state,
                        onIntent = vm::onIntent,
                        onNavigateToSettings = { appState.navigateToSettings() },
                        onNavigateToFigureDetail = { id -> appState.navigateToFigureDetail(id) },
                        onNavigateToArticleDetail = { url -> appState.navigateToHeadlineDetail(url) },
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
                    val vm = koinViewModel<YouHistoryViewModel>(
                        key = "you-history-${route.epochDay}",
                        parameters = { parametersOf(route.epochDay) },
                    )
                    val state by vm.state.collectAsState()
                    YouHistoryScreen(
                        state = state,
                        onIntent = vm::onIntent,
                        onNavigateBack = { appState.navigateBack() },
                        onNavigateToDetail = { url -> appState.navigateToHeadlineDetail(url) },
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
    } // Box
}

@Composable
private fun MediaSageBottomBar(
    destinations: List<TopLevelDestination>,
    currentDestination: Any?,
    onNavigate: (TopLevelDestination) -> Unit
) {
    Column {
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            thickness = 1.dp,
        )
        NavigationBar(
            modifier = Modifier.navigationBarsPadding(),
            windowInsets = WindowInsets(0),
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
}
