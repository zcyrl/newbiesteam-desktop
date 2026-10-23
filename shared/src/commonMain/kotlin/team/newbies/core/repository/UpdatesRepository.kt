package team.newbies.core.repository

import team.newbies.core.model.UpdatePost
import team.newbies.core.parser.FeedParser
import team.newbies.core.platform.HttpFetcher

/**
 * Repository for fetching and synchronizing live NewbiesTeam hub updates.
 * Ported from `android-app/.../core/repository/UpdatesRepository.kt` with the
 * dependency-inverted HTTP layer so the same code runs on desktop and (later) Android.
 */
class UpdatesRepository(
    private val fetcher: HttpFetcher,
    private val jsonUrl: String = DEFAULT_JSON_FEED,
    private val mirrorJsonUrl: String = MIRROR_JSON_FEED,
    private val fallbackJsUrl: String = DEFAULT_DATA_JS,
    private val userAgent: String = DEFAULT_USER_AGENT
) {
    companion object {
        const val DEFAULT_JSON_FEED = "https://newbiesteam.surge.sh/updates.json"
        const val MIRROR_JSON_FEED = "https://newbiesbase.surge.sh/updates.json"
        const val DEFAULT_DATA_JS = "https://newbiesteam.surge.sh/data.js"
        const val DEFAULT_USER_AGENT = "NewbiesTeam-Desktop/3.1.1"
        const val CACHE_BUST_QUERY_PARAM = "t"
    }

    /** Latest post, resolving through the standard fallback chain (JSON → mirror → data.js). */
    suspend fun fetchLatestUpdate(): UpdatePost? {
        val timestamp = currentMillis()

        // 1. Primary: JSON Endpoint with cache-busting
        fetcher.get("$jsonUrl?$CACHE_BUST_QUERY_PARAM=$timestamp", defaultHeaders()).let { res ->
            if (res is HttpFetcher.HttpResult.Success && res.body.isNotBlank()) {
                FeedParser.parseJsonFeed(res.body).firstOrNull()?.let { return it }
            }
        }

        // 2. Mirror: JSON Endpoint with cache-busting
        fetcher.get("$mirrorJsonUrl?$CACHE_BUST_QUERY_PARAM=$timestamp", defaultHeaders()).let { res ->
            if (res is HttpFetcher.HttpResult.Success && res.body.isNotBlank()) {
                FeedParser.parseJsonFeed(res.body).firstOrNull()?.let { return it }
            }
        }

        // 3. Fallback: data.js regex with cache-busting
        fetcher.get("$fallbackJsUrl?$CACHE_BUST_QUERY_PARAM=$timestamp", defaultHeaders()).let { res ->
            if (res is HttpFetcher.HttpResult.Success && res.body.isNotBlank()) {
                FeedParser.parseDataJsFallback(res.body)?.let { return it }
            }
        }

        return null
    }

    /** Recent posts (already sorted newest first by the hub itself). */
    suspend fun fetchRecentPosts(limit: Int = 10): List<UpdatePost> {
        val timestamp = currentMillis()
        val posts = mutableListOf<UpdatePost>()
        for (url in listOf("$jsonUrl?$CACHE_BUST_QUERY_PARAM=$timestamp", "$mirrorJsonUrl?$CACHE_BUST_QUERY_PARAM=$timestamp")) {
            when (val res = fetcher.get(url, defaultHeaders())) {
                is HttpFetcher.HttpResult.Success -> {
                    posts += FeedParser.parseJsonFeed(res.body)
                    if (posts.isNotEmpty()) break
                }
                else -> continue
            }
        }
        return posts.take(limit)
    }

    private fun defaultHeaders() = mapOf(
        "User-Agent" to userAgent,
        "Cache-Control" to "no-cache, no-store, must-revalidate",
        "Pragma" to "no-cache"
    )
}

/** Wall clock abstraction (kept for KMP friendliness; JVM actual = System.currentTimeMillis). */
expect fun currentMillis(): Long
