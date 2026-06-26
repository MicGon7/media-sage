package com.mediasage.orchestrator.feedback.github

interface GitHubApiClient {
    suspend fun hasOpenAnalystPr(owner: String, repo: String): Boolean
    suspend fun getFileContents(owner: String, repo: String, path: String): FileContents
    suspend fun getBranchSha(owner: String, repo: String, branch: String): String
    suspend fun createBranch(owner: String, repo: String, name: String, sha: String)
    suspend fun updateFile(
        owner: String,
        repo: String,
        path: String,
        branch: String,
        content: String,
        currentSha: String,
    )
    suspend fun createPr(
        owner: String,
        repo: String,
        title: String,
        body: String,
        head: String,
        base: String,
    ): String
}

data class FileContents(val content: String, val sha: String)
