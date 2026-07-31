package com.mediasage.feature.quotes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mediasage.theme.MediaSageTheme
import com.mediasage.ui.FigurePlaceholder
import com.mediasage.ui.MediaSageBackRow
import com.mediasage.ui.MediaSageEmptyState
import com.mediasage.ui.QuoteCard
import mediasage.composeapp.generated.resources.Res
import mediasage.composeapp.generated.resources.quotes_empty_subtitle
import mediasage.composeapp.generated.resources.quotes_empty_title
import mediasage.composeapp.generated.resources.quotes_screen_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun QuotesScreen(
    state: QuotesContract.UiState,
    onIntent: (QuotesContract.Intent) -> Unit,
    onNavigateBack: () -> Unit = {},
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            MediaSageBackRow(onNavigateBack = onNavigateBack) {
                Text(
                    text = stringResource(Res.string.quotes_screen_title),
                    style = MaterialTheme.typography.titleLarge,
                )
            }

            when (state) {
                is QuotesContract.UiState.Loading -> LoadingState()
                is QuotesContract.UiState.Success -> QuotesList(
                    sections = state.sections,
                    onQuoteSelected = { figureId, quoteText ->
                        onIntent(QuotesContract.Intent.QuoteSelected(figureId, quoteText))
                    },
                )
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun QuotesList(
    sections: List<QuotesContract.FigureSection>,
    onQuoteSelected: (figureId: Long, quoteText: String) -> Unit,
) {
    if (sections.isEmpty()) {
        MediaSageEmptyState(
            title = stringResource(Res.string.quotes_empty_title),
            subtitle = stringResource(Res.string.quotes_empty_subtitle),
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        sections.forEach { section ->
            stickyHeader(key = "figure-${section.figureId}") {
                FigureSectionHeader(section = section)
            }
            items(section.quotes, key = { "${section.figureId}-${it.quoteText}" }) { quote ->
                QuoteCard(
                    quoteText = quote.quoteText,
                    isPinned = quote.isMemorized,
                    onPinQuote = { onQuoteSelected(section.figureId, quote.quoteText) },
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun FigureSectionHeader(section: QuotesContract.FigureSection) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (section.figureImageUrl != null) {
            AsyncImage(
                model = section.figureImageUrl,
                contentDescription = section.figureName,
                modifier = Modifier.size(32.dp).clip(CircleShape),
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                error = rememberVectorPainter(Icons.Filled.Person),
                fallback = rememberVectorPainter(Icons.Filled.Person),
            )
        } else {
            FigurePlaceholder(name = section.figureName, size = 32.dp)
        }
        Text(
            text = section.figureName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// region Previews

@Preview(showBackground = true)
@Composable
private fun QuotesScreenPreview(
    @PreviewParameter(QuotesStateProvider::class) state: QuotesContract.UiState
) {
    MediaSageTheme {
        QuotesScreen(state = state, onIntent = {})
    }
}

// endregion
