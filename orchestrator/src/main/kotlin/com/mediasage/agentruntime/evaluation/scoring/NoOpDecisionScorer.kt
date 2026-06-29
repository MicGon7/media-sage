package com.mediasage.agentruntime.evaluation.scoring

import java.util.UUID

class NoOpDecisionScorer : DecisionScorer {
    override suspend fun score(jobId: UUID) = Unit
}
