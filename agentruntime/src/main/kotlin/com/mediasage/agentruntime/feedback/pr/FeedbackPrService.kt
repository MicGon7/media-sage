package com.mediasage.agentruntime.feedback.pr

import com.mediasage.agentruntime.AnthropicApi
import com.mediasage.agentruntime.feedback.detector.DetectedPattern
import com.mediasage.agentruntime.feedback.detector.PatternDetector
import com.mediasage.agentruntime.feedback.detector.label
import com.mediasage.agentruntime.feedback.github.GitHubApiClient
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.slf4j.LoggerFactory
import java.time.LocalDate

private val log = LoggerFactory.getLogger(FeedbackPrService::class.java)
private val json = Json { ignoreUnknownKeys = true }

class FeedbackPrService(
    private val detector: PatternDetector,
    private val githubClient: GitHubApiClient,
    private val httpClient: HttpClient,
    private val authToken: String,
    private val claudeBaseUrl: String,
    private val repoOwner: String,
    private val repoName: String,
    private val model: String,
) {
    private val patchProposalPrompt: String by lazy {
        FeedbackPrService::class.java
            .getResourceAsStream("/prompts/patch-proposal.md")
            ?.bufferedReader()
            ?.readText()
            ?: error("Prompt file not found at /prompts/patch-proposal.md")
    }

    suspend fun proposePatch() {
        val patterns = withContext(Dispatchers.IO) { detector.detectPatterns() }
        if (patterns.isEmpty()) {
            log.info("No recurring patterns detected — skipping PR")
            return
        }

        val hasOpenPr = runCatching { githubClient.hasOpenFeedbackPr(repoOwner, repoName) }
            .getOrElse { log.error("GitHub PR check failed — skipping: {}", it.message); return }
        if (hasOpenPr) {
            log.info("Open feedback PR already exists — skipping to avoid flooding")
            return
        }

        val pattern = patterns.first()
        val skillPath = SkillFileMapper.skillFileFor(pattern)
        log.info("Pattern detected: {} — proposing edit to {}", pattern.label(), skillPath)

        runCatching { openPr(pattern, skillPath, patterns) }
            .onSuccess { url -> log.info("Feedback PR opened: {}", url) }
            .onFailure { e -> log.error("Feedback PR failed for {}: {}", pattern.label(), e.message) }
    }

    private suspend fun openPr(pattern: DetectedPattern, skillPath: String, allPatterns: List<DetectedPattern>) {
        val currentFile = githubClient.getFileContents(repoOwner, repoName, skillPath)
        val proposedContent = synthesizePatch(allPatterns, currentFile.content, skillPath)
        val branchName = "feedback/scan-${LocalDate.now()}"
        val mainSha = githubClient.getBranchSha(repoOwner, repoName, "main")
        githubClient.createBranch(repoOwner, repoName, branchName, mainSha)
        githubClient.updateFile(repoOwner, repoName, skillPath, branchName, proposedContent, currentFile.sha)
        val prUrl = githubClient.createPr(
            owner = repoOwner,
            repo = repoName,
            title = "[Feedback] ${prTitle(pattern)}",
            body = prBody(allPatterns),
            head = branchName,
            base = "main",
        )
        log.info("PR created: {}", prUrl)
    }

    internal suspend fun synthesizePatch(
        patterns: List<DetectedPattern>,
        currentContent: String,
        skillPath: String,
    ): String {
        val request = ClaudeRequest(
            model = model,
            maxTokens = AnthropicApi.TokenBudget.STANDARD,
            system = patchProposalPrompt,
            messages = listOf(ClaudeMessage("user", buildUserMessage(patterns, currentContent, skillPath))),
            tools = listOf(buildPatchTool()),
            toolChoice = buildJsonObject { put("type", "tool"); put("name", "propose_patch") },
        )
        val response = httpClient.post("${claudeBaseUrl.trimEnd('/')}/v1/messages") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $authToken")
            header("anthropic-version", AnthropicApi.VERSION)
            setBody(request)
        }
        check(response.status.isSuccess()) {
            "Claude synthesis failed (${response.status}): ${response.bodyAsText()}"
        }
        val claudeResponse = response.body<ClaudeResponse>()
        val toolUse = claudeResponse.content.firstOrNull { it.type == "tool_use" }
            ?: error("No tool_use block in Claude synthesis response")
        val input = toolUse.input ?: error("No input in tool_use block")
        return json.decodeFromString<PatchResponse>(input.toString()).proposedContent
    }
}

private fun buildEvidence(pattern: DetectedPattern): String = when (pattern) {
    is DetectedPattern.GateFailure ->
        "Gate '${pattern.gate}' failed in ${pattern.runCount} runs within the last ${pattern.windowDays} days."
    is DetectedPattern.LowRubricScore ->
        "Rubric criterion '${pattern.criterion}' averaged " +
            "${"%.1f".format(pattern.avgScore)}/5 across ${pattern.runCount} runs " +
            "within the last ${pattern.windowDays} days."
}

private fun buildPatchTool(): JsonObject = buildJsonObject {
    put("name", "propose_patch")
    put("description", "Propose a minimal edit to the skill file")
    putJsonObject("input_schema") {
        put("type", "object")
        putJsonObject("properties") { putJsonObject("proposed_content") { put("type", "string") } }
        putJsonArray("required") { add("proposed_content") }
    }
}

private fun buildUserMessage(
    patterns: List<DetectedPattern>,
    currentContent: String,
    skillPath: String,
): String = """
    Evidence:
    ${patterns.joinToString("\n    ") { "- ${buildEvidence(it)}" }}

    Current skill file ($skillPath):
    ---
    $currentContent
    ---

    Propose a minimal edit — 1 to 2 sentences added, modified, or clarified — that would help
    workers avoid this pattern. Return the complete updated file content.
""".trimIndent()

private fun prTitle(pattern: DetectedPattern): String = when (pattern) {
    is DetectedPattern.GateFailure -> "Address recurring ${pattern.gate} gate failures"
    is DetectedPattern.LowRubricScore -> "Improve ${pattern.criterion.replace('_', ' ')} score"
}

private fun prBody(patterns: List<DetectedPattern>): String {
    val evidenceSummary = patterns.joinToString("; ") { buildEvidence(it) }
    return "Detected by the feedback scanner: $evidenceSummary. " +
        "Review the proposed edit to the skill file and merge if the change looks right. " +
        "This PR was opened automatically by the feedback scanner and never self-merges."
}

// ---- Claude request/response types (private to this file) ----

@Serializable
private data class ClaudeRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val system: String,
    val messages: List<ClaudeMessage>,
    val tools: List<JsonObject>,
    @SerialName("tool_choice") val toolChoice: JsonObject,
)

@Serializable
private data class ClaudeMessage(val role: String, val content: String)

@Serializable
private data class ClaudeResponse(val content: List<ContentBlock>)

@Serializable
private data class ContentBlock(
    val type: String = "",
    val input: JsonObject? = null,
)

@Serializable
private data class PatchResponse(
    @SerialName("proposed_content") val proposedContent: String,
)
