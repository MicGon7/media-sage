package com.mediasage.agentruntime.feedback.detector

interface PatternDetector {
    fun detectPatterns(windowDays: Int = 7, minOccurrences: Int = 3): List<DetectedPattern>
}
