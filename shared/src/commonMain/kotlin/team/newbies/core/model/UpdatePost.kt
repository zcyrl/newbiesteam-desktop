package team.newbies.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Platform-agnostic domain model representing a news or release post from the
 * NewbiesTeam hub (`updates.json`).
 */
@Serializable
data class UpdatePost(
    val id: String,
    val title: String = "NewbiesTeam Update",
    @SerialName("by") val author: String = "NewbiesTeam",
    val tag: String = "Update",
    val body: String = "",
    val date: String = "",
    @SerialName("url") val targetUrl: String = "",
    val image: String = "",
    val linkLabel: String = "",
    val timestamp: Long = 0L
) {
    val notificationTitle: String
        get() = title.ifBlank { "NewbiesTeam Update" }

    val notificationBody: String
        get() = buildString {
            append(tag)
            if (author.isNotBlank()) append(" by ").append(author)
        }
}
