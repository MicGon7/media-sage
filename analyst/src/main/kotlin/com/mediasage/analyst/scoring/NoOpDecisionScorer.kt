package com.mediasage.analyst.scoring

import java.util.UUID

/** No-op scorer used when `CLAUDE_API_KEY` is not configured. */
class NoOpDecisionScorer : DecisionScorer {
    override suspend fun score(jobId: UUID) = Unit
}
