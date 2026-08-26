package com.mediasage.domain.model

/**
 * The categories shown as tabs on the Headlines screen and used by Briefing's NEWS lens to filter
 * headlines. A subset of the categories tagged server-side (HeadlineFetchService.CATEGORIES) —
 * General and Technology are fetched and cached like the others but excluded here (General is a
 * catch-all similar to the removed "All" tab; Technology doesn't fit the app); the server continues
 * fetching both so this can move to a backend-managed list without a re-fetch later.
 */
enum class HeadlineCategoryFilter(val value: String) {
    WORLD("world"),
    NATION("nation"),
    BUSINESS("business"),
    SCIENCE("science"),
    HEALTH("health"),
}
