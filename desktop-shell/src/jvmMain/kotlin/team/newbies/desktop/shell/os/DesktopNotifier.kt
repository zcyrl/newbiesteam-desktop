package team.newbies.desktop.shell.os

/**
 * Cross-platform desktop notifications, mirroring the Android WorkManager
 * "new ROM / update posted" push behavior.
 *
 * - Windows: PowerShell WinRT toast (Windows 10 / 11 built in)
 * - macOS: AppleScript `display notification`
 * - Linux: `notify-send` (libnotify)
 */
object DesktopNotifier {

    @Volatile var enabled: Boolean = true

    fun notify(title: String, body: String) {
        if (!enabled) return
        try {
            when (DesktopPlatform.current) {
                DesktopOs.WINDOWS -> windowsToast(title, body)
                DesktopOs.MAC -> macNotify(title, body)
                DesktopOs.LINUX -> linuxNotify(title, body)
                else -> Unit
            }
        } catch (_: Exception) {
            // Notifications must never crash the shell.
        }
    }

    private fun windowsToast(title: String, body: String) {
        val ps = """
            [Windows.Data.Xml.Dom.XmlDocument,Windows.Data.Xml.Dom.XmlDocument,Windows.Data.Xml.Dom, ContentType=WindowsRuntime] | Out-Null;
            [Windows.UI.Notifications.ToastNotificationManager,Windows.UI.Notifications,ContentType=WindowsRuntime] | Out-Null;
            ${'$'}t = @"
            <toast><visual><binding template="ToastGeneric">
            <text>${xmlEscape(title)}</text><text>${xmlEscape(body)}</text></binding></visual></toast>
            "@;
            ${'$'}x = New-Object Windows.Data.Xml.Dom.XmlDocument; ${'$'}x.LoadXml(${'$'}t);
            ${'$'}n = New-Object Windows.UI.Notifications.ToastNotification(${'$'}x);
            [Windows.UI.Notifications.ToastNotificationManager]::CreateToastNotifier("NewbiesTeamDesktop").Show(${'$'}n);
        """.trimIndent()
        ProcessBuilder(
            "powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-Command", ps
        ).start()
    }

    private fun macNotify(title: String, body: String) {
        val script = "display notification \"${escapeApple(body)}\" with title \"${escapeApple(title)}\""
        ProcessBuilder("osascript", "-e", script).start()
    }

    private fun linuxNotify(title: String, body: String) {
        ProcessBuilder(
            "notify-send",
            "--app-name=NewbiesTeam",
            "-u", "normal",
            escapeXdg(title), escapeXdg(body)
        ).start()
    }

    private fun xmlEscape(text: String) = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\n", " ")
        .trim()

    private fun escapeXdg(text: String) = text.replace("\n", " ").trim()

    private fun escapeApple(text: String) = text
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", " ")
        .trim()
}
