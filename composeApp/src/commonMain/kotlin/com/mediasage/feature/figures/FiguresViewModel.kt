package com.mediasage.feature.figures

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mediasage.domain.repository.EncouragementRepository
import com.mediasage.domain.repository.FigureRepository
import com.mediasage.domain.repository.PinnedFigureRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class FiguresViewModel(
    private val figureRepository: FigureRepository,
    private val encouragementRepository: EncouragementRepository,
    private val pinnedFigureRepository: PinnedFigureRepository
) : ViewModel() {

    private val _state = MutableStateFlow<FiguresContract.UiState>(FiguresContract.UiState.Loading)
    val state: StateFlow<FiguresContract.UiState> = _state.asStateFlow()

    private val _searchQuery = MutableStateFlow("")

    init {
        viewModelScope.launch {
            combine(
                figureRepository.observeAllFigures(),
                encouragementRepository.observeCountByFigureName(),
                pinnedFigureRepository.observePinnedFigureId(),
                _searchQuery
            ) { figures, counts, pinnedId, query ->
                val items = figures.map { figure ->
                    VoiceFigureItem(
                        id = figure.id,
                        name = figure.name,
                        role = figure.role,
                        imageUrl = figure.portraitUrl,
                        quoteCount = counts[figure.name] ?: 0,
                        isPinned = figure.id == pinnedId
                    )
                }
                val filtered = if (query.isBlank()) {
                    items
                } else {
                    items.filter { item ->
                        item.name.contains(query, ignoreCase = true) ||
                            item.role.contains(query, ignoreCase = true)
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
        viewModelScope.launch {
            _state.value = current.copy(isRefreshing = true)
            try {
                figureRepository.syncFigures()
            } catch (_: Exception) {
                // network error — dismiss spinner silently, figures list stays intact
            } finally {
                _state.value = (_state.value as? FiguresContract.UiState.Success)
                    ?.copy(isRefreshing = false)
                    ?: current.copy(isRefreshing = false)
            }
        }
    }
}
