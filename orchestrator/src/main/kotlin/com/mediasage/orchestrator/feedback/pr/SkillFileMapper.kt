package com.mediasage.orchestrator.feedback.pr

import com.mediasage.orchestrator.feedback.detector.DetectedPattern

object SkillFileMapper {
    fun skillFileFor(pattern: DetectedPattern): String = when (pattern) {
        is DetectedPattern.GateFailure -> ".claude/commands/ticket-work.md"
        is DetectedPattern.LowRubricScore -> ".claude/commands/ticket-work.md"
    }
}
