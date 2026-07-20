package com.mediasage.eval

import kotlin.test.Test
import kotlin.test.assertEquals

class CandidateRankerTest {

    private fun defaultConfig(
        minScore: Int = 0,
        blockedCategories: Set<String> = emptySet(),
        perFigureLimit: Int = Int.MAX_VALUE,
        maxResultSize: Int = Int.MAX_VALUE
    ) = RankingConfig(minScore, blockedCategories, perFigureLimit, maxResultSize)

    @Test
    fun emptyInputProducesEmptyList() {
        val result = rankCandidates(emptyList(), defaultConfig())
        assertEquals(emptyList(), result)
    }

    @Test
    fun nonPositiveMaxResultSizeProducesEmptyList() {
        val candidates = listOf(MatchCandidate("Augustine", "theologian", 90))
        assertEquals(emptyList(), rankCandidates(candidates, defaultConfig(maxResultSize = 0)))
        assertEquals(emptyList(), rankCandidates(candidates, defaultConfig(maxResultSize = -1)))
    }

    @Test
    fun excludesCandidatesBelowMinimumScore() {
        val candidates = listOf(
            MatchCandidate("Augustine", "theologian", 50),
            MatchCandidate("Julian of Norwich", "mystic", 80)
        )
        val result = rankCandidates(candidates, defaultConfig(minScore = 60))
        assertEquals(listOf(candidates[1]), result)
    }

    @Test
    fun excludesBlockedCategoriesCaseInsensitively() {
        val candidates = listOf(
            MatchCandidate("Augustine", "Theologian", 90),
            MatchCandidate("Julian of Norwich", "mystic", 80)
        )
        val result = rankCandidates(candidates, defaultConfig(blockedCategories = setOf("theologian")))
        assertEquals(listOf(candidates[1]), result)
    }

    @Test
    fun ordersByScoreDescendingThenFigureNameThenCategory() {
        val candidates = listOf(
            MatchCandidate("Bonhoeffer", "theologian", 70),
            MatchCandidate("Augustine", "church_father", 70),
            MatchCandidate("Augustine", "theologian", 70),
            MatchCandidate("Julian of Norwich", "mystic", 95)
        )
        val result = rankCandidates(candidates, defaultConfig())
        assertEquals(
            listOf(
                MatchCandidate("Julian of Norwich", "mystic", 95),
                MatchCandidate("Augustine", "church_father", 70),
                MatchCandidate("Augustine", "theologian", 70),
                MatchCandidate("Bonhoeffer", "theologian", 70)
            ),
            result
        )
    }

    @Test
    fun orderingIsStableAcrossRepeatedCalls() {
        val candidates = listOf(
            MatchCandidate("Augustine", "theologian", 70),
            MatchCandidate("Bonhoeffer", "theologian", 70),
            MatchCandidate("Julian of Norwich", "mystic", 95)
        )
        val first = rankCandidates(candidates, defaultConfig())
        val second = rankCandidates(candidates, defaultConfig())
        assertEquals(first, second)
    }

    @Test
    fun perFigureLimitMovesExtraMatchesAfterWithinLimitOnesInsteadOfDropping() {
        val candidates = listOf(
            MatchCandidate("Augustine", "theologian", 90),
            MatchCandidate("Augustine", "church_father", 85),
            MatchCandidate("Augustine", "intellectual", 80),
            MatchCandidate("Julian of Norwich", "mystic", 82)
        )
        val result = rankCandidates(candidates, defaultConfig(perFigureLimit = 1))
        assertEquals(
            listOf(
                MatchCandidate("Augustine", "theologian", 90),
                MatchCandidate("Julian of Norwich", "mystic", 82),
                MatchCandidate("Augustine", "church_father", 85),
                MatchCandidate("Augustine", "intellectual", 80)
            ),
            result
        )
    }

    @Test
    fun maxResultSizeCapsAfterOrderingAndPerFigureLimitAreApplied() {
        val candidates = listOf(
            MatchCandidate("Augustine", "theologian", 90),
            MatchCandidate("Augustine", "church_father", 85),
            MatchCandidate("Julian of Norwich", "mystic", 82),
            MatchCandidate("Bonhoeffer", "theologian", 75)
        )
        val result = rankCandidates(candidates, defaultConfig(perFigureLimit = 1, maxResultSize = 2))
        assertEquals(
            listOf(
                MatchCandidate("Augustine", "theologian", 90),
                MatchCandidate("Julian of Norwich", "mystic", 82)
            ),
            result
        )
    }
}
