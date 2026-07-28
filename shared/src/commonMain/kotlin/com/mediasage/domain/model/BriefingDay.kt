package com.mediasage.domain.model

data class BriefingDay(
    val epochDay: Long,
    val figureId: Long,
    val scriptureReference: String = "",
    val scriptureText: String = "",
    val inspiration: String = "",
)
