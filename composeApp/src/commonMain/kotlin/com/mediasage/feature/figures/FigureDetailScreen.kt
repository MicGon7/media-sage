package com.mediasage.feature.figures

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.mediasage.theme.MediaSageTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mediasage.ui.FigurePlaceholder
import com.mediasage.ui.MediaSageBackRow
import mediasage.composeapp.generated.resources.Res
import mediasage.composeapp.generated.resources.figure_detail_biography
import mediasage.composeapp.generated.resources.figure_detail_quotes_button
import mediasage.composeapp.generated.resources.figure_detail_wikipedia_source
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FigureDetailScreen(
    state: FigureDetailContract.UiState,
    onNavigateBack: () -> Unit = {}
) {
    var showQuotesSheet by rememberSaveable { mutableStateOf(false) }

    val figureName = (state as? FigureDetailContract.UiState.Success)?.figureName

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            MediaSageBackRow(onNavigateBack = onNavigateBack) {
                AnimatedVisibility(visible = figureName != null, enter = fadeIn()) {
                    Text(
                        text = figureName.orEmpty(),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            }

            when (state) {
                is FigureDetailContract.UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is FigureDetailContract.UiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                is FigureDetailContract.UiState.Success -> {
                    FigureDetailContent(
                        state = state,
                        onShowQuotes = { showQuotesSheet = true }
                    )

                    if (showQuotesSheet) {
                        ModalBottomSheet(onDismissRequest = { showQuotesSheet = false }) {
                            QuotesBottomSheet(quotes = state.quotes)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FigureDetailContent(
    state: FigureDetailContract.UiState.Success,
    onShowQuotes: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Hero portrait
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
        ) {
            if (state.figureImageUrl != null) {
                AsyncImage(
                    model = state.figureImageUrl,
                    contentDescription = state.figureName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    FigurePlaceholder(name = state.figureName, size = 120.dp)
                }
            }
        }

        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
            Text(
                text = state.figureName,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            if (state.figureRole.isNotBlank()) {
                Text(
                    text = state.figureRole,
                    style = MaterialTheme.typography.bodyLarge,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.primary, thickness = 1.dp)
            Spacer(modifier = Modifier.height(16.dp))

            if (!state.bio.isNullOrBlank()) {
                Text(
                    text = stringResource(Res.string.figure_detail_biography).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing * 1.5f,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = state.bio,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 24.sp,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(Res.string.figure_detail_wikipedia_source),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (state.quotes.isNotEmpty()) {
                OutlinedButton(
                    onClick = onShowQuotes,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(pluralStringResource(Res.plurals.figure_detail_quotes_button, state.quotes.size, state.quotes.size))
                }
            }
        }
    }
}

@Composable
private fun QuotesBottomSheet(quotes: List<FigureQuoteItem>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        items(quotes) { quote ->
            QuoteRow(quote = quote)
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                thickness = 0.5.dp,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

@Composable
private fun QuoteRow(quote: FigureQuoteItem) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text(
            text = "“${quote.quoteText}”",
            style = MaterialTheme.typography.bodyLarge,
            fontStyle = FontStyle.Italic,
            lineHeight = 24.sp,
        )
        if (quote.headlineTitle.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "In response to: ${quote.headlineTitle}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// region Previews

private class FigureDetailStateProvider : PreviewParameterProvider<FigureDetailContract.UiState> {
    override val values = sequenceOf(
        FigureDetailContract.UiState.Loading,
        FigureDetailContract.UiState.Success(
            figureName = "C.S. Lewis",
            figureRole = "Author & Apologist",
            figureImageUrl = null,
            bio = "Clive Staples Lewis (1898–1963) was a British writer, literary scholar, and lay theologian. " +
                "He held academic positions at both Oxford and Cambridge and is best known for his works of " +
                "fiction, including The Chronicles of Narnia and The Screwtape Letters, as well as his " +
                "Christian apologetics such as Mere Christianity and The Problem of Pain.",
            quotes = listOf(
                FigureQuoteItem("Hardships often prepare ordinary people for an extraordinary destiny.", "Schools Nationwide Integrate Compassion and Empathy Into Core Curriculum"),
                FigureQuoteItem("You are never too old to set another goal or to dream a new dream.", "Community Gardens Transform Urban Neighborhoods Across America"),
                FigureQuoteItem("We are what we believe we are.", "New Research Links Daily Gratitude Practice to Mental Health Improvements"),
            )
        ),
        FigureDetailContract.UiState.Success(
            figureName = "Dietrich Bonhoeffer",
            figureRole = "Theologian & Martyr",
            figureImageUrl = null,
            bio = null,
            quotes = listOf(
                FigureQuoteItem("Silence in the face of evil is itself evil. Not to speak is to speak.", "Bipartisan Coalition Introduces Comprehensive Poverty Relief Legislation")
            )
        ),
        FigureDetailContract.UiState.Error("Something went wrong. Please try again.")
    )
}

@Preview(showBackground = true)
@Composable
private fun FigureDetailScreenPreview(
    @PreviewParameter(FigureDetailStateProvider::class) state: FigureDetailContract.UiState
) {
    MediaSageTheme {
        FigureDetailScreen(state = state)
    }
}

// endregion
