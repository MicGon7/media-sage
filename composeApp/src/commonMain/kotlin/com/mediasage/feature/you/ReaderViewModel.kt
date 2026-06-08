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

class ReaderViewModel(
    private val figureRepository: FigureRepository,
    private val dayAssignmentRepository: DayAssignmentRepository,
    private val quoteRepository: QuoteRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<ReaderContract.UiState>(ReaderContract.UiState.Ready())
    val state: StateFlow<ReaderContract.UiState> = _state.asStateFlow()

    init {
        combine(
            figureRepository.observeAllFigures(),
            dayAssignmentRepository.observeAssignments(),
            quoteRepository.observeAllQuotes(),
        ) { figures, assignments, allQuotes ->
            val current = _state.value as? ReaderContract.UiState.Ready ?: ReaderContract.UiState.Ready()
            val figuresById = figures.associateBy { it.id }
            val latestQuote = allQuotes.maxByOrNull { it.id }
            val quoteFigure = latestQuote?.let { figuresById[it.figureId] }
            current.copy(
                weekSlots = buildWeekSlots(assignments, figuresById),
                pickerFigures = figures,
                quoteCard = if (latestQuote != null && quoteFigure != null) {
                    ReaderContract.QuoteCard(
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

    fun onIntent(intent: ReaderContract.Intent) {
        val current = _state.value as? ReaderContract.UiState.Ready ?: return
        when (intent) {
            is ReaderContract.Intent.DaySlotTapped ->
                _state.value = current.copy(pickerOpenForDay = current.weekSlots[intent.index].dayOfWeek.ordinal)

            is ReaderContract.Intent.LensSelected ->
                _state.value = current.copy(selectedLens = intent.lens)

            is ReaderContract.Intent.PickerDismissed ->
                _state.value = current.copy(pickerOpenForDay = null)

            is ReaderContract.Intent.FigureAssigned -> viewModelScope.launch {
                dayAssignmentRepository.assign(intent.dayOfWeek, intent.figureId)
                _state.value = current.copy(pickerOpenForDay = null)
            }

            is ReaderContract.Intent.AssignmentCleared -> viewModelScope.launch {
                dayAssignmentRepository.clear(intent.dayOfWeek)
                _state.value = current.copy(pickerOpenForDay = null)
            }
        }
    }

    private fun buildWeekSlots(
        assignments: Map<Int, Long>,
        figuresById: Map<Long, com.mediasage.domain.model.Figure>,
    ): List<ReaderContract.DaySlot> {
        val today = Instant.fromEpochMilliseconds(epochMillis())
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
        val startOfWeek = today.minus(today.dayOfWeek.ordinal, DateTimeUnit.DAY)
        return (0..6).map { i ->
            val date = startOfWeek.plus(i, DateTimeUnit.DAY)
            val figure = assignments[date.dayOfWeek.ordinal]?.let { figuresById[it] }
            ReaderContract.DaySlot(
                dayOfWeek = date.dayOfWeek,
                isToday = date == today,
                assignedFigureName = figure?.name,
                assignedFigureImageUrl = figure?.portraitUrl,
            )
        }
    }

}
