package team.newbies.desktop.shell.web

import com.multiplatform.webview.jsbridge.IJsMessageHandler
import com.multiplatform.webview.jsbridge.JsMessage
import com.multiplatform.webview.jsbridge.WebViewJsBridge
import com.multiplatform.webview.jsbridge.dataToJsonString
import com.multiplatform.webview.jsbridge.processParams
import com.multiplatform.webview.web.WebViewNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import team.newbies.desktop.shell.os.AppDirs
import team.newbies.desktop.shell.os.DesktopNotifier
import team.newbies.desktop.shell.os.DesktopOpener
import team.newbies.desktop.shell.os.userDownloadsDir
import java.awt.FileDialog
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.URI
import java.util.Base64

/** kmpJsBridge request/response DTOs used by the desktop shell bridges. */
@Serializable
data class RouteLinkRequest(val url: String = "", val kind: String = "external", val label: String = "")

@Serializable
data class CopyClipboardRequest(val text: String = "")

@Serializable
data class DiskStateRequest(val raw: String = "")

@Serializable
data class PickFilesRequest(val accept: String = "*/*", val multiple: Boolean = false)

@Serializable
data class DownloadFileRequest(val url: String = "", val name: String = "")

@Serializable
data class SaveFileRequest(val name: String = "", val contentBase64: String = "")

@Serializable
data class PickedFile(val url: String = "", val name: String = "", val type: String = "")

@Serializable
data class PickFilesResponse(val ok: Boolean = false, val files: List<PickedFile> = emptyList())

private fun respondOk(): String = """{"ok":true}"""

/** `routeLink` → open external pages / direct downloads in the OS browser. */
class RouteLinkHandler(
    private val onRoute: (RouteLinkRequest) -> Unit = {}
) : IJsMessageHandler {
    override fun methodName() = "routeLink"

    override fun handle(message: JsMessage, navigator: WebViewNavigator?, callback: (String) -> Unit) {
        val req = runCatching { processParams<RouteLinkRequest>(message) }.getOrDefault(RouteLinkRequest())
        onRoute(req)
        when (req.kind) {
            "download" ->
                if (req.url.endsWith(".apk", ignoreCase = true)) {
                    DownloadManager.schedule(req.url, req.label.substringBefore("?").substringAfterLast("/").ifBlank { "NewbiesDev.apk" })
                } else {
                    DesktopOpener.openUrl(req.url)
                }

            else -> DesktopOpener.openUrl(req.url)
        }
        callback(respondOk())
    }
}

/** `copyToClipboard` fallback path (JS already tries navigator.clipboard first). */
class CopyToClipboardHandler : IJsMessageHandler {
    override fun methodName() = "copyToClipboard"

    override fun handle(message: JsMessage, navigator: WebViewNavigator?, callback: (String) -> Unit) {
        try {
            val req = processParams<CopyClipboardRequest>(message)
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(StringSelection(req.text), null)
        } catch (_: Exception) {
        }
        callback(respondOk())
    }
}

/** `diskDevStateSave`/`diskDevStateClear` mirror of the Android SharedPreferences state. */
class DiskDevStateHandler(
    private val dirs: AppDirs,
    private val save: Boolean
) : IJsMessageHandler {
    override fun methodName() = if (save) "diskDevStateSave" else "diskDevStateClear"

    private val stateFile: File
        get() = File(dirs.stateDir, "dev-state.json")

    override fun handle(message: JsMessage, navigator: WebViewNavigator?, callback: (String) -> Unit) {
        try {
            if (save) {
                val req = processParams<DiskStateRequest>(message)
                stateFile.parentFile?.mkdirs()
                stateFile.writeText(req.raw)
            } else {
                stateFile.delete()
            }
        } catch (_: Exception) {
        }
        callback(respondOk())
    }
}

