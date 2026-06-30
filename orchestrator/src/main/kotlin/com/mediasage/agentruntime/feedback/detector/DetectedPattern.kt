package com.mediasage.agentruntime.feedback.detector

sealed class DetectedPattern {
    data class GateFailure(
        val gate: String,
        val runCount: Int,
        val windowDays: Int,
    ) : DetectedPattern()

    data class LowRubricScore(
        val criterion: String,
        val avgScore: Double,
        val runCount: Int,
        val windowDays: Int,
    ) : DetectedPattern()
}

fun DetectedPattern.label(): String = when (this) {
    is DetectedPattern.GateFailure -> "gate:$gate"
    is DetectedPattern.LowRubricScore -> "score:$criterion"
}
