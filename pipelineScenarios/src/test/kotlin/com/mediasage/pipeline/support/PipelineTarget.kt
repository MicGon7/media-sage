package com.mediasage.pipeline.support

/**
 * Per-client pipeline configuration: the three things a client must provide
 * to point the agent pipeline at their project.
 *
 * Everything else — GCP Cloud Run Jobs, Pub/Sub, Anthropic/Fuelix — is shared
 * infrastructure reused across all targets. Only these are client-specific.
 *
 * All values are read from environment variables injected by the Gradle task.
 * The task is responsible for mapping client-specific machine env vars
 * (e.g. `<CLIENT>_SUPABASE_DB_URL`) to the standard names this class reads.
 * No project names are hardcoded here.
 */
data class PipelineTarget(
    /** Jira project key (e.g. "MS"). Used as ticket key prefix in branch names. */
    val jiraProjectKey: String,
    /** GitHub org or user that owns the target repo (e.g. "michael-gonzalez-dev"). */
    val githubOwner: String,
    /** GitHub repository name (e.g. "media-sage"). */
    val githubRepo: String,
    /** Supabase Postgres connection URL for this client's job registry. */
    val supabaseDbUrl: String,
    /** Base URL of the orchestrator Cloud Run Service pointed at this client's Supabase. */
    val orchestratorUrl: String,
    /** GitHub webhook secret for HMAC-SHA256 signature computation. */
    val webhookSecret: String,
    /**
     * Ticket key used in fixture branch names for full pipeline scenarios
     * (e.g. "MS-262"). The orchestrator extracts this key via the
     * [A-Z]+-\d+ regex and uses it for Jira comment routing.
     */
    val fixtureTicketKey: String
) {
    companion object {
        /**
         * Builds a [PipelineTarget] from standard environment variable names.
         * The Gradle task that invokes the scenario is responsible for populating
         * these vars — mapping client-specific machine env vars to these names.
         */
        fun fromEnv(): PipelineTarget = PipelineTarget(
            jiraProjectKey = envRequired("JIRA_PROJECT_KEY"),
            githubOwner = envRequired("GITHUB_OWNER"),
            githubRepo = envRequired("GITHUB_REPO"),
            supabaseDbUrl = envRequired("SUPABASE_DB_URL"),
            orchestratorUrl = envOptional("ORCHESTRATOR_URL"),
            webhookSecret = envOptional("GITHUB_WEBHOOK_SECRET"),
            fixtureTicketKey = envRequired("FIXTURE_TICKET_KEY")
        )

        private fun envRequired(name: String) = System.getenv(name)?.takeIf { it.isNotBlank() }
            ?: error("Missing required env var: $name")

        private fun envOptional(name: String) = System.getenv(name)?.takeIf { it.isNotBlank() } ?: ""
    }
}
