package team.newbies.desktop.shell.os

import java.util.Locale

/** Desktop OS classification used across the shell. */
enum class DesktopOs { WINDOWS, MAC, LINUX, UNKNOWN }

object DesktopPlatform {
    val current: DesktopOs = parse(System.getProperty("os.name"))

    private fun parse(name: String?): DesktopOs {
        val n = (name ?: "").lowercase(Locale.ROOT)
        return when {
            n.contains("win") -> DesktopOs.WINDOWS
            n.contains("mac") || n.contains("darwin") -> DesktopOs.MAC
            n.contains("nix") || n.contains("nux") || n.contains("linux") -> DesktopOs.LINUX
            else -> DesktopOs.UNKNOWN
        }
    }

    val isMac: Boolean get() = current == DesktopOs.MAC
    val isWindows: Boolean get() = current == DesktopOs.WINDOWS
    val isLinux: Boolean get() = current == DesktopOs.LINUX
}
