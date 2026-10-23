package team.newbies.core.router

import team.newbies.core.model.UrlRoute

/**
 * Pure Kotlin URL Router and link interceptor, ported 1:1 from
 * `android-app/app/src/main/java/team/newbies/core/router/UrlRouter.kt`.
 *
 * Classifies URLs into internal navigation, external browsers,
 * direct downloads, and custom-scheme intents.
 */
object UrlRouter {
    const val DEFAULT_HUB_HOST = "newbiesteam.surge.sh"
    const val DEFAULT_HUB_URL = "https://newbiesteam.surge.sh"
    const val UPDATES_SECTION_URL = "https://newbiesteam.surge.sh/#updates"
    const val DEV_HUB_URL = "https://newbiesteam.surge.sh"

    val DOWNLOAD_EXTENSIONS = listOf(
        ".zip", ".apk", ".tar", ".gz", ".tgz", ".bin", ".img", ".iso", ".7z"
    )

    val KNOWN_EXTERNAL_HOSTS = listOf(
        "drive.google.com",
        "docs.google.com",
        "sourceforge.net",
        "github.com",
        "raw.githubusercontent.com",
        "t.me",
        "telegram.me",
        "mediafire.com",
        "mega.nz",
        "androidfilehost.com"
    )

    fun resolveRoute(url: String): UrlRoute {
        val cleanUrl = url.trim()
        val lower = cleanUrl.lowercase()

        // Protocol / Intent schemes
        if (lower.startsWith("intent:")) {
            return UrlRoute.SpecialIntent(cleanUrl, "intent")
        }
        if (lower.startsWith("tel:") || lower.startsWith("mailto:") || lower.startsWith("tg:")) {
            val scheme = cleanUrl.substringBefore(":")
            return UrlRoute.SpecialIntent(cleanUrl, scheme)
        }

        // Parse host & path without framework Uri dependencies for KMP
        val withoutScheme = lower.substringAfter("://", lower)
        val hostAndPort = withoutScheme.substringBefore("/")
        val host = hostAndPort.substringBefore(":")
        val path = withoutScheme.substringAfter("/", "")

        // Check file extension downloads
        val isDirectFile = DOWNLOAD_EXTENSIONS.any { ext -> path.contains(ext) }
        if (isDirectFile) {
            val filename = path.substringAfterLast("/")
            return UrlRoute.DirectDownload(cleanUrl, filename)
        }

        // Check external mirrors & services
        if (KNOWN_EXTERNAL_HOSTS.any { host == it || host.endsWith(".$it") }) {
            return UrlRoute.ExternalBrowser(cleanUrl)
        }

        // Check internal NewbiesTeam web hub
        if (host == DEFAULT_HUB_HOST || host.endsWith(".$DEFAULT_HUB_HOST") ||
            host == "newbiesbase.surge.sh" || host.endsWith(".newbiesbase.surge.sh") ||
            host == "localhost" || host == "127.0.0.1") {
            return UrlRoute.InternalWeb(cleanUrl)
        }

        // Fallback default: open third-party websites externally
        return UrlRoute.ExternalBrowser(cleanUrl)
    }
}
