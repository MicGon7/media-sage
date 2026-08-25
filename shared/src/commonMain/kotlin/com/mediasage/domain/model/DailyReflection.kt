package com.mediasage.domain.model

data class DailyReflection(
    val scriptureReference: String,
    val scriptureText: String,
    val insight: String,
    val implication: String,
    val inspiration: String,
    val sources: List<String>,
    val tone: String,
    val theme: String? = null,
    val challenge: String? = null
) {
    companion object {
        /** The `daily_reflection`/`user_reflection_note` shared key format — keep all three in sync. */
        fun id(epochDay: Long, tone: String, theme: String? = null): String =
            "${epochDay}_${tone}_${theme ?: "NEWS"}"
    }
}
