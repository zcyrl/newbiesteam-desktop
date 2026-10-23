package team.newbies.desktop.shell.os

import java.io.File

/**
 * Per application data/config/cache directories, mirroring where the Android
 * apps would read/write their private state (prefs, cache, kcef bundles).
 */
data class AppDirs(
    val appName: String
) {
    val dataDir: File = when (DesktopPlatform.current) {
        DesktopOs.WINDOWS -> {
            val base = System.getenv("LOCALAPPDATA")
                ?.takeIf { it.isNotBlank() } ?: "${System.getProperty("user.home")}${File.separator}AppData${File.separator}Local"
            File(base, appName)
        }
        DesktopOs.MAC -> File(
            System.getProperty("user.home"),
            "Library/Application Support/$appName"
        )
        DesktopOs.LINUX -> {
            val base = System.getenv("XDG_DATA_HOME")
                ?.takeIf { it.isNotBlank() } ?: "${System.getProperty("user.home")}/.local/share"
            File(base, appName)
        }
        else -> File(System.getProperty("user.home"), ".$appName")
    }

    val kcefBundleDir: File get() = File(dataDir, "kcef-bundle")
    val kcefCacheDir: File get() = File(dataDir, "cef-cache")
    val stateDir: File get() = File(dataDir, "state")
    val downloadsDir: File get() = File(dataDir, "downloads")
    val pickedFilesDir: File get() = File(stateDir, "picked")

    /** Last-seen update bookkeeping (mirrors the Android app's SharedPreferences usage). */
    val updateStateFile: File get() = File(stateDir, "update-state.json")
    val deviceIdFile: File get() = File(stateDir, "device-id.txt")

    fun ensureAllCreated() {
        dataDir.mkdirs()
        stateDir.mkdirs()
        downloadsDir.mkdirs()
    }
}

/** Downloads directory that mirrors `Environment.DIRECTORY_DOWNLOADS` on Android. */
fun userDownloadsDir(): File {
    val home = System.getProperty("user.home")
    val dir = when (DesktopPlatform.current) {
        DesktopOs.WINDOWS -> File(System.getenv("USERPROFILE") ?: home, "Downloads")
        DesktopOs.LINUX -> {
            val xdg = System.getenv("XDG_DOWNLOAD_DIR")
            if (xdg != null && xdg.isNotBlank() && File(xdg).isDirectory) File(xdg) else File(home, "Downloads")
        }
        else -> File(home, "Downloads")
    }
    return dir.apply { if (!isDirectory) mkdirs() }
}
