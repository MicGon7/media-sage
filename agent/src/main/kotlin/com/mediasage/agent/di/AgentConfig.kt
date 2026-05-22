package com.mediasage.agent.di

data class AgentConfig(
    val repoPath: String,
    val githubWebhookSecret: String,
    val jiraEmail: String,
    val jiraApiToken: String,
    val jiraCloudId: String,
    val jiraBotAccountId: String = "",
    val jiraBotEmail: String = "",
    val jiraBotApiToken: String = "",
    val verboseLogging: Boolean = false,
    // Cloud Run worker feature flag — false keeps existing local process behaviour
    val useCloudRunWorkers: Boolean = false,
    val gcpProjectId: String = "",
    val gcpRegion: String = "us-central1",
    val gcpJobName: String = "media-sage-agent-worker",
    val googleCredentialsJson: String = "",
    val supabaseDbUrl: String = "",
    // AgentBriefing feature flag — off by default until latency issues are resolved.
    // Set AGENT_BRIEFING_ENABLED=true to enable for demos.
    val agentBriefingEnabled: Boolean = false
)
