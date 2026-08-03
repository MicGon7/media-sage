package com.mediasage.feature.headlines

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.mediasage.theme.MediaSageTheme
import com.mediasage.ui.ErrorType
import com.mediasage.ui.MediaSageErrorDialog
import com.mediasage.ui.MediaSageHeadlineCard
import com.mediasage.ui.ScreenHeader
import mediasage.composeapp.generated.resources.Res
import mediasage.composeapp.generated.resources.home_error_generic
import mediasage.composeapp.generated.resources.home_error_network
import mediasage.composeapp.generated.resources.home_retry
import mediasage.composeapp.generated.resources.headlines_story_count
import mediasage.composeapp.generated.resources.nav_headlines
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun HeadlinesScreen(
    state: HeadlinesContract.UiState,
    onIntent: (HeadlinesContract.Intent) -> Unit,
    onNavigateToDetail: (String) -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        when (state) {
            is HeadlinesContract.UiState.Loading -> Box(modifier = Modifier.fillMaxSize())
            is HeadlinesContract.UiState.Error -> {
                MediaSageErrorDialog(
                    message = when (state.errorType) {
                        ErrorType.NETWORK -> stringResource(Res.string.home_error_network)
                        ErrorType.GENERIC -> stringResource(Res.string.home_error_generic)
                    },
                    retryLabel = stringResource(Res.string.home_retry),
                    onRetry = { onIntent(HeadlinesContract.Intent.Load) }
                )
            }
            is HeadlinesContract.UiState.Success -> HeadlinesFeed(
                state = state,
                onRefresh = { onIntent(HeadlinesContract.Intent.Refresh) },
                onHeadlineClick = { onNavigateToDetail(it.articleUrl) },
                onBookmarkClick = { onIntent(HeadlinesContract.Intent.ToggleBookmark(it.articleUrl)) }
            )
        }
    }
}

@Composable
private fun HeadlinesFeed(
    state: HeadlinesContract.UiState.Success,
    onRefresh: () -> Unit,
    onHeadlineClick: (HeadlineItem) -> Unit,
    onBookmarkClick: (HeadlineItem) -> Unit
) {
    val listState = rememberLazyListState()
    val pullToRefreshState = rememberPullToRefreshState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .pullToRefresh(
                isRefreshing = state.isRefreshing,
                state = pullToRefreshState,
                onRefresh = onRefresh
            )
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
            stickyHeader {
                ScreenHeader(
                    title = stringResource(Res.string.nav_headlines),
                    listState = listState,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp),
                    expandedTitleSize = 24f,
                    showDivider = false,
                    subtitle = if (state.headlines.isNotEmpty()) ({
                        DateCountRow(
                            todayLabel = state.todayLabel,
                            storyCount = state.headlines.size
                        )
                    }) else null
                )
            }
            itemsIndexed(state.headlines, key = { _, it -> it.id }) { _, headline ->
                MediaSageHeadlineCard(
                    imageUrl = headline.imageUrl,
                    headlineTitle = headline.title,
                    grayscaleImage = false,
                    onClick = { onHeadlineClick(headline) },
                    source = headline.source,
                    category = headline.category,
                    publishedAtLabel = headline.publishedAtLabel,
                    snippet = headline.snippet,
                    figureName = headline.figureName.takeIf { headline.isRead },
                    figureRole = headline.figureRole.takeIf { headline.isRead },
                    quotePreview = headline.quotePreview.takeIf { headline.isRead },
                    isBookmarked = headline.isBookmarked.takeIf { headline.isRead },
                    onBookmarkClick = { onBookmarkClick(headline) }.takeIf { headline.isRead },
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
            isRefreshing = state.isRefreshing,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

@Composable
private fun DateCountRow(todayLabel: String, storyCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = todayLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = pluralStringResource(Res.plurals.headlines_story_count, storyCount, storyCount),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HeadlinesScreenPreview() {
    MediaSageTheme {
        HeadlinesScreen(
            state = HeadlinesContract.UiState.Success(
                headlines = listOf(
                    HeadlineItem(
                        id = 1L,
                        articleUrl = "https://example.com/1",
                        title = "World Leaders Gather for Climate Summit in Geneva",
                        source = "Reuters",
                        category = "World",
                        snippet = "Delegates from over 190 countries convene to discuss new emissions targets.",
                        imageUrl = null,
                        publishedAtLabel = "Jun 5, 2026"
                    ),
                    HeadlineItem(
                        id = 2L,
                        articleUrl = "https://example.com/2",
                        title = "Markets Rally on Positive Economic Data",
                        source = "Financial Times",
                        category = "Business",
                        snippet = "Global indices rise sharply following better-than-expected jobs report.",
                        imageUrl = null,
                        publishedAtLabel = "Jun 5, 2026"
                    )
                ),
                todayLabel = "Friday, June 5, 2026"
            ),
            onIntent = {},
            onNavigateToDetail = {}
        )
    }
}
