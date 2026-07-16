package com.mediasage.agentruntime.di

/**
 * Configuration for the agent orchestration server.
 *
 * Holds all runtime parameters needed by the orchestrator: GitHub App credentials for webhook
 * verification and installation token generation, Jira credentials for ticket transitions and
 * comment posting, GCP credentials for Cloud Run Job dispatch, and database/Pub/Sub settings
 * for persistent job state and completion callbacks.
 *
 * All values are sourced from environment variables via `application.conf` using Ktor's `${?VAR}`
 * substitution syntax.
 *
 * @property githubWebhookSecret Secret used to verify HMAC-SHA256 signatures on incoming GitHub
 *   webhook payloads. Sourced from env var `GITHUB_WEBHOOK_SECRET`.
 * @property githubBotLogin GitHub login of the bot account (e.g. `media-sage-worker[bot]`). Webhook
 *   events are only acted on when the PR was authored by this identity — prevents the orchestrator
 *   from responding to human-authored PRs. Sourced from env var `GITHUB_BOT_LOGIN`.
 *@property jiraEmail Email address for authenticating with the Jira REST API (human account).
 *   Sourced from env var `JIRA_EMAIL`.
 * @property jiraApiToken API token for the Jira REST API, paired with [jiraEmail].
 *   Sourced from env var `JIRA_API_TOKEN`.
 * @property jiraCloudId Jira cloud instance identifier (e.g. `ad358528-f7e9-4e40-9531-c51049908d6d`).
 *   Sourced from env var `JIRA_CLOUD_ID`; falls back to the hardcoded media-sage cloud ID if unset.
 * @property jiraBotAccountId Jira account ID of the bot account. Tickets assigned to this account
 *   and transitioned to In Progress trigger autonomous mode via the Jira webhook.
 *   Sourced from env var `JIRA_BOT_ACCOUNT_ID`.
 * @property jiraBotEmail Email of the bot Jira account, used when posting automated comments as the
 *   bot identity. Falls back to the human account if blank. Sourced from env var `JIRA_BOT_EMAIL`.
 * @property jiraBotApiToken API token for the bot Jira account, paired with [jiraBotEmail].
 *   Sourced from env var `JIRA_BOT_API_TOKEN`.
 * @property gcpProjectId GCP project ID used for Cloud Run Job dispatch (e.g. `media-sage-agent`).
 *   Sourced from env var `GCP_PROJECT_ID`.
 * @property gcpRegion GCP region for Cloud Run Jobs. Defaults to `us-central1`.
 *   Sourced from env var `GCP_REGION`.
 * @property gcpJobName Cloud Run Job name for the ticket-work and pr-review-work images. Defaults to `media-sage-agent-worker`.
 *   Sourced from env var `GCP_JOB_NAME`.
 * @property googleCredentialsJson GCP service account JSON key decoded from the base64 value in env
 *   var `GOOGLE_CREDENTIALS_BASE64`. Used to authenticate Cloud Run API calls.
 * @property supabaseDbUrl PostgreSQL connection URL for the Supabase job registry, used for
 *   persistent dedup and job recovery across restarts. Sourced from env var `SUPABASE_DB_URL`.
 *@property pubSubWebhookSecret Shared secret token appended as `?token=` to the Pub/Sub push
 *   subscription URL. The orchestrator verifies this on every push delivery to reject spoofed
 *   requests. Sourced from env var `PUBSUB_WEBHOOK_SECRET`.
 * @property claudeAuthToken Auth token for [com.mediasage.agentruntime.evaluation.scoring.ClaudeDecisionScorer].
 *   Sourced from env var `ANTHROPIC_AUTH_TOKEN`. When blank, decision scoring is disabled.
 * @property claudeBaseUrl Anthropic API base URL. Defaults to `https://api.anthropic.com`.
 *   Sourced from env var `ANTHROPIC_BASE_URL`. Set to the Fuelix proxy URL on Cloud Run.
 * @property claudeModel Model ID for Claude calls. Defaults to `claude-sonnet-4-6`.
 *   Sourced from env var `ANTHROPIC_MODEL`.
 * @property githubAppId GitHub App ID for the feedback-scan auto-PR feature. Sourced from
 *   `GITHUB_APP_ID`. When blank, the auto-PR feature is disabled.
 * @property githubAppPrivateKey PEM-encoded RSA private key for the GitHub App. Sourced from
 *   `GITHUB_APP_PRIVATE_KEY_BASE64`.
 * @property githubAppInstallationId GitHub App installation ID for this repo. Sourced from
 *   `GITHUB_APP_INSTALLATION_ID`.
 * @property githubRepoOwner Repository owner for the auto-PR feature. Sourced from `GITHUB_OWNER`.
 * @property githubRepoName Repository name for the auto-PR feature. Sourced from `GITHUB_REPO`.
 * @property slackWebhookUrl Slack incoming-webhook URL for job-completion notifications. Sourced
 *   from `SLACK_WEBHOOK_URL`. When blank, no Slack messages are posted.
 */
data class AgentConfig(
    val githubWebhookSecret: String,
    val githubBotLogin: String = "",
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
    val pubSubWebhookSecret: String = "",
    val claudeAuthToken: String = "",
    val claudeBaseUrl: String = "https://api.anthropic.com",
    val claudeModel: String = "claude-sonnet-4-6",
    val githubAppId: String = "",
    val githubAppPrivateKey: String = "",
    val githubAppInstallationId: String = "",
    val githubRepoOwner: String = "",
    val githubRepoName: String = "",
    val slackWebhookUrl: String = "",
)
