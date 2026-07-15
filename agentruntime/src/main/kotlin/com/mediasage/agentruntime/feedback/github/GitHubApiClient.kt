package com.mediasage.agentruntime.feedback.github

interface GitHubApiClient {
    suspend fun hasOpenFeedbackPr(owner: String, repo: String): Boolean
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
    suspend fun getPrDetails(owner: String, repo: String, prNumber: Int): PrDetails
    suspend fun getPrDiff(owner: String, repo: String, prNumber: Int): String
}

data class FileContents(val content: String, val sha: String)

data class PrDetails(
    val title: String,
    val body: String,
    val headRef: String,
    val baseRef: String,
)
