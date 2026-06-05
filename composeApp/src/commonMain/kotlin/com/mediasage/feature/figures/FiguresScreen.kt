package com.mediasage.feature.figures

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.mediasage.theme.MediaSageTheme
import coil3.compose.AsyncImage
import com.mediasage.ui.FigurePlaceholder
import mediasage.composeapp.generated.resources.Res
import mediasage.composeapp.generated.resources.figure_detail_quotes_button
import mediasage.composeapp.generated.resources.search_voices_hint
import mediasage.composeapp.generated.resources.title_voices
import mediasage.composeapp.generated.resources.voices_empty_state
import mediasage.composeapp.generated.resources.voices_subtitle
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun FiguresScreen(
    state: FiguresContract.UiState,
    onIntent: (FiguresContract.Intent) -> Unit,
    onNavigateToFigureDetail: (figureId: Long) -> Unit = {}
) {
    when (state) {
        is FiguresContract.UiState.Loading -> LoadingState()
        is FiguresContract.UiState.Success -> VoicesList(
            figures = state.figures,
            isRefreshing = state.isRefreshing,
            onRefresh = { onIntent(FiguresContract.Intent.Refresh) },
            searchQuery = state.searchQuery,
            onSearchQueryChanged = { query -> onIntent(FiguresContract.Intent.SearchQueryChanged(query)) },
            onFigureClick = { id ->
                onIntent(FiguresContract.Intent.FigureClicked(id))
                onNavigateToFigureDetail(id)
            }
        )
    }
}

@Composable
private fun VoicesHeader(collapsed: Boolean) {
    val titleSize by animateFloatAsState(
        targetValue = if (collapsed) 22f else 36f,
        label = "titleSize"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Text(
            text = stringResource(Res.string.title_voices),
            fontSize = titleSize.sp,
            fontWeight = FontWeight.Bold,
        )
        AnimatedVisibility(visible = !collapsed) {
            Column {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(Res.string.voices_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(
            color = MaterialTheme.colorScheme.primary,
            thickness = 1.dp
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
            .padding(vertical = 16.dp),
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
private fun VoicesList(
    figures: List<VoiceFigureItem>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    onFigureClick: (Long) -> Unit
) {
    val pullToRefreshState = rememberPullToRefreshState()
    val listState = rememberLazyListState()
    val headerCollapsed by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 } }

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .pullToRefresh(
                    isRefreshing = isRefreshing,
                    state = pullToRefreshState,
                    onRefresh = onRefresh
                )
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                stickyHeader {
                    Column {
                        VoicesHeader(collapsed = headerCollapsed)
                        SearchBar(query = searchQuery, onQueryChanged = onSearchQueryChanged)
                    }
                }

                if (figures.isEmpty()) {
                    item { EmptyState() }
                } else {
                    items(figures, key = { it.id }) { figure ->
                        VoiceCard(figure = figure, onClick = { onFigureClick(figure.id) })
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

@Composable
private fun VoiceCard(figure: VoiceFigureItem, onClick: () -> Unit) {
    Box {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .clickable(onClick = onClick),
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (figure.imageUrl != null) {
                    AsyncImage(
                        model = figure.imageUrl,
                        contentDescription = figure.name,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter
                    )
                } else {
                    FigurePlaceholder(name = figure.name, size = 64.dp)
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = figure.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (figure.role.isNotBlank()) {
                        Text(
                            text = figure.role,
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        if (figure.quoteCount > 0) {
            Text(
                text = pluralStringResource(Res.plurals.figure_detail_quotes_button, figure.quoteCount, figure.quoteCount),
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-8).dp, y = 2.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        if (figure.isPinned) {
            Icon(
                imageVector = Icons.Filled.PushPin,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = 8.dp, y = 2.dp)
                    .size(20.dp)
            )
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

private class FiguresStateProvider : PreviewParameterProvider<FiguresContract.UiState> {
    override val values = sequenceOf(
        FiguresContract.UiState.Loading,
        FiguresContract.UiState.Success(figures = emptyList()),
        FiguresContract.UiState.Success(
            figures = listOf(
                VoiceFigureItem(id = 1L, name = "C.S. Lewis", role = "Author & Apologist", imageUrl = null, quoteCount = 3, isPinned = true),
                VoiceFigureItem(id = 2L, name = "Dietrich Bonhoeffer", role = "Theologian & Martyr", imageUrl = null, quoteCount = 1),
                VoiceFigureItem(id = 3L, name = "Martin Luther King Jr.", role = "Pastor & Civil Rights Leader", imageUrl = null, quoteCount = 0),
            )
        ),
        FiguresContract.UiState.Success(
            figures = listOf(
                VoiceFigureItem(id = 1L, name = "C.S. Lewis", role = "Author & Apologist", imageUrl = null, quoteCount = 3, isPinned = true),
            ),
            searchQuery = "lewis"
        )
    )
}

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
