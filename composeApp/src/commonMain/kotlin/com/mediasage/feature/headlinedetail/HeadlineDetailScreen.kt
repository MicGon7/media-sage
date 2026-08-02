package com.mediasage.feature.headlinedetail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
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
import com.mediasage.ui.MediaSageBottomSheet
import com.mediasage.ui.MediaSageErrorDialog
import com.mediasage.ui.SepiaColorFilter
import io.github.alexzhirkevich.compottie.DotLottie
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.animateLottieCompositionAsState
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import mediasage.composeapp.generated.resources.Res
import mediasage.composeapp.generated.resources.bookmark_add
import mediasage.composeapp.generated.resources.bookmark_remove
import mediasage.composeapp.generated.resources.match_error_generic
import mediasage.composeapp.generated.resources.match_error_network
import mediasage.composeapp.generated.resources.match_finding
import mediasage.composeapp.generated.resources.match_retry
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeadlineDetailScreen(
    state: HeadlineDetailContract.UiState,
    onIntent: (HeadlineDetailContract.Intent) -> Unit,
    onNavigateBack: () -> Unit = {}
) {
    val successState = state as? HeadlineDetailContract.UiState.Success
    val matchTheme = successState?.encouragement
        ?.let { (it as? HeadlineDetailContract.EncouragementState.Loaded)?.matchTheme }
    val isEncouragementLoaded = successState?.encouragement is HeadlineDetailContract.EncouragementState.Loaded
    val isBookmarked = successState?.isBookmarked ?: false

    Surface(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
        MediaSageBackRow(onNavigateBack = onNavigateBack) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnimatedVisibility(
                    visible = matchTheme != null,
                    enter = fadeIn()
                ) {
                    Text(
                        text = matchTheme.orEmpty(),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                if (isEncouragementLoaded) {
                    IconButton(onClick = { onIntent(HeadlineDetailContract.Intent.ToggleBookmark) }) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                            contentDescription = stringResource(
                                if (isBookmarked) Res.string.bookmark_remove else Res.string.bookmark_add
                            ),
                            tint = if (isBookmarked) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
        when (state) {
            is HeadlineDetailContract.UiState.Loading -> FullLoadingState()
            is HeadlineDetailContract.UiState.Error -> {
                Box(modifier = Modifier.fillMaxSize())
                MediaSageErrorDialog(
                    message = when (state.errorType) {
                        ErrorType.NETWORK -> stringResource(Res.string.match_error_network)
                        ErrorType.GENERIC -> stringResource(Res.string.match_error_generic)
                    },
                    retryLabel = stringResource(Res.string.match_retry),
                    onRetry = { onIntent(HeadlineDetailContract.Intent.RetryMatch) }
                )
            }
            is HeadlineDetailContract.UiState.Success -> HeadlineDetailContent(
                state = state,
                onRetry = { onIntent(HeadlineDetailContract.Intent.RetryMatch) },
                onFigureTap = { onIntent(HeadlineDetailContract.Intent.ShowFigureProfile) }
            )
        }
    }
    }

    val figureProfile = successState?.figureProfile
    if (figureProfile is HeadlineDetailContract.FigureProfileState.Visible) {
        MediaSageBottomSheet(
            onDismissRequest = { onIntent(HeadlineDetailContract.Intent.DismissFigureProfile) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            FigureProfileSheetContent(profile = figureProfile)
        }
    }
}

@Composable
private fun HeadlineDetailContent(
    state: HeadlineDetailContract.UiState.Success,
    onRetry: () -> Unit,
    onFigureTap: () -> Unit
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
                colorFilter = SepiaColorFilter
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
                is HeadlineDetailContract.EncouragementState.Loading -> EncouragementLoading()
                is HeadlineDetailContract.EncouragementState.Error -> EncouragementError(
                    errorType = state.encouragement.errorType,
                    onRetry = onRetry
                )
                is HeadlineDetailContract.EncouragementState.Loaded -> EncouragementContent(
                    encouragement = state.encouragement,
                    onFigureTap = onFigureTap
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
private fun EncouragementContent(
    encouragement: HeadlineDetailContract.EncouragementState.Loaded,
    onFigureTap: () -> Unit
) {
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
                modifier = Modifier.clickable(onClick = onFigureTap),
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
private fun FigureProfileSheetContent(profile: HeadlineDetailContract.FigureProfileState.Visible) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .clip(MaterialTheme.shapes.medium)
        ) {
            if (profile.figureImageUrl != null) {
                AsyncImage(
                    model = profile.figureImageUrl,
                    contentDescription = profile.figureName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    error = rememberVectorPainter(Icons.Default.Person),
                    fallback = rememberVectorPainter(Icons.Default.Person),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    FigurePlaceholder(name = profile.figureName, size = 96.dp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = profile.figureName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        if (profile.figureRole.isNotBlank()) {
            Text(
                text = profile.figureRole,
                style = MaterialTheme.typography.bodyLarge,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        if (!profile.bio.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = profile.bio,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 24.sp,
            )
        }
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

