package com.mediasage.feature.headlines

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.mediasage.theme.MediaSageTheme
import com.mediasage.ui.ErrorType
import com.mediasage.ui.HeadlineImage
import com.mediasage.ui.MediaSageErrorState
import com.mediasage.ui.SepiaColorFilter
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
            is HeadlinesContract.UiState.Error -> MediaSageErrorState(
                message = when (state.errorType) {
                    ErrorType.NETWORK -> stringResource(Res.string.home_error_network)
                    ErrorType.GENERIC -> stringResource(Res.string.home_error_generic)
                },
                retryLabel = stringResource(Res.string.home_retry),
                onRetry = { onIntent(HeadlinesContract.Intent.Load) }
            )
            is HeadlinesContract.UiState.Success -> HeadlinesFeed(
                state = state,
                onRefresh = { onIntent(HeadlinesContract.Intent.Refresh) },
                onHeadlineClick = { onNavigateToDetail(it.articleUrl) }
            )
        }
    }
}

@Composable
private fun HeadlinesFeed(
    state: HeadlinesContract.UiState.Success,
    onRefresh: () -> Unit,
    onHeadlineClick: (HeadlineItem) -> Unit
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
            item {
                HeroPaintingPlaceholder()
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 0.5.dp
                )
            }
            itemsIndexed(state.headlines, key = { _, it -> it.id }) { _, headline ->
                HeadlineRow(
                    headline = headline,
                    onClick = { onHeadlineClick(headline) }
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

@Composable
private fun HeroPaintingPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(MaterialTheme.shapes.small)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                    )
                )
            )
    ) {
        Text(
            text = "Today's Featured Image".uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = MaterialTheme.shapes.extraSmall
                )
                .padding(horizontal = 6.dp, vertical = 2.dp)
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
            modifier = Modifier.clip(MaterialTheme.shapes.small),
            colorFilter = SepiaColorFilter
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
                        imageUrl = null
                    ),
                    HeadlineItem(
                        id = 2L,
                        articleUrl = "https://example.com/2",
                        title = "Markets Rally on Positive Economic Data",
                        source = "Financial Times",
                        category = "Business",
                        snippet = "Global indices rise sharply following better-than-expected jobs report.",
                        imageUrl = null
                    )
                ),
                todayLabel = "Friday, June 5, 2026"
            ),
            onIntent = {},
            onNavigateToDetail = {}
        )
    }
}
