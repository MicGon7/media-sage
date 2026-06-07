package com.mediasage.feature.figures

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediasage.data.repository.epochMillis
import com.mediasage.domain.repository.DayAssignmentRepository
import com.mediasage.domain.repository.EncouragementRepository
import com.mediasage.domain.repository.FigureRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

class FiguresViewModel(
    private val figureRepository: FigureRepository,
    private val encouragementRepository: EncouragementRepository,
    private val dayAssignmentRepository: DayAssignmentRepository
) : ViewModel() {

    private val _state = MutableStateFlow<FiguresContract.UiState>(FiguresContract.UiState.Loading)
    val state: StateFlow<FiguresContract.UiState> = _state.asStateFlow()

    private val _sideEffects = Channel<FiguresContract.SideEffect>(Channel.BUFFERED)
    val sideEffects = _sideEffects.receiveAsFlow()

    private val _searchQuery = MutableStateFlow("")

    init {
        viewModelScope.launch {
            combine(
                figureRepository.observeAllFigures(),
                encouragementRepository.observeCountByFigureName(),
                dayAssignmentRepository.observeAssignments(),
                _searchQuery
            ) { figures, counts, assignments, query ->
                val todayOrdinal = todayDayOfWeekOrdinal()
                val todayFigureId = assignments[todayOrdinal]
                val items = figures.map { figure ->
                    VoiceFigureItem(
                        id = figure.id,
                        name = figure.name,
                        role = figure.role,
                        lifespan = figure.lifespan,
                        themes = figure.themes,
                        imageUrl = figure.portraitUrl,
                        quoteCount = counts[figure.name] ?: 0,
                        isPinned = figure.id == todayFigureId
                    )
                }
                val filtered = if (query.isBlank()) {
                    items
                } else {
                    items.filter { item ->
                        item.name.contains(query, ignoreCase = true) ||
                            item.role.contains(query, ignoreCase = true) ||
                            item.lifespan.contains(query, ignoreCase = true) ||
                            item.themes.any { it.contains(query, ignoreCase = true) }
                    }
                }
                val sorted = filtered.sortedWith(
                    compareByDescending<VoiceFigureItem> { it.isPinned }.thenBy { it.name }
                )
                FiguresContract.UiState.Success(figures = sorted, searchQuery = query)
            }.collect { state ->
                _state.value = state
            }
        }
    }

    fun onIntent(intent: FiguresContract.Intent) {
        when (intent) {
            is FiguresContract.Intent.LoadFigures -> { /* reactive — no manual reload needed */ }
            is FiguresContract.Intent.Refresh -> refresh()
            is FiguresContract.Intent.FigureClicked -> { /* handled via navigation callback */ }
            is FiguresContract.Intent.SearchQueryChanged -> { _searchQuery.value = intent.query }
        }
    }

    private fun refresh() {
        val current = _state.value as? FiguresContract.UiState.Success ?: return
        _state.value = current.copy(isRefreshing = true)
        viewModelScope.launch {
            runCatching { figureRepository.syncFigures() }
                .onFailure { e ->
                    _sideEffects.send(FiguresContract.SideEffect.ShowError(e.message ?: "Failed to sync voices"))
                }
            val updated = _state.value as? FiguresContract.UiState.Success ?: return@launch
            _state.value = updated.copy(isRefreshing = false)
        }
    }

    private fun todayDayOfWeekOrdinal(): Int =
        Instant.fromEpochMilliseconds(epochMillis())
            .toLocalDateTime(TimeZone.currentSystemDefault()).date.dayOfWeek.ordinal
}
