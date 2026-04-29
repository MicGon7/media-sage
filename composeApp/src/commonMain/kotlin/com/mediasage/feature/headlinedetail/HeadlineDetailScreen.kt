package com.mediasage.feature.headlinedetail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mediasage.domain.model.StreamField
import com.mediasage.ui.ErrorType
import com.mediasage.ui.FigurePlaceholder
import com.mediasage.ui.MediaSageBackRow
import mediasage.composeapp.generated.resources.Res
import mediasage.composeapp.generated.resources.match_error_generic
import mediasage.composeapp.generated.resources.match_error_network
import mediasage.composeapp.generated.resources.match_retry
import org.jetbrains.compose.resources.stringResource

@Composable
fun HeadlineDetailScreen(
    state: HeadlineDetailContract.UiState,
    onIntent: (HeadlineDetailContract.Intent) -> Unit,
    onNavigateBack: () -> Unit = {}
) {
    val matchTheme = (state as? HeadlineDetailContract.UiState.Success)
        ?.encouragement
        ?.let {
            when (it) {
                is HeadlineDetailContract.EncouragementState.Loaded -> it.matchTheme
                is HeadlineDetailContract.EncouragementState.Streaming -> it.matchTheme.ifBlank { null }
                else -> null
            }
        }

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
            is HeadlineDetailContract.UiState.Loading -> { /* Room is fast — imperceptible */ }
            is HeadlineDetailContract.UiState.Error -> FullErrorState(
                message = when (state.errorType) {
                    ErrorType.NETWORK -> stringResource(Res.string.match_error_network)
                    ErrorType.GENERIC -> stringResource(Res.string.match_error_generic)
                },
                onRetry = { onIntent(HeadlineDetailContract.Intent.RetryMatch) }
            )
            is HeadlineDetailContract.UiState.Success -> HeadlineDetailContent(
                state = state,
                onRetry = { onIntent(HeadlineDetailContract.Intent.RetryMatch) }
            )
        }
    }
    }
}

@Composable
private fun HeadlineDetailContent(
    state: HeadlineDetailContract.UiState.Success,
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
                is HeadlineDetailContract.EncouragementState.Streaming -> EncouragementStreaming(
                    streaming = state.encouragement
                )
                is HeadlineDetailContract.EncouragementState.Error -> EncouragementError(
                    errorType = state.encouragement.errorType,
                    onRetry = onRetry
                )
                is HeadlineDetailContract.EncouragementState.Loaded -> EncouragementContent(
                    encouragement = state.encouragement
                )
            }
        }
    }
}

@Composable
private fun HeadlineSection(state: HeadlineDetailContract.UiState.Success) {
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
private fun ShimmerSkeleton(modifier: Modifier = Modifier, width: Dp = Dp.Unspecified) {
    val transition = rememberInfiniteTransition()
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse)
    )
    Box(
        modifier = modifier
            .then(if (width != Dp.Unspecified) Modifier.width(width) else Modifier.fillMaxWidth())
            .height(16.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
    )
}

@Composable
private fun EncouragementStreaming(streaming: HeadlineDetailContract.EncouragementState.Streaming) {
    val active = streaming.activeField.ordinal

    fun started(field: StreamField) = active >= field.ordinal

    // Summary
    if (!started(StreamField.SUMMARY)) {
        repeat(2) {
            ShimmerSkeleton()
            Spacer(Modifier.height(4.dp))
        }
        ShimmerSkeleton(width = 220.dp)
        Spacer(Modifier.height(16.dp))
    } else if (streaming.summary.isNotBlank()) {
        Text(
            text = streaming.summary,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 22.sp,
        )
        Spacer(Modifier.height(16.dp))
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
                text = "“",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary,
                lineHeight = 48.sp,
            )
            if (!started(StreamField.QUOTE)) {
                repeat(3) {
                    ShimmerSkeleton()
                    Spacer(Modifier.height(4.dp))
                }
                ShimmerSkeleton(width = 180.dp)
            } else {
                Text(
                    text = streaming.quoteText,
                    style = MaterialTheme.typography.headlineMedium,
                    lineHeight = 36.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
            Text(
                text = "”",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.End,
                lineHeight = 48.sp,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val figureStarted = started(StreamField.FIGURE_NAME)
                if (streaming.figureImageUrl != null) {
                    AsyncImage(
                        model = streaming.figureImageUrl,
                        contentDescription = streaming.figureName,
                        modifier = Modifier.size(48.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter,
                    )
                } else if (figureStarted && streaming.figureName.isNotBlank()) {
                    FigurePlaceholder(name = streaming.figureName, size = 48.dp)
                } else {
                    Box(
                        modifier = Modifier.size(48.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    )
                }
                Column {
                    if (!figureStarted) {
                        ShimmerSkeleton(width = 150.dp)
                        Spacer(Modifier.height(4.dp))
                        ShimmerSkeleton(width = 100.dp)
                    } else {
                        if (streaming.figureName.isNotBlank()) {
                            Text(
                                text = streaming.figureName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        if (streaming.figureRole.isNotBlank()) {
                            Text(
                                text = streaming.figureRole,
                                style = MaterialTheme.typography.bodyMedium,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else if (!started(StreamField.FIGURE_ROLE)) {
                            ShimmerSkeleton(width = 100.dp)
                        }
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(16.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
    Spacer(Modifier.height(16.dp))

    // Scripture
    if (!started(StreamField.SCRIPTURE_REF)) {
        ShimmerSkeleton(width = 120.dp)
        Spacer(Modifier.height(8.dp))
        repeat(2) {
            ShimmerSkeleton()
            Spacer(Modifier.height(4.dp))
        }
        ShimmerSkeleton(width = 200.dp)
    } else {
        if (streaming.scriptureReference.isNotBlank()) {
            Text(
                text = streaming.scriptureReference,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (streaming.scriptureText.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = streaming.scriptureText,
                style = MaterialTheme.typography.bodyLarge,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp,
            )
        } else if (!started(StreamField.SCRIPTURE_TEXT)) {
            Spacer(Modifier.height(8.dp))
            repeat(2) {
                ShimmerSkeleton()
                Spacer(Modifier.height(4.dp))
            }
        }
    }

    Spacer(Modifier.height(16.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
    Spacer(Modifier.height(16.dp))

    // Explanation
    if (!started(StreamField.EXPLANATION)) {
        repeat(3) {
            ShimmerSkeleton()
            Spacer(Modifier.height(4.dp))
        }
    } else if (streaming.explanation.isNotBlank()) {
        Text(
            text = streaming.explanation,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 20.sp,
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
private fun EncouragementContent(encouragement: HeadlineDetailContract.EncouragementState.Loaded) {
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
                text = "“",
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
                text = "”",
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
                if (encouragement.figureImageUrl != null) {
                    AsyncImage(
                        model = encouragement.figureImageUrl,
                        contentDescription = encouragement.figureName,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter,
                        error = rememberVectorPainter(Icons.Default.Person),
                        fallback = rememberVectorPainter(Icons.Default.Person),
                    )
                } else {
                    FigurePlaceholder(name = encouragement.figureName, size = 48.dp)
                }

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
