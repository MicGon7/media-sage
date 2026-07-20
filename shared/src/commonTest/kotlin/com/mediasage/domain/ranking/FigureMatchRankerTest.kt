package com.mediasage.domain.ranking

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FigureMatchRankerTest {

    @Test
    fun excludesCandidatesBelowMinimumScore() {
        val candidates = listOf(
            FigureMatchCandidate("Augustine", "theologian", 0.9),
            FigureMatchCandidate("Aquinas", "theologian", 0.2)
        )

        val result = rankFigureMatches(
            candidates, minScore = 0.5, blockedCategories = emptySet(), perFigureLimit = 5, maxResults = 10
        )

        assertEquals(listOf("Augustine"), result.map { it.figureName })
    }

    @Test
    fun excludesBlockedCategoriesCaseInsensitively() {
        val candidates = listOf(
            FigureMatchCandidate("Augustine", "theologian", 0.9),
            FigureMatchCandidate("Athlete", "Sports", 0.9)
        )

        val result = rankFigureMatches(
            candidates, minScore = 0.0, blockedCategories = setOf("sports"), perFigureLimit = 5, maxResults = 10
        )

        assertEquals(listOf("Augustine"), result.map { it.figureName })
    }

    @Test
    fun ordersHighestScoreFirst() {
        val candidates = listOf(
            FigureMatchCandidate("Aquinas", "theologian", 0.5),
            FigureMatchCandidate("Augustine", "theologian", 0.9),
            FigureMatchCandidate("Bonhoeffer", "theologian", 0.7)
        )

        val result = rankFigureMatches(
            candidates, minScore = 0.0, blockedCategories = emptySet(), perFigureLimit = 5, maxResults = 10
        )

        assertEquals(listOf("Augustine", "Bonhoeffer", "Aquinas"), result.map { it.figureName })
    }

    @Test
    fun breaksScoreTiesByFigureNameThenCategory() {
        val candidates = listOf(
            FigureMatchCandidate("Bonhoeffer", "mystic", 0.8),
            FigureMatchCandidate("Bonhoeffer", "theologian", 0.8),
            FigureMatchCandidate("Augustine", "theologian", 0.8)
        )

        val result = rankFigureMatches(
            candidates, minScore = 0.0, blockedCategories = emptySet(), perFigureLimit = 5, maxResults = 10
        )

        assertEquals(
            listOf(
                "Augustine" to "theologian",
                "Bonhoeffer" to "mystic",
                "Bonhoeffer" to "theologian"
            ),
            result.map { it.figureName to it.category }
        )
    }

    @Test
    fun orderingIsStableAcrossRepeatedRuns() {
        val candidates = listOf(
            FigureMatchCandidate("Bonhoeffer", "mystic", 0.8),
            FigureMatchCandidate("Augustine", "theologian", 0.8),
            FigureMatchCandidate("Aquinas", "theologian", 0.9)
        )

        val first = rankFigureMatches(candidates, 0.0, emptySet(), 5, 10)
        val second = rankFigureMatches(candidates, 0.0, emptySet(), 5, 10)

        assertEquals(first, second)
    }

    @Test
    fun overflowBeyondPerFigureLimitAppearsAfterWithinLimitMatches() {
        val candidates = listOf(
            FigureMatchCandidate("Augustine", "theologian", 0.9),
            FigureMatchCandidate("Augustine", "theologian", 0.8),
            FigureMatchCandidate("Augustine", "theologian", 0.7),
            FigureMatchCandidate("Bonhoeffer", "theologian", 0.6)
        )

        val result = rankFigureMatches(
            candidates, minScore = 0.0, blockedCategories = emptySet(), perFigureLimit = 2, maxResults = 10
        )

        assertEquals(4, result.size)
        assertEquals(listOf(0.9, 0.8, 0.6, 0.7), result.map { it.score })
    }

    @Test
    fun maxResultsCapAppliesAfterOrderingAndPerFigureLimit() {
        val candidates = listOf(
            FigureMatchCandidate("Augustine", "theologian", 0.9),
            FigureMatchCandidate("Augustine", "theologian", 0.85),
            FigureMatchCandidate("Augustine", "theologian", 0.8),
            FigureMatchCandidate("Bonhoeffer", "theologian", 0.7)
        )

        val result = rankFigureMatches(
            candidates, minScore = 0.0, blockedCategories = emptySet(), perFigureLimit = 1, maxResults = 2
        )

        assertEquals(listOf(0.9, 0.7), result.map { it.score })
        assertTrue(result.size <= 2)
    }

    @Test
    fun emptyInputProducesEmptyList() {
        val result = rankFigureMatches(
            emptyList(), minScore = 0.0, blockedCategories = emptySet(), perFigureLimit = 5, maxResults = 10
        )

        assertEquals(emptyList(), result)
    }

    @Test
    fun nonPositiveMaxResultsProducesEmptyList() {
        val candidates = listOf(FigureMatchCandidate("Augustine", "theologian", 0.9))

        assertEquals(
            emptyList(),
            rankFigureMatches(candidates, minScore = 0.0, blockedCategories = emptySet(), perFigureLimit = 5, maxResults = 0)
        )
        assertEquals(
            emptyList(),
            rankFigureMatches(candidates, minScore = 0.0, blockedCategories = emptySet(), perFigureLimit = 5, maxResults = -1)
        )
    }
}
