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
     * Generates a plain-text briefing for the given dispatch context.
     *
     * Never throws — all failures are collapsed to null so dispatch is never blocked.
     *
     * @param context the dispatch context containing ticket, prompt, and job metadata
     * @return a plain-text briefing string, or null if the call fails or times out
     */
    suspend fun brief(context: BriefingContext): String?
}
