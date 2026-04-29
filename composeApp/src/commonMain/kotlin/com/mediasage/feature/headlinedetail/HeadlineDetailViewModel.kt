package com.mediasage.feature.headlinedetail

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

                val encouragement = encouragementRepository.getEncouragement(
                    headlineTitle = headline?.title ?: "",
                    headlineSource = headline?.source ?: "",
                    headlineImageUrl = headline?.imageUrl,
                    articleUrl = articleUrl,
                    articleSnippet = headline?.snippet
                )

                _state.value = HeadlineDetailContract.UiState.Success(
                    headlineTitle = headline?.title ?: encouragement.headlineTitle,
                    headlineSource = headline?.source ?: encouragement.headlineSource,
                    headlineCategory = "",
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
            } catch (e: Exception) {
                _state.value = HeadlineDetailContract.UiState.Error(e.toErrorType())
            }
        }
    }
}
