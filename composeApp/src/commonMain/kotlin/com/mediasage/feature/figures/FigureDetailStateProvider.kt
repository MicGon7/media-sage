package com.mediasage.feature.figures

import androidx.compose.ui.tooling.preview.PreviewParameterProvider

internal class FigureDetailStateProvider : PreviewParameterProvider<FigureDetailContract.UiState> {
    override val values = sequenceOf(
        FigureDetailContract.UiState.Loading,
        FigureDetailContract.UiState.Success(
            figureName = "C.S. Lewis",
            figureRole = "Author & Apologist",
            figureImageUrl = null,
            bio = "Clive Staples Lewis (1898–1963) was a British writer, literary scholar, and lay theologian. " +
                "He held academic positions at both Oxford and Cambridge and is best known for his works of " +
                "fiction, including The Chronicles of Narnia and The Screwtape Letters, as well as his " +
                "Christian apologetics such as Mere Christianity and The Problem of Pain.",
            quotes = listOf(
                FigureQuoteItem(
                    "Hardships often prepare ordinary people for an extraordinary destiny.",
                    "Schools Nationwide Integrate Compassion and Empathy Into Core Curriculum"
                ),
                FigureQuoteItem(
                    "You are never too old to set another goal or to dream a new dream.",
                    "Community Gardens Transform Urban Neighborhoods Across America"
                ),
                FigureQuoteItem(
                    "We are what we believe we are.",
                    "New Research Links Daily Gratitude Practice to Mental Health Improvements"
                ),
            )
        ),
        FigureDetailContract.UiState.Success(
            figureName = "Dietrich Bonhoeffer",
            figureRole = "Theologian & Martyr",
            figureImageUrl = null,
            bio = null,
            quotes = listOf(
                FigureQuoteItem(
                    "Silence in the face of evil is itself evil. Not to speak is to speak.",
                    "Bipartisan Coalition Introduces Comprehensive Poverty Relief Legislation"
                )
            )
        ),
        FigureDetailContract.UiState.Error("Something went wrong. Please try again.")
    )
}
