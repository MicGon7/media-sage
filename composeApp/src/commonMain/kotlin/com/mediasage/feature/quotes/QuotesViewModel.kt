package com.mediasage.feature.quotes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediasage.domain.model.Figure
import com.mediasage.domain.model.Quote
import com.mediasage.domain.repository.FigureRepository
import com.mediasage.domain.repository.QuoteRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Reference implementation of the Now in Android reactive state-holder pattern for UI state
 * derived purely from live repository streams — there is no user-owned selection to combine in
 * (see composeApp/CLAUDE.md, "State-holder pattern").
 */
class QuotesViewModel(
    private val quoteRepository: QuoteRepository,
    private val figureRepository: FigureRepository,
) : ViewModel() {

    val state: StateFlow<QuotesContract.UiState> = combine(
        quoteRepository.observeAllQuotes(),
        figureRepository.observeAllFigures(),
        quoteRepository.observeMemorizedQuote(),
    ) { quotes, figures, memorizedQuote ->
        buildSuccess(quotes, figures, memorizedQuote)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = QuotesContract.UiState.Loading,
    )

    fun onIntent(intent: QuotesContract.Intent) {
        when (intent) {
            is QuotesContract.Intent.QuoteSelected -> viewModelScope.launch {
                quoteRepository.memorizeQuote(intent.figureId, intent.quoteText)
            }
        }
    }

    private fun buildSuccess(
        quotes: List<Quote>,
        figures: List<Figure>,
        memorizedQuote: Quote?,
    ): QuotesContract.UiState.Success {
        val figuresById = figures.associateBy { it.id }
        val sections = quotes
            .groupBy { it.figureId }
            .mapNotNull { (figureId, figureQuotes) -> toSection(figureId, figureQuotes, figuresById, memorizedQuote) }
            .sortedBy { it.figureName }
        return QuotesContract.UiState.Success(sections)
    }

    private fun toSection(
        figureId: Long,
        figureQuotes: List<Quote>,
        figuresById: Map<Long, Figure>,
        memorizedQuote: Quote?,
    ): QuotesContract.FigureSection? {
        val figure = figuresById[figureId] ?: return null
        return QuotesContract.FigureSection(
            figureId = figureId,
            figureName = figure.name,
            figureImageUrl = figure.portraitUrl,
            quotes = figureQuotes.map { quote ->
                QuotesContract.QuoteItem(
                    quoteText = quote.text,
                    isMemorized = memorizedQuote?.figureId == figureId && memorizedQuote.text == quote.text,
                )
            },
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
