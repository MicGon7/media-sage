package com.mediasage.agent

import com.mediasage.agent.service.AgentLaunchService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for AgentLaunchService.
 *
 * Strategy: each test creates an anonymous subclass that overrides the three
 * protected-open extension points:
 *
 *   - createWorktree  — captures path/branchRef without running git
 *   - removeWorktree  — captures cleanup calls without running git
 *   - buildAgentProcess — returns a real but controlled OS process:
 *       • `cat`          blocks on stdin → process stays alive for concurrency tests
 *       • `sh -c exit 0` exits immediately → tests cleanup/teardown behaviour
 *
 * Why real processes instead of mocks? The service monitors a real Process object
 * via waitFor() inside a coroutine. A real blocking process is the simplest way to
 * keep the agent "alive" long enough to verify concurrent behaviour without
 * introducing a mocking library.
 */
class AgentLaunchServiceTest {

    private val scope = CoroutineScope(Dispatchers.IO)

    // Tracks processes started by blocking-service instances so we can clean them up.
    private val activeProcesses = mutableListOf<Process>()

    @AfterTest
    fun teardown() {
        // Forcibly destroy any processes left running by blocking-service instances.
        // Without this, `cat` processes would outlive the test suite.
        activeProcesses.forEach { it.destroyForcibly() }
        activeProcesses.clear()
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Returns a service whose process never exits (runs `cat`, blocking on stdin).
     * Use this when you need the agent to appear "in flight" during the assertion.
     */
    private fun blockingService(
        repoPath: String = "/repo",
        createdWorktrees: MutableList<String> = mutableListOf(),
        removedWorktrees: MutableList<String> = mutableListOf(),
    ): AgentLaunchService = object : AgentLaunchService(repoPath, scope) {
        override fun createWorktree(path: String, branchRef: String?): Boolean {
            createdWorktrees.add(path)
            return true
        }
        override fun removeWorktree(path: String) {
            removedWorktrees.add(path)
        }
        override fun buildAgentProcess(prompt: String, workDir: File): Process =
            ProcessBuilder("cat").start().also { activeProcesses.add(it) }
    }

    /**
     * Returns a service whose process exits immediately (runs `sh -c "exit 0"`).
     * Use this when you need to verify teardown behaviour after the agent completes.
     */
    private fun exitingService(
        repoPath: String = "/repo",
        onRemoveWorktree: (String) -> Unit = {},
    ): AgentLaunchService = object : AgentLaunchService(repoPath, scope) {
        override fun createWorktree(path: String, branchRef: String?) = true
        override fun removeWorktree(path: String) { onRemoveWorktree(path) }
        override fun buildAgentProcess(prompt: String, workDir: File): Process =
            ProcessBuilder("sh", "-c", "exit 0").start()
    }

    // ── Deduplication ────────────────────────────────────────────────────────

    @Test
    fun `first launch for a key returns true`() {
        assertTrue(blockingService().launch("MS-99"))
    }

    @Test
    fun `duplicate launch for the same key is ignored`() {
        val service = blockingService()
        assertTrue(service.launch("MS-99"))
        // Second call while the first agent is still running — must be a no-op.
        assertFalse(service.launch("MS-99"))
    }

    @Test
    fun `two different ticket keys both launch successfully`() {
        val service = blockingService()
        assertTrue(service.launch("MS-99"))
        // A different key must not be blocked by the first.
        assertTrue(service.launch("MS-100"))
    }

    // ── isActive ─────────────────────────────────────────────────────────────

    @Test
    fun `isActive returns false before any launch`() {
        assertFalse(blockingService().isActive("MS-99"))
    }

    @Test
    fun `isActive returns true while agent is running`() {
        val service = blockingService()
        service.launch("MS-99")
        assertTrue(service.isActive("MS-99"))
    }

    // ── Worktree path ─────────────────────────────────────────────────────────

    @Test
    fun `launch creates worktree at the correct sibling path`() {
        val created = mutableListOf<String>()
        val service = object : AgentLaunchService("/home/agent/media-sage", scope) {
            override fun createWorktree(path: String, branchRef: String?): Boolean {
                created.add(path)
                return false // return false → falls back to repoPath, no real process needed
            }
            override fun removeWorktree(path: String) { /* not called when createWorktree returns false */ }
        }
        service.launch("MS-99")
        // Worktrees dir is a sibling of the repo root, not inside it.
        assertEquals("/home/agent/media-sage-worktrees/MS-99", created.single())
    }

    @Test
    fun `launch uses no-checkout for Jira ticket worktree`() {
        // The Jira flow must pass null branchRef so createWorktree uses --no-checkout.
        // The agent creates its own branch — there is nothing to check out at worktree time.
        var capturedBranchRef: String? = "sentinel"
        val service = object : AgentLaunchService("/repo", scope) {
            override fun createWorktree(path: String, branchRef: String?): Boolean {
                capturedBranchRef = branchRef
                return false
            }
            override fun removeWorktree(path: String) { /* not called when createWorktree returns false */ }
        }
        service.launch("MS-99")
        assertNull(capturedBranchRef, "Jira launch must use --no-checkout (null branchRef)")
    }

    // ── Teardown ──────────────────────────────────────────────────────────────

    @Test
    fun `worktree is removed after agent exits`() {
        val removals = mutableListOf<String>()
        val latch = CountDownLatch(1)
        val service = exitingService(repoPath = "/repo") { path ->
            removals.add(path)
            latch.countDown()
        }
        service.launch("MS-99")
        assertTrue(latch.await(5, TimeUnit.SECONDS), "removeWorktree was not called within 5 s")
        assertEquals("/repo-worktrees/MS-99", removals.single())
    }

    @Test
    fun `key is released after agent exits so the ticket can be re-triggered`() {
        val latch = CountDownLatch(1)
        val service = object : AgentLaunchService("/repo", scope) {
            override fun createWorktree(path: String, branchRef: String?) = true
            override fun removeWorktree(path: String) { latch.countDown() }
            override fun buildAgentProcess(prompt: String, workDir: File): Process =
                ProcessBuilder("sh", "-c", "exit 0").start()
        }
        service.launch("MS-99")
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Agent did not complete within 5 s")
        // After exit the key must be cleared — a fresh launch for the same ticket must succeed.
        assertTrue(service.launch("MS-99"), "Key was not released after agent exited")
        // Clean up the second agent's process
        activeProcesses.forEach { it.destroyForcibly() }
    }
}
