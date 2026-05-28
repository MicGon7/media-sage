package com.mediasage.pipeline.support

/**
 * Structured validation report for a pipeline E2E scenario.
 *
 * Records pass/fail checkpoints and prints a human-readable summary to stdout.
 * The non-zero exit from [assertAllPassed] makes the Gradle task fail when any
 * checkpoint is missed — compatible with CI and `deploy-orchestrator.yml`.
 *
 * Example output:
 * ```
 * ═══════════════════════════════════════════════════════
 *  Pipeline Scenario: Conflict Resolution
 * ═══════════════════════════════════════════════════════
 *  ✅ Job inserted (PENDING)
 *  ✅ Cloud Run dispatched (RUNNING)
 *  ✅ Job COMPLETED in Supabase
 *
 *  Turns: 23 · Duration: 4m 12s · Cost: $0.0841
 * ═══════════════════════════════════════════════════════
 *  PASS
 * ═══════════════════════════════════════════════════════
 * ```
 */
class ValidationReport(private val scenarioName: String) {

    private val checkpoints = mutableListOf<Pair<String, Boolean>>()
    private var metricsLine: String? = null

    /**
     * Records a named checkpoint and returns [passed] for inline chaining.
     *
     * Example: `val inserted = report.checkpoint("Job inserted", jobId != null)`
     */
    fun checkpoint(name: String, passed: Boolean): Boolean {
        checkpoints.add(name to passed)
        val icon = if (passed) "✅" else "❌"
        println(" $icon $name")
        return passed
    }

    /** Attaches a single-line metrics string to the report footer (turns, duration, cost). */
    fun metrics(value: String) {
        metricsLine = value
    }

    /** Prints the full bordered report to stdout. */
    fun print() {
        val border = "═".repeat(57)
        println()
        println(border)
        println(" Pipeline Scenario: $scenarioName")
        println(border)
        checkpoints.forEach { (name, passed) ->
            val icon = if (passed) "✅" else "❌"
            println(" $icon $name")
        }
        metricsLine?.let {
            println()
            println(" $it")
        }
        println()
        val allPassed = checkpoints.all { it.second }
        println(" ${if (allPassed) "PASS ✅" else "FAIL ❌"}")
        println(border)
        println()
    }

    /**
     * Throws [AssertionError] if any checkpoint failed.
     * Call after [print] to fail the Gradle task with a descriptive message.
     */
    fun assertAllPassed() {
        val failed = checkpoints.filter { !it.second }.map { it.first }
        check(failed.isEmpty()) {
            "Scenario '$scenarioName' FAILED — checkpoints: ${failed.joinToString(", ")}"
        }
    }
}
