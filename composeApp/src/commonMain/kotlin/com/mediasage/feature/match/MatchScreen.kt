package com.mediasage.feature.match

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediasage.theme.LoraFamily
import com.mediasage.ui.FigurePlaceholder
import mediasage.composeapp.generated.resources.Res
import mediasage.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun MatchScreen(
    headlineId: Long,
    state: MatchContract.UiState,
    onIntent: (MatchContract.Intent) -> Unit
) {
    when (state) {
        is MatchContract.UiState.Loading -> LoadingState()
        is MatchContract.UiState.Error -> ErrorState(
            message = state.message,
            onRetry = { onIntent(MatchContract.Intent.RetryMatch) }
        )
        is MatchContract.UiState.Success -> MatchContent(state = state)
    }
}

@Composable
private fun MatchContent(state: MatchContract.UiState.Success) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        // Headline section
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

        Spacer(modifier = Modifier.height(16.dp))

        // Divider
        HorizontalDivider(
            color = MaterialTheme.colorScheme.primary,
            thickness = 1.dp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Quote card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = Color.Transparent,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Pull quote
                Text(
                    text = "\u201C",
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.primary,
                    lineHeight = 48.sp,
                )

                Text(
                    text = state.quoteText,
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
                    FigurePlaceholder(
                        name = state.figureName,
                        size = 48.dp
                    )

                    Column {
                        Text(
                            text = state.figureName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (state.figureRole.isNotBlank()) {
                            Text(
                                text = state.figureRole,
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

        // Divider
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            thickness = 0.5.dp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Scripture reference
        if (state.scriptureReference.isNotBlank()) {
            Text(
                text = state.scriptureReference,
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Explanation
        if (state.matchExplanation.isNotBlank()) {
            Text(
                text = state.matchExplanation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(Res.string.match_finding),
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorState(
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
