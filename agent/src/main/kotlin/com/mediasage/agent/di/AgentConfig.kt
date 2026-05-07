package com.mediasage.agent.di

data class AgentConfig(
    val repoPath: String,
    val githubWebhookSecret: String,
    val jiraEmail: String,
    val jiraApiToken: String,
    val jiraCloudId: String,
    val verboseLogging: Boolean = false
)
