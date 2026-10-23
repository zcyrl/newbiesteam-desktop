/**
 * NewbiesTeam Desktop — root.
 *
 * Modules:
 *  :shared         KMP core (models, URL router, feed parsing, updates repository)
 *  :desktop-shell  Compose Desktop shell: KCEF bootstrap, WebView shells, splash,
 *                  tray, system notifications, update polling engine
 *  :app-public     NewbiesTeam Desktop   (public web-hub clone)
 *  :app-dev        NewbiesDev Desktop    (developer portal clone)
 */
plugins {
    // Pinned once on the root classpath so every subproject shares a single
    // Kotlin Gradle plugin instance (KGP multi-loading breaks the build).
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.compose.plugin) apply false
    alias(libs.plugins.kotlin.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

allprojects {
    group = "team.newbies"
    version = project.findProperty("newbies.versionName") as String? ?: "3.1.1"
}
