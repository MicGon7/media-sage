package com.mediasage.server.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jsoup.Jsoup
import org.jsoup.safety.Safelist
import java.util.concurrent.ConcurrentHashMap

class ArticleScraperService {

    companion object {
        private const val TIMEOUT_MS = 15_000
        private const val MAX_TEXT_LENGTH = 5_000
        private const val USER_AGENT =
            "Mozilla/5.0 (compatible; MediaSageBot/1.0; +https://github.com/MicGon7/media-sage)"
    }

    private val cache = ConcurrentHashMap<String, String>()
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Pre-scrapes articles in the background for a list of URLs.
     * Called when headlines are fetched so article text is ready when the user taps.
     */
    fun preScrape(urls: List<String>) {
        urls.forEach { url ->
            if (!cache.containsKey(url)) {
                scope.launch { scrape(url) }
            }
        }
    }

    /**
     * Gets cached article text, or scrapes on demand if not cached.
     * Returns null if scraping fails.
     */
    fun getArticleText(url: String): String? {
        return cache[url] ?: scrape(url)
    }

    private fun scrape(url: String): String? {
        return try {
            val doc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .get()

            doc.select("script, style, nav, header, footer, aside, .ad, .advertisement").remove()

            val articleText = extractArticleBody(doc) ?: return null
            val trimmed = articleText.take(MAX_TEXT_LENGTH)

            cache[url] = trimmed
            trimmed
        } catch (_: Exception) {
            null
        }
    }

    private fun extractArticleBody(doc: org.jsoup.nodes.Document): String? {
        val selectors = listOf(
            "article",
            "[role=main]",
            ".article-body",
            ".story-body",
            ".post-content",
            ".entry-content",
            "main"
        )

        for (selector in selectors) {
            val element = doc.selectFirst(selector)
            if (element != null) {
                val text = cleanText(element.html())
                if (text.length > 100) return text
            }
        }

        val bodyText = cleanText(doc.body().html())
        return if (bodyText.length > 100) bodyText else null
    }

    private fun cleanText(html: String): String {
        return Jsoup.clean(html, Safelist.none())
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }
}
