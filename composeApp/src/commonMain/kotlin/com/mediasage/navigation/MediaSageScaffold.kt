package com.mediasage.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
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
fun MediaSageScaffold() {
    val backStack = rememberNavBackStack(
        configuration = SavedStateConfiguration {
            serializersModule = navSerializersModule
        },
        Route.Home
    )

    val currentRoute = backStack.lastOrNull()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (currentRoute) {
                            is Route.Home -> stringResource(Res.string.title_home)
                            is Route.Match -> stringResource(Res.string.title_match)
                            is Route.Figures -> stringResource(Res.string.title_figures)
                            else -> stringResource(Res.string.title_home)
                        }
                    )
                },
                navigationIcon = {
                    if (currentRoute !is Route.Home && currentRoute != null) {
                        IconButton(onClick = { backStack.removeLastOrNull() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(Res.string.nav_back)
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentRoute is Route.Home,
                    onClick = {
                        if (currentRoute !is Route.Home) {
                            backStack.clear()
                            backStack.add(Route.Home)
                        }
                    },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text(stringResource(Res.string.nav_headlines)) }
                )
                NavigationBarItem(
                    selected = currentRoute is Route.Figures,
                    onClick = {
                        if (currentRoute !is Route.Figures) {
                            backStack.clear()
                            backStack.add(Route.Figures)
                        }
                    },
                    icon = { Icon(Icons.Default.Person, contentDescription = null) },
                    label = { Text(stringResource(Res.string.nav_figures)) }
                )
            }
        }
    ) { padding ->
        NavDisplay(
            backStack = backStack,
            modifier = Modifier.padding(padding)
        ) { route ->
            when (route) {
                is Route.Home -> NavEntry(route) {
                    val vm = viewModel { HomeViewModel() }
                    val state by vm.state.collectAsState()
                    HomeScreen(
                        state = state,
                        onIntent = vm::onIntent,
                        onNavigateToDetail = { id -> backStack.add(Route.Match(id)) }
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
