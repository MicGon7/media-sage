package com.mediasage.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.mediasage.theme.MediaSageTheme
import coil3.compose.AsyncImage
import com.mediasage.ui.ErrorType
import com.mediasage.ui.FigurePlaceholder
import com.mediasage.ui.HeadlineImage
import com.mediasage.ui.MediaSageErrorState
import io.github.alexzhirkevich.compottie.DotLottie
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.animateLottieCompositionAsState
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import mediasage.composeapp.generated.resources.Res
import mediasage.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun HomeScreen(
    state: HomeContract.UiState,
    onIntent: (HomeContract.Intent) -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToFigureDetail: (Long) -> Unit = {}
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        when (state) {
            is HomeContract.UiState.Loading -> HeadlinesFeedLoading(state.todayLabel)
            is HomeContract.UiState.Error -> MediaSageErrorState(
                message = when (state.errorType) {
                    ErrorType.NETWORK -> stringResource(Res.string.home_error_network)
                    ErrorType.GENERIC -> stringResource(Res.string.home_error_generic)
                },
                retryLabel = stringResource(Res.string.home_retry),
                onRetry = { onIntent(HomeContract.Intent.LoadHeadlines) }
            )

            is HomeContract.UiState.Success -> HeadlinesFeed(
                headlines = state.headlines,
                briefingCard = state.briefingCard,
                todayLabel = state.todayLabel,
                isRefreshing = state.isRefreshing,
                onRefresh = { onIntent(HomeContract.Intent.RefreshHeadlines) },
                onHeadlineClick = { onNavigateToDetail(it.articleUrl) },
                onFigureTap = onNavigateToFigureDetail
            )
        }
    }
}

