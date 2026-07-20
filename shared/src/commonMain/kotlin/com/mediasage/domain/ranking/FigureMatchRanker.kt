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
    if (maxResults <= 0) return emptyList()

    val blocked = blockedCategories.map { it.lowercase() }.toSet()
    val maxPerFigure = (maxResults + 1) / 2

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

    val result = mutableListOf<FigureMatchCandidate>()
    val perFigureCount = mutableMapOf<String, Int>()
    for ((_, candidate) in eligible) {
        if (result.size == maxResults) break
        val key = candidate.figureName.lowercase()
        val used = perFigureCount.getOrDefault(key, 0)
        if (used >= maxPerFigure) continue
        result += candidate
        perFigureCount[key] = used + 1
    }
    return result
}
