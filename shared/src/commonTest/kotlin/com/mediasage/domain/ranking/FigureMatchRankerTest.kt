package com.mediasage.domain.ranking

import kotlin.test.Test
import kotlin.test.assertEquals

class FigureMatchRankerTest {

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
    fun excludesCandidatesBelowMinimumScore() {
        val candidates = listOf(
            FigureMatchCandidate("Augustine", "theologian", 0.5),
            FigureMatchCandidate("Bonhoeffer", "theologian", 0.9)
        )
        val result = rankFigureMatches(candidates, minScore = 0.6, blockedCategories = emptySet(), maxResults = 5)
        assertEquals(listOf(candidates[1]), result)
    }

    @Test
    fun includesCandidatesAtExactlyMinimumScore() {
        val candidates = listOf(FigureMatchCandidate("Augustine", "theologian", 0.6))
        val result = rankFigureMatches(candidates, minScore = 0.6, blockedCategories = emptySet(), maxResults = 5)
        assertEquals(candidates, result)
    }

    @Test
    fun excludesBlockedCategoriesCaseInsensitively() {
        val candidates = listOf(
            FigureMatchCandidate("Athlete", "Sports", 0.95),
            FigureMatchCandidate("Augustine", "theologian", 0.5)
        )
        val result = rankFigureMatches(
            candidates,
            minScore = 0.0,
            blockedCategories = setOf("sports"),
            maxResults = 5
        )
        assertEquals(listOf(candidates[1]), result)
    }

    @Test
    fun perFigureShareIsHalfOfMaxResultsRoundedUp() {
        val candidates = listOf(
            FigureMatchCandidate("Augustine", "theologian", 0.9),
            FigureMatchCandidate("Augustine", "church_father", 0.8),
            FigureMatchCandidate("Augustine", "mystic", 0.7),
            FigureMatchCandidate("Augustine", "intellectual", 0.6),
            FigureMatchCandidate("Bonhoeffer", "theologian", 0.5)
        )
        val result = rankFigureMatches(candidates, minScore = 0.0, blockedCategories = emptySet(), maxResults = 5)
        assertEquals(
            listOf(candidates[0], candidates[1], candidates[2], candidates[4]),
            result
        )
    }

    @Test
    fun omittedOverShareCandidatesShortenTheListRatherThanBeingBackfilled() {
        val candidates = listOf(
            FigureMatchCandidate("Augustine", "theologian", 0.9),
            FigureMatchCandidate("Augustine", "church_father", 0.8),
            FigureMatchCandidate("Augustine", "mystic", 0.7)
        )
        val result = rankFigureMatches(candidates, minScore = 0.0, blockedCategories = emptySet(), maxResults = 3)
        assertEquals(listOf(candidates[0], candidates[1]), result)
    }

    @Test
    fun ordersByScoreDescendingThenFigureNameThenCategoryThenOriginalPosition() {
        val candidates = listOf(
            FigureMatchCandidate("Bonhoeffer", "theologian", 0.5),
            FigureMatchCandidate("augustine", "mystic", 0.5),
            FigureMatchCandidate("Augustine", "Church_Father", 0.5),
            FigureMatchCandidate("Augustine", "church_father", 0.5)
        )
        val result = rankFigureMatches(candidates, minScore = 0.0, blockedCategories = emptySet(), maxResults = 4)
        assertEquals(
            listOf(candidates[2], candidates[3], candidates[1], candidates[0]),
            result
        )
    }

    @Test
    fun sameInputAlwaysProducesTheSameOrder() {
        val candidates = listOf(
            FigureMatchCandidate("Bonhoeffer", "theologian", 0.7),
            FigureMatchCandidate("Augustine", "mystic", 0.9),
            FigureMatchCandidate("Aquinas", "theologian", 0.9)
        )
        val first = rankFigureMatches(candidates, minScore = 0.0, blockedCategories = emptySet(), maxResults = 3)
        val second = rankFigureMatches(candidates, minScore = 0.0, blockedCategories = emptySet(), maxResults = 3)
        assertEquals(first, second)
    }
}
