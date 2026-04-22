package com.mediasage.feature.match

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediasage.data.remote.EncourageRequestDto
import com.mediasage.data.remote.MediaSageApi
import com.mediasage.domain.repository.HeadlineRepository
import com.mediasage.ui.toErrorType
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class MatchViewModel(
    private val headlineId: Long,
    private val headlineRepository: HeadlineRepository,
    private val api: MediaSageApi
) : ViewModel() {

    private val _state = MutableStateFlow<MatchContract.UiState>(MatchContract.UiState.Loading)
    val state: StateFlow<MatchContract.UiState> = _state.asStateFlow()

    private val _sideEffects = Channel<MatchContract.SideEffect>()
    val sideEffects = _sideEffects.receiveAsFlow()

    init {
        loadMatch()
    }

    fun onIntent(intent: MatchContract.Intent) {
        when (intent) {
            is MatchContract.Intent.RetryMatch -> retryMatch()
        }
    }

    private fun loadMatch() {
        _state.value = MatchContract.UiState.Loading

        viewModelScope.launch {
            try {
                val headline = headlineRepository.getHeadlineById(headlineId)
                    ?: throw IllegalStateException("Headline not found")

                val result = api.encourage(
                    EncourageRequestDto(headlineTitle = headline.title)
                )

                _state.value = MatchContract.UiState.Success(
                    headlineTitle = headline.title,
                    headlineSource = headline.source,
                    headlineCategory = "",
                    headlineImageUrl = headline.imageUrl,
                    summary = result.summary,
                    quoteText = result.quoteText,
                    figureName = result.figureName,
                    figureRole = result.figureRole,
                    scriptureReference = result.scriptureReference,
                    scriptureText = result.scriptureText,
                    matchExplanation = result.explanation,
                    matchTheme = result.matchTheme,
                    tone = result.tone,
                )
            } catch (e: Exception) {
                _state.value = MatchContract.UiState.Error(e.toErrorType())
            }
        }
    }

    private fun retryMatch() {
        loadMatch()
    }
}
