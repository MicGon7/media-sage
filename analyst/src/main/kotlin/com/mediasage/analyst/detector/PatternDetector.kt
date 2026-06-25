package com.mediasage.analyst.detector

interface PatternDetector {
    fun detectPatterns(windowDays: Int = 7, minOccurrences: Int = 3): List<DetectedPattern>
}
