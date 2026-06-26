package com.mediasage.orchestrator.feedback.scoring

import java.util.UUID

interface DecisionScorer {
    suspend fun score(jobId: UUID)
}
