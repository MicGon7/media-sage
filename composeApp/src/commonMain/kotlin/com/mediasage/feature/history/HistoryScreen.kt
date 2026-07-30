package com.mediasage.feature.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mediasage.ui.MediaSageEmptyState
import com.mediasage.ui.MediaSageHeadlineCard
import com.mediasage.ui.MediaSageLoadingState
import mediasage.composeapp.generated.resources.Res
import mediasage.composeapp.generated.resources.history_empty_subtitle
import mediasage.composeapp.generated.resources.history_empty_title
import mediasage.composeapp.generated.resources.nav_back
import mediasage.composeapp.generated.resources.title_history
import org.jetbrains.compose.resources.stringResource

@Suppress("LongParameterList")
@Composable
fun HistoryScreen(
    state: HistoryContract.UiState,
    onIntent: (HistoryContract.Intent) -> Unit,
    onNavigateBack: () -> Unit = {},
    onNavigateToDetail: (String) -> Unit = {}
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(Res.string.nav_back)
                    )
                }
                Text(
                    text = stringResource(Res.string.title_history),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.primary,
                thickness = 1.dp
            )
            when (state) {
                is HistoryContract.UiState.Loading -> MediaSageLoadingState()
                is HistoryContract.UiState.Empty -> MediaSageEmptyState(
                    title = stringResource(Res.string.history_empty_title),
                    subtitle = stringResource(Res.string.history_empty_subtitle)
                )
                is HistoryContract.UiState.Success -> HistoryList(
                    items = state.items,
                    onItemClick = { item -> onNavigateToDetail(item.articleUrl) },
                    onBookmarkToggle = { item ->
                        onIntent(HistoryContract.Intent.ToggleBookmark(item.articleUrl))
                    }
                )
            }
        }
    }
}

@Composable
private fun HistoryList(
    items: List<HistoryItem>,
    onItemClick: (HistoryItem) -> Unit,
    onBookmarkToggle: (HistoryItem) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items, key = { it.articleUrl }) { item ->
            HistoryCard(
                item = item,
                onClick = { onItemClick(item) },
                onBookmarkToggle = { onBookmarkToggle(item) }
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
                thickness = 0.5.dp
            )
        }
    }
}

@Composable
private fun HistoryCard(
    item: HistoryItem,
    onClick: () -> Unit,
    onBookmarkToggle: () -> Unit
) {
    MediaSageHeadlineCard(
        imageUrl = item.headlineImageUrl,
        headlineTitle = item.headlineTitle,
        figureName = item.figureName,
        figureRole = item.figureRole,
        figureImageUrl = item.figureImageUrl,
        isBookmarked = item.isBookmarked,
        grayscaleImage = true,
        onClick = onClick,
        onBookmarkClick = onBookmarkToggle
    )
}

