package com.mediasage.feature.quotes

import androidx.compose.ui.tooling.preview.PreviewParameterProvider

internal class QuotesStateProvider : PreviewParameterProvider<QuotesContract.UiState> {
    override val values = sequenceOf(
        QuotesContract.UiState.Loading,
        QuotesContract.UiState.Success(sections = emptyList()),
        QuotesContract.UiState.Success(
            sections = listOf(
                QuotesContract.FigureSection(
                    figureId = 1L,
                    figureName = "C.S. Lewis",
                    figureImageUrl = null,
                    quotes = listOf(
                        QuotesContract.QuoteItem(
                            quoteText = "You are never too old to set another goal or to dream a new dream.",
                            isMemorized = true,
                        ),
                        QuotesContract.QuoteItem(
                            quoteText = "Hardships often prepare ordinary people for an extraordinary destiny.",
                        ),
                    ),
                ),
                QuotesContract.FigureSection(
                    figureId = 2L,
                    figureName = "Julian of Norwich",
                    figureImageUrl = null,
                    quotes = listOf(
                        QuotesContract.QuoteItem(
                            quoteText = "All shall be well, and all shall be well, and all manner of thing shall be well.",
                        ),
                    ),
                ),
            )
        ),
    )
}
