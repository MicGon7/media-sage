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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mediasage.theme.ComicGradientOrientation
import com.mediasage.theme.rememberComicSurfaceColors
import mediasage.composeapp.generated.resources.Res
import mediasage.composeapp.generated.resources.bookmark_add
import mediasage.composeapp.generated.resources.bookmark_remove
import org.jetbrains.compose.resources.stringResource

private val FigurePortraitSize = 40.dp

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
    figureImageUrl: String? = null,
    isBookmarked: Boolean? = null,
    onBookmarkClick: (() -> Unit)? = null,
) {
    val metadataLine = listOf(source, publishedAtLabel).filter { it.isNotBlank() }.joinToString(" · ")
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
                .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
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
                if (snippet.isNotBlank()) {
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
                figureRole = figureRole,
                figureImageUrl = figureImageUrl,
            )
        }
    }
}

@Composable
private fun FigureMatchRow(
    figureName: String,
    figureRole: String?,
    figureImageUrl: String?,
) {
    val comicColors = rememberComicSurfaceColors(orientation = ComicGradientOrientation.Horizontal)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(comicColors.background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (figureImageUrl != null) {
            AsyncImage(
                model = figureImageUrl,
                contentDescription = figureName,
                modifier = Modifier.size(FigurePortraitSize).clip(CircleShape),
                contentScale = ContentScale.Crop,
                colorFilter = SepiaColorFilter,
            )
        } else {
            FigurePlaceholder(name = figureName, size = FigurePortraitSize)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = figureName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = comicColors.content,
            )
            if (figureRole != null) {
                Text(
                    text = figureRole,
                    style = MaterialTheme.typography.labelSmall,
                    color = comicColors.content,
                )
            }
        }
    }
}
