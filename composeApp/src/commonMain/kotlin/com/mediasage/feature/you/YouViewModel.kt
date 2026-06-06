package com.mediasage.feature.you

import androidx.lifecycle.ViewModel
import com.mediasage.data.repository.epochMillis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

class YouViewModel : ViewModel() {

    private val _state = MutableStateFlow<YouContract.UiState>(
        YouContract.UiState.Ready(
            weekSlots = buildWeekSlots(),
            // TODO MS-315: replace with real saved quotes from QuoteRepository
            quoteCard = MOCK_QUOTE_CARD,
        )
    )
    val state: StateFlow<YouContract.UiState> = _state.asStateFlow()

    fun onIntent(intent: YouContract.Intent) {
        val current = _state.value as? YouContract.UiState.Ready ?: return
        when (intent) {
            is YouContract.Intent.LensSelected -> _state.value = current.copy(selectedLens = intent.lens)
            is YouContract.Intent.DaySlotTapped -> Unit
        }
    }

    private fun buildWeekSlots(): List<YouContract.DaySlot> {
        val today = Instant.fromEpochMilliseconds(epochMillis())
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
        val startOfWeek = today.minus(today.dayOfWeek.ordinal, DateTimeUnit.DAY)
        return (0..6).map { i ->
            val date = startOfWeek.plus(i, DateTimeUnit.DAY)
            YouContract.DaySlot(
                dayOfWeek = date.dayOfWeek,
                isToday = date == today,
            )
        }
    }

    companion object {
        private val MOCK_QUOTE_CARD = YouContract.QuoteCard(
            quoteText = "You can't go back and change the beginning, but you can start where you are and change the ending.",
            figureName = "C.S. Lewis",
            figureRole = "Author & Apologist",
            figureImageUrl = null,
            figureId = -1L,
        )
    }
}
