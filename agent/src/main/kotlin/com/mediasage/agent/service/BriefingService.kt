package com.mediasage.agent.service

/**
 * Generates a pre-dispatch briefing for workers before each Cloud Run Job launch.
 *
 * Implemented by [HaikuBriefingService], which calls the Claude Messages API.
 * [AgentLaunchService] depends on this interface so tests can inject a synchronous fake
 * without the Ktor MockEngine dispatcher mismatch that occurs in [runTest].
 */
interface BriefingService {

    /**
     * Returns a plain-text briefing for [context], or null if the call fails or times out.
     * Never throws — all failures are collapsed to null so dispatch is never blocked.
     */
    suspend fun brief(context: BriefingContext): String?
}
