package com.mediasage.domain.ranking

import kotlin.test.Test
import kotlin.test.assertEquals

class MatchCandidateRankerTest {

    @Test
    fun emptyInputProducesEmptyList() {
        val result = rankMatchCandidates(
            candidates = emptyList(),
            minScore = 0.0,
            blockedCategories = emptySet(),
            maxResultSize = 5
        )
        assertEquals(emptyList(), result)
    }

    @Test
    fun nonPositiveMaxResultSizeProducesEmptyList() {
        val candidates = listOf(MatchCandidate("Augustine", "theologian", 0.9))
        assertEquals(
            emptyList(),
            rankMatchCandidates(candidates, minScore = 0.0, blockedCategories = emptySet(), maxResultSize = 0)
        )
        assertEquals(
            emptyList(),
            rankMatchCandidates(candidates, minScore = 0.0, blockedCategories = emptySet(), maxResultSize = -1)
        )
    }

    @Test
    fun filtersOutCandidatesBelowMinimumScore() {
        val candidates = listOf(
            MatchCandidate("Augustine", "theologian", 0.5),
            MatchCandidate("Bonhoeffer", "theologian", 0.9)
        )
        val result = rankMatchCandidates(candidates, minScore = 0.6, blockedCategories = emptySet(), maxResultSize = 5)
        assertEquals(listOf(MatchCandidate("Bonhoeffer", "theologian", 0.9)), result)
    }

    @Test
    fun filtersOutBlockedCategoriesCaseInsensitively() {
        val candidates = listOf(
            MatchCandidate("Serena Williams", "Sports", 0.9),
            MatchCandidate("Augustine", "theologian", 0.8)
        )
        val result = rankMatchCandidates(
            candidates, minScore = 0.0, blockedCategories = setOf("sports"), maxResultSize = 5
        )
        assertEquals(listOf(MatchCandidate("Augustine", "theologian", 0.8)), result)
    }

    @Test
    fun noSingleFigureExceedsHalfOfMaxResultSizeRoundedUp() {
        val candidates = listOf(
            MatchCandidate("Augustine", "theologian", 0.9),
            MatchCandidate("Augustine", "church_father", 0.85),
            MatchCandidate("Augustine", "mystic", 0.8),
            MatchCandidate("Augustine", "intellectual", 0.75),
            MatchCandidate("Bonhoeffer", "theologian", 0.6)
        )
        val result = rankMatchCandidates(candidates, minScore = 0.0, blockedCategories = emptySet(), maxResultSize = 5)
        assertEquals(
            listOf(
                MatchCandidate("Augustine", "theologian", 0.9),
                MatchCandidate("Augustine", "church_father", 0.85),
                MatchCandidate("Augustine", "mystic", 0.8),
                MatchCandidate("Bonhoeffer", "theologian", 0.6)
            ),
            result
        )
    }

    @Test
    fun candidatesExceedingFigureShareAreOmittedRatherThanBackfilled() {
        val candidates = listOf(
            MatchCandidate("Augustine", "theologian", 0.9),
            MatchCandidate("Augustine", "church_father", 0.8),
            MatchCandidate("Augustine", "mystic", 0.7),
            MatchCandidate("Augustine", "intellectual", 0.6)
        )
        val result = rankMatchCandidates(candidates, minScore = 0.0, blockedCategories = emptySet(), maxResultSize = 5)
        assertEquals(
            listOf(
                MatchCandidate("Augustine", "theologian", 0.9),
                MatchCandidate("Augustine", "church_father", 0.8),
                MatchCandidate("Augustine", "mystic", 0.7)
            ),
            result
        )
    }

    @Test
    fun ordersByScoreDescendingThenFigureNameThenCategoryThenOriginalPositionCaseInsensitively() {
        val candidates = listOf(
            MatchCandidate("bonhoeffer", "theologian", 0.5),
            MatchCandidate("Augustine", "MYSTIC", 0.5),
            MatchCandidate("Augustine", "church_father", 0.5),
            MatchCandidate("Augustine", "church_father", 0.5)
        )
        val result = rankMatchCandidates(candidates, minScore = 0.0, blockedCategories = emptySet(), maxResultSize = 8)
        assertEquals(
            listOf(
                MatchCandidate("Augustine", "church_father", 0.5),
                MatchCandidate("Augustine", "church_father", 0.5),
                MatchCandidate("Augustine", "MYSTIC", 0.5),
                MatchCandidate("bonhoeffer", "theologian", 0.5)
            ),
            result
        )
    }

    @Test
    fun resultIsDeterministicAcrossRuns() {
        val candidates = listOf(
            MatchCandidate("Augustine", "theologian", 0.9),
            MatchCandidate("Bonhoeffer", "theologian", 0.6),
            MatchCandidate("Teresa", "mystic", 0.6)
        )
        val first = rankMatchCandidates(candidates, minScore = 0.0, blockedCategories = emptySet(), maxResultSize = 3)
        val second = rankMatchCandidates(candidates, minScore = 0.0, blockedCategories = emptySet(), maxResultSize = 3)
        assertEquals(first, second)
    }
}
