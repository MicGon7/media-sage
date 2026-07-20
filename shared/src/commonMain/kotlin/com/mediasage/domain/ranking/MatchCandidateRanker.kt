package com.mediasage.domain.ranking

import kotlin.math.ceil

data class MatchCandidate(
    val figureName: String,
    val category: String,
    val score: Double
)

fun rankMatchCandidates(
    candidates: List<MatchCandidate>,
    minScore: Double,
    blockedCategories: Set<String>,
    maxResultSize: Int
): List<MatchCandidate> {
    if (candidates.isEmpty() || maxResultSize <= 0) return emptyList()

    val blockedLower = blockedCategories.map { it.lowercase() }.toSet()
    val eligible = candidates.withIndex()
        .filter { (_, candidate) ->
            candidate.score >= minScore && candidate.category.lowercase() !in blockedLower
        }
        .sortedWith(
            compareByDescending<IndexedValue<MatchCandidate>> { it.value.score }
                .thenBy { it.value.figureName.lowercase() }
                .thenBy { it.value.category.lowercase() }
                .thenBy { it.index }
        )

    val maxPerFigure = ceil(maxResultSize / 2.0).toInt()
    val countsByFigure = mutableMapOf<String, Int>()
    val result = mutableListOf<MatchCandidate>()

    for ((_, candidate) in eligible) {
        if (result.size >= maxResultSize) break
        val key = candidate.figureName.lowercase()
        val currentCount = countsByFigure.getOrDefault(key, 0)
        if (currentCount >= maxPerFigure) continue
        countsByFigure[key] = currentCount + 1
        result.add(candidate)
    }

    return result
}
