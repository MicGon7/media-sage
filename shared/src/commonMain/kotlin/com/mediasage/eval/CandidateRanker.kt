package com.mediasage.eval

data class MatchCandidate(
    val figureName: String,
    val category: String,
    val score: Int
)

data class RankingConfig(
    val minScore: Int,
    val blockedCategories: Set<String>,
    val perFigureLimit: Int,
    val maxResultSize: Int
)

fun rankCandidates(candidates: List<MatchCandidate>, config: RankingConfig): List<MatchCandidate> {
    if (candidates.isEmpty() || config.maxResultSize <= 0) return emptyList()

    val blocked = config.blockedCategories.map { it.lowercase() }.toSet()
    val eligible = candidates.filter { it.score >= config.minScore && it.category.lowercase() !in blocked }

    val comparator = compareByDescending<MatchCandidate> { it.score }
        .thenBy { it.figureName }
        .thenBy { it.category }
    val ordered = eligible.sortedWith(comparator)

    val withinLimit = mutableListOf<MatchCandidate>()
    val overflow = mutableListOf<MatchCandidate>()
    val seenPerFigure = mutableMapOf<String, Int>()
    for (candidate in ordered) {
        val seen = seenPerFigure.getOrDefault(candidate.figureName, 0)
        if (seen < config.perFigureLimit) withinLimit += candidate else overflow += candidate
        seenPerFigure[candidate.figureName] = seen + 1
    }

    return (withinLimit + overflow).take(config.maxResultSize)
}
