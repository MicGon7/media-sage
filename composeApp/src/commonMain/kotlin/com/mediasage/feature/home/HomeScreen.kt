package com.mediasage.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.mediasage.ui.ErrorType
import com.mediasage.ui.HeadlineImage
import mediasage.composeapp.generated.resources.Res
import mediasage.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun HomeScreen(
    state: HomeContract.UiState,
    onIntent: (HomeContract.Intent) -> Unit,
    onNavigateToDetail: (Long) -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize()) {
    when (state) {
        is HomeContract.UiState.Loading -> LoadingState()
        is HomeContract.UiState.Empty -> EmptyState(
            onRefresh = { onIntent(HomeContract.Intent.RefreshHeadlines) }
        )
        is HomeContract.UiState.Error -> ErrorState(
            message = when (state.errorType) {
                ErrorType.NETWORK -> stringResource(Res.string.home_error_network)
                ErrorType.GENERIC -> stringResource(Res.string.home_error_generic)
            },
            onRetry = { onIntent(HomeContract.Intent.LoadHeadlines) }
        )
        is HomeContract.UiState.Success -> HeadlinesFeed(
            headlines = state.headlines,
            isRefreshing = state.isRefreshing,
            onRefresh = { onIntent(HomeContract.Intent.RefreshHeadlines) },
            onHeadlineClick = onNavigateToDetail
        )
    }
    }
}

@Composable
private fun Masthead() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(Res.string.app_name),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(Res.string.home_tagline),
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(
            color = MaterialTheme.colorScheme.primary,
            thickness = 1.dp
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HeadlinesFeed(
    headlines: List<HeadlineItem>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onHeadlineClick: (Long) -> Unit
) {
    val pullToRefreshState = rememberPullToRefreshState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullToRefresh(
                isRefreshing = isRefreshing,
                state = pullToRefreshState,
                onRefresh = onRefresh
            )
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item { Masthead() }

            items(headlines, key = { it.id }) { headline ->
                HeadlineRow(
                    headline = headline,
                    onClick = { onHeadlineClick(headline.id) }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 0.5.dp
                )
            }
        }

        PullToRefreshDefaults.Indicator(
            state = pullToRefreshState,
            isRefreshing = isRefreshing,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

@Composable
private fun HeadlineRow(
    headline: HeadlineItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HeadlineImage(
            imageUrl = headline.imageUrl,
            contentDescription = headline.title,
            size = 80.dp,
            modifier = Modifier.clip(MaterialTheme.shapes.small)
        )

        Column(modifier = Modifier.weight(1f)) {
            if (headline.category.isNotBlank()) {
                Text(
                    text = headline.category.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing * 1.5f,
                )
                Spacer(modifier = Modifier.height(2.dp))
            }

            Text(
                text = headline.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            if (headline.snippet.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = headline.snippet,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = headline.source,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyState(onRefresh: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(Res.string.home_empty_message),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(onClick = onRefresh) {
            Text(stringResource(Res.string.home_empty_refresh))
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(onClick = onRetry) {
            Text(stringResource(Res.string.home_retry))
        }
    }
}
