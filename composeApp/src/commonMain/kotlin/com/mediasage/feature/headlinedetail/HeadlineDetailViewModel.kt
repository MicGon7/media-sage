package com.mediasage.feature.headlinedetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediasage.domain.repository.EncouragementRepository
import com.mediasage.domain.repository.FigureRepository
import com.mediasage.domain.repository.HeadlineRepository
import com.mediasage.domain.repository.QuoteRepository
import com.mediasage.ui.toErrorType
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class HeadlineDetailViewModel(
    private val articleUrl: String,
    private val headlineRepository: HeadlineRepository,
    private val encouragementRepository: EncouragementRepository,
    private val figureRepository: FigureRepository,
    private val quoteRepository: QuoteRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<HeadlineDetailContract.UiState>(HeadlineDetailContract.UiState.Loading)
    val state: StateFlow<HeadlineDetailContract.UiState> = _state.asStateFlow()

    private val _sideEffects = Channel<HeadlineDetailContract.SideEffect>(Channel.BUFFERED)
    val sideEffects = _sideEffects.receiveAsFlow()

    init {
        loadMatch()
        observeBookmark()
    }

    fun onIntent(intent: HeadlineDetailContract.Intent) {
        when (intent) {
            is HeadlineDetailContract.Intent.RetryMatch -> {
                _state.value = HeadlineDetailContract.UiState.Loading
                loadMatch()
            }
            is HeadlineDetailContract.Intent.ToggleBookmark -> {
                viewModelScope.launch { encouragementRepository.toggleBookmark(articleUrl) }
            }
            is HeadlineDetailContract.Intent.ShowFigureProfile -> showFigureProfile()
            is HeadlineDetailContract.Intent.DismissFigureProfile -> {
                val current = _state.value as? HeadlineDetailContract.UiState.Success ?: return
                _state.value = current.copy(figureProfile = HeadlineDetailContract.FigureProfileState.Hidden)
            }
        }
    }

    private fun showFigureProfile() {
        val current = _state.value as? HeadlineDetailContract.UiState.Success ?: return
        val encouragement = current.encouragement as? HeadlineDetailContract.EncouragementState.Loaded ?: return

        viewModelScope.launch {
            val bio = runCatching { figureRepository.getFigureByName(encouragement.figureName)?.bio }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }

            val latest = _state.value as? HeadlineDetailContract.UiState.Success ?: return@launch
            _state.value = latest.copy(
                figureProfile = HeadlineDetailContract.FigureProfileState.Visible(
                    figureName = encouragement.figureName,
                    figureRole = encouragement.figureRole,
                    figureImageUrl = encouragement.figureImageUrl,
                    bio = bio,
                )
            )
        }
    }

    private fun observeBookmark() {
        viewModelScope.launch {
            encouragementRepository.observeIsBookmarked(articleUrl).collect { isBookmarked ->
                val current = _state.value
                if (current is HeadlineDetailContract.UiState.Success) {
                    _state.value = current.copy(isBookmarked = isBookmarked)
                }
            }
        }
    }

    private fun loadMatch() {
        viewModelScope.launch {
            try {
                val headline = headlineRepository.getHeadlineByUrl(articleUrl)

                val encouragement = encouragementRepository.getEncouragement(
                    headlineTitle = headline?.title ?: "",
                    headlineSource = headline?.source ?: "",
                    headlineImageUrl = headline?.imageUrl,
                    articleUrl = articleUrl,
                    articleSnippet = headline?.snippet,
                    headlineCategory = headline?.category ?: "",
                    headlinePublishedAt = headline?.publishedAt ?: 0L
                )

                _state.value = HeadlineDetailContract.UiState.Success(
                    headlineTitle = headline?.title ?: encouragement.headlineTitle,
                    headlineSource = headline?.source ?: encouragement.headlineSource,
                    headlineCategory = headline?.category ?: encouragement.headlineCategory,
                    headlineImageUrl = headline?.imageUrl ?: encouragement.headlineImageUrl,
                    encouragement = HeadlineDetailContract.EncouragementState.Loaded(
                        summary = encouragement.summary,
                        quoteText = encouragement.quoteText,
                        figureName = encouragement.figureName,
                        figureRole = encouragement.figureRole,
                        figureImageUrl = encouragement.figureImageUrl,
                        scriptureReference = encouragement.scriptureReference,
                        scriptureText = encouragement.scriptureText,
                        matchExplanation = encouragement.explanation,
                        matchTheme = encouragement.matchTheme,
                        tone = encouragement.tone,
                    )
                )

                runCatching {
                    val figure = figureRepository.getFigureByName(encouragement.figureName)
                    if (figure != null) {
                        quoteRepository.saveQuote(
                            text = encouragement.quoteText,
                            source = encouragement.scriptureReference,
                            themes = encouragement.connectionThemes,
                            figureId = figure.id,
                        )
                    }
                }
            } catch (e: Exception) {
                _state.value = HeadlineDetailContract.UiState.Error(e.toErrorType())
            }
        }
    }
}
