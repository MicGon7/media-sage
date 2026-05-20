package com.mediasage.agent.service

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

private const val BRIEFING_PROMPT = """You are preparing a concise briefing for an autonomous agent about to implement a Jira ticket.

Analyze the codebase and respond with ONLY the following three sections — no preamble, no extra commentary:

## Relevant Files
List the 3–5 most important files the agent should read first to understand the context for this task.

## Existing Pattern
Identify the pattern this task should follow. Give the file path of the best existing example.

## CLAUDE.md Constraints
List any constraints from CLAUDE.md that are specifically relevant to this task (e.g. screen parameter rules, expect/actual warnings, CompositionLocal guidance). Omit general reminders — only constraints that apply here.

Ticket: %s

%s"""

/**
 * Runs a bounded `claude -p` call against the local repo before worker dispatch.
 * Produces a concise briefing (relevant files, patterns, constraints) so the worker
 * can skip codebase exploration and go straight to implementation.
 *
 * If the briefing fails or times out, returns an empty string — dispatch always proceeds.
 */
class AgentBriefing(
    private val repoPath: String,
    private val timeoutSeconds: Long = 180L
) {

    private val log = Logger.getLogger(AgentBriefing::class.java.name)

    fun prepare(ticketKey: String, ticketContent: String): String {
        val prompt = BRIEFING_PROMPT.format(ticketKey, ticketContent)
        log.info("[$ticketKey] AgentBriefing starting (timeout ${timeoutSeconds}s)...")
        return try {
            val pb = ProcessBuilder("claude", "-p", prompt, "--max-turns", "3", "--output-format", "text")
                .directory(java.io.File(repoPath))
                .redirectInput(ProcessBuilder.Redirect.from(java.io.File("/dev/null")))
                .redirectErrorStream(true)

            // Inherit auth env vars so the subprocess can authenticate.
            // In the Cloud Run worker these come from the container environment.
            // Locally they may not be in the shell env if stored only in settings.json.
            val env = pb.environment()
            System.getenv("ANTHROPIC_AUTH_TOKEN")?.let { env["ANTHROPIC_AUTH_TOKEN"] = it }
            System.getenv("ANTHROPIC_BASE_URL")?.let { env["ANTHROPIC_BASE_URL"] = it }
            System.getenv("ANTHROPIC_API_KEY")?.let { env["ANTHROPIC_API_KEY"] = it }

            val process = pb.start()

            // Read output on a separate thread — avoids pipe buffer deadlock if output is large,
            // and lets waitFor() enforce the timeout independently.
            val executor = Executors.newSingleThreadExecutor()
            val outputFuture = executor.submit<String> {
                process.inputStream.bufferedReader().readText()
            }
            executor.shutdown()

            val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                log.warning("[$ticketKey] AgentBriefing timed out after ${timeoutSeconds}s — proceeding without briefing")
                return ""
            }

            val output = outputFuture.get().trim()
            if (process.exitValue() != 0 || output.isBlank()) {
                log.warning("[$ticketKey] AgentBriefing exited with code ${process.exitValue()} — proceeding without briefing. Output: ${output.take(500)}")
                return ""
            }

            log.info("[$ticketKey] AgentBriefing prepared (${output.length} chars)")
            output
        } catch (e: Exception) {
            log.warning("[$ticketKey] AgentBriefing failed: ${e.message} — proceeding without briefing")
            ""
        }
    }
}
