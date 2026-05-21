package com.mediasage.agent.service

interface AgentLauncher {
    fun launch(ticketKey: String, ticketContent: String? = null, dryRun: Boolean = false): Boolean
    fun launchForPrReview(
        ticketKey: String,
        prNumber: Int,
        branchRef: String,
        commentBody: String,
        reviewerLogin: String
    ): Boolean
    fun launchForCommentReview(
        ticketKey: String,
        prNumber: Int,
        branchRef: String,
        commentBody: String
    ): Boolean
    fun postInlineCommentReply(prNumber: Int)
}
