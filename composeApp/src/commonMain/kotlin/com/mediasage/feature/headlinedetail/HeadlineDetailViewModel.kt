package com.mediasage.feature.headlinedetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediasage.domain.model.StreamEvent
import com.mediasage.domain.model.StreamField
import com.mediasage.domain.repository.EncouragementRepository
import com.mediasage.domain.repository.HeadlineRepository
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
    private val encouragementRepository: EncouragementRepository
) : ViewModel() {

    private val _state = MutableStateFlow<HeadlineDetailContract.UiState>(HeadlineDetailContract.UiState.Loading)
    val state: StateFlow<HeadlineDetailContract.UiState> = _state.asStateFlow()

    private val _sideEffects = Channel<HeadlineDetailContract.SideEffect>()
    val sideEffects = _sideEffects.receiveAsFlow()

    init {
        loadMatch()
    }

    fun onIntent(intent: HeadlineDetailContract.Intent) {
        when (intent) {
            is HeadlineDetailContract.Intent.RetryMatch -> {
                _state.value = HeadlineDetailContract.UiState.Loading
                loadMatch()
            }
        }
    }

    private fun loadMatch() {
        viewModelScope.launch {
            try {
                val headline = headlineRepository.getHeadlineByUrl(articleUrl)

                _state.value = HeadlineDetailContract.UiState.Success(
                    headlineTitle = headline?.title.orEmpty(),
                    headlineSource = headline?.source.orEmpty(),
                    headlineCategory = "",
                    headlineImageUrl = headline?.imageUrl,
                    encouragement = HeadlineDetailContract.EncouragementState.Streaming()
                )

                encouragementRepository.streamEncouragement(
                    headlineTitle = headline?.title.orEmpty(),
                    headlineSource = headline?.source.orEmpty(),
                    headlineImageUrl = headline?.imageUrl,
                    articleUrl = articleUrl
                ).collect { event ->
                    handleStreamEvent(event, headline)
                }
            } catch (e: Exception) {
                _state.value = HeadlineDetailContract.UiState.Error(e.toErrorType())
            }
        }
    }

    private fun handleStreamEvent(
        event: StreamEvent,
        headline: com.mediasage.domain.model.Headline?
    ) {
        when (event) {
            is StreamEvent.Cached -> {
                val enc = event.encouragement
                val success = _state.value as? HeadlineDetailContract.UiState.Success ?: return
                _state.value = success.copy(
                    headlineTitle = headline?.title ?: enc.headlineTitle,
                    headlineSource = headline?.source ?: enc.headlineSource,
                    headlineImageUrl = headline?.imageUrl ?: enc.headlineImageUrl,
                    encouragement = HeadlineDetailContract.EncouragementState.Loaded(
                        summary = enc.summary,
                        quoteText = enc.quoteText,
                        figureName = enc.figureName,
                        figureRole = enc.figureRole,
                        figureImageUrl = enc.figureImageUrl,
                        scriptureReference = enc.scriptureReference,
                        scriptureText = enc.scriptureText,
                        matchExplanation = enc.explanation,
                        matchTheme = enc.matchTheme,
                        tone = enc.tone,
                    )
                )
            }
            is StreamEvent.FieldDelta -> {
                val success = _state.value as? HeadlineDetailContract.UiState.Success ?: return
                val streaming = success.encouragement as? HeadlineDetailContract.EncouragementState.Streaming
                    ?: HeadlineDetailContract.EncouragementState.Streaming()
                _state.value = success.copy(encouragement = streaming.withDelta(event))
            }
            is StreamEvent.Portrait -> {
                val success = _state.value as? HeadlineDetailContract.UiState.Success ?: return
                val streaming = success.encouragement as? HeadlineDetailContract.EncouragementState.Streaming ?: return
                _state.value = success.copy(encouragement = streaming.copy(figureImageUrl = event.url))
            }
            is StreamEvent.Done -> {
                val success = _state.value as? HeadlineDetailContract.UiState.Success ?: return
                val streaming = success.encouragement as? HeadlineDetailContract.EncouragementState.Streaming ?: return
                _state.value = success.copy(
                    encouragement = HeadlineDetailContract.EncouragementState.Loaded(
                        summary = streaming.summary.ifBlank { null },
                        quoteText = streaming.quoteText,
                        figureName = streaming.figureName,
                        figureRole = streaming.figureRole,
                        figureImageUrl = streaming.figureImageUrl,
                        scriptureReference = streaming.scriptureReference,
                        scriptureText = streaming.scriptureText,
                        matchExplanation = streaming.explanation,
                        matchTheme = streaming.matchTheme,
                        tone = streaming.tone,
                    )
                )
            }
        }
    }

    private fun HeadlineDetailContract.EncouragementState.Streaming.withDelta(
        event: StreamEvent.FieldDelta
    ): HeadlineDetailContract.EncouragementState.Streaming = when (event.field) {
        StreamField.MATCH_THEME -> copy(activeField = StreamField.MATCH_THEME, matchTheme = matchTheme + event.text)
        StreamField.TONE -> copy(activeField = StreamField.TONE, tone = tone + event.text)
        StreamField.SUMMARY -> copy(activeField = StreamField.SUMMARY, summary = summary + event.text)
        StreamField.QUOTE -> copy(activeField = StreamField.QUOTE, quoteText = quoteText + event.text)
        StreamField.FIGURE_NAME -> copy(activeField = StreamField.FIGURE_NAME, figureName = figureName + event.text)
        StreamField.FIGURE_ROLE -> copy(activeField = StreamField.FIGURE_ROLE, figureRole = figureRole + event.text)
        StreamField.SCRIPTURE_REF -> copy(activeField = StreamField.SCRIPTURE_REF, scriptureReference = scriptureReference + event.text)
        StreamField.SCRIPTURE_TEXT -> copy(activeField = StreamField.SCRIPTURE_TEXT, scriptureText = scriptureText + event.text)
        StreamField.EXPLANATION -> copy(activeField = StreamField.EXPLANATION, explanation = explanation + event.text)
        StreamField.CONNECTION_THEMES -> copy(activeField = StreamField.CONNECTION_THEMES)
    }
}
