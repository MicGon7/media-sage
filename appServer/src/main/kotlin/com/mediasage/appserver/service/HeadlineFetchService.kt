package com.mediasage.appserver.service

import com.mediasage.appserver.repository.HeadlineRepository

/**
 * Fetches all relevant GNews categories and stores the results, so clients read from the
 * shared cache instead of triggering a live provider call per device/request.
 */
class HeadlineFetchService(
    private val newsApiClient: NewsApiClient,
    private val headlineRepository: HeadlineRepository,
    private val scraperService: ArticleScraperService
) {
    companion object {
        val CATEGORIES = listOf("general", "world", "nation", "business", "technology", "science", "health")
    }

    suspend fun fetchAndStoreAll(nowMillis: Long = System.currentTimeMillis()): FetchSummary {
        val succeeded = mutableListOf<String>()
        val failed = mutableListOf<String>()

        CATEGORIES.forEach { category ->
            try {
                val articles = newsApiClient.getTopHeadlines(category = category)
                headlineRepository.replaceCategory(category, articles, nowMillis)
                scraperService.preScrape(articles.map { it.url })
                succeeded += category
            } catch (_: Exception) {
                failed += category
            }
        }

        return FetchSummary(succeeded = succeeded, failed = failed)
    }
}

data class FetchSummary(val succeeded: List<String>, val failed: List<String>)
