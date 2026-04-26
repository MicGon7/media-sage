package com.mediasage.feature.match

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mediasage.ui.ErrorType
import com.mediasage.ui.FigurePlaceholder
import com.mediasage.ui.MediaSageBackRow
import io.github.alexzhirkevich.compottie.DotLottie
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.animateLottieCompositionAsState
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import mediasage.composeapp.generated.resources.Res
import mediasage.composeapp.generated.resources.match_error_generic
import mediasage.composeapp.generated.resources.match_error_network
import mediasage.composeapp.generated.resources.match_finding
import mediasage.composeapp.generated.resources.match_retry
import org.jetbrains.compose.resources.stringResource

@Composable
fun MatchScreen(
    state: MatchContract.UiState,
    onIntent: (MatchContract.Intent) -> Unit,
    onNavigateBack: () -> Unit = {}
) {
    val matchTheme = (state as? MatchContract.UiState.Success)
        ?.encouragement
        ?.let { (it as? MatchContract.EncouragementState.Loaded)?.matchTheme }

    Surface(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
        MediaSageBackRow(onNavigateBack = onNavigateBack) {
            AnimatedVisibility(
                visible = matchTheme != null,
                enter = fadeIn()
            ) {
                Text(
                    text = matchTheme.orEmpty(),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        }
        when (state) {
            is MatchContract.UiState.Loading -> FullLoadingState()
            is MatchContract.UiState.Error -> FullErrorState(
                message = when (state.errorType) {
                    ErrorType.NETWORK -> stringResource(Res.string.match_error_network)
                    ErrorType.GENERIC -> stringResource(Res.string.match_error_generic)
                },
                onRetry = { onIntent(MatchContract.Intent.RetryMatch) }
            )
            is MatchContract.UiState.Success -> MatchContent(
                state = state,
                onRetry = { onIntent(MatchContract.Intent.RetryMatch) }
            )
        }
    }
    }
}

@Composable
private fun MatchContent(
    state: MatchContract.UiState.Success,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Hero image
        if (state.headlineImageUrl != null) {
            AsyncImage(
                model = state.headlineImageUrl,
                contentDescription = state.headlineTitle,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentScale = ContentScale.Crop,
            )
        }

        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
            // Headline section — always visible immediately
            HeadlineSection(state)

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.primary, thickness = 1.dp)
            Spacer(modifier = Modifier.height(16.dp))

            // Encouragement section — progressive loading
            when (state.encouragement) {
                is MatchContract.EncouragementState.Loading -> EncouragementLoading()
                is MatchContract.EncouragementState.Error -> EncouragementError(
                    errorType = state.encouragement.errorType,
                    onRetry = onRetry
                )
                is MatchContract.EncouragementState.Loaded -> EncouragementContent(
                    encouragement = state.encouragement
                )
            }
        }
    }
}

@Composable
private fun HeadlineSection(state: MatchContract.UiState.Success) {
    if (state.headlineCategory.isNotBlank()) {
        Text(
            text = state.headlineCategory.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing * 1.5f,
        )
        Spacer(modifier = Modifier.height(4.dp))
    }

    Text(
        text = state.headlineTitle,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
    )

    Spacer(modifier = Modifier.height(4.dp))

    Text(
        text = state.headlineSource,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun EncouragementLoading() {
    val composition by rememberLottieComposition {
        LottieCompositionSpec.DotLottie(
            Res.readBytes("files/book_loader.lottie")
        )
    }
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = Int.MAX_VALUE
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
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
            text = stringResource(Res.string.match_finding),
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EncouragementError(
    errorType: ErrorType,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = when (errorType) {
                ErrorType.NETWORK -> stringResource(Res.string.match_error_network)
                ErrorType.GENERIC -> stringResource(Res.string.match_error_generic)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(onClick = onRetry) {
            Text(stringResource(Res.string.match_retry))
        }
    }
}

@Composable
private fun EncouragementContent(encouragement: MatchContract.EncouragementState.Loaded) {
    // Article summary
    if (!encouragement.summary.isNullOrBlank()) {
        Text(
            text = encouragement.summary,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 22.sp,
        )
        Spacer(modifier = Modifier.height(16.dp))
    }

    // Quote card
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "\u201C",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary,
                lineHeight = 48.sp,
            )

            Text(
                text = encouragement.quoteText,
                style = MaterialTheme.typography.headlineMedium,
                lineHeight = 36.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Text(
                text = "\u201D",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.End,
                lineHeight = 48.sp,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Figure attribution
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FigurePlaceholder(name = encouragement.figureName, size = 48.dp)

                Column {
                    Text(
                        text = encouragement.figureName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (encouragement.figureRole.isNotBlank()) {
                        Text(
                            text = encouragement.figureRole,
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)

    Spacer(modifier = Modifier.height(16.dp))

    // Scripture reference
    if (encouragement.scriptureReference.isNotBlank()) {
        Text(
            text = encouragement.scriptureReference,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        if (encouragement.scriptureText.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = encouragement.scriptureText,
                style = MaterialTheme.typography.bodyLarge,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }

    // Explanation
    if (encouragement.matchExplanation.isNotBlank()) {
        Text(
            text = encouragement.matchExplanation,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 20.sp,
        )
    }
}

@Composable
private fun FullLoadingState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        EncouragementLoading()
    }
}

@Composable
private fun FullErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(onClick = onRetry) {
            Text(stringResource(Res.string.match_retry))
        }
    }
}
