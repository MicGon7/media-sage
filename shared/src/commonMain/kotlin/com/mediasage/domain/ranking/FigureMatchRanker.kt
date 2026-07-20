package com.mediasage.domain.ranking

data class FigureMatchCandidate(
    val figureName: String,
    val category: String,
    val score: Double,
)

/**
 * Ranks candidate figure matches for a headline, filtering out low-score and blocked-category
 * candidates, then capping how many results any single figure may contribute so the list stays
 * varied rather than dominated by one figure.
 */
fun rankFigureMatches(
    candidates: List<FigureMatchCandidate>,
    minScore: Double,
    blockedCategories: Set<String>,
    maxResults: Int,
): List<FigureMatchCandidate> {
    if (candidates.isEmpty() || maxResults <= 0) return emptyList()

    val blocked = blockedCategories.map { it.lowercase() }.toSet()
    val eligible = candidates.withIndex()
        .filter { (_, candidate) ->
            candidate.score >= minScore && candidate.category.lowercase() !in blocked
        }
        .sortedWith(
            compareByDescending<IndexedValue<FigureMatchCandidate>> { it.value.score }
                .thenBy { it.value.figureName.lowercase() }
                .thenBy { it.value.category.lowercase() }
                .thenBy { it.index }
        )
        .map { it.value }

    val maxPerFigure = (maxResults + 1) / 2
    val countsByFigure = mutableMapOf<String, Int>()
    val result = mutableListOf<FigureMatchCandidate>()
    for (candidate in eligible) {
        if (result.size >= maxResults) break
        val key = candidate.figureName.lowercase()
        val count = countsByFigure.getOrDefault(key, 0)
        if (count >= maxPerFigure) continue
        result.add(candidate)
        countsByFigure[key] = count + 1
    }
    return result
}
