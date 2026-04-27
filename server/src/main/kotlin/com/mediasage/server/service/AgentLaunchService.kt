package com.mediasage.server.service

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

private const val BOOTSTRAP_PROMPT =
    "Your assigned ticket is %s. Retrieve it from Jira (cloudId: media-sage.atlassian.net), " +
    "read the description and acceptance criteria, then follow the Agent Guidelines in CLAUDE.md " +
    "to execute the full autonomous workflow."

/**
 * Spawns an autonomous Claude Code agent for a Jira ticket.
 * Guards against double-firing: a second launch call for the same ticket is a no-op
 * until the first agent process exits.
 */
class AgentLaunchService(private val repoPath: String) {

    private val log = Logger.getLogger(AgentLaunchService::class.java.name)
    private val activeTickets: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /**
     * Launches an autonomous agent for [ticketKey].
     * Returns true if the agent was spawned, false if one is already running for this ticket.
     */
    fun launch(ticketKey: String): Boolean {
        if (!activeTickets.add(ticketKey)) return false

        val command = listOf(
            "claude", "-p",
            BOOTSTRAP_PROMPT.format(ticketKey),
            "--dangerously-skip-permissions"
        )

        try {
            val process = ProcessBuilder(command)
                .directory(File(repoPath))
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start()

            log.info("Agent launched for $ticketKey (pid ${process.pid()})")

            Thread {
                try {
                    val exitCode = process.waitFor()
                    log.info("Agent for $ticketKey exited with code $exitCode")
                } finally {
                    activeTickets.remove(ticketKey)
                }
            }.also { it.isDaemon = true }.start()
        } catch (e: Exception) {
            activeTickets.remove(ticketKey)
            log.warning("Failed to launch agent for $ticketKey: ${e.message}")
        }

        return true
    }

    fun isActive(ticketKey: String): Boolean = ticketKey in activeTickets
}
