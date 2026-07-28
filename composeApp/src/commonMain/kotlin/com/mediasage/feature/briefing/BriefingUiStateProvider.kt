package com.mediasage.feature.briefing

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.mediasage.ui.ErrorType

internal class BriefingUiStateProvider : PreviewParameterProvider<BriefingContract.UiState> {
    override val values = sequenceOf(
        BriefingContract.UiState.Loading(todayLabel = "Friday, June 5, 2026"),
        BriefingContract.UiState.Success(
            todayLabel = "Friday, June 5, 2026",
            card = BriefingContract.CardState.LoadingWithFigure(
                figureId = 1L,
                figureName = "C.S. Lewis",
                figureImageUrl = null,
                theme = "Faith"
            )
        ),
        BriefingContract.UiState.Success(
            todayLabel = "Friday, June 5, 2026",
            card = BriefingContract.CardState.Ready(
                figureId = 1L,
                figureName = "C.S. Lewis",
                figureImageUrl = null,
                scriptureReference = "Romans 8:28",
                scriptureText = "And we know that in all things God works for the good of those who love him.",
                insight = "Even setbacks are woven into a larger, purposeful story.",
                implication = "Trust that today's difficulty is not the whole story.",
                inspiration = "Hardships often prepare ordinary people for an extraordinary destiny.",
                sources = listOf("Schools Nationwide Integrate Compassion Into Core Curriculum"),
                tone = "Encouraging",
                theme = "Faith"
            )
        ),
        BriefingContract.UiState.Error(errorType = ErrorType.NETWORK)
    )
}
