package com.mediasage.feature.match

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediasage.domain.repository.EncouragementRepository
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
    private val encouragementRepository: EncouragementRepository
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
            is MatchContract.Intent.RetryMatch -> {
                _state.value = MatchContract.UiState.Loading
                loadMatch()
            }
        }
    }

    private fun loadMatch() {
        viewModelScope.launch {
            try {
                val headline = headlineRepository.getHeadlineById(headlineId)
                    ?: throw IllegalStateException("Headline not found")

                val encouragement = encouragementRepository.getEncouragement(
                    headlineTitle = headline.title,
                    articleUrl = headline.url
                )

                _state.value = MatchContract.UiState.Success(
                    headlineTitle = headline.title,
                    headlineSource = headline.source,
                    headlineCategory = "",
                    headlineImageUrl = headline.imageUrl,
                    encouragement = MatchContract.EncouragementState.Loaded(
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
            } catch (e: Exception) {
                _state.value = MatchContract.UiState.Error(e.toErrorType())
            }
        }
    }
}
