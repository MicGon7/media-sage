package com.mediasage.agentruntime.evaluation

interface AgentService {
    suspend fun evaluate(ticketKey: String, prNumber: Int)
}
