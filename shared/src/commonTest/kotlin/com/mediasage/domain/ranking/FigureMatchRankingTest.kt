package com.mediasage.domain.ranking

import kotlin.test.Test
import kotlin.test.assertEquals

class FigureMatchRankingTest {

    @Test
    fun excludesCandidatesBelowMinScore() {
        val candidates = listOf(
            MatchCandidate(figureName = "Augustine", category = "theologian", score = 0.4f),
            MatchCandidate(figureName = "Aquinas", category = "theologian", score = 0.9f),
        )
        val result = rankMatchCandidates(candidates, minScore = 0.5f, blockedCategories = emptySet(), maxResults = 5)
        assertEquals(listOf("Aquinas"), result.map { it.figureName })
    }

    @Test
    fun excludesCandidatesInBlockedCategoryCaseInsensitively() {
        val candidates = listOf(
            MatchCandidate(figureName = "Athlete", category = "Sports", score = 0.9f),
            MatchCandidate(figureName = "Augustine", category = "theologian", score = 0.8f),
        )
        val result = rankMatchCandidates(
            candidates,
            minScore = 0f,
            blockedCategories = setOf("SPORTS"),
            maxResults = 5,
        )
        assertEquals(listOf("Augustine"), result.map { it.figureName })
    }

    @Test
    fun noFigureExceedsHalfOfMaxResultsRoundedUp() {
        val candidates = listOf(
            MatchCandidate(figureName = "Augustine", category = "theologian", score = 0.9f),
            MatchCandidate(figureName = "Augustine", category = "theologian", score = 0.8f),
            MatchCandidate(figureName = "Augustine", category = "theologian", score = 0.7f),
            MatchCandidate(figureName = "Augustine", category = "theologian", score = 0.6f),
            MatchCandidate(figureName = "Aquinas", category = "theologian", score = 0.5f),
        )
        val result = rankMatchCandidates(candidates, minScore = 0f, blockedCategories = emptySet(), maxResults = 5)
        assertEquals(3, result.count { it.figureName == "Augustine" })
        assertEquals(4, result.size)
    }

    @Test
    fun candidatesExceedingFigureShareAreDroppedNotBackfilled() {
        val candidates = listOf(
            MatchCandidate(figureName = "Augustine", category = "theologian", score = 0.9f),
            MatchCandidate(figureName = "Augustine", category = "theologian", score = 0.8f),
            MatchCandidate(figureName = "Augustine", category = "theologian", score = 0.7f),
            MatchCandidate(figureName = "Augustine", category = "theologian", score = 0.6f),
        )
        val result = rankMatchCandidates(candidates, minScore = 0f, blockedCategories = emptySet(), maxResults = 4)
        assertEquals(2, result.size)
        assertEquals(listOf(0.9f, 0.8f), result.map { it.score })
    }

    @Test
    fun ordersByHighestScoreFirst() {
        val candidates = listOf(
            MatchCandidate(figureName = "Augustine", category = "theologian", score = 0.3f),
            MatchCandidate(figureName = "Aquinas", category = "theologian", score = 0.9f),
            MatchCandidate(figureName = "Bonhoeffer", category = "theologian", score = 0.6f),
        )
        val result = rankMatchCandidates(candidates, minScore = 0f, blockedCategories = emptySet(), maxResults = 5)
        assertEquals(listOf("Aquinas", "Bonhoeffer", "Augustine"), result.map { it.figureName })
    }

    @Test
    fun equalScoresBreakTiesByFigureNameThenCategoryCaseInsensitively() {
        val candidates = listOf(
            MatchCandidate(figureName = "bonhoeffer", category = "theologian", score = 0.5f),
            MatchCandidate(figureName = "Augustine", category = "Theologian", score = 0.5f),
            MatchCandidate(figureName = "augustine", category = "mystic", score = 0.5f),
        )
        val result = rankMatchCandidates(candidates, minScore = 0f, blockedCategories = emptySet(), maxResults = 5)
        assertEquals(
            listOf("Augustine" to "Theologian", "augustine" to "mystic", "bonhoeffer" to "theologian"),
            result.map { it.figureName to it.category },
        )
    }

    @Test
    fun equalScoresAndNamesBreakTiesByOriginalInputPosition() {
        val candidates = listOf(
            MatchCandidate(figureName = "Augustine", category = "theologian", score = 0.5f),
            MatchCandidate(figureName = "Augustine", category = "theologian", score = 0.5f),
        )
        val result = rankMatchCandidates(candidates, minScore = 0f, blockedCategories = emptySet(), maxResults = 5)
        assertEquals(candidates, result)
    }

    @Test
    fun orderingIsDeterministicAcrossRuns() {
        val candidates = listOf(
            MatchCandidate(figureName = "Bonhoeffer", category = "theologian", score = 0.5f),
            MatchCandidate(figureName = "Augustine", category = "theologian", score = 0.9f),
            MatchCandidate(figureName = "Aquinas", category = "theologian", score = 0.5f),
        )
        val firstRun = rankMatchCandidates(candidates, minScore = 0f, blockedCategories = emptySet(), maxResults = 5)
        val secondRun = rankMatchCandidates(candidates, minScore = 0f, blockedCategories = emptySet(), maxResults = 5)
        assertEquals(firstRun, secondRun)
    }

    @Test
    fun emptyInputProducesEmptyList() {
        val result = rankMatchCandidates(emptyList(), minScore = 0f, blockedCategories = emptySet(), maxResults = 5)
        assertEquals(emptyList(), result)
    }

    @Test
    fun nonPositiveMaxResultsProducesEmptyList() {
        val candidates = listOf(MatchCandidate(figureName = "Augustine", category = "theologian", score = 0.9f))
        assertEquals(
            emptyList(),
            rankMatchCandidates(candidates, minScore = 0f, blockedCategories = emptySet(), maxResults = 0),
        )
        assertEquals(
            emptyList(),
            rankMatchCandidates(candidates, minScore = 0f, blockedCategories = emptySet(), maxResults = -1),
        )
    }
}
