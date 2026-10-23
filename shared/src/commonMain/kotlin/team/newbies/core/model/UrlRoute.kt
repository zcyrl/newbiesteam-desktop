package team.newbies.core.model

/**
 * Platform-agnostic route classification, ported 1:1 from
 * `android-app/app/src/main/java/team/newbies/core/model/UrlRoute.kt`.
 */
sealed interface UrlRoute {
    /** Stays inside the embedded WebView (hub pages, local dev portal). */
    data class InternalWeb(val url: String) : UrlRoute

    /** Open in the OS default browser (Drive, SourceForge, GitHub release mirrors...). */
    data class ExternalBrowser(val url: String) : UrlRoute

    /** Direct downloadable file (ROMs, boot.img, kernels...). */
    data class DirectDownload(val url: String, val filename: String) : UrlRoute

    /** Custom schemes (tel:, mailto:, intent:...) handled by the platform handler. */
    data class SpecialIntent(val uriString: String, val scheme: String) : UrlRoute
}
