package com.mediasage.feature.figures

import androidx.lifecycle.ViewModel
import com.mediasage.domain.model.FigureCategory
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

/** ViewModel for the Voices screen — wired to real data in a future ticket. */
class FiguresViewModel : ViewModel() {

    private val _state = MutableStateFlow<FiguresContract.UiState>(
        FiguresContract.UiState.Success(figures = sampleFigures)
    )
    val state: StateFlow<FiguresContract.UiState> = _state.asStateFlow()

    private val _sideEffects = Channel<FiguresContract.SideEffect>()
    val sideEffects = _sideEffects.receiveAsFlow()

    fun onIntent(intent: FiguresContract.Intent) {
        when (intent) {
            is FiguresContract.Intent.LoadFigures -> { /* TODO MS-45 */ }
            is FiguresContract.Intent.FilterByCategory -> { /* TODO MS-45 */ }
            is FiguresContract.Intent.FigureClicked -> { /* Handled via navigation callback */ }
        }
    }
}

// Sample data matching InitialFigures — replaced with real DB data when wired
private val sampleFigures = listOf(
    FigureItem(
        id = 1, name = "Dietrich Bonhoeffer",
        category = FigureCategory.THEOLOGIAN,
        role = "Theologian & Martyr", lifespan = "1906-1945",
        description = "German pastor and theologian who resisted the Nazi regime. Executed for his role in a plot to assassinate Hitler."
    ),
    FigureItem(
        id = 2, name = "Watchman Nee",
        category = FigureCategory.THEOLOGIAN,
        role = "Church Leader & Author", lifespan = "1903-1972",
        description = "Chinese church leader and Christian author. His books on the spiritual life have influenced millions worldwide."
    ),
    FigureItem(
        id = 3, name = "Julian of Norwich",
        category = FigureCategory.MYSTIC,
        role = "Medieval Mystic", lifespan = "1342-1416",
        description = "English anchoress and theologian who received profound visions of divine love."
    ),
    FigureItem(
        id = 4, name = "Dorothy Day",
        category = FigureCategory.MODERN,
        role = "Social Activist & Servant", lifespan = "1897-1980",
        description = "American journalist and Catholic convert who co-founded the Catholic Worker Movement."
    ),
    FigureItem(
        id = 5, name = "Pope Francis",
        category = FigureCategory.MODERN,
        role = "Bishop of Rome", lifespan = "1936-present",
        description = "First Jesuit pope and first from the Americas. Known for his humility, emphasis on mercy, and advocacy for the poor."
    ),
    FigureItem(
        id = 6, name = "Augustine of Hippo",
        category = FigureCategory.THEOLOGIAN,
        role = "Bishop & Church Father", lifespan = "354-430",
        description = "Bishop, philosopher, and one of the most influential Church Fathers."
    ),
    FigureItem(
        id = 7, name = "Corrie ten Boom",
        category = FigureCategory.MODERN,
        role = "Holocaust Survivor & Evangelist", lifespan = "1892-1983",
        description = "Dutch watchmaker who helped many Jews escape the Holocaust."
    ),
    FigureItem(
        id = 8, name = "Francis of Assisi",
        category = FigureCategory.MYSTIC,
        role = "Friar & Founder", lifespan = "1181-1226",
        description = "Italian friar who founded the Franciscan Order. Known for his love of nature, poverty, and peace."
    ),
    FigureItem(
        id = 9, name = "C.S. Lewis",
        category = FigureCategory.THEOLOGIAN,
        role = "Author & Apologist", lifespan = "1898-1963",
        description = "British writer and lay theologian. Author of Mere Christianity, The Screwtape Letters, and The Chronicles of Narnia."
    ),
    FigureItem(
        id = 10, name = "Mother Teresa",
        category = FigureCategory.MODERN,
        role = "Nun & Missionary", lifespan = "1910-1997",
        description = "Albanian-Indian nun who founded the Missionaries of Charity. Dedicated her life to serving the poorest of the poor."
    ),
)
