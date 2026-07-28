package com.mediasage.feature.figures

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediasage.data.repository.epochMillis
import com.mediasage.domain.repository.DailyReflectionRepository
import com.mediasage.domain.repository.DayAssignmentRepository
import com.mediasage.domain.repository.EncouragementRepository
import com.mediasage.domain.repository.FigureRepository
import com.mediasage.domain.repository.QuoteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

class FigureDetailViewModel(
    private val figureId: Long,
    private val figureRepository: FigureRepository,
    private val encouragementRepository: EncouragementRepository,
    private val dayAssignmentRepository: DayAssignmentRepository,
    private val dailyReflectionRepository: DailyReflectionRepository,
    private val quoteRepository: QuoteRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<FigureDetailContract.UiState>(FigureDetailContract.UiState.Loading)
    val state: StateFlow<FigureDetailContract.UiState> = _state.asStateFlow()

    /** The only user selection this screen owns: an in-flight reassignment awaiting confirmation. */
    private val input = MutableStateFlow<FigureDetailContract.PendingReassignment?>(null)

    init {
        load()
    }

    fun onIntent(intent: FigureDetailContract.Intent) {
        when (intent) {
            is FigureDetailContract.Intent.PinToHome -> handlePinToggle()
            is FigureDetailContract.Intent.ConfirmReassignment -> handleConfirmReassignment()
            is FigureDetailContract.Intent.CancelReassignment -> input.value = null
            is FigureDetailContract.Intent.PinQuote -> handlePinQuote(intent.quoteText)
        }
    }

    private fun handlePinQuote(quoteText: String) {
        viewModelScope.launch { quoteRepository.memorizeQuote(figureId, quoteText) }
    }

    private fun handlePinToggle() {
        val current = _state.value as? FigureDetailContract.UiState.Success ?: return
        val todayOrdinal = todayDayOfWeekOrdinal()
        if (current.isPinned) {
            viewModelScope.launch { dayAssignmentRepository.clear(todayOrdinal) }
            return
        }
        viewModelScope.launch {
            val lockedFigureId = dailyReflectionRepository.getLockedFigureId(todayEpochDay())
            if (lockedFigureId != null && lockedFigureId != figureId) {
                val lockedFigureName = figureRepository.getFigureById(lockedFigureId)?.name ?: return@launch
                input.value = FigureDetailContract.PendingReassignment(
                    todayOrdinal = todayOrdinal,
                    currentFigureName = lockedFigureName,
                    newFigureName = current.figureName,
                    nextWeekdayLabel = weekdayLabel(todayOrdinal),
                )
            } else {
                dayAssignmentRepository.assign(todayOrdinal, figureId)
            }
        }
    }

    private fun handleConfirmReassignment() {
        val pending = input.value ?: return
        viewModelScope.launch {
            dayAssignmentRepository.assign(pending.todayOrdinal, figureId)
            input.value = null
        }
    }

    private fun load() {
        viewModelScope.launch {
            val figure = figureRepository.getFigureById(figureId) ?: return@launch
            combine(
                encouragementRepository.observeByFigureId(figure.id),
                dayAssignmentRepository.observeAssignments(),
                input,
                quoteRepository.observeMemorizedQuote(),
            ) { encouragements, assignments, pendingReassignment, memorizedQuote ->
                val todayOrdinal = todayDayOfWeekOrdinal()
                FigureDetailContract.UiState.Success(
                    figureName = figure.name,
                    figureRole = figure.role,
                    figureImageUrl = figure.portraitUrl,
                    bio = figure.bio,
                    quotes = encouragements.map {
                        FigureQuoteItem(
                            quoteText = it.quoteText,
                            headlineTitle = it.headlineTitle,
                            isPinned = memorizedQuote?.figureId == figure.id && memorizedQuote.text == it.quoteText,
                        )
                    },
                    isPinned = assignments[todayOrdinal]?.figureId == figureId,
                    pendingReassignment = pendingReassignment,
                )
            }.collect { _state.value = it }
        }
    }

    private fun weekdayLabel(dayOfWeekOrdinal: Int): String =
        DayOfWeek.entries[dayOfWeekOrdinal].name.lowercase().replaceFirstChar { it.uppercase() }

    private fun todayEpochDay(): Long =
        Instant.fromEpochMilliseconds(epochMillis())
            .toLocalDateTime(TimeZone.currentSystemDefault()).date.toEpochDays().toLong()

    private fun todayDayOfWeekOrdinal(): Int =
        Instant.fromEpochMilliseconds(epochMillis())
            .toLocalDateTime(TimeZone.currentSystemDefault()).date.dayOfWeek.ordinal
}
