package com.mediasage.domain.ranking

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FigureMatchRankerTest {

    @Test
    fun excludesCandidatesBelowMinimumScore() {
        val candidates = listOf(
            FigureMatchCandidate("Augustine", "theologian", 0.9),
            FigureMatchCandidate("Teresa of Avila", "mystic", 0.2),
        )

        val result = rankFigureMatches(
            candidates, minScore = 0.5, blockedCategories = emptySet(),
            perFigureLimit = 10, maxResults = 10,
        )

        assertEquals(listOf("Augustine"), result.map { it.figureName })
    }

    @Test
    fun excludesBlockedCategoriesCaseInsensitively() {
        val candidates = listOf(
            FigureMatchCandidate("Augustine", "theologian", 0.9),
            FigureMatchCandidate("Random Athlete", "Sports", 0.9),
        )

        val result = rankFigureMatches(
            candidates, minScore = 0.0, blockedCategories = setOf("sports"),
            perFigureLimit = 10, maxResults = 10,
        )

        assertEquals(listOf("Augustine"), result.map { it.figureName })
    }

    @Test
    fun ordersHighestScoreFirst() {
        val candidates = listOf(
            FigureMatchCandidate("Augustine", "theologian", 0.5),
            FigureMatchCandidate("Bonhoeffer", "theologian", 0.9),
            FigureMatchCandidate("Teresa of Avila", "mystic", 0.7),
        )

        val result = rankFigureMatches(
            candidates, minScore = 0.0, blockedCategories = emptySet(),
            perFigureLimit = 10, maxResults = 10,
        )

        assertEquals(listOf("Bonhoeffer", "Teresa of Avila", "Augustine"), result.map { it.figureName })
    }

    @Test
    fun breaksScoreTiesByFigureNameThenCategory() {
        val candidates = listOf(
            FigureMatchCandidate("Bonhoeffer", "theologian", 0.8),
            FigureMatchCandidate("Augustine", "mystic", 0.8),
            FigureMatchCandidate("Augustine", "church_father", 0.8),
        )

        val result = rankFigureMatches(
            candidates, minScore = 0.0, blockedCategories = emptySet(),
            perFigureLimit = 10, maxResults = 10,
        )

        assertEquals(
            listOf(
                "Augustine" to "church_father",
                "Augustine" to "mystic",
                "Bonhoeffer" to "theologian",
            ),
            result.map { it.figureName to it.category },
        )
    }

    @Test
    fun orderingIsStableAcrossRepeatedRuns() {
        val candidates = listOf(
            FigureMatchCandidate("Bonhoeffer", "theologian", 0.8),
            FigureMatchCandidate("Augustine", "mystic", 0.8),
            FigureMatchCandidate("Teresa of Avila", "mystic", 0.6),
        )

        val first = rankFigureMatches(
            candidates, minScore = 0.0, blockedCategories = emptySet(),
            perFigureLimit = 10, maxResults = 10,
        )
        val second = rankFigureMatches(
            candidates, minScore = 0.0, blockedCategories = emptySet(),
            perFigureLimit = 10, maxResults = 10,
        )

        assertEquals(first, second)
    }

    @Test
    fun pushesMatchesBeyondPerFigureLimitToTheEndInsteadOfDroppingThem() {
        val candidates = listOf(
            FigureMatchCandidate("Augustine", "theologian", 0.9),
            FigureMatchCandidate("Augustine", "church_father", 0.8),
            FigureMatchCandidate("Bonhoeffer", "theologian", 0.7),
        )

        val result = rankFigureMatches(
            candidates, minScore = 0.0, blockedCategories = emptySet(),
            perFigureLimit = 1, maxResults = 10,
        )

        assertEquals(
            listOf(
                "Augustine" to "theologian",
                "Bonhoeffer" to "theologian",
                "Augustine" to "church_father",
            ),
            result.map { it.figureName to it.category },
        )
    }

    @Test
    fun capsToMaxResultsOnlyAfterOrderingAndPerFigureLimit() {
        val candidates = listOf(
            FigureMatchCandidate("Augustine", "theologian", 0.9),
            FigureMatchCandidate("Augustine", "church_father", 0.8),
            FigureMatchCandidate("Bonhoeffer", "theologian", 0.7),
        )

        val result = rankFigureMatches(
            candidates, minScore = 0.0, blockedCategories = emptySet(),
            perFigureLimit = 1, maxResults = 2,
        )

        assertEquals(
            listOf(
                "Augustine" to "theologian",
                "Bonhoeffer" to "theologian",
            ),
            result.map { it.figureName to it.category },
        )
    }

    @Test
    fun emptyInputProducesEmptyList() {
        val result = rankFigureMatches(
            emptyList(), minScore = 0.0, blockedCategories = emptySet(),
            perFigureLimit = 10, maxResults = 10,
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun nonPositiveMaxResultsProducesEmptyList() {
        val candidates = listOf(FigureMatchCandidate("Augustine", "theologian", 0.9))

        val zero = rankFigureMatches(
            candidates, minScore = 0.0, blockedCategories = emptySet(),
            perFigureLimit = 10, maxResults = 0,
        )
        val negative = rankFigureMatches(
            candidates, minScore = 0.0, blockedCategories = emptySet(),
            perFigureLimit = 10, maxResults = -1,
        )

        assertTrue(zero.isEmpty())
        assertTrue(negative.isEmpty())
    }
}
