package com.mediasage.agentruntime.evaluation

class NoOpAcComplianceEvaluator : AcComplianceEvaluator {
    override suspend fun evaluate(ticketKey: String, prNumber: Int) = Unit
}
