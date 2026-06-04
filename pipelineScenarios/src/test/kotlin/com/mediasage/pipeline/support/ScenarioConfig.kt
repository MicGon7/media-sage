package com.mediasage.pipeline.support

import java.util.Base64

/**
 * Runtime configuration for pipeline E2E scenarios, sourced from environment variables.
 *
 * Per-client config (Jira project key, GitHub repo, Supabase URL, orchestrator URL,
 * webhook secret) lives in [target] and is injected by the Gradle task. Shared
 * infrastructure config (GCP credentials, GitHub token) lives here.
 *
 * Dedup scenarios only require `target.supabaseDbUrl`.
 * Full pipeline scenarios additionally require [gcpProjectId], [googleCredentialsJson],
 * `target.orchestratorUrl`, and `target.webhookSecret`.
 */
data class ScenarioConfig(
    /** Per-client config — populated from env vars injected by the Gradle task. */
    val target: PipelineTarget,
    val gcpProjectId: String,
    val gcpRegion: String,
    val gcpJobName: String,
    val googleCredentialsJson: String,
    /** GitHub token (GH_TOKEN) — used by GitHubFixtureClient for branch/PR operations. */
    val githubToken: String
) {
    companion object {
        fun fromEnv(): ScenarioConfig {
            val credentialsBase64 = System.getenv("GOOGLE_CREDENTIALS_BASE64")?.takeIf { it.isNotBlank() } ?: ""
            val credentialsJson = if (credentialsBase64.isNotBlank()) {
                String(Base64.getDecoder().decode(credentialsBase64))
            } else ""

            return ScenarioConfig(
                target = PipelineTarget.fromEnv(),
                gcpProjectId = System.getenv("GCP_PROJECT_ID")?.takeIf { it.isNotBlank() } ?: "",
                gcpRegion = System.getenv("GCP_REGION")?.takeIf { it.isNotBlank() } ?: "us-central1",
                gcpJobName = System.getenv("GCP_JOB_NAME")?.takeIf { it.isNotBlank() } ?: "media-sage-agent-worker",
                googleCredentialsJson = credentialsJson,
                githubToken = System.getenv("GH_TOKEN")?.takeIf { it.isNotBlank() } ?: ""
            )
        }
    }
}
