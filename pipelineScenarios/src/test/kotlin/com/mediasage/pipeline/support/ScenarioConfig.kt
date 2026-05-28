package com.mediasage.pipeline.support

import java.util.Base64

/**
 * Runtime configuration for pipeline E2E scenarios, sourced from environment variables.
 *
 * Dedup scenarios only require [supabaseDbUrl].
 * Full pipeline scenarios additionally require [gcpProjectId] and [googleCredentialsJson].
 */
data class ScenarioConfig(
    val supabaseDbUrl: String,
    val gcpProjectId: String,
    val gcpRegion: String,
    val gcpJobName: String,
    val googleCredentialsJson: String,
    val repoPath: String,
    /** Jira ticket key used as the dispatch key in full pipeline scenarios (e.g. "MS-42"). */
    val e2eTicketKey: String,
    /** GitHub PR number used in PR review and conflict resolution scenarios. */
    val e2ePrNumber: Int,
    /** Branch ref used in full pipeline scenarios (e.g. "feature/MS-42-test"). */
    val e2eBranchRef: String
) {
    companion object {
        fun fromEnv(): ScenarioConfig {
            fun require(name: String) = System.getenv(name)?.takeIf { it.isNotBlank() }
                ?: error("Missing required env var: $name")

            fun optional(name: String, default: String = "") =
                System.getenv(name)?.takeIf { it.isNotBlank() } ?: default

            val credentialsBase64 = optional("GOOGLE_CREDENTIALS_BASE64")
            val credentialsJson = if (credentialsBase64.isNotBlank()) {
                String(Base64.getDecoder().decode(credentialsBase64))
            } else ""

            return ScenarioConfig(
                supabaseDbUrl = require("SUPABASE_DB_URL"),
                gcpProjectId = optional("GCP_PROJECT_ID"),
                gcpRegion = optional("GCP_REGION", "us-central1"),
                gcpJobName = optional("GCP_JOB_NAME", "media-sage-agent-worker"),
                googleCredentialsJson = credentialsJson,
                repoPath = optional("AGENT_REPO_PATH", "."),
                e2eTicketKey = optional("E2E_TICKET_KEY", "MS-E2E"),
                e2ePrNumber = optional("E2E_PR_NUMBER", "0").toIntOrNull() ?: 0,
                e2eBranchRef = optional("E2E_BRANCH_REF", "feature/MS-E2E-test")
            )
        }
    }
}
