package com.mediasage.agentruntime.evaluation.scoring

import java.util.UUID

interface DecisionScorer {
    suspend fun score(jobId: UUID)
}
