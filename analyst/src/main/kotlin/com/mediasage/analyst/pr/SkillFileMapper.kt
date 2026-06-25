package com.mediasage.analyst.pr

import com.mediasage.analyst.detector.DetectedPattern

object SkillFileMapper {
    fun skillFileFor(pattern: DetectedPattern): String = when (pattern) {
        is DetectedPattern.GateFailure -> ".claude/commands/ticket-work.md"
        is DetectedPattern.LowRubricScore -> ".claude/commands/ticket-work.md"
    }
}
