package com.mediasage.orchestrator.feedback.scoring

import java.util.UUID

class NoOpDecisionScorer : DecisionScorer {
    override suspend fun score(jobId: UUID) = Unit
}
