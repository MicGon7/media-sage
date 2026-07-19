package com.mediasage.advisor.tools

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertContains

class CompareRunsToolTest {

    private fun buildSummary(
        jobId: String,
        ticketKey: String,
        status: String,
        cost: BigDecimal? = null,
        turns: Int? = null,
        durationMs: Long? = null,
    ) = JobSummary(
        jobId = jobId,
        ticketKey = ticketKey,
        status = status,
        totalCostUsd = cost,
        numTurns = turns,
        claudeDurationMs = durationMs,
        inputTokens = null,
        outputTokens = null,
    )

    @Test
    fun `formatComparison includes both job IDs`() {
        val a = buildSummary("aaa", "MS-1", "COMPLETED", BigDecimal("0.030"), 8)
        val b = buildSummary("bbb", "MS-2", "FAILED", null, null)
        val result = formatComparison(a, b)
        assertContains(result, "aaa")
        assertContains(result, "bbb")
    }

    @Test
    fun `formatComparison shows status and cost for both runs`() {
        val a = buildSummary("a1", "MS-10", "COMPLETED", BigDecimal("0.050"), 15, 120_000)
        val b = buildSummary("b1", "MS-10", "COMPLETED", BigDecimal("0.035"), 10, 90_000)
        val result = formatComparison(a, b)
        assertContains(result, "0.050")
        assertContains(result, "0.035")
        assertContains(result, "COMPLETED")
    }

    @Test
    fun `formatComparison uses dash for null optional fields`() {
        val a = buildSummary("a2", "MS-5", "INTERRUPTED")
        val b = buildSummary("b2", "MS-5", "FAILED")
        val result = formatComparison(a, b)
        assertContains(result, "-")
        assertContains(result, "INTERRUPTED")
    }

    @Test
    fun `formatComparison shows model and effort for both runs`() {
        val a = buildSummary("a3", "MS-604", "COMPLETED").copy(modelVersion = "claude-sonnet-5", effort = "high")
        val b = buildSummary("b3", "MS-604", "COMPLETED")
            .copy(modelVersion = "claude-haiku-4-5-20251001", effort = "low")
        val result = formatComparison(a, b)
        assertContains(result, "model")
        assertContains(result, "claude-sonnet-5")
        assertContains(result, "claude-haiku-4-5-20251001")
        assertContains(result, "effort")
        assertContains(result, "high")
        assertContains(result, "low")
    }
}
