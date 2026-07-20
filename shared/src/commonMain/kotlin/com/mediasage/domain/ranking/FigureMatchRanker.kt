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
    perFigureLimit: Int,
    maxResults: Int
): List<FigureMatchCandidate> {
    if (candidates.isEmpty() || maxResults <= 0) return emptyList()

    val blockedLower = blockedCategories.map { it.lowercase() }.toSet()
    val filtered = candidates.filter {
        it.score >= minScore && it.category.lowercase() !in blockedLower
    }

    val sorted = filtered.sortedWith(
        compareByDescending<FigureMatchCandidate> { it.score }
            .thenBy { it.figureName }
            .thenBy { it.category }
    )

    val withinLimit = mutableListOf<FigureMatchCandidate>()
    val overflow = mutableListOf<FigureMatchCandidate>()
    val countByFigure = mutableMapOf<String, Int>()
    for (candidate in sorted) {
        val count = countByFigure.getOrDefault(candidate.figureName, 0)
        if (count < perFigureLimit) {
            withinLimit += candidate
            countByFigure[candidate.figureName] = count + 1
        } else {
            overflow += candidate
        }
    }

    return (withinLimit + overflow).take(maxResults)
}
