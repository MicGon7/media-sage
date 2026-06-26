package com.mediasage.advisor.tools

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class QueryRunsToolTest {

    @Test
    fun `formatJobRows returns no-runs message for empty list`() {
        val result = formatJobRows(emptyList())
        assertEquals("No runs found.", result)
    }

    @Test
    fun `formatJobRows includes header and row data`() {
        val rows = listOf(
            JobRow(
                jobId = "aaaa-bbbb-cccc-dddd-eeee",
                ticketKey = "MS-123",
                status = "COMPLETED",
                createdAt = "2026-01-01T10:00:00",
                totalCostUsd = "0.042",
                numTurns = 12,
                failedGate = null,
            ),
        )
        val result = formatJobRows(rows)
        assertContains(result, "MS-123")
        assertContains(result, "COMPLETED")
        assertContains(result, "0.042")
        assertContains(result, "12")
        assertContains(result, "job_id")
    }

    @Test
    fun `formatJobRows renders dash for null optional fields`() {
        val rows = listOf(
            JobRow(
                jobId = "1111",
                ticketKey = "MS-1",
                status = "FAILED",
                createdAt = "2026-01-01T00:00:00",
                totalCostUsd = "-",
                numTurns = null,
                failedGate = "tests",
            ),
        )
        val result = formatJobRows(rows)
        assertContains(result, "FAILED")
        assertContains(result, "tests")
    }
}
