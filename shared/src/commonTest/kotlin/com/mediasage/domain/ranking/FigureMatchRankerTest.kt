package com.mediasage.domain.ranking

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FigureMatchRankerTest {

    @Test
    fun emptyInputProducesEmptyList() {
        val result = rankFigureMatches(
            candidates = emptyList(),
            minScore = 0.0,
            blockedCategories = emptySet(),
            maxResults = 5,
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun nonPositiveMaxResultsProducesEmptyList() {
        val candidates = listOf(FigureMatchCandidate("Augustine", "theologian", 0.9))
        assertTrue(rankFigureMatches(candidates, 0.0, emptySet(), 0).isEmpty())
        assertTrue(rankFigureMatches(candidates, 0.0, emptySet(), -1).isEmpty())
    }

    @Test
    fun filtersCandidatesBelowMinimumScore() {
        val candidates = listOf(
            FigureMatchCandidate("Augustine", "theologian", 0.4),
            FigureMatchCandidate("Julian of Norwich", "mystic", 0.6),
        )
        val result = rankFigureMatches(candidates, minScore = 0.5, blockedCategories = emptySet(), maxResults = 5)
        assertEquals(listOf("Julian of Norwich"), result.map { it.figureName })
    }

    @Test
    fun filtersBlockedCategoriesCaseInsensitively() {
        val candidates = listOf(
            FigureMatchCandidate("Athlete", "Sports", 0.9),
            FigureMatchCandidate("Augustine", "theologian", 0.5),
        )
        val result = rankFigureMatches(
            candidates,
            minScore = 0.0,
            blockedCategories = setOf("sports"),
            maxResults = 5,
        )
        assertEquals(listOf("Augustine"), result.map { it.figureName })
    }

    @Test
    fun capsResultsPerFigureAtHalfOfMaxRoundedUp() {
        val candidates = listOf(
            FigureMatchCandidate("Augustine", "theologian", 0.9),
            FigureMatchCandidate("Augustine", "theologian", 0.8),
            FigureMatchCandidate("Augustine", "theologian", 0.7),
            FigureMatchCandidate("Augustine", "theologian", 0.6),
            FigureMatchCandidate("Julian of Norwich", "mystic", 0.5),
        )
        val result = rankFigureMatches(candidates, minScore = 0.0, blockedCategories = emptySet(), maxResults = 5)
        assertEquals(3, result.count { it.figureName == "Augustine" })
        assertEquals(listOf(0.9, 0.8, 0.7, 0.5), result.map { it.score })
    }

    @Test
    fun perFigureCapCanLeaveResultShorterThanMax() {
        val candidates = listOf(
            FigureMatchCandidate("Augustine", "theologian", 0.9),
            FigureMatchCandidate("Augustine", "theologian", 0.8),
            FigureMatchCandidate("Augustine", "theologian", 0.7),
            FigureMatchCandidate("Augustine", "theologian", 0.6),
        )
        val result = rankFigureMatches(candidates, minScore = 0.0, blockedCategories = emptySet(), maxResults = 5)
        assertEquals(3, result.size)
        assertEquals(listOf(0.9, 0.8, 0.7), result.map { it.score })
    }

    @Test
    fun ordersByScoreDescendingThenNameThenCategoryThenOriginalPositionCaseInsensitively() {
        val candidates = listOf(
            FigureMatchCandidate("julian of norwich", "mystic", 0.5),
            FigureMatchCandidate("Augustine", "Theologian", 0.5),
            FigureMatchCandidate("Augustine", "church father", 0.5),
            FigureMatchCandidate("Teresa of Avila", "mystic", 0.9),
        )
        val result = rankFigureMatches(candidates, minScore = 0.0, blockedCategories = emptySet(), maxResults = 4)
        assertEquals(
            listOf(
                "Teresa of Avila" to "mystic",
                "Augustine" to "church father",
                "Augustine" to "Theologian",
                "julian of norwich" to "mystic",
            ),
            result.map { it.figureName to it.category },
        )
    }

    @Test
    fun isDeterministicAcrossRuns() {
        val candidates = listOf(
            FigureMatchCandidate("Augustine", "theologian", 0.9),
            FigureMatchCandidate("Julian of Norwich", "mystic", 0.9),
            FigureMatchCandidate("Teresa of Avila", "mystic", 0.7),
        )
        val first = rankFigureMatches(candidates, minScore = 0.0, blockedCategories = emptySet(), maxResults = 2)
        val second = rankFigureMatches(candidates, minScore = 0.0, blockedCategories = emptySet(), maxResults = 2)
        assertEquals(first, second)
    }
}
