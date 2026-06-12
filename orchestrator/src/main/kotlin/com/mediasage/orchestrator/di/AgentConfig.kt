package com.mediasage.orchestrator.di

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
 * @property gcpJudgeJobName Cloud Run Job name for the judge-work image. Defaults to `media-sage-agent-judge`.
 *   Sourced from env var `GCP_JUDGE_JOB_NAME`.
 * @property gcpCommentJobName Cloud Run Job name for the pr-comment-work image. Defaults to `media-sage-agent-comment`.
 *   Sourced from env var `GCP_COMMENT_JOB_NAME`.
 * @property googleCredentialsJson GCP service account JSON key decoded from the base64 value in env
 *   var `GOOGLE_CREDENTIALS_BASE64`. Used to authenticate Cloud Run API calls.
 * @property supabaseDbUrl PostgreSQL connection URL for the Supabase job registry, used for
 *   persistent dedup and job recovery across restarts. Sourced from env var `SUPABASE_DB_URL`.
 *@property pubSubWebhookSecret Shared secret token appended as `?token=` to the Pub/Sub push
 *   subscription URL. The orchestrator verifies this on every push delivery to reject spoofed
 *   requests. Sourced from env var `PUBSUB_WEBHOOK_SECRET`.
 * @property intelligentDispatchEnabled When true, [com.mediasage.orchestrator.service.BriefingService]
 *   generates a pre-dispatch briefing for every worker launch via the Claude Messages API,
 *   eliminating discovery turns and reducing cached token cost (~8x ROI per run). When false,
 *   the orchestrator acts as a pure dispatcher.
 *   Sourced from env var `INTELLIGENT_DISPATCH_ENABLED`; defaults to true.
 * @property anthropicBaseUrl Base URL for the Claude API used by [com.mediasage.orchestrator.service.BriefingService].
 *   Sourced from env var `ANTHROPIC_BASE_URL`; defaults to the Fuelix proxy.
 * @property anthropicAuthToken Bearer token for the Claude API used by [com.mediasage.orchestrator.service.BriefingService].
 *   Sourced from env var `ANTHROPIC_AUTH_TOKEN`.
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
    val gcpJudgeJobName: String = "media-sage-agent-judge",
    val gcpCommentJobName: String = "media-sage-agent-comment",
    val googleCredentialsJson: String = "",
    val supabaseDbUrl: String = "",
    val pubSubWebhookSecret: String = "",
    val intelligentDispatchEnabled: Boolean = true,
    val anthropicBaseUrl: String = "https://api.fuelix.ai",
    val anthropicAuthToken: String = "",
)
