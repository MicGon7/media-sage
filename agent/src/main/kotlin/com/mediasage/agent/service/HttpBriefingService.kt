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
// 4096 gives Haiku room to brief complex multi-file tickets without truncation.
// Simple tickets produce shorter output naturally; this is a ceiling, not a target.
private const val MAX_TOKENS = 4096

private val log = LoggerFactory.getLogger(HttpBriefingService::class.java)

/**
 * [BriefingService] implementation that calls the Claude Messages API via HTTP.
 *
 * Named for its transport (HTTP), not the model — [BRIEFING_MODEL] is a configuration value
 * that can change independently of the service. The briefing eliminates worker discovery turns:
 * instead of spending the first 1-3 turns figuring out what to do, the worker receives a concise
 * summary of the task, relevant context, and a clear action plan before the Cloud Run Job starts.
 *
 * Uses the existing Ktor [HttpClient] rather than the `anthropic-java` SDK. The SDK uses
 * CompletableFuture with no native coroutine support, which would require a `future.await()`
 * bridge running on a thread pool we don't control. The Messages API is a single POST — using
 * Ktor avoids the dependency and stays in the project's async model.
 *
 * Only instantiated when `INTELLIGENT_DISPATCH_ENABLED=true`. When disabled, [AgentLaunchService]
 * receives `null` and dispatches without a briefing.
 *
 * @param httpClient Dedicated briefing client with a 15s timeout (see [AgentModule]) — not the
 *   shared client. The tighter timeout enforces the briefing budget without affecting other calls.
 * @param anthropicBaseUrl Base URL for the Claude API (e.g. `https://api.fuelix.ai`).
 * @param anthropicAuthToken Bearer token for the Claude API.
 */
class HttpBriefingService(
    private val httpClient: HttpClient,
    private val anthropicBaseUrl: String,
    private val anthropicAuthToken: String,
) : BriefingService {

    /**
     * Calls the Claude Messages API ([BRIEFING_MODEL], [MAX_TOKENS] tokens) to generate a
     * briefing for [context].
     *
     * Trims diff content to at most [MAX_DIFF_LINES] lines before sending to stay within token
     * budget. On any failure (network error, timeout, non-OK status), logs a warning and returns
     * null — dispatch is never blocked by a briefing failure.
     *
     * @param context Describes the work scenario. The concrete subtype selects the prompt template:
     *   - [BriefingContext.TicketWork] — summarises the ticket description and acceptance criteria;
     *     used when a new ticket is assigned to the bot.
     *   - [BriefingContext.PrReview] — explains what the reviewer wants changed and where in the
     *     diff; used for formal "changes requested" reviews.
     *   - [BriefingContext.CommentReview] — frames the reviewer's question and the codebase context
     *     needed to answer it; used for PR comments that do not request code changes.
     *   - [BriefingContext.ConflictResolution] — describes which branch conflicted and what to
     *     watch for when rebasing; used when a branch is ejected from the merge queue.
     */
    override suspend fun brief(context: BriefingContext): String? = runCatching {
        val prompt = buildPrompt(context)
        val request = MessagesRequest(
            model = BRIEFING_MODEL,
            maxTokens = MAX_TOKENS,
            messages = listOf(Message(role = "user", content = prompt)),
        )
        val response: MessagesResponse = httpClient.post("$anthropicBaseUrl/v1/messages") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $anthropicAuthToken")
            header("anthropic-version", "2023-06-01")
            setBody(request)
        }.body()
        response.content.firstOrNull()?.text?.takeIf { it.isNotBlank() }
            ?.also { log.info("Briefing content for ${context.ticketKey()}: ${it.replace('\n', ' ')}") }
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
        Cover everything they need to start coding immediately: what needs to be built, why it matters,
        which files and modules are involved, and any non-obvious constraints or patterns to follow.
        Be as detailed as the task requires — a complex multi-file change needs more context than a one-liner.

        Ticket: ${ctx.ticketKey}
        Content:
        ${ctx.ticketContent}
    """.trimIndent()

    private fun prReviewPrompt(ctx: BriefingContext.PrReview) = """
        You are briefing a software engineer about to address a PR review comment.
        Explain what the reviewer wants changed, where in the diff the change is needed, and what the fix looks like.
        Reference specific file names and line context from the diff. Cover every concern the reviewer raised.

        Ticket: ${ctx.ticketKey}
        PR: #${ctx.prNumber}
        Reviewer comment: ${ctx.commentBody}
        Diff (first $MAX_DIFF_LINES lines):
        ${ctx.diff.lines().take(MAX_DIFF_LINES).joinToString("\n")}
    """.trimIndent()

    private fun commentReviewPrompt(ctx: BriefingContext.CommentReview) = """
        You are briefing a software engineer about to answer a question left on a PR.
        Explain what the reviewer is asking and what codebase context is needed to answer well.
        The engineer will post a comment reply — no code changes.

        Ticket: ${ctx.ticketKey}
        PR: #${ctx.prNumber}
        Comment: ${ctx.commentBody}
    """.trimIndent()

    private fun conflictResolutionPrompt(ctx: BriefingContext.ConflictResolution) = """
        You are briefing a software engineer about to resolve a merge conflict.
        Explain which branch conflicted, what the likely cause is based on the branch name, and what to watch for when rebasing.
        Include any patterns in the branch name that suggest which files are likely affected.

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
