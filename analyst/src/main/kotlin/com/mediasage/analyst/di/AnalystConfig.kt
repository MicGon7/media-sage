package com.mediasage.analyst.di

/**
 * Configuration for the Analyst feedback server.
 *
 * All values are sourced from environment variables via `application.conf` using Ktor's
 * `${?VAR}` substitution syntax. Optional features are disabled when their required fields
 * are blank — no process exit for missing optional config.
 *
 * @property supabaseDbUrl PostgreSQL connection URL for the Supabase job registry. The Analyst
 *   reads cross-run history from the `jobs` table written by the orchestrator; it never writes
 *   to it. Sourced from env var `SUPABASE_DB_URL`.
 * @property pubSubWebhookSecret Shared secret token appended as `?token=` to the Analyst's Pub/Sub
 *   push subscription URL. Verified on every push delivery to reject spoofed requests. Sourced from
 *   env var `PUBSUB_WEBHOOK_SECRET`. When blank, the Pub/Sub route is not registered.
 * @property claudeAuthToken Auth token for [ClaudeDecisionScorer]. Sourced from env var
 *   `ANTHROPIC_AUTH_TOKEN` — same token used by worker jobs via the Fuelix proxy. When blank,
 *   decision scoring is disabled.
 * @property claudeBaseUrl Anthropic API base URL. Sourced from env var `ANTHROPIC_BASE_URL`.
 *   Defaults to `https://api.anthropic.com`. Set to the Fuelix proxy URL on Cloud Run.
 * @property githubAppId GitHub App ID for the Analyst's installation token. Sourced from
 *   `GITHUB_APP_ID`. When blank, the auto-PR feature is disabled.
 * @property githubPrivateKey PEM-encoded RSA private key (PKCS#1 or PKCS#8) for the GitHub App.
 *   Sourced from `GITHUB_PRIVATE_KEY`.
 * @property githubInstallationId GitHub App installation ID for this repo. Sourced from
 *   `GITHUB_INSTALLATION_ID`.
 * @property githubRepoOwner Repository owner (org or user). Sourced from `GITHUB_REPO_OWNER`.
 * @property githubRepoName Repository name. Sourced from `GITHUB_REPO_NAME`.
 */
data class AnalystConfig(
    val supabaseDbUrl: String = "",
    val pubSubWebhookSecret: String = "",
    val claudeAuthToken: String = "",
    val claudeBaseUrl: String = "https://api.anthropic.com",
    val githubAppId: String = "",
    val githubPrivateKey: String = "",
    val githubInstallationId: String = "",
    val githubRepoOwner: String = "",
    val githubRepoName: String = "",
)
