package team.newbies.desktop.shell.os

import java.awt.Image
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.TrayIcon
import java.io.File
import javax.imageio.ImageIO

/**
 * System tray object for the desktop apps (menu: Show / Check for updates /
 * Open Updates / Quit). Gracefully no-ops on environments without a tray.
 */
object TrayService {

    @Volatile private var trayIcon: TrayIcon? = null
    @Volatile private var listener: TrayMenuListener? = null

    fun hasTray(): Boolean = try {
        SystemTray.isSupported()
    } catch (_: Exception) {
        false
    }

    /** Installs the tray icon. Any failure just means "no tray", never a crash. */
    fun install(
        iconResource: String,
        tooltip: String,
        menuListener: TrayMenuListener
    ): Boolean {
        if (!hasTray()) return false
        this.listener = menuListener
        return try {
            val image = loadIcon(iconResource) ?: return false
            val menu = PopupMenu()
            menu.add(java.awt.MenuItem("Show Window").apply { addActionListener { menuListener.onShowWindow() } })
            menu.add(java.awt.MenuItem("Check for Updates").apply { addActionListener { menuListener.onCheckUpdates() } })
            menu.add(java.awt.MenuItem("Open Updates Tab").apply { addActionListener { menuListener.onOpenUpdatesTab() } })
            menu.addSeparator()
            menu.add(java.awt.MenuItem("Quit").apply { addActionListener { menuListener.onQuit() } })

            val icon = TrayIcon(image, tooltip, menu).apply { isImageAutoSize = true }
            SystemTray.getSystemTray().add(icon)
            trayIcon = icon
            true
        } catch (_: Exception) {
            false
        }
    }

    fun remove() {
        try {
            trayIcon?.let { SystemTray.getSystemTray().remove(it) }
        } catch (_: Exception) {
        }
        trayIcon = null
    }

    fun displayMessage(title: String, text: String) {
        try {
            trayIcon?.displayMessage(title, text, TrayIcon.MessageType.INFO)
        } catch (_: Exception) {
        }
    }

    private fun loadIcon(resource: String): Image? = try {
        val source = this::class.java.classLoader.getResourceAsStream(resource)?.use { streams ->
            ImageIO.read(streams)
        } ?: return null
        val size = 64
        if (source.width == size) {
            source
        } else {
            val target = java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB)
            target.createGraphics().apply {
                drawImage(source, 0, 0, size, size, null)
                dispose()
            }
            target
        }
    } catch (_: Exception) {
        null
    }
}

interface TrayMenuListener {
    fun onShowWindow()
    fun onCheckUpdates()
    fun onOpenUpdatesTab()
    fun onQuit()
}
