package com.mediasage.agentruntime.feedback.pr

import com.mediasage.agentruntime.feedback.detector.DetectedPattern

object SkillFileMapper {
    fun skillFileFor(pattern: DetectedPattern): String = when (pattern) {
        is DetectedPattern.GateFailure -> ".claude/commands/ticket-work.md"
        is DetectedPattern.LowRubricScore -> ".claude/commands/ticket-work.md"
    }
}
