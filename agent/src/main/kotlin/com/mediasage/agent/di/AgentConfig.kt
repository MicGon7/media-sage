package com.mediasage.agent.di

data class AgentConfig(
    val repoPath: String,
    val githubWebhookSecret: String,
    val jiraEmail: String,
    val jiraApiToken: String,
    val jiraCloudId: String,
    val jiraBotAccountId: String = "",
    val verboseLogging: Boolean = false,
    // Cloud Run worker feature flag — false keeps existing local process behaviour
    val useCloudRunWorkers: Boolean = false,
    val gcpProjectId: String = "",
    val gcpRegion: String = "us-central1",
    val gcpJobName: String = "media-sage-agent-worker",
    val googleCredentialsJson: String = "",
    val supabaseDbUrl: String = ""
)
