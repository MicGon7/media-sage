package com.mediasage.feature.headlines

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.mediasage.domain.model.HeadlineCategoryFilter
import com.mediasage.theme.ComicGradientOrientation
import com.mediasage.theme.MediaSageTheme
import com.mediasage.theme.rememberComicSurfaceColors
import com.mediasage.ui.ErrorType
import com.mediasage.ui.MediaSageEmptyState
import com.mediasage.ui.MediaSageErrorDialog
import com.mediasage.ui.MediaSageHeadlineCard
import com.mediasage.ui.ScreenHeader
import mediasage.composeapp.generated.resources.Res
import mediasage.composeapp.generated.resources.headline_category_business
import mediasage.composeapp.generated.resources.headline_category_health
import mediasage.composeapp.generated.resources.headline_category_nation
import mediasage.composeapp.generated.resources.headline_category_science
import mediasage.composeapp.generated.resources.headline_category_world
import mediasage.composeapp.generated.resources.headlines_category_empty_subtitle
import mediasage.composeapp.generated.resources.headlines_category_empty_title
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
                onBookmarkClick = { onIntent(HeadlinesContract.Intent.ToggleBookmark(it.articleUrl)) },
                onCategorySelected = { onIntent(HeadlinesContract.Intent.CategorySelected(it)) }
            )
        }
    }
}

@Composable
private fun HeadlinesFeed(
    state: HeadlinesContract.UiState.Success,
    onRefresh: () -> Unit,
    onHeadlineClick: (HeadlineItem) -> Unit,
    onBookmarkClick: (HeadlineItem) -> Unit,
    onCategorySelected: (String) -> Unit
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
                // Chip row sits outside ScreenHeader's 16dp inset so it can scroll edge-to-edge;
                // its own contentPadding aligns resting chips with the title above.
                Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                    ScreenHeader(
                        title = stringResource(Res.string.nav_headlines),
                        listState = listState,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        expandedTitleSize = 24f,
                        showDivider = false,
                        subtitle = if (state.headlines.isNotEmpty()) ({
                            DateCountRow(
                                todayLabel = state.todayLabel,
                                storyCount = state.headlines.size
                            )
                        }) else null
                    )
                    CategoryChipRow(
                        selectedCategory = state.selectedCategory,
                        onCategorySelected = onCategorySelected
                    )
                }
            }
            if (state.headlines.isEmpty()) {
                item {
                    MediaSageEmptyState(
                        title = stringResource(Res.string.headlines_category_empty_title),
                        subtitle = stringResource(Res.string.headlines_category_empty_subtitle),
                        modifier = Modifier.fillParentMaxHeight()
                    )
                }
            } else {
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
private fun CategoryChipRow(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(HeadlineCategoryFilter.entries) { category ->
            CategoryChip(
                label = stringResource(category.labelRes()),
                selected = category.value == selectedCategory,
                onClick = { onCategorySelected(category.value) }
            )
        }
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    // FilterChip for its selected semantics and checkmark animation; the comic gradient can't be
    // expressed through SelectableChipColors (flat Colors only), so the selected container is
    // transparent and rememberComicSurfaceColors' background paints on a sibling Box behind it.
    // The chip's 32dp pill renders centered inside its 48dp minimum touch target, so the gradient
    // is inset to those visual bounds instead of filling the full (invisible) interactive layout.
    val comicColors = rememberComicSurfaceColors(ComicGradientOrientation.Horizontal)
    Box {
        if (selected) {
            SelectedChipBackground(background = comicColors.background)
        }
        FilterChip(
            selected = selected,
            onClick = onClick,
            label = { Text(text = label, style = MaterialTheme.typography.labelMedium) },
            leadingIcon = if (selected) ({ SelectedCheckIcon() }) else null,
            shape = CircleShape,
            colors = FilterChipDefaults.filterChipColors(
                labelColor = comicColors.content.copy(alpha = 0.75f),
                selectedContainerColor = Color.Transparent,
                selectedLabelColor = comicColors.content,
                selectedLeadingIconColor = comicColors.content
            ),
            border = FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = selected,
                borderColor = comicColors.border.copy(alpha = 0.5f),
                selectedBorderColor = comicColors.border,
                selectedBorderWidth = 1.dp
            )
        )
    }
}

@Composable
private fun BoxScope.SelectedChipBackground(background: Modifier) {
    val touchTargetInset = (LocalMinimumInteractiveComponentSize.current - FilterChipDefaults.Height) / 2
    Box(
        modifier = Modifier
            .matchParentSize()
            .padding(vertical = touchTargetInset.coerceAtLeast(0.dp))
            .clip(CircleShape)
            .then(background)
    )
}

@Composable
private fun SelectedCheckIcon() {
    Icon(
        imageVector = Icons.Filled.Check,
        contentDescription = null,
        modifier = Modifier.size(FilterChipDefaults.IconSize)
    )
}

private fun HeadlineCategoryFilter.labelRes() = when (this) {
    HeadlineCategoryFilter.WORLD -> Res.string.headline_category_world
    HeadlineCategoryFilter.NATION -> Res.string.headline_category_nation
    HeadlineCategoryFilter.BUSINESS -> Res.string.headline_category_business
    HeadlineCategoryFilter.SCIENCE -> Res.string.headline_category_science
    HeadlineCategoryFilter.HEALTH -> Res.string.headline_category_health
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
