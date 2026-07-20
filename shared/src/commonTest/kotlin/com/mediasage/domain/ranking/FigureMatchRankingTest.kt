package com.mediasage.domain.ranking

import kotlin.test.Test
import kotlin.test.assertEquals

class FigureMatchRankingTest {

    @Test
    fun emptyInputProducesEmptyList() {
        val result = rankFigureMatches(
            candidates = emptyList(),
            minScore = 0.0,
            blockedCategories = emptySet(),
            maxResults = 5
        )
        assertEquals(emptyList(), result)
    }

    @Test
    fun zeroOrNegativeMaxResultsProducesEmptyList() {
        val candidates = listOf(FigureMatchCandidate("Augustine", "theologian", 0.9))

        assertEquals(
            emptyList(),
            rankFigureMatches(candidates, minScore = 0.0, blockedCategories = emptySet(), maxResults = 0)
        )
        assertEquals(
            emptyList(),
            rankFigureMatches(candidates, minScore = 0.0, blockedCategories = emptySet(), maxResults = -1)
        )
    }

    @Test
    fun candidatesBelowMinimumScoreAreExcluded() {
        val candidates = listOf(
            FigureMatchCandidate("Augustine", "theologian", 0.5),
            FigureMatchCandidate("Bonhoeffer", "theologian", 0.7)
        )

        val result = rankFigureMatches(candidates, minScore = 0.6, blockedCategories = emptySet(), maxResults = 5)

        assertEquals(listOf(candidates[1]), result)
    }

    @Test
    fun candidatesInBlockedCategoryAreExcludedCaseInsensitively() {
        val candidates = listOf(
            FigureMatchCandidate("Augustine", "Sports", 0.9),
            FigureMatchCandidate("Bonhoeffer", "theologian", 0.8)
        )

        val result = rankFigureMatches(
            candidates, minScore = 0.0, blockedCategories = setOf("sports"), maxResults = 5
        )

        assertEquals(listOf(candidates[1]), result)
    }

    @Test
    fun noSingleFigureExceedsHalfOfMaxResultsRoundedUp() {
        val candidates = listOf(
            FigureMatchCandidate("Augustine", "theologian", 0.9),
            FigureMatchCandidate("Augustine", "church_father", 0.8),
            FigureMatchCandidate("Augustine", "intellectual", 0.7),
            FigureMatchCandidate("Augustine", "mystic", 0.6),
            FigureMatchCandidate("Bonhoeffer", "theologian", 0.5)
        )

        val result = rankFigureMatches(candidates, minScore = 0.0, blockedCategories = emptySet(), maxResults = 5)

        assertEquals(3, result.count { it.figureName == "Augustine" })
        assertEquals(listOf("Augustine", "Augustine", "Augustine", "Bonhoeffer"), result.map { it.figureName })
    }

    @Test
    fun overflowingFigureShareShortensResultInsteadOfPaddingWithRepeats() {
        val candidates = listOf(
            FigureMatchCandidate("Augustine", "theologian", 0.9),
            FigureMatchCandidate("Augustine", "church_father", 0.8),
            FigureMatchCandidate("Augustine", "intellectual", 0.7),
            FigureMatchCandidate("Augustine", "mystic", 0.6)
        )

        val result = rankFigureMatches(candidates, minScore = 0.0, blockedCategories = emptySet(), maxResults = 4)

        assertEquals(2, result.size)
        assertEquals(listOf("Augustine", "Augustine"), result.map { it.figureName })
    }

    @Test
    fun resultIsOrderedByHighestScoreFirst() {
        val candidates = listOf(
            FigureMatchCandidate("Augustine", "theologian", 0.4),
            FigureMatchCandidate("Bonhoeffer", "theologian", 0.9),
            FigureMatchCandidate("TeresaOfAvila", "mystic", 0.6)
        )

        val result = rankFigureMatches(candidates, minScore = 0.0, blockedCategories = emptySet(), maxResults = 5)

        assertEquals(listOf("Bonhoeffer", "TeresaOfAvila", "Augustine"), result.map { it.figureName })
    }

    @Test
    fun equalScoresAreOrderedByNameThenCategoryThenOriginalPositionCaseInsensitively() {
        val candidates = listOf(
            FigureMatchCandidate("bonhoeffer", "mystic", 0.5),
            FigureMatchCandidate("Bonhoeffer", "Intellectual", 0.5),
            FigureMatchCandidate("Augustine", "theologian", 0.5),
            FigureMatchCandidate("Augustine", "theologian", 0.5)
        )

        val result = rankFigureMatches(candidates, minScore = 0.0, blockedCategories = emptySet(), maxResults = 10)

        assertEquals(
            listOf(candidates[2], candidates[3], candidates[1], candidates[0]),
            result
        )
    }

    @Test
    fun resultIsDeterministicAcrossRepeatedRuns() {
        val candidates = listOf(
            FigureMatchCandidate("Augustine", "theologian", 0.9),
            FigureMatchCandidate("Bonhoeffer", "theologian", 0.9),
            FigureMatchCandidate("TeresaOfAvila", "mystic", 0.3)
        )

        val first = rankFigureMatches(candidates, minScore = 0.0, blockedCategories = emptySet(), maxResults = 2)
        val second = rankFigureMatches(candidates, minScore = 0.0, blockedCategories = emptySet(), maxResults = 2)

        assertEquals(first, second)
    }
}
