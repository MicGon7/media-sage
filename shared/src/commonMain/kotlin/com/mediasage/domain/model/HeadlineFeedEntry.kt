package com.mediasage.domain.model

data class HeadlineFeedEntry(
    val headline: Headline,
    val figureName: String? = null,
    val figureRole: String? = null,
    val figureImageUrl: String? = null,
    val quotePreview: String? = null,
    val isBookmarked: Boolean = false
)
