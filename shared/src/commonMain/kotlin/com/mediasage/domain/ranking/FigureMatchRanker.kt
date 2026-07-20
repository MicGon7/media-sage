package com.mediasage.domain.ranking

data class FigureMatchCandidate(
    val figureName: String,
    val category: String,
    val score: Double,
)

/**
 * Filters out weak and blocked-category candidates, orders the rest deterministically by score
 * (then figure name, then category), pushes matches beyond [perFigureLimit] for a given figure to
 * the end instead of dropping them, and caps the final list to [maxResults].
 *
 * Pure and deterministic — no I/O, no shared state.
 */
fun rankFigureMatches(
    candidates: List<FigureMatchCandidate>,
    minScore: Double,
    blockedCategories: Set<String>,
    perFigureLimit: Int,
    maxResults: Int,
): List<FigureMatchCandidate> {
    if (candidates.isEmpty() || maxResults <= 0) return emptyList()

    val blockedLower = blockedCategories.mapTo(mutableSetOf()) { it.lowercase() }
    val ordered = candidates
        .filter { it.score >= minScore && it.category.lowercase() !in blockedLower }
        .sortedWith(
            compareByDescending<FigureMatchCandidate> { it.score }
                .thenBy { it.figureName }
                .thenBy { it.category },
        )

    return applyPerFigureLimit(ordered, perFigureLimit).take(maxResults)
}

private fun applyPerFigureLimit(
    ordered: List<FigureMatchCandidate>,
    perFigureLimit: Int,
): List<FigureMatchCandidate> {
    val withinLimit = mutableListOf<FigureMatchCandidate>()
    val overflow = mutableListOf<FigureMatchCandidate>()
    val countByFigure = mutableMapOf<String, Int>()

    for (candidate in ordered) {
        val count = countByFigure.getOrDefault(candidate.figureName, 0)
        if (count < perFigureLimit) {
            withinLimit += candidate
            countByFigure[candidate.figureName] = count + 1
        } else {
            overflow += candidate
        }
    }

    return withinLimit + overflow
}
