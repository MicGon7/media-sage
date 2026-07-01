package com.mediasage.agentruntime.evaluation

import com.mediasage.agentruntime.AnthropicApi
import com.mediasage.agentruntime.AnthropicClient
import com.mediasage.agentruntime.feedback.github.GitHubApiClient
import com.mediasage.agentruntime.feedback.github.PrDetails
import com.mediasage.agentruntime.service.JiraApiClient
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(ClaudeAgentService::class.java)

private val responseJson = Json { ignoreUnknownKeys = true }

class ClaudeAgentService(
    private val anthropicClient: AnthropicClient,
    private val githubApiClient: GitHubApiClient,
    private val jiraApiClient: JiraApiClient,
    private val model: String,
    private val repoOwner: String,
    private val repoName: String,
) : AgentService {

    private val systemPromptTemplate: String by lazy {
        ClaudeAgentService::class.java.getResourceAsStream("/prompts/judge-evaluation.md")
            ?.bufferedReader()
            ?.readText()
            ?: error("Judge prompt not found at /prompts/judge-evaluation.md")
    }

    override suspend fun evaluate(ticketKey: String, prNumber: Int) {
        log.info("[$ticketKey] Starting AC compliance evaluation for PR #$prNumber")
        runCatching {
            val pr = githubApiClient.getPrDetails(repoOwner, repoName, prNumber)
            val diff = githubApiClient.getPrDiff(repoOwner, repoName, prNumber)
            val ticketContent = jiraApiClient.getTicketContent(ticketKey)
            val fetchOutput = buildFetchOutput(ticketKey, prNumber, pr, diff, ticketContent)
            val verdict = callClaude(fetchOutput, ticketKey, prNumber)
            githubApiClient.postPrComment(repoOwner, repoName, prNumber, formatPrComment(verdict))
            jiraApiClient.addComment(ticketKey, verdict)
            postInlineObservations(ticketKey, prNumber, verdict)
            log.info("[$ticketKey] AC compliance evaluation complete for PR #$prNumber")
        }.onFailure { e ->
            log.error("[$ticketKey] AC compliance evaluation failed for PR #$prNumber: ${e.message}", e)
        }
    }

    private suspend fun postInlineObservations(ticketKey: String, prNumber: Int, verdict: String) {
        parseCodeObservations(verdict).forEach { obs ->
            runCatching {
                githubApiClient.postInlineReviewComment(repoOwner, repoName, prNumber, obs.path, obs.line, obs.body)
            }.onFailure { e ->
                log.warn("[$ticketKey] Inline comment skipped for ${obs.path}:${obs.line}: ${e.message}")
            }
        }
    }

    private fun parseCodeObservations(verdict: String): List<CodeObservation> {
        val section = verdict.substringAfter("Code observations:", "").trim()
        if (section.isBlank()) return emptyList()
        val pattern = Regex("""^- (.+?):(\d+) — (.+)$""", RegexOption.MULTILINE)
        return pattern.findAll(section).mapNotNull { m ->
            val line = m.groupValues[2].toIntOrNull() ?: return@mapNotNull null
            CodeObservation(path = m.groupValues[1], line = line, body = m.groupValues[3].trim())
        }.toList()
    }

    private suspend fun callClaude(fetchOutput: String, ticketKey: String, prNumber: Int): String {
        val systemPrompt = systemPromptTemplate
            .replace("{TICKET_KEY}", ticketKey)
            .replace("{PR_NUMBER}", prNumber.toString())
        val responseText = anthropicClient.post(buildJsonBody(systemPrompt, fetchOutput))
        val claudeResponse = responseJson.decodeFromString<ClaudeResponse>(responseText)
        return claudeResponse.content.firstOrNull { it.type == "text" }?.text?.trim()
            ?: error("No text block in Claude response")
    }

    private fun buildJsonBody(systemPrompt: String, userContent: String): String {
        val sys = escapeJson(systemPrompt)
        val usr = escapeJson(userContent)
        val maxTokens = AnthropicApi.TokenBudget.STANDARD
        return """{"model":"$model","max_tokens":$maxTokens,"system":"$sys","messages":[{"role":"user","content":"$usr"}]}"""
    }

    private fun escapeJson(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")

    private fun formatPrComment(verdict: String): String =
        verdict.replace("🤖 Agent:", "🤖 **Agent:**", ignoreCase = false) +
            "\n\nThis verdict is informational. The human reviewer makes the final call."
}

private data class CodeObservation(val path: String, val line: Int, val body: String)

private data class DiffSignals(
    val changedFiles: List<String>,
    val testFiles: List<String>,
    val sharedFiles: List<String>,
    val added: Int,
    val removed: Int,
)

private fun analyzeDiff(diff: String): DiffSignals {
    val lines = diff.lines()
    val changedFiles = lines.filter { it.startsWith("+++ b/") }.map { it.removePrefix("+++ b/") }
    val testFiles = changedFiles.filter { it.contains("Test") || it.contains("/test/") }
    val sharedRe = Regex(
        "repository|Repository|Database|database|Module\\.kt|di/" +
            "|mapper|Mapper|Entity\\.kt|Dao\\.kt|Api\\.kt|Config\\.kt"
    )
    val sharedFiles = changedFiles.filter { sharedRe.containsMatchIn(it) && it !in testFiles }
    return DiffSignals(
        changedFiles = changedFiles,
        testFiles = testFiles,
        sharedFiles = sharedFiles,
        added = lines.count { it.startsWith("+") && !it.startsWith("+++") },
        removed = lines.count { it.startsWith("-") && !it.startsWith("---") },
    )
}

private fun buildFetchOutput(ticketKey: String, prNumber: Int, pr: PrDetails, diff: String, ticketContent: String?): String {
    val signals = analyzeDiff(diff)
    return buildString {
        appendLine("JIRA_KEY=$ticketKey")
        appendLine("PR_NUMBER=$prNumber")
        appendLine()
        appendLine("━━━ PR METADATA ━━━")
        appendLine("Title:  ${pr.title}")
        appendLine("Branch: ${pr.headRef} → ${pr.baseRef}")
        appendLine("Diff size: +${signals.added} -${signals.removed} lines across ${signals.changedFiles.size} file(s)")
        appendLine()
        appendLine("━━━ PR BODY ━━━")
        appendLine(pr.body.ifBlank { "(empty)" })
        appendLine()
        appendLine("━━━ JIRA TICKET ($ticketKey) ━━━")
        appendLine(ticketContent ?: "(could not fetch Jira ticket)")
        appendLine()
        appendLine("━━━ DIFF SIGNALS ━━━")
        appendLine("Test files in diff:                    ${signals.testFiles.ifEmpty { listOf("none") }}")
        appendLine("Shared infra files (regression risk):  ${signals.sharedFiles.ifEmpty { listOf("none") }}")
        appendLine("All changed files:                     ${signals.changedFiles}")
        appendLine()
        appendLine("━━━ PR DIFF ━━━")
        append(diff)
    }
}

@Serializable
private data class ClaudeResponse(val content: List<ContentBlock>)

@Serializable
private data class ContentBlock(
    val type: String = "",
    val text: String = "",
    val input: JsonObject? = null,
)
