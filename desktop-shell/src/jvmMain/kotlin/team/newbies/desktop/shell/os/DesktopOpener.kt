package team.newbies.desktop.shell.os

import java.awt.Desktop
import java.io.File

/**
 * System integration: open external links / downloaded files, mirroring the
 * `Intent(ACTION_VIEW)` behavior of the Android apps.
 */
object DesktopOpener {

    fun openUrl(url: String): Boolean {
        val os = DesktopPlatform.current
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return false
        return try {
            when (os) {
                DesktopOs.WINDOWS -> {
                    // rundll32 avoids shelling into cmd.exe; graceful fallback to Desktop
                    val ok = runCommand("rundll32", "url.dll,FileProtocolHandler", trimmed)
                    ok || swingBrowse(trimmed)
                }
                DesktopOs.LINUX -> runCommand("xdg-open", trimmed) || swingBrowse(trimmed)
                DesktopOs.MAC -> runCommand("open", trimmed) || swingBrowse(trimmed)
                else -> swingBrowse(trimmed)
            }
        } catch (_: Exception) {
            false
        }
    }

    fun openPath(path: File): Boolean = try {
        if (DesktopPlatform.isMac) {
            runCommand("open", path.absolutePath)
        } else if (path.isDirectory && !DesktopPlatform.isWindows) {
            runCommand("xdg-open", path.absolutePath)
        } else {
            Desktop.getDesktop().open(path)
            true
        }
    } catch (_: Exception) {
        false
    }

    fun revealInFileManager(path: File): Boolean = try {
        when (DesktopPlatform.current) {
            DesktopOs.MAC -> runCommand("open", "-R", path.absolutePath)
            DesktopOs.WINDOWS -> runCommand("explorer", "/select,", path.absolutePath)
            else -> runCommand("xdg-open", (path.takeUnless { it.isDirectory }?.parent ?: path.absolutePath))
        }
    } catch (_: Exception) {
        false
    }

    private fun swingBrowse(url: String): Boolean = try {
        Desktop.getDesktop().browse(java.net.URI(url))
        true
    } catch (_: Exception) {
        false
    }

    private fun runCommand(vararg cmd: String): Boolean = try {
        val p = ProcessBuilder(*cmd).apply { redirectErrorStream(true) }
        p.start()
        true
    } catch (_: Exception) {
        false
    }
}
