package com.mediasage.analyst.pr

import com.mediasage.analyst.detector.DetectedPattern
import com.mediasage.analyst.detector.PatternDetector
import com.mediasage.analyst.detector.label
import com.mediasage.analyst.github.GitHubApiClient
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

private val log = LoggerFactory.getLogger(SkillPrService::class.java)
private val json = Json { ignoreUnknownKeys = true }

private const val CLAUDE_MODEL = "claude-sonnet-4-6"
private const val ANTHROPIC_API_VERSION = "2023-06-01"
private const val MAX_TOKENS = 4096
private const val SYNTHESIS_SYSTEM_PROMPT =
    "You are reviewing an AI agent skill file. Propose a precise, minimal change that addresses " +
        "the identified pattern. Do not rewrite sections unrelated to the pattern."

class SkillPrService(
    private val detector: PatternDetector,
    private val githubClient: GitHubApiClient,
    private val httpClient: HttpClient,
    private val authToken: String,
    private val claudeBaseUrl: String,
    private val repoOwner: String,
    private val repoName: String,
) {
    suspend fun maybeOpenPr() {
        val patterns = withContext(Dispatchers.IO) { detector.detectPatterns() }
        if (patterns.isEmpty()) {
            log.info("No recurring patterns detected — skipping PR")
            return
        }

        if (githubClient.hasOpenAnalystPr(repoOwner, repoName)) {
            log.info("Open Analyst PR already exists — skipping to avoid flooding")
            return
        }

        val pattern = patterns.first()
        val skillPath = SkillFileMapper.skillFileFor(pattern)
        log.info("Pattern detected: {} — proposing edit to {}", pattern.label(), skillPath)

        runCatching { openPr(pattern, skillPath) }
            .onSuccess { url -> log.info("Analyst PR opened: {}", url) }
            .onFailure { e -> log.error("Analyst PR failed for {}: {}", pattern.label(), e.message) }
    }

    private suspend fun openPr(pattern: DetectedPattern, skillPath: String) {
        val currentFile = githubClient.getFileContents(repoOwner, repoName, skillPath)
        val proposedContent = synthesizePatch(pattern, currentFile.content, skillPath)
        val branchName = "feedback/analyst-${LocalDate.now()}"
        val mainSha = githubClient.getBranchSha(repoOwner, repoName, "main")
        githubClient.createBranch(repoOwner, repoName, branchName, mainSha)
        githubClient.updateFile(repoOwner, repoName, skillPath, branchName, proposedContent, currentFile.sha)
        val prUrl = githubClient.createPr(
            owner = repoOwner,
            repo = repoName,
            title = "[Analyst] ${prTitle(pattern)}",
            body = prBody(pattern, skillPath),
            head = branchName,
            base = "main",
        )
        log.info("PR created: {}", prUrl)
    }

    internal suspend fun synthesizePatch(
        pattern: DetectedPattern,
        currentContent: String,
        skillPath: String,
    ): String {
        val request = ClaudeRequest(
            model = CLAUDE_MODEL,
            maxTokens = MAX_TOKENS,
            system = SYNTHESIS_SYSTEM_PROMPT,
            messages = listOf(ClaudeMessage("user", buildUserMessage(pattern, currentContent, skillPath))),
            tools = listOf(buildPatchTool()),
            toolChoice = buildJsonObject { put("type", "tool"); put("name", "propose_patch") },
        )
        val response = httpClient.post("${claudeBaseUrl.trimEnd('/')}/v1/messages") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $authToken")
            header("anthropic-version", ANTHROPIC_API_VERSION)
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

private fun buildUserMessage(pattern: DetectedPattern, currentContent: String, skillPath: String): String = """
    Evidence: ${buildEvidence(pattern)}

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

private fun prBody(pattern: DetectedPattern, skillPath: String): String {
    val evidence = when (pattern) {
        is DetectedPattern.GateFailure ->
            "**Gate:** `${pattern.gate}`  \n**Occurrences:** ${pattern.runCount} in the last ${pattern.windowDays} days"
        is DetectedPattern.LowRubricScore ->
            "**Criterion:** `${pattern.criterion}`  \n" +
                "**Average score:** ${"%.1f".format(pattern.avgScore)}/5 across " +
                "${pattern.runCount} runs in the last ${pattern.windowDays} days"
    }
    return """
        ## Pattern detected

        $evidence

        ## Proposed change

        The diff below shows the proposed edit to `$skillPath`.
        Review it, adjust if needed, and merge if the change looks right.

        > This PR was opened automatically by the Analyst. It never self-merges.
    """.trimIndent()
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
