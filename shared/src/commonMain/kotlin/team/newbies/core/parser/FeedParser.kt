package team.newbies.core.parser

import kotlinx.serialization.json.Json
import team.newbies.core.model.UpdatePost

/**
 * Feed parser for the NewbiesTeam hubs.
 *
 * Ported from `android-app/.../core/parser/FeedParser.kt` (org.json based) and
 * rewritten on kotlinx-serialization so it stays platform agnostic for KMP:
 * the Parse data.js fallback uses pure regex, the JSON feed is real JSON.
 */
object FeedParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    fun parseJsonFeed(jsonString: String): List<UpdatePost> {
        return try {
            val trimmed = jsonString.trim()
            if (trimmed.startsWith("[")) {
                json.decodeFromString<List<UpdatePost>>(trimmed)
                    .filter { it.id.isNotBlank() }
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun parseDataJsFallback(jsContent: String): UpdatePost? {
        return try {
            val id = Regex("id\\s*:\\s*[\"']([^\"']+)[\"']").find(jsContent)?.groupValues?.getOrNull(1)
            val title = Regex("title\\s*:\\s*[\"']([^\"']+)[\"']").find(jsContent)?.groupValues?.getOrNull(1)
            if (id != null && title != null) {
                val author = Regex("by\\s*:\\s*[\"']([^\"']+)[\"']").find(jsContent)?.groupValues?.getOrNull(1)
                    ?: "NewbiesTeam"
                val tag = Regex("tag\\s*:\\s*[\"']([^\"']+)[\"']").find(jsContent)?.groupValues?.getOrNull(1)
                    ?: "Update"
                UpdatePost(
                    id = id,
                    title = title,
                    author = author,
                    tag = tag
                )
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
