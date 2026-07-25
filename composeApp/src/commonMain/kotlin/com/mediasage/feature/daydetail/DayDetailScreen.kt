package com.mediasage.feature.daydetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import coil3.compose.AsyncImage
import com.mediasage.theme.BrandAmber
import kotlinx.datetime.LocalDate
import mediasage.composeapp.generated.resources.Res
import mediasage.composeapp.generated.resources.briefing_card_evening
import mediasage.composeapp.generated.resources.briefing_card_morning
import mediasage.composeapp.generated.resources.history_empty_day_for
import mediasage.composeapp.generated.resources.history_empty_day_subtitle
import mediasage.composeapp.generated.resources.history_implication_label
import mediasage.composeapp.generated.resources.history_insight_label
import mediasage.composeapp.generated.resources.history_inspiration_label
import mediasage.composeapp.generated.resources.history_scripture_label
import mediasage.composeapp.generated.resources.history_tab_briefing
import mediasage.composeapp.generated.resources.you_day_detail_articles_tab_count
import mediasage.composeapp.generated.resources.you_day_detail_figure_attribution
import mediasage.composeapp.generated.resources.you_day_detail_no_articles
import org.jetbrains.compose.resources.stringResource
import com.mediasage.feature.you.SectionLabel
import com.mediasage.ui.MediaSageBackRow

private const val TONE_MORNING = "morning"

@Composable
fun DayDetailScreen(
    state: DayDetailContract.UiState,
    onIntent: (DayDetailContract.Intent) -> Unit,
    onNavigateBack: () -> Unit = {},
) {
    val ready = state as? DayDetailContract.UiState.Ready ?: return
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            MediaSageBackRow(onNavigateBack = onNavigateBack) {
                DayDetailHeader(epochDay = ready.epochDay, figureName = ready.figureName, figureImageUrl = ready.figureImageUrl)
            }
            DayDetailTabRow(selectedTab = ready.selectedTab, articleCount = ready.articles.size, onIntent = onIntent)
            when (ready.selectedTab) {
                DayDetailContract.Tab.BRIEFINGS -> BriefingsTabContent(epochDay = ready.epochDay, reflections = ready.reflections)
                DayDetailContract.Tab.ARTICLES -> ArticlesTabContent(articles = ready.articles)
            }
        }
    }
}

private fun formatEpochDay(epochDay: Long): String {
    val date = LocalDate.fromEpochDays(epochDay.toInt())
    val day = date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
    val month = date.month.name.lowercase().replaceFirstChar { it.uppercase() }
    return "$day, $month ${date.dayOfMonth}"
}

@Composable
private fun DayDetailHeader(epochDay: Long, figureName: String?, figureImageUrl: String?) {
    val dateText = remember(epochDay) { formatEpochDay(epochDay) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (figureImageUrl != null) {
            AsyncImage(
                model = figureImageUrl,
                contentDescription = figureName,
                modifier = Modifier.size(36.dp).clip(CircleShape),
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column {
            Text(text = dateText, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (figureName != null) {
                Text(text = figureName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DayDetailTabRow(
    selectedTab: DayDetailContract.Tab,
    articleCount: Int,
    onIntent: (DayDetailContract.Intent) -> Unit,
) {
    val selectedIndex = DayDetailContract.Tab.entries.indexOf(selectedTab)
    TabRow(selectedTabIndex = selectedIndex) {
        Tab(
            selected = selectedTab == DayDetailContract.Tab.BRIEFINGS,
            onClick = { onIntent(DayDetailContract.Intent.TabSelected(DayDetailContract.Tab.BRIEFINGS)) },
            text = { Text(stringResource(Res.string.history_tab_briefing)) },
        )
        Tab(
            selected = selectedTab == DayDetailContract.Tab.ARTICLES,
            onClick = { onIntent(DayDetailContract.Intent.TabSelected(DayDetailContract.Tab.ARTICLES)) },
            text = { Text(stringResource(Res.string.you_day_detail_articles_tab_count, articleCount)) },
        )
    }
}

@Composable
private fun BriefingsTabContent(epochDay: Long, reflections: List<DayDetailContract.ReflectionSummary>) {
    if (reflections.isEmpty()) {
        BriefingsEmptyState(epochDay)
        return
    }
    val pagerState = rememberPagerState { reflections.size }
    Column(modifier = Modifier.fillMaxSize().padding(top = 16.dp)) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 32.dp),
            pageSpacing = 12.dp,
        ) { page ->
            ReflectionCard(reflections[page])
        }
        if (reflections.size > 1) {
            PageIndicator(pageCount = reflections.size, currentPage = pagerState.currentPage)
        }
    }
}

@Composable
private fun PageIndicator(pageCount: Int, currentPage: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(pageCount) { index ->
            val color = if (index == currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
            Spacer(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .then(Modifier.background(color)),
            )
        }
    }
}

@Composable
private fun ReflectionCard(reflection: DayDetailContract.ReflectionSummary) {
    val toneLabel = if (reflection.tone == TONE_MORNING) Res.string.briefing_card_morning else Res.string.briefing_card_evening
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(
            text = stringResource(toneLabel),
            style = MaterialTheme.typography.labelSmall,
            color = BrandAmber,
        )
        Spacer(modifier = Modifier.height(8.dp))
        LabeledSection(stringResource(Res.string.history_scripture_label), reflection.scriptureReference)
        Text(
            text = "“${reflection.scriptureText}”",
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        LabeledSection(stringResource(Res.string.history_insight_label), reflection.insight)
        LabeledSection(stringResource(Res.string.history_implication_label), reflection.implication)
        LabeledSection(stringResource(Res.string.history_inspiration_label), reflection.inspiration)
    }
}

@Composable
private fun LabeledSection(label: String, body: String) {
    Column(modifier = Modifier.padding(bottom = 12.dp)) {
        SectionLabel(text = label)
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun BriefingsEmptyState(epochDay: Long) {
    val dateText = remember(epochDay) { formatEpochDay(epochDay) }
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(Res.string.history_empty_day_for, dateText),
            style = MaterialTheme.typography.titleSmall,
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
private fun ArticlesTabContent(articles: List<DayDetailContract.ArticleItem>) {
    if (articles.isEmpty()) {
        Text(
            text = stringResource(Res.string.you_day_detail_no_articles),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(32.dp),
        )
        return
    }
    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        items(articles, key = { it.articleUrl.ifEmpty { it.headlineTitle } }) { article ->
            ArticleRow(article)
        }
    }
}

@Composable
private fun ArticleRow(article: DayDetailContract.ArticleItem) {
    val quotePreview = if (article.quoteText.length > 100) article.quoteText.take(100) + "…" else article.quoteText
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(text = article.headlineTitle, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Text(
            text = "“$quotePreview”",
            style = MaterialTheme.typography.bodySmall,
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
