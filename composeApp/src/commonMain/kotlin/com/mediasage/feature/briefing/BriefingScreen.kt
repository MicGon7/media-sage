package com.mediasage.feature.briefing

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mediasage.theme.MediaSageTheme
import com.mediasage.ui.ErrorType
import com.mediasage.ui.FigurePlaceholder
import com.mediasage.ui.MediaSageBriefingCard
import com.mediasage.ui.MediaSageDateDivider
import com.mediasage.ui.MediaSageErrorDialog
import com.mediasage.ui.ReflectionSheet
import com.mediasage.ui.SepiaColorFilter
import com.mediasage.ui.ThemeChip
import mediasage.composeapp.generated.resources.Res
import mediasage.composeapp.generated.resources.app_name
import mediasage.composeapp.generated.resources.briefing_card_loading
import mediasage.composeapp.generated.resources.home_error_generic
import mediasage.composeapp.generated.resources.home_error_network
import mediasage.composeapp.generated.resources.home_retry
import mediasage.composeapp.generated.resources.home_tagline
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BriefingScreen(
    state: BriefingContract.UiState,
    onIntent: (BriefingContract.Intent) -> Unit,
    onNavigateToFigureDetail: (Long) -> Unit = {}
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        when (state) {
            is BriefingContract.UiState.Loading -> BriefingLoading(state.todayLabel)
            is BriefingContract.UiState.Error -> {
                MediaSageErrorDialog(
                    message = when (state.errorType) {
                        ErrorType.NETWORK -> stringResource(Res.string.home_error_network)
                        ErrorType.GENERIC -> stringResource(Res.string.home_error_generic)
                    },
                    retryLabel = stringResource(Res.string.home_retry),
                    onRetry = { onIntent(BriefingContract.Intent.Retry) }
                )
            }
            is BriefingContract.UiState.Success -> BriefingContent(
                todayLabel = state.todayLabel,
                card = state.card,
                onFigureTap = onNavigateToFigureDetail,
                onReflectTapped = { onIntent(BriefingContract.Intent.ReflectTapped) }
            )
        }
    }
    if (state is BriefingContract.UiState.Success) {
        val sheet = state.reflectSheet
        if (sheet != null) {
            ReflectionSheet(
                challenge = sheet.challenge,
                noteText = sheet.noteText,
                editable = sheet.editable,
                hasUnsavedChanges = sheet.noteText != sheet.savedNoteText,
                onNoteChange = { onIntent(BriefingContract.Intent.ReflectNoteChanged(it)) },
                onSave = { onIntent(BriefingContract.Intent.ReflectNoteSaved) },
                onDismiss = { onIntent(BriefingContract.Intent.ReflectDismissed) },
            )
        }
    }
}

@Composable
private fun BriefingLoading(todayLabel: String) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item { Masthead() }
        item { MediaSageDateDivider(dateLabel = todayLabel) }
    }
}

@Composable
private fun BriefingContent(
    todayLabel: String,
    card: BriefingContract.CardState,
    onFigureTap: (Long) -> Unit,
    onReflectTapped: () -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item { Masthead() }
        item { MediaSageDateDivider(dateLabel = todayLabel) }
        item {
            AnimatedContent(
                targetState = card,
                transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(200)) },
                label = "briefingCard",
                modifier = Modifier.animateContentSize(tween(300, easing = LinearEasing))
            ) { cardState ->
                when (cardState) {
                    is BriefingContract.CardState.Loading -> BriefingCardSkeleton()
                    is BriefingContract.CardState.LoadingWithFigure -> BriefingCardLoadingWithFigure(cardState, onFigureTap)
                    is BriefingContract.CardState.Ready -> BriefingCard(cardState, onFigureTap, onReflectTapped)
                    is BriefingContract.CardState.Hidden -> Box(Modifier.fillMaxWidth())
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
private fun BriefingCard(
    card: BriefingContract.CardState.Ready,
    onFigureTap: (Long) -> Unit,
    onReflectTapped: () -> Unit
) {
    MediaSageBriefingCard(
        figureName = card.figureName,
        figureImageUrl = card.figureImageUrl,
        scriptureReference = card.scriptureReference,
        scriptureText = card.scriptureText,
        insight = card.insight,
        implication = card.implication,
        inspiration = card.inspiration,
        onFigureTap = { onFigureTap(card.figureId) },
        theme = card.theme,
        sources = card.sources,
        onReflectClick = card.challenge?.let { { onReflectTapped() } },
    )
}

@Composable
private fun BriefingCardLoadingWithFigure(
    card: BriefingContract.CardState.LoadingWithFigure,
    onFigureTap: (Long) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .clip(MaterialTheme.shapes.small)
                .clickable { onFigureTap(card.figureId) }
        ) {
            if (card.figureImageUrl != null) {
                AsyncImage(
                    model = card.figureImageUrl,
                    contentDescription = card.figureName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    colorFilter = SepiaColorFilter
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

        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(
                text = card.figureName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (card.theme != null) {
                Spacer(modifier = Modifier.width(8.dp))
                ThemeChip(theme = card.theme)
            }
        }
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
private fun BriefingCardSkeleton() {
    val infiniteTransition = rememberInfiniteTransition(label = "skeleton")
    val shimmerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.06f,
        targetValue = 0.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeletonPulse"
    )
    val shimmer = MaterialTheme.colorScheme.onSurface.copy(alpha = shimmerAlpha)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(300.dp).clip(MaterialTheme.shapes.small).background(shimmer))
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

// region Previews

@Preview(showBackground = true)
@Composable
private fun BriefingScreenPreview(
    @PreviewParameter(BriefingUiStateProvider::class) state: BriefingContract.UiState
) {
    MediaSageTheme {
        BriefingScreen(state = state, onIntent = {})
    }
}

// endregion
