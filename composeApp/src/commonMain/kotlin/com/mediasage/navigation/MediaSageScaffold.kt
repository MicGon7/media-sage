package com.mediasage.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.mediasage.feature.detail.DetailScreen
import com.mediasage.feature.detail.DetailViewModel
import com.mediasage.feature.figures.FiguresScreen
import com.mediasage.feature.figures.FiguresViewModel
import com.mediasage.feature.home.HomeScreen
import com.mediasage.feature.home.HomeViewModel
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
                            is Route.Detail -> stringResource(Res.string.title_detail)
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
                    val viewModel = viewModel { HomeViewModel() }
                    HomeScreen(
                        viewModel = viewModel,
                        onHeadlineClick = { id -> backStack.add(Route.Detail(id)) }
                    )
                }
                is Route.Detail -> NavEntry(route) {
                    val viewModel = viewModel { DetailViewModel() }
                    DetailScreen(
                        headlineId = route.headlineId,
                        viewModel = viewModel
                    )
                }
                is Route.Figures -> NavEntry(route) {
                    val viewModel = viewModel { FiguresViewModel() }
                    FiguresScreen(viewModel = viewModel)
                }
                else -> NavEntry(route) {}
            }
        }
    }
}
