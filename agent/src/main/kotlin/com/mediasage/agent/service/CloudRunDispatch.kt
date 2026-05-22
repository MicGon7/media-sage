package com.mediasage.agent.service

import com.mediasage.agent.db.JobRegistry

/**
 * Bundles the Cloud Run job dispatcher and persistent job registry into a single optional
 * dependency for [AgentLaunchService]. When present, the agent dispatches Claude Code workers
 * as Cloud Run Jobs and tracks their state in Supabase Postgres; when null, workers run
 * in-process instead.
 */
data class CloudRunDispatch(
    val dispatcher: JobDispatcher,
    val jobs: JobRegistry, // persistent job registry for dedup and recovery across restarts
)
