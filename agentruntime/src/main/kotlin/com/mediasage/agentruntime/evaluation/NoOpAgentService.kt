package com.mediasage.agentruntime.evaluation

class NoOpAgentService : AgentService {
    override suspend fun evaluate(ticketKey: String, prNumber: Int) = Unit
}
