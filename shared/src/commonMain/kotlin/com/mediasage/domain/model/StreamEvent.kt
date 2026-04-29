package com.mediasage.domain.model

enum class StreamField {
    MATCH_THEME, TONE, SUMMARY, QUOTE, FIGURE_NAME,
    FIGURE_ROLE, SCRIPTURE_REF, SCRIPTURE_TEXT,
    EXPLANATION, CONNECTION_THEMES
}

sealed class StreamEvent {
    data class FieldDelta(val field: StreamField, val text: String) : StreamEvent()
    data class Portrait(val url: String) : StreamEvent()
    data class Cached(val encouragement: Encouragement) : StreamEvent()
    data object Done : StreamEvent()
}