@Composable
private fun HeadlinesFeedLoading(todayLabel: String) {
    val composition by rememberLottieComposition {
        LottieCompositionSpec.DotLottie(Res.readBytes("files/book_loader.lottie"))
    }
    val progress by animateLottieCompositionAsState(composition, iterations = Int.MAX_VALUE)
    var showIndicator by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(1000)
        showIndicator = true
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item { Masthead() }
        item { NewspaperDateRow(todayLabel = todayLabel) }
        if (showIndicator) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (composition != null) {
                        Image(
                            painter = rememberLottiePainter(composition = composition, progress = { progress }),
                            contentDescription = null,
                            modifier = Modifier.size(120.dp),
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary)
                        )
                    } else {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(Res.string.home_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun Masthead() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(Res.string.app_name),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(Res.string.home_tagline),
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NewspaperDateRow(todayLabel: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.primary,
            thickness = 1.dp
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = todayLabel,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.primary,
            thickness = 1.dp
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HeadlinesFeed(
    headlines: List<HeadlineItem>,
    briefingCard: HomeContract.BriefingCardState,
    todayLabel: String,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onHeadlineClick: (HeadlineItem) -> Unit,
    onFigureTap: (Long) -> Unit
) {
    val pullToRefreshState = rememberPullToRefreshState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .pullToRefresh(
                isRefreshing = isRefreshing,
                state = pullToRefreshState,
                onRefresh = onRefresh
            )
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item { Masthead() }
            item { NewspaperDateRow(todayLabel = todayLabel) }

            item {
                AnimatedContent(
                    targetState = briefingCard,
                    transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(200)) },
                    label = "briefingCard",
                    modifier = Modifier.animateContentSize(tween(300, easing = LinearEasing))
                ) { state ->
                    when (state) {
                        is HomeContract.BriefingCardState.Loading -> BriefingCardSkeleton()
                        is HomeContract.BriefingCardState.LoadingWithFigure -> BriefingCardLoadingWithFigure(state, onFigureTap)
                        is HomeContract.BriefingCardState.Ready -> BriefingCard(state, onFigureTap)
                        is HomeContract.BriefingCardState.Hidden -> Box(Modifier.fillMaxWidth())
                    }
                }
            }

            items(headlines, key = { it.id }) { headline ->
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
            isRefreshing = isRefreshing,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

@Composable
private fun BriefingCard(
    card: HomeContract.BriefingCardState.Ready,
    onFigureTap: (Long) -> Unit
) {
    val sepiaMatrix = ColorMatrix().apply {
        set(0, 0, 0.393f); set(0, 1, 0.769f); set(0, 2, 0.189f)
        set(1, 0, 0.349f); set(1, 1, 0.686f); set(1, 2, 0.168f)
        set(2, 0, 0.272f); set(2, 1, 0.534f); set(2, 2, 0.131f)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Portrait
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(MaterialTheme.shapes.small)
                .clickable { onFigureTap(card.figureId) }
        ) {
            if (card.figureImageUrl != null) {
                AsyncImage(
                    model = card.figureImageUrl,
                    contentDescription = card.figureName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    colorFilter = ColorFilter.colorMatrix(sepiaMatrix)
                )
            } else {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        FigurePlaceholder(name = card.figureName, size = 80.dp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Figure name
        Text(
            text = card.figureName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Attribution
        if (card.sources.isNotEmpty()) {
            Text(
                text = "${stringResource(Res.string.briefing_card_based_on)} ${
                    card.sources.joinToString(
                        ", "
                    )
                }",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontStyle = FontStyle.Italic
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.primary, thickness = 1.dp)
        Spacer(modifier = Modifier.height(8.dp))

        // Scripture
        Text(
            text = card.scriptureReference,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "“${card.scriptureText}”",
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Reflection
        Text(
            text = card.reflection,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
    }
}

@Composable
private fun BriefingCardSkeleton() {
    val shimmer = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(MaterialTheme.shapes.small)
                .background(shimmer)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(modifier = Modifier.width(160.dp).height(18.dp).clip(MaterialTheme.shapes.small).background(shimmer))
        Spacer(modifier = Modifier.height(6.dp))
        Box(modifier = Modifier.width(220.dp).height(12.dp).clip(MaterialTheme.shapes.small).background(shimmer))
        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.primary, thickness = 1.dp)
        Spacer(modifier = Modifier.height(10.dp))
        Box(modifier = Modifier.width(100.dp).height(12.dp).clip(MaterialTheme.shapes.small).background(shimmer))
        Spacer(modifier = Modifier.height(6.dp))
        Box(modifier = Modifier.fillMaxWidth().height(14.dp).clip(MaterialTheme.shapes.small).background(shimmer))
        Spacer(modifier = Modifier.height(4.dp))
        Box(modifier = Modifier.fillMaxWidth(0.85f).height(14.dp).clip(MaterialTheme.shapes.small).background(shimmer))
        Spacer(modifier = Modifier.height(10.dp))
        Box(modifier = Modifier.fillMaxWidth().height(14.dp).clip(MaterialTheme.shapes.small).background(shimmer))
        Spacer(modifier = Modifier.height(4.dp))
        Box(modifier = Modifier.fillMaxWidth(0.7f).height(14.dp).clip(MaterialTheme.shapes.small).background(shimmer))
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
    }
}

@Composable
private fun BriefingCardLoadingWithFigure(
    card: HomeContract.BriefingCardState.LoadingWithFigure,
    onFigureTap: (Long) -> Unit
) {
    val sepiaMatrix = ColorMatrix().apply {
        set(0, 0, 0.393f); set(0, 1, 0.769f); set(0, 2, 0.189f)
        set(1, 0, 0.349f); set(1, 1, 0.686f); set(1, 2, 0.168f)
        set(2, 0, 0.272f); set(2, 1, 0.534f); set(2, 2, 0.131f)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(MaterialTheme.shapes.small)
                .clickable { onFigureTap(card.figureId) }
        ) {
            if (card.figureImageUrl != null) {
                AsyncImage(
                    model = card.figureImageUrl,
                    contentDescription = card.figureName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    colorFilter = ColorFilter.colorMatrix(sepiaMatrix)
                )
            } else {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        FigurePlaceholder(name = card.figureName, size = 80.dp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = card.figureName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = stringResource(Res.string.briefing_card_loading).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing * 1.5f,
        )
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().height(2.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun BriefingCardLoading() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(Res.string.briefing_card_loading).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing * 1.5f,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().height(2.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
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
            modifier = Modifier.clip(MaterialTheme.shapes.small)
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


@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
private fun HeadlinesFeedPreview() {
    MediaSageTheme {
        HeadlinesFeed(
            headlines = listOf(
                HeadlineItem(
                    id = 1L,
                    articleUrl = "https://example.com/article-1",
                    title = "World Leaders Gather for Climate Summit in Geneva",
                    source = "Reuters",
                    category = "World",
                    snippet = "Delegates from over 190 countries convene to discuss new emissions targets.",
                    imageUrl = null
                ),
                HeadlineItem(
                    id = 2L,
                    articleUrl = "https://example.com/article-2",
                    title = "Markets Rally on Positive Economic Data",
                    source = "Financial Times",
                    category = "Business",
                    snippet = "Global indices rise sharply following better-than-expected jobs report.",
                    imageUrl = null
                )
            ),
            briefingCard = HomeContract.BriefingCardState.Hidden,
            todayLabel = "Wednesday, May 7, 2026",
            isRefreshing = false,
            onRefresh = {},
            onHeadlineClick = {},
            onFigureTap = {}
        )
    }
}
