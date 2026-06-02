package com.mediasage.agent.service

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

private const val BRIEFING_MODEL = "claude-haiku-4-5-20251001"
private const val MAX_DIFF_LINES = 500
private const val MAX_TOKENS = 1024

private val log = LoggerFactory.getLogger(HttpBriefingService::class.java)

/**
 * Generates a pre-dispatch briefing for workers by calling the Claude Messages API with Haiku.
 *
 * The briefing eliminates the worker's discovery turns — instead of spending the first 1-3 turns
 * figuring out what to do, the worker receives a concise summary of the task, the relevant
 * context, and a clear action plan before the Cloud Run Job starts.
 *
 * This service is only instantiated when `INTELLIGENT_DISPATCH_ENABLED=true`. When disabled,
 * [AgentLaunchService] receives `null` and dispatches without a briefing.
 *
 * @param httpClient Shared Ktor client — must be configured with JSON content negotiation.
 * @param anthropicBaseUrl Base URL for the Claude API (e.g. `https://api.fuelix.ai`).
 * @param anthropicAuthToken Bearer token for the Claude API.
 */
/**
 * [BriefingService] implementation that calls the Claude Messages API using Haiku.
 *
 * Uses the existing Ktor [HttpClient] rather than the `anthropic-java` SDK.
 * The SDK uses CompletableFuture with no native coroutine support, which would require a
 * `future.await()` bridge running on a thread pool we don't control. The Messages API is a
 * single POST — using the existing Ktor client avoids the dependency and stays in the
 * project's async model.
 *
 * @param httpClient Dedicated briefing client with a 5s timeout (see [AgentModule]).
 * @param anthropicBaseUrl Base URL for the Claude API (e.g. `https://api.fuelix.ai`).
 * @param anthropicAuthToken Bearer token for the Claude API.
 */
class HttpBriefingService(
    private val httpClient: HttpClient,
    private val anthropicBaseUrl: String,
    private val anthropicAuthToken: String,
) : BriefingService {

    override suspend fun brief(context: BriefingContext): String? = runCatching {
        val prompt = buildPrompt(context)
        val request = MessagesRequest(
            model = BRIEFING_MODEL,
            maxTokens = MAX_TOKENS,
            messages = listOf(Message(role = "user", content = prompt)),
        )
        val response: MessagesResponse = httpClient.post("$anthropicBaseUrl/v1/messages") {
            contentType(ContentType.Application.Json)
            header("x-api-key", anthropicAuthToken)
            header("anthropic-version", "2023-06-01")
            setBody(request)
        }.body()
        response.content.firstOrNull()?.text?.takeIf { it.isNotBlank() }
    }.onFailure { log.warn("Briefing call failed for ${context.ticketKey()}: ${it.message}") }
        .getOrNull()

    private fun buildPrompt(context: BriefingContext): String = when (context) {
        is BriefingContext.TicketWork -> ticketWorkPrompt(context)
        is BriefingContext.PrReview -> prReviewPrompt(context)
        is BriefingContext.CommentReview -> commentReviewPrompt(context)
        is BriefingContext.ConflictResolution -> conflictResolutionPrompt(context)
    }

    private fun ticketWorkPrompt(ctx: BriefingContext.TicketWork) = """
        You are briefing a software engineer about to start work on a Jira ticket.
        Summarize in 3-5 sentences: what needs to be built, why, and which files or modules are likely involved.
        Be concrete and direct — the engineer will use this to start coding immediately.

        Ticket: ${ctx.ticketKey}
        Content:
        ${ctx.ticketContent}
    """.trimIndent()

    private fun prReviewPrompt(ctx: BriefingContext.PrReview) = """
        You are briefing a software engineer about to address a PR review comment.
        Summarize in 3-5 sentences: what the reviewer wants changed, where in the diff the change is needed, and what the fix looks like.
        Be specific — reference file names and line context from the diff where possible.

        Ticket: ${ctx.ticketKey}
        PR: #${ctx.prNumber}
        Reviewer comment: ${ctx.commentBody}
        Diff (first $MAX_DIFF_LINES lines):
        ${ctx.diff.lines().take(MAX_DIFF_LINES).joinToString("\n")}
    """.trimIndent()

    private fun commentReviewPrompt(ctx: BriefingContext.CommentReview) = """
        You are briefing a software engineer about to answer a question left on a PR.
        Summarize in 2-3 sentences: what the reviewer is asking and what context from the codebase is needed to answer well.
        The engineer will post a comment reply — no code changes.

        Ticket: ${ctx.ticketKey}
        PR: #${ctx.prNumber}
        Comment: ${ctx.commentBody}
    """.trimIndent()

    private fun conflictResolutionPrompt(ctx: BriefingContext.ConflictResolution) = """
        You are briefing a software engineer about to resolve a merge conflict.
        Summarize in 3-4 sentences: which branch conflicted, what the likely cause is based on the branch name, and what to watch for when rebasing.

        Ticket: ${ctx.ticketKey}
        PR: #${ctx.prNumber}
        Branch: ${ctx.branchRef}
        Base branch: ${ctx.baseBranch}
    """.trimIndent()

    private fun BriefingContext.ticketKey(): String = when (this) {
        is BriefingContext.TicketWork -> ticketKey
        is BriefingContext.PrReview -> ticketKey
        is BriefingContext.CommentReview -> ticketKey
        is BriefingContext.ConflictResolution -> ticketKey
    }
}

@Serializable
internal data class MessagesRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val messages: List<Message>,
)

@Serializable
internal data class Message(
    val role: String,
    val content: String,
)

@Serializable
internal data class MessagesResponse(
    val content: List<ContentBlock>,
)

@Serializable
internal data class ContentBlock(
    val type: String,
    val text: String = "",
)
