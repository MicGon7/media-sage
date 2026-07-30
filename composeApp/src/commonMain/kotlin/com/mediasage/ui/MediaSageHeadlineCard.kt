package com.mediasage.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mediasage.theme.ComicTan
import com.mediasage.theme.MediaSageTheme
import com.mediasage.theme.rememberComicSurfaceColors
import mediasage.composeapp.generated.resources.Res
import mediasage.composeapp.generated.resources.bookmark_add
import mediasage.composeapp.generated.resources.bookmark_remove
import org.jetbrains.compose.resources.stringResource

@Suppress("LongParameterList")
@Composable
fun MediaSageHeadlineCard(
    imageUrl: String?,
    headlineTitle: String,
    grayscaleImage: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    source: String = "",
    category: String = "",
    publishedAtLabel: String = "",
    snippet: String = "",
    figureName: String? = null,
    figureRole: String? = null,
    quotePreview: String? = null,
    isBookmarked: Boolean? = null,
    onBookmarkClick: (() -> Unit)? = null,
) {
    val metadataLine =
        listOf(source, publishedAtLabel).filter { it.isNotBlank() }.joinToString(" · ")
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                contentScale = ContentScale.Crop,
                colorFilter = if (grayscaleImage) {
                    ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
                } else {
                    null
                }
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Article,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 16.dp,
                    end = 4.dp,
                    top = 12.dp,
                    bottom = if (figureName != null) 0.dp else 12.dp
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (category.isNotBlank()) {
                    Text(
                        text = category.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing * 1.5f,
                    )
                }
                Text(
                    text = headlineTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (metadataLine.isNotBlank()) {
                    Text(
                        text = metadataLine,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (snippet.isNotBlank() && figureName == null) {
                    Text(
                        text = snippet,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (isBookmarked != null && onBookmarkClick != null) {
                IconButton(onClick = onBookmarkClick) {
                    Icon(
                        imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = stringResource(
                            if (isBookmarked) Res.string.bookmark_remove else Res.string.bookmark_add
                        ),
                        tint = if (isBookmarked) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (figureName != null) {
            FigureMatchRow(
                figureName = figureName,
                quotePreview = quotePreview,
            )
        }
    }
}

@Composable
private fun FigureMatchRow(
    figureName: String,
    quotePreview: String?,
) {
    val comicColors = rememberComicSurfaceColors()
    // Fades from the card's own surface color into the comic tone instead of a flat fill, so the
    // boundary with the news section above blends rather than cutting in as a hard edge.
    val backgroundModifier = if (MediaSageTheme.isDark) {
        comicColors.background
    } else {
        Modifier.background(Brush.verticalGradient(colors = listOf(MaterialTheme.colorScheme.surface, ComicTan)))
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(backgroundModifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = figureName,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = comicColors.content,
        )
        if (quotePreview != null) {
            Text(
                text = quotePreview,
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
                color = comicColors.content,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// region Previews

@Preview(showBackground = true)
@Composable
private fun MediaSageHeadlineCardUnreadPreview() {
    MediaSageTheme {
        MediaSageHeadlineCard(
            imageUrl = null,
            headlineTitle = "World Leaders Gather for Climate Summit in Geneva",
            grayscaleImage = false,
            onClick = {},
            source = "Reuters",
            category = "World",
            publishedAtLabel = "Jun 5, 2026",
            snippet = "Delegates from over 190 countries convene to discuss new emissions targets.",
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MediaSageHeadlineCardReadPreview() {
    MediaSageTheme {
        MediaSageHeadlineCard(
            imageUrl = null,
            headlineTitle = "Local Parish Marks Fiftieth Anniversary",
            grayscaleImage = false,
            onClick = {},
            source = "The New Life Times",
            category = "Community",
            publishedAtLabel = "Jun 4, 2026",
            figureName = "Augustine",
            figureRole = "Bishop of Hippo",
            quotePreview = "Our heart is restless until it rests in Thee.",
            isBookmarked = false,
            onBookmarkClick = {},
        )
    }
}

// endregion
