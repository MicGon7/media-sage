package com.mediasage.feature.you

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mediasage.theme.BrandAmber
import kotlinx.datetime.LocalDate
import mediasage.composeapp.generated.resources.Res
import mediasage.composeapp.generated.resources.history_empty_day_for
import mediasage.composeapp.generated.resources.history_empty_day_subtitle
import mediasage.composeapp.generated.resources.you_day_detail_articles_label
import mediasage.composeapp.generated.resources.you_day_detail_figure_attribution
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun DayDetailSheetContent(dayDetail: ReaderContract.DayDetail) {
    LazyColumn(contentPadding = PaddingValues(bottom = 40.dp)) {
        item { DayDetailHeader(dayDetail) }
        item { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp)) }
        if (dayDetail.reflection != null) {
            item { DayDetailReflection(dayDetail.reflection) }
        } else {
            item { DayDetailEmptyState(dayDetail.epochDay) }
        }
        if (dayDetail.articles.isNotEmpty()) {
            item {
                SectionLabel(
                    text = stringResource(Res.string.you_day_detail_articles_label),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            items(dayDetail.articles, key = { it.articleUrl.ifEmpty { it.headlineTitle } }) { article ->
                DayDetailArticleRow(article)
            }
        }
    }
}

@Composable
private fun DayDetailHeader(dayDetail: ReaderContract.DayDetail) {
    val dateText = remember(dayDetail.epochDay) {
        val date = LocalDate.fromEpochDays(dayDetail.epochDay.toInt())
        val day = date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
        val month = date.month.name.lowercase().replaceFirstChar { it.uppercase() }
        "$day, $month ${date.dayOfMonth}"
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dayDetail.figureImageUrl != null) {
            AsyncImage(
                model = dayDetail.figureImageUrl,
                contentDescription = dayDetail.figureName,
                modifier = Modifier.size(40.dp).clip(CircleShape),
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column {
            Text(
                text = dateText,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (dayDetail.figureName != null) {
                Text(
                    text = dayDetail.figureName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DayDetailReflection(reflection: ReaderContract.ReflectionSummary) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = reflection.scriptureReference,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "“${reflection.scriptureText}”",
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(reflection.insight, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(reflection.implication, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(reflection.inspiration, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DayDetailEmptyState(epochDay: Long) {
    val dateText = remember(epochDay) {
        val date = LocalDate.fromEpochDays(epochDay.toInt())
        val day = date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
        val month = date.month.name.lowercase().replaceFirstChar { it.uppercase() }
        "$day, $month ${date.dayOfMonth}"
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(Res.string.history_empty_day_for, dateText),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(Res.string.history_empty_day_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun DayDetailArticleRow(article: ReaderContract.ArticleItem) {
    val quotePreview = if (article.quoteText.length > 100) {
        article.quoteText.take(100) + "…"
    } else {
        article.quoteText
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = article.headlineTitle,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = "\u201C$quotePreview\u201D",
            style = MaterialTheme.typography.bodySmall.copy(letterSpacing = 0.sp),
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            text = stringResource(Res.string.you_day_detail_figure_attribution, article.figureName),
            style = MaterialTheme.typography.labelSmall,
            color = BrandAmber,
            modifier = Modifier.padding(top = 2.dp),
        )
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    }
}
