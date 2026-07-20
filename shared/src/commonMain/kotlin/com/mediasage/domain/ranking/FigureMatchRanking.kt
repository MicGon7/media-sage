package com.mediasage.domain.ranking

data class MatchCandidate(
    val figureName: String,
    val category: String,
    val score: Float,
)

/**
 * Filters candidates by [minScore] and [blockedCategories] (matched case-insensitively), then
 * returns up to [maxResults] of the highest-scoring survivors — capping any single figure at
 * ceil([maxResults] / 2) so no one figure can dominate the list. A candidate that would exceed
 * its figure's share is dropped rather than replaced, so the result can end up shorter than
 * [maxResults].
 */
fun rankMatchCandidates(
    candidates: List<MatchCandidate>,
    minScore: Float,
    blockedCategories: Set<String>,
    maxResults: Int,
): List<MatchCandidate> {
    if (candidates.isEmpty() || maxResults <= 0) return emptyList()

    val blocked = blockedCategories.mapTo(mutableSetOf()) { it.lowercase() }
    val maxPerFigure = (maxResults + 1) / 2

    val eligible = candidates.withIndex()
        .filter { (_, candidate) ->
            candidate.score >= minScore && candidate.category.lowercase() !in blocked
        }
        .sortedWith(
            compareByDescending<IndexedValue<MatchCandidate>> { it.value.score }
                .thenBy { it.value.figureName.lowercase() }
                .thenBy { it.value.category.lowercase() }
                .thenBy { it.index },
        )

    val result = mutableListOf<MatchCandidate>()
    val countsByFigure = mutableMapOf<String, Int>()

    for ((_, candidate) in eligible) {
        if (result.size == maxResults) break
        val figureKey = candidate.figureName.lowercase()
        val count = countsByFigure.getOrDefault(figureKey, 0)
        if (count == maxPerFigure) continue
        result += candidate
        countsByFigure[figureKey] = count + 1
    }

    return result
}
