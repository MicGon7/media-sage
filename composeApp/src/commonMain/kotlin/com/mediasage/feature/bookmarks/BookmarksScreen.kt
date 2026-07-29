package com.mediasage.feature.bookmarks

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediasage.ui.MediaSageEmptyState
import com.mediasage.ui.MediaSageHeadlineCard
import com.mediasage.ui.MediaSageLoadingState
import mediasage.composeapp.generated.resources.Res
import mediasage.composeapp.generated.resources.bookmarks_empty_subtitle
import mediasage.composeapp.generated.resources.bookmarks_empty_title
import mediasage.composeapp.generated.resources.bookmarks_section_title
import mediasage.composeapp.generated.resources.nav_back
import mediasage.composeapp.generated.resources.saved_insights_banner
import mediasage.composeapp.generated.resources.title_bookmarks
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun BookmarksScreen(
    state: BookmarksContract.UiState,
    onIntent: (BookmarksContract.Intent) -> Unit,
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
                    text = stringResource(Res.string.title_bookmarks),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.primary,
                thickness = 1.dp
            )
            when (state) {
                is BookmarksContract.UiState.Loading -> MediaSageLoadingState()
                is BookmarksContract.UiState.Empty -> MediaSageEmptyState(
                    title = stringResource(Res.string.bookmarks_empty_title),
                    subtitle = stringResource(Res.string.bookmarks_empty_subtitle)
                )
                is BookmarksContract.UiState.Success -> BookmarkList(
                    items = state.items,
                    onItemClick = { item -> onNavigateToDetail(item.articleUrl) },
                    onRemoveBookmark = { item ->
                        onIntent(BookmarksContract.Intent.ToggleBookmark(item.articleUrl))
                    }
                )
            }
        }
    }
}

@Composable
private fun BookmarkList(
    items: List<BookmarkItem>,
    onItemClick: (BookmarkItem) -> Unit,
    onRemoveBookmark: (BookmarkItem) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                text = stringResource(Res.string.bookmarks_section_title),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        items(items, key = { it.articleUrl }) { item ->
            BookmarkCard(
                item = item,
                onClick = { onItemClick(item) },
                onRemoveBookmark = { onRemoveBookmark(item) }
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
private fun BookmarkCard(
    item: BookmarkItem,
    onClick: () -> Unit,
    onRemoveBookmark: () -> Unit
) {
    Column {
        BookmarkCardBanner()
        MediaSageHeadlineCard(
            imageUrl = item.headlineImageUrl,
            headlineTitle = item.headlineTitle,
            figureName = item.figureName,
            figureRole = item.figureRole,
            quotePreview = item.quotePreview,
            isBookmarked = true,
            grayscaleImage = false,
            onClick = onClick,
            onBookmarkClick = onRemoveBookmark
        )
    }
}

@Composable
private fun BookmarkCardBanner() {
    Image(
        painter = painterResource(Res.drawable.saved_insights_banner),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
    )
}

