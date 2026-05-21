package com.mediasage.agent.service

import java.io.File
import org.slf4j.LoggerFactory

interface WorktreeManager {
    fun createWorktree(path: String, branchRef: String? = null): Boolean
    fun removeWorktree(path: String)
    fun buildAgentProcess(prompt: String, workDir: File): Process
}

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
