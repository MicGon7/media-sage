package com.mediasage.feature.figures

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mediasage.theme.MediaSageTheme
import com.mediasage.theme.ReaderAmber
import com.mediasage.ui.FigurePlaceholder
import com.mediasage.ui.ScreenHeader
import mediasage.composeapp.generated.resources.Res
import mediasage.composeapp.generated.resources.search_voices_hint
import mediasage.composeapp.generated.resources.title_voices
import mediasage.composeapp.generated.resources.voices_empty_state
import mediasage.composeapp.generated.resources.voices_subtitle
import org.jetbrains.compose.resources.stringResource

private const val MAX_THEME_CHIPS = 2

@Composable
fun FiguresScreen(
    state: FiguresContract.UiState,
    onIntent: (FiguresContract.Intent) -> Unit,
    onNavigateToFigureDetail: (figureId: Long) -> Unit = {}
) {
    when (state) {
        is FiguresContract.UiState.Loading -> LoadingState()
        is FiguresContract.UiState.Success -> VoicesGrid(
            figures = state.figures,
            isRefreshing = state.isRefreshing,
            onRefresh = { onIntent(FiguresContract.Intent.Refresh) },
            searchQuery = state.searchQuery,
            onSearchQueryChanged = { query ->
                onIntent(FiguresContract.Intent.SearchQueryChanged(query))
            },
            onFigureClick = { id ->
                onIntent(FiguresContract.Intent.FigureClicked(id))
                onNavigateToFigureDetail(id)
            }
        )
    }
}

@Composable
private fun SearchBar(query: String, onQueryChanged: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChanged,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 8.dp),
        label = { Text(stringResource(Res.string.search_voices_hint)) },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        trailingIcon = {
            if (query.isNotBlank()) {
                IconButton(onClick = { onQueryChanged("") }) {
                    Icon(imageVector = Icons.Filled.Close, contentDescription = null)
                }
            }
        }
    )
}

@Composable
private fun VoicesGrid(
    figures: List<VoiceFigureItem>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    onFigureClick: (Long) -> Unit
) {
    val pullToRefreshState = rememberPullToRefreshState()
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()

    val collapsed by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Sticky header — lives outside the grid so it never scrolls away
            ScreenHeader(
                title = stringResource(Res.string.title_voices),
                listState = listState,
                isCollapsed = collapsed,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp),
                expandedTitleSize = 24f,
                subtitle = {
                    Text(
                        text = stringResource(Res.string.voices_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                },
                stickyContent = {
                    SearchBar(
                        query = searchQuery,
                        onQueryChanged = onSearchQueryChanged
                    )
                }
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .pullToRefresh(
                        isRefreshing = isRefreshing,
                        state = pullToRefreshState,
                        onRefresh = onRefresh
                    )
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (figures.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            EmptyState()
                        }
                    } else {
                        items(figures, key = { it.id }) { figure ->
                            PortraitCard(figure = figure, onClick = { onFigureClick(figure.id) })
                        }
                    }
                }

                PullToRefreshDefaults.Indicator(
                    state = pullToRefreshState,
                    isRefreshing = isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PortraitCard(figure: VoiceFigureItem, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .border(
                width = if (figure.isPinned) 2.dp else 1.dp,
                color = if (figure.isPinned) ReaderAmber else MaterialTheme.colorScheme.outline,
                shape = MaterialTheme.shapes.medium
            )
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column {
            Box {
                if (figure.imageUrl != null) {
                    AsyncImage(
                        model = figure.imageUrl,
                        contentDescription = figure.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(3f / 4f)
                            .then(
                                // Gold ring inset on the portrait when pinned
                                if (figure.isPinned) Modifier.border(
                                    width = 3.dp,
                                    color = ReaderAmber,
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp)
                                ) else Modifier
                            ),
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(3f / 4f)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .then(
                                if (figure.isPinned) Modifier.border(
                                    width = 3.dp,
                                    color = ReaderAmber,
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp)
                                ) else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        FigurePlaceholder(name = figure.name, size = 72.dp)
                    }
                }

                if (figure.quoteCount > 0) {
                    Text(
                        text = "${figure.quoteCount}",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = MaterialTheme.shapes.small
                            )
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }

            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                Text(
                    text = figure.name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                )
                if (figure.role.isNotBlank()) {
                    Text(
                        text = figure.role,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontStyle = FontStyle.Italic,
                        modifier = Modifier.padding(top = 2.dp),
                        maxLines = 1,
                    )
                }
                if (figure.lifespan.isNotBlank()) {
                    Text(
                        text = figure.lifespan,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }
                if (figure.themes.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        figure.themes.take(MAX_THEME_CHIPS).forEach { theme ->
                            SuggestionChip(
                                onClick = {},
                                label = {
                                    Text(
                                        text = theme,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 9.sp
                                    )
                                },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                border = null,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 64.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(Res.string.voices_empty_state),
            style = MaterialTheme.typography.bodyLarge,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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

// region Previews

@Preview(showBackground = true)
@Composable
private fun FiguresScreenPreview(
    @PreviewParameter(FiguresStateProvider::class) state: FiguresContract.UiState
) {
    MediaSageTheme {
        FiguresScreen(state = state, onIntent = {})
    }
}

// endregion
