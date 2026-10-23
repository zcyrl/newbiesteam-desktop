package team.newbies.desktop.shell.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.WindowScope
import team.newbies.desktop.shell.brand.Brand
import team.newbies.desktop.shell.os.AppDirs
import team.newbies.desktop.shell.os.TrayMenuListener
import team.newbies.desktop.shell.os.TrayService
import team.newbies.desktop.shell.ui.ThemedSplashOverlay
import team.newbies.desktop.shell.update.UpdateService
import team.newbies.desktop.shell.web.HubWebHost
import kotlinx.coroutines.launch

/**
 * Shared window body for both desktop apps: brand splash over the WebView shell,
 * update polling, and the tray install. Configured per app via [ShellSpec].
 */
data class ShellSpec(
    val appName: String,
    val appSubtitle: String,
    val windowTitle: String,
    val versionName: String,
    val versionCode: Long,
    val isDevApp: Boolean,
    /** Start URL — hub for the public app, loopback server for the dev app. */
    val startUrl: String,
    val dirs: AppDirs,
    val logoResource: String = "newbies/logo-512.png",
    val trayIconResource: String = "newbies/logo-64.png",
    val splashBackground: Color = Brand.SplashBackground
)

@Composable
fun WindowScope.ShellWindowContent(
    spec: ShellSpec,
    scope: kotlinx.coroutines.CoroutineScope,
    updateService: UpdateService,
    onClose: () -> Unit,
    onOpenUpdatesTab: () -> Unit
) {
    var splashVisible by remember { mutableStateOf(true) }
    val dirs = spec.dirs

    // Tray menu (Show / Check updates / Open updates / Quit).
    LaunchedEffect(Unit) {
        TrayService.install(
            iconResource = spec.trayIconResource,
            tooltip = spec.appName,
            menuListener = object : TrayMenuListener {
                override fun onShowWindow() { }
                override fun onCheckUpdates() {
                    scope.launch { updateService.checkOnce() }
                }
                override fun onOpenUpdatesTab() {
                    updateService.openUpdatesSection()
                }
                override fun onQuit() {
                    onClose()
                }
            }
        )
    }

    DisposableEffect(Unit) {
        onDispose { TrayService.remove() }
    }

    // 15-min background update polling — parity with the WorkManager worker.
    LaunchedEffect(Unit) {
        updateService.start(scope) { }
    }

    HubWebHost(
        startUrl = spec.startUrl,
        dirs = dirs,
        versionName = spec.versionName,
        versionCode = spec.versionCode,
        isDevApp = spec.isDevApp,
        onPageLoaded = { splashVisible = false },
        background = Brand.BlackDark,
        onRouteLink = { /* link opened externally */ }
    )

    if (splashVisible) {
        ThemedSplashOverlay(
            appName = spec.appName,
            appSubtitle = spec.appSubtitle,
            logoResource = spec.logoResource,
            show = splashVisible,
            onDismissed = { splashVisible = false }
        )
    }
}
