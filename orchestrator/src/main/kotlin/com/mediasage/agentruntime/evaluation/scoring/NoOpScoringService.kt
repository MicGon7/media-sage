package com.mediasage.agentruntime.evaluation.scoring

import java.util.UUID

class NoOpScoringService : DecisionScorer {
    override suspend fun score(jobId: UUID) = Unit
}
