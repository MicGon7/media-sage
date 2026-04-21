package com.mediasage.feature.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mediasage.composeapp.generated.resources.Res
import mediasage.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** Headlines feed screen — full implementation in MS-13. */
@Composable
fun HomeScreen(
    state: HomeContract.UiState,
    onIntent: (HomeContract.Intent) -> Unit,
    onNavigateToDetail: (Long) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(Res.string.home_headlines_feed),
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.home_coming_soon),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = { onNavigateToDetail(1L) }) {
            Text(stringResource(Res.string.home_view_sample))
        }
    }
}
