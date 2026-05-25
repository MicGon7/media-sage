package com.mediasage.agent.di

/**
 * Configuration for the agent orchestration server.
 *
 * @property repoPath Absolute path to the local clone of the media-sage repository used by worker processes.
 * @property githubWebhookSecret Secret used to verify HMAC-SHA256 signatures on incoming GitHub webhook payloads.
 * @property githubAppId Numeric GitHub App ID for `media-sage-worker`. Used to generate JWTs for installation token exchange.
 * @property githubAppInstallationId Installation ID of `media-sage-worker` on the media-sage repo.
 * @property githubAppPrivateKey RSA private key PEM content (decoded from base64 env var) for signing JWTs.
 * @property jiraEmail Email address for authenticating with the Jira REST API.
 * @property jiraApiToken API token for the Jira REST API, paired with [jiraEmail].
 * @property jiraCloudId Jira cloud instance hostname (e.g. `media-sage.atlassian.net`).
 * @property jiraBotAccountId Jira account ID of the bot account. Tickets assigned to this account
 *   and transitioned to In Progress trigger autonomous mode via the Jira webhook.
 * @property jiraBotEmail Email of the bot Jira account, used for filtering webhook events.
 * @property jiraBotApiToken API token for the bot Jira account.
 * @property gcpProjectId GCP project ID used for Cloud Run Job dispatch.
 * @property gcpRegion GCP region for Cloud Run Jobs. Defaults to `us-central1`.
 * @property gcpJobName Cloud Run Job name for the worker image. Defaults to `media-sage-agent-worker`.
 * @property googleCredentialsJson Base64-encoded GCP service account JSON key for authenticating
 *   Cloud Run API calls.
 * @property supabaseDbUrl PostgreSQL connection URL for the Supabase job registry, used for
 *   persistent dedup and job recovery across restarts.
 * @property pubSubWebhookSecret Shared secret token appended as `?token=` to the Pub/Sub push
 *   subscription URL. The orchestrator verifies this on every push delivery to reject spoofed requests.
 */
data class AgentConfig(
    val repoPath: String,
    val githubWebhookSecret: String,
    val githubAppId: String = "",
    val githubAppInstallationId: String = "",
    val githubAppPrivateKey: String = "",
    val jiraEmail: String,
    val jiraApiToken: String,
    val jiraCloudId: String,
    val jiraBotAccountId: String = "",
    val jiraBotEmail: String = "",
    val jiraBotApiToken: String = "",
    val gcpProjectId: String = "",
    val gcpRegion: String = "us-central1",
    val gcpJobName: String = "media-sage-agent-worker",
    val googleCredentialsJson: String = "",
    val supabaseDbUrl: String = "",
    val pubSubWebhookSecret: String = ""
)