/** `pickFiles` → native multi-select file chooser, staged for same-origin fetch. */
class PickFilesHandler(
    private val dirs: AppDirs,
    private val server: DevUiServer?
) : IJsMessageHandler {
    override fun methodName() = "pickFiles"

    override fun handle(message: JsMessage, navigator: WebViewNavigator?, callback: (String) -> Unit) {
        val req = runCatching { processParams<PickFilesRequest>(message) }.getOrDefault(PickFilesRequest())

        val picked = kotlinx.coroutines.runBlocking {
            withContext(Dispatchers.IO) { pickWithNativeDialog(req) }
        }
        callback(dataToJsonString(PickFilesResponse(ok = picked.isNotEmpty(), files = picked)))
    }

    private fun pickWithNativeDialog(req: PickFilesRequest): List<PickedFile> {
        if (server == null) return emptyList()
        return try {
            val dialog = FileDialog(null as java.awt.Frame?, "NewbiesDev — pick files", FileDialog.LOAD)
            dialog.isMultipleMode = req.multiple
            dialog.isVisible = true
            val chosen = dialog.files ?: return emptyList()
            chosen.map { file ->
                val staged = server.stagePickedFile(file.name, file.readBytes())
                if (staged != null) {
                    PickedFile(url = staged, name = file.name, type = sniffMime(file.name))
                } else {
                    PickedFile(url = file.toURI().toString(), name = file.name, type = sniffMime(file.name))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun sniffMime(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "zip", "apk" -> "application/zip"
            "txt", "md" -> "text/plain; charset=utf-8"
            "html" -> "text/html; charset=utf-8"
            "css" -> "text/css; charset=utf-8"
            "js" -> "application/javascript; charset=utf-8"
            else -> "application/octet-stream"
        }
    }
}

/** `saveFile` → native save-as dialog for export flows (changelogs, patches). */
class SaveFileHandler : IJsMessageHandler {
    override fun methodName() = "saveFile"

    override fun handle(message: JsMessage, navigator: WebViewNavigator?, callback: (String) -> Unit) {
        val req = runCatching { processParams<SaveFileRequest>(message) }.getOrDefault(SaveFileRequest())
        kotlinx.coroutines.runBlocking {
            withContext(Dispatchers.IO) {
                try {
                    val chooser = javax.swing.JFileChooser()
                    chooser.selectedFile = File(req.name.ifBlank { "newbies-file.txt" })
                    val choice = chooser.showSaveDialog(null)
                    if (choice == javax.swing.JFileChooser.APPROVE_OPTION) {
                        val bytes = Base64.getDecoder().decode(req.contentBase64)
                        chooser.selectedFile.absolutePath.let { path ->
                            val target = if (File(path).extension.isNullOrBlank()) {
                                "$path.${req.name.substringAfterLast('.', "txt")}"
                            } else {
                                path
                            }
                            File(target).writeBytes(bytes)
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }
        callback(respondOk())
    }
}

/** APK / release downloads: save to ~/Downloads, notify, reveal — DownloadManager parity. */
object DownloadManager {
    fun schedule(url: String, fileName: String): Boolean {
        return try {
            val target = File(userDownloadsDir(), fileName.substringBefore("?").ifBlank { "download.bin" })
            val thread = Thread {
                try {
                    URI.create(url).toURL().openStream().use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                    DesktopNotifier.notify("Download complete", target.name)
                    DesktopOpener.revealInFileManager(target)
                } catch (_: Exception) {
                    DesktopNotifier.notify("Download failed", "${target.name} — opening the link in your browser")
                    DesktopOpener.openUrl(url)
                }
            }
            thread.isDaemon = true
            thread.start()
            true
        } catch (_: Exception) {
            DesktopOpener.openUrl(url)
            false
        }
    }
}

/** `downloadFile` handler wrapper so it can live behind the shared bridge. */
class DownloadFileHandler : IJsMessageHandler {
    override fun methodName() = "downloadFile"

    override fun handle(message: JsMessage, navigator: WebViewNavigator?, callback: (String) -> Unit) {
        val req = runCatching { processParams<DownloadFileRequest>(message) }.getOrDefault(DownloadFileRequest())
        DownloadManager.schedule(req.url, req.name.ifBlank { "download.bin" })
        callback(respondOk())
    }
}

/** `reloadPage` handler → shell-side page reload (F5 / Ctrl+R parity). */
class ReloadPageHandler : IJsMessageHandler {
    override fun methodName() = "reloadPage"

    override fun handle(message: JsMessage, navigator: WebViewNavigator?, callback: (String) -> Unit) {
        try {
            navigator?.reload()
        } catch (_: Exception) {
        }
        callback(respondOk())
    }
}

/** Registers the bridge message handlers needed by a given shell (`public` or `dev`). */
fun WebViewJsBridge.registerShellBridgeHandlers(
    isDevApp: Boolean,
    dirs: AppDirs,
    devUiServer: DevUiServer?,
    onRouteLink: (RouteLinkRequest) -> Unit = {}
) {
    register(RouteLinkHandler(onRoute = onRouteLink))
    register(ReloadPageHandler())
    if (isDevApp) {
        register(CopyToClipboardHandler())
        register(DiskDevStateHandler(dirs, save = true))
        register(DiskDevStateHandler(dirs, save = false))
        register(PickFilesHandler(dirs, devUiServer))
        register(SaveFileHandler())
        register(DownloadFileHandler())
    }
}
