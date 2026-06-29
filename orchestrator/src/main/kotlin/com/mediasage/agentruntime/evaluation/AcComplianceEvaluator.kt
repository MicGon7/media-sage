package com.mediasage.agentruntime.evaluation

interface AcComplianceEvaluator {
    suspend fun evaluate(ticketKey: String, prNumber: Int)
}
