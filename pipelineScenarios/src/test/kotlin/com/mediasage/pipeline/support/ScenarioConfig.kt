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
    /** GitHub token (GH_TOKEN) — used by GitHubFixtureClient for branch/PR operations. */
    val githubToken: String,
    /** Base URL of the live orchestrator (e.g. Cloud Run Service URL). Used to POST simulated webhook events. */
    val orchestratorUrl: String,
    /** GitHub webhook secret — used to compute HMAC-SHA256 signatures on simulated webhook payloads. */
    val webhookSecret: String
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
                githubToken = optional("GH_TOKEN"),
                orchestratorUrl = optional("ORCHESTRATOR_URL"),
                webhookSecret = optional("GITHUB_WEBHOOK_SECRET")
            )
        }
    }
}
