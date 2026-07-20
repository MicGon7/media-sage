package com.mediasage.domain.ranking

data class FigureMatchCandidate(
    val figureName: String,
    val category: String,
    val score: Double
)

fun rankFigureMatches(
    candidates: List<FigureMatchCandidate>,
    minScore: Double,
    blockedCategories: Set<String>,
    maxResults: Int
): List<FigureMatchCandidate> {
    if (candidates.isEmpty() || maxResults <= 0) return emptyList()

    val blockedLower = blockedCategories.map { it.lowercase() }.toSet()
    val eligible = candidates.withIndex()
        .filter { (_, candidate) ->
            candidate.score >= minScore && candidate.category.lowercase() !in blockedLower
        }
        .sortedWith(
            compareByDescending<IndexedValue<FigureMatchCandidate>> { it.value.score }
                .thenBy { it.value.figureName.lowercase() }
                .thenBy { it.value.category.lowercase() }
                .thenBy { it.index }
        )
        .map { it.value }

    return applyShareLimit(eligible, maxResults)
}

private fun applyShareLimit(
    candidates: List<FigureMatchCandidate>,
    maxResults: Int
): List<FigureMatchCandidate> {
    val maxPerFigure = (maxResults + 1) / 2
    val counts = mutableMapOf<String, Int>()
    val result = mutableListOf<FigureMatchCandidate>()

    for (candidate in candidates) {
        if (result.size >= maxResults) break
        val key = candidate.figureName.lowercase()
        val count = counts.getOrDefault(key, 0)
        if (count >= maxPerFigure) continue
        counts[key] = count + 1
        result.add(candidate)
    }
    return result
}
