package com.mediasage.agent.service

interface JobDispatcher {
    suspend fun executeJob(ticketKey: String, prompt: String): Boolean
}
