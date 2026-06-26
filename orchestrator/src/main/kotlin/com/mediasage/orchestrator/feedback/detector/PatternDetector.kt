package com.mediasage.orchestrator.feedback.detector

interface PatternDetector {
    fun detectPatterns(windowDays: Int = 7, minOccurrences: Int = 3): List<DetectedPattern>
}
