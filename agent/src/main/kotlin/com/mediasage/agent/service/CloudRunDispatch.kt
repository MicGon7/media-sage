package com.mediasage.agent.service

import com.mediasage.pipeline.core.JobRegistry

/**
 * Bundles the Cloud Run job dispatcher and persistent job registry into a single optional
 * dependency for [AgentLaunchService]. When present, the agent dispatches Claude Code workers
 * as Cloud Run Jobs and tracks their state in Supabase Postgres; when null, workers run
 * in-process instead.
 */
data class CloudRunDispatch(
    /** Executes Cloud Run Jobs and polls LRO URLs until completion or failure. */
    val dispatcher: JobDispatcher,
    /** Persists and queries job state in Supabase Postgres. */
    val jobs: JobRegistry
) {
    /** Returns the [CloudRunJobsClient] backing [dispatcher], or null if a different implementation is used. */
    val client: CloudRunJobsClient? get() = dispatcher as? CloudRunJobsClient
}
