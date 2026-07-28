package com.mediasage.feature.figures

import androidx.compose.ui.tooling.preview.PreviewParameterProvider

internal class FiguresStateProvider : PreviewParameterProvider<FiguresContract.UiState> {
    override val values = sequenceOf(
        FiguresContract.UiState.Loading,
        FiguresContract.UiState.Success(figures = emptyList()),
        FiguresContract.UiState.Success(
            figures = listOf(
                VoiceFigureItem(
                    id = 1L,
                    name = "C.S. Lewis",
                    role = "Author & Apologist",
                    lifespan = "1898–1963",
                    themes = listOf("Faith", "Reason"),
                    imageUrl = null,
                    quoteCount = 3,
                    isPinned = true
                ),
                VoiceFigureItem(
                    id = 2L,
                    name = "Dietrich Bonhoeffer",
                    role = "Theologian & Martyr",
                    lifespan = "1898–1963",
                    themes = listOf("Justice", "Discipleship", "Grace"),
                    imageUrl = null,
                    quoteCount = 1
                ),
                VoiceFigureItem(
                    id = 3L,
                    name = "Martin Luther King Jr.",
                    role = "Pastor & Civil Rights Leader",
                    lifespan = "1898–1963",
                    themes = listOf("Justice", "Hope"),
                    imageUrl = null,
                    quoteCount = 0
                ),
                VoiceFigureItem(
                    id = 4L,
                    name = "Julian of Norwich",
                    role = "Mystic & Theologian",
                    lifespan = "1347–1380",
                    themes = listOf("Love", "Contemplation"),
                    imageUrl = null,
                    quoteCount = 2
                ),
            )
        ),
    )
}
