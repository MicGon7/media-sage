package com.mediasage.feature.figures

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediasage.data.repository.epochMillis
import com.mediasage.domain.repository.DayAssignmentRepository
import com.mediasage.domain.repository.EncouragementRepository
import com.mediasage.domain.repository.FigureRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

class FigureDetailViewModel(
    private val figureId: Long,
    private val figureRepository: FigureRepository,
    private val encouragementRepository: EncouragementRepository,
    private val dayAssignmentRepository: DayAssignmentRepository
) : ViewModel() {

    private val _state = MutableStateFlow<FigureDetailContract.UiState>(FigureDetailContract.UiState.Loading)
    val state: StateFlow<FigureDetailContract.UiState> = _state.asStateFlow()

    init {
        load()
    }

    fun onIntent(intent: FigureDetailContract.Intent) {
        when (intent) {
            is FigureDetailContract.Intent.PinToHome -> {
                val current = _state.value as? FigureDetailContract.UiState.Success ?: return
                val todayOrdinal = todayDayOfWeekOrdinal()
                viewModelScope.launch {
                    if (current.isPinned) {
                        dayAssignmentRepository.clear(todayOrdinal)
                    } else {
                        dayAssignmentRepository.assign(todayOrdinal, figureId)
                    }
                }
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            val figure = figureRepository.getFigureById(figureId) ?: return@launch
            combine(
                encouragementRepository.observeByFigureId(figure.id),
                dayAssignmentRepository.observeAssignments()
            ) { encouragements, assignments ->
                val todayOrdinal = todayDayOfWeekOrdinal()
                FigureDetailContract.UiState.Success(
                    figureName = figure.name,
                    figureRole = figure.role,
                    figureImageUrl = figure.portraitUrl,
                    bio = figure.bio,
                    quotes = encouragements.map { FigureQuoteItem(it.quoteText, it.headlineTitle) },
                    isPinned = assignments[todayOrdinal]?.figureId == figureId
                )
            }.collect { _state.value = it }
        }
    }

    private fun todayDayOfWeekOrdinal(): Int =
        Instant.fromEpochMilliseconds(epochMillis())
            .toLocalDateTime(TimeZone.currentSystemDefault()).date.dayOfWeek.ordinal
}
