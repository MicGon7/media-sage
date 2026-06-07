package com.mediasage.feature.you

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediasage.data.repository.epochMillis
import com.mediasage.domain.repository.DayAssignmentRepository
import com.mediasage.domain.repository.FigureRepository
import com.mediasage.domain.repository.QuoteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

class YouViewModel(
    private val figureRepository: FigureRepository,
    private val dayAssignmentRepository: DayAssignmentRepository,
    private val quoteRepository: QuoteRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<YouContract.UiState>(YouContract.UiState.Ready())
    val state: StateFlow<YouContract.UiState> = _state.asStateFlow()

    init {
        combine(
            figureRepository.observeAllFigures(),
            dayAssignmentRepository.observeAssignments(),
            quoteRepository.observeAllQuotes(),
        ) { figures, assignments, allQuotes ->
            val current = _state.value as? YouContract.UiState.Ready ?: YouContract.UiState.Ready()
            val figuresById = figures.associateBy { it.id }
            val latestQuote = allQuotes.maxByOrNull { it.id }
            val quoteFigure = latestQuote?.let { figuresById[it.figureId] }
            current.copy(
                weekSlots = buildWeekSlots(assignments, figuresById),
                pickerFigures = figures,
                quoteCard = if (latestQuote != null && quoteFigure != null) {
                    YouContract.QuoteCard(
                        quoteText = latestQuote.text,
                        figureName = quoteFigure.name,
                        figureRole = quoteFigure.role,
                        figureImageUrl = quoteFigure.portraitUrl,
                        figureId = quoteFigure.id,
                    )
                } else null,
            )
        }.onEach { _state.value = it }.launchIn(viewModelScope)
    }

    fun onIntent(intent: YouContract.Intent) {
        val current = _state.value as? YouContract.UiState.Ready ?: return
        when (intent) {
            is YouContract.Intent.DaySlotTapped ->
                _state.value = current.copy(pickerOpenForDay = current.weekSlots[intent.index].dayOfWeek.ordinal)

            is YouContract.Intent.LensSelected ->
                _state.value = current.copy(selectedLens = intent.lens)

            is YouContract.Intent.PickerDismissed ->
                _state.value = current.copy(pickerOpenForDay = null)

            is YouContract.Intent.FigureAssigned -> viewModelScope.launch {
                dayAssignmentRepository.assign(intent.dayOfWeek, intent.figureId)
                _state.value = current.copy(pickerOpenForDay = null)
            }

            is YouContract.Intent.AssignmentCleared -> viewModelScope.launch {
                dayAssignmentRepository.clear(intent.dayOfWeek)
                _state.value = current.copy(pickerOpenForDay = null)
            }
        }
    }

    private fun buildWeekSlots(
        assignments: Map<Int, Long>,
        figuresById: Map<Long, com.mediasage.domain.model.Figure>,
    ): List<YouContract.DaySlot> {
        val today = Instant.fromEpochMilliseconds(epochMillis())
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
        val startOfWeek = today.minus(today.dayOfWeek.ordinal, DateTimeUnit.DAY)
        return (0..6).map { i ->
            val date = startOfWeek.plus(i, DateTimeUnit.DAY)
            val figure = assignments[date.dayOfWeek.ordinal]?.let { figuresById[it] }
            YouContract.DaySlot(
                dayOfWeek = date.dayOfWeek,
                isToday = date == today,
                assignedFigureName = figure?.name,
                assignedFigureImageUrl = figure?.portraitUrl,
            )
        }
    }

}
