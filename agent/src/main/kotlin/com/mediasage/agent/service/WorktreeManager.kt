package com.mediasage.agent.service

import java.io.File
import org.slf4j.LoggerFactory

/**
 * Manages git worktrees for isolated Claude Code agent runs.
 *
 * Each agent invocation operates in its own worktree so concurrent jobs do not interfere with
 * each other or with the main repo checkout.
 */
interface WorktreeManager {
    /**
     * Creates a git worktree at [path], optionally checked out to [branchRef].
     *
     * @param path Absolute path where the worktree will be created.
     * @param branchRef Branch or commit ref to check out; if null, the worktree is created with
     *   `--no-checkout`.
     * @return `true` if the worktree was created successfully; `false` if creation failed or fell
     *   back to the main repo path.
     */
    fun createWorktree(path: String, branchRef: String? = null): Boolean

    /**
     * Force-removes the git worktree at [path].
     *
     * @param path Absolute path of the worktree to remove.
     */
    fun removeWorktree(path: String)

    /**
     * Builds and starts a `claude -p` process for the given [prompt] in [workDir].
     *
     * The process runs with `--dangerously-skip-permissions` and `--output-format stream-json` so
     * the caller can stream structured JSON output.
     *
     * @param prompt The prompt passed to Claude Code via `-p`.
     * @param workDir Working directory for the process (typically the worktree root).
     * @return A started [Process] running the Claude Code CLI.
     */
    fun buildAgentProcess(prompt: String, workDir: File): Process
}

/**
 * Production [WorktreeManager] backed by `git worktree` shell commands and the `claude` CLI.
 *
 * @param repoPath Absolute path to the main repository used as the working directory for git
 *   commands.
 */
class DefaultWorktreeManager(private val repoPath: String) : WorktreeManager {

    private val log = LoggerFactory.getLogger(DefaultWorktreeManager::class.java)

    override fun createWorktree(path: String, branchRef: String?): Boolean = try {
        val args = if (branchRef != null) {
            listOf("git", "worktree", "add", path, branchRef)
        } else {
            listOf("git", "worktree", "add", "--no-checkout", path)
        }
        val exitCode = ProcessBuilder(args)
            .directory(File(repoPath))
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()
            .waitFor()
        if (exitCode != 0) log.warn("Worktree creation failed at $path — running agent in repoPath.")
        exitCode == 0
    } catch (e: Exception) {
        log.warn("Worktree creation failed at $path: ${e.message}. Running agent in repoPath.")
        false
    }

    override fun removeWorktree(path: String) {
        ProcessBuilder("git", "worktree", "remove", "--force", path)
            .directory(File(repoPath))
            .start()
            .waitFor()
    }

    override fun buildAgentProcess(prompt: String, workDir: File): Process =
        ProcessBuilder(claudeCommand(prompt))
            .directory(workDir)
            .redirectInput(ProcessBuilder.Redirect.from(File("/dev/null")))
            .start()
}

private fun claudeCommand(prompt: String) = listOf(
    "claude", "-p", prompt,
    "--dangerously-skip-permissions",
    "--output-format", "stream-json",
    "--verbose"
)
