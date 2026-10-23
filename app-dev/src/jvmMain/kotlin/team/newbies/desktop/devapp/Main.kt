package team.newbies.desktop.devapp

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import team.newbies.core.repository.UpdatesRepository
import team.newbies.desktop.shell.brand.Brand
import team.newbies.desktop.shell.kcef.KcefSupport
import team.newbies.desktop.shell.os.AppDirs
import team.newbies.desktop.shell.os.DesktopPlatform
import team.newbies.desktop.shell.os.TrayService
import team.newbies.desktop.shell.update.UpdateService
import team.newbies.desktop.shell.web.DevUiServer
import team.newbies.desktop.shell.window.ShellSpec
import team.newbies.desktop.shell.window.ShellWindowContent
import team.newbies.platform.JvmHttpFetcher
import kotlin.system.exitProcess

/** NewbiesDev Desktop — version parity with the NewbiesDev Android app. */
const val APP_VERSION_NAME = "3.1.1"
const val APP_VERSION_CODE = 38L

/** CDN copy used only if the loopback server cannot bind (never shipped UI loss). */
private const val DEV_CDN_FALLBACK = "https://newbiesteam.surge.sh/index.html"

private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
private val dirs = AppDirs("NewbiesDevDesktop")
private val updateService = UpdateService(
    dirs = dirs,
    repository = UpdatesRepository(
        fetcher = JvmHttpFetcher,
        userAgent = "NewbiesDevDesktop/$APP_VERSION_NAME"
    )
)

/**
 * The dev portal is served from **bundled resources through a loopback HTTP
 * server** — the desktop twin of the APK's `file:///android_asset/index.html`
 * while keeping relative fetch()/localStorage behavior identical to the phone
 * (the APK serves assets locally under the same-host origin rules).
 */
private val devServer = DevUiServer(
    bundledRoot = "newbies-dev-ui",
    pickedFilesDir = dirs.pickedFilesDir
)

private val shellSpec = ShellSpec(
    appName = Brand.DEV_APP_NAME,
    appSubtitle = Brand.DEV_APP_SUBTITLE,
    windowTitle = Brand.DEV_WINDOW_TITLE,
    versionName = APP_VERSION_NAME,
    versionCode = APP_VERSION_CODE,
    isDevApp = true,
    startUrl = resolveStartUrl(),
    dirs = dirs
)

/** Warms the loopback server and returns the URL for the shell window. */
private fun resolveStartUrl(): String {
    dirs.ensureAllCreated()
    devServer.start()
    if (devServer.port == 0) {
        // Extremely defensive fallback (loopback firewalled off): the CDN copy
        // keeps the window functional instead of showing a blank page.
        return System.getenv("NBT_DEV_FALLBACK_URL") ?: DEV_CDN_FALLBACK
    }
    return devServer.baseUrl
}

fun main() {
    // macOS niceties (dock/app name take effect before AWT boots).
    System.setProperty("apple.awt.application.name", "NewbiesDev")
    if (DesktopPlatform.isMac) {
        System.setProperty("apple.awt.application.appearance", "system")
    }

    // CI smoke-test mode: boot KCEF only, prove the packaged engine wiring works.
    if (System.getenv("NBT_DESKTOP_SMOKE") == "1") {
        runSmokeTest()
        return
    }

    try {
        application {
            Window(
                onCloseRequest = ::exitApplication,
                title = Brand.DEV_WINDOW_TITLE,
                state = WindowState(width = 1200.dp, height = 840.dp)
            ) {
                ShellWindowContent(
                    spec = shellSpec,
                    scope = appScope,
                    updateService = updateService,
                    onClose = { exitApplication() },
                    onOpenUpdatesTab = { updateService.openUpdatesSection() }
                )
            }
        }
    } finally {
        shutdown()
    }

    exitProcess(0)
}

/** Runs once after the compose event loop has fully unwound. */
private fun shutdown() {
    runCatching { updateService.stop() }
    runCatching { devServer.stop() }
    TrayService.remove()
    appScope.cancel()
    KcefSupport.disposeBlocking()
}

/** Headless CI proof (same contract as the public app). */
private fun runSmokeTest() {
    devServer.start()
    val state = runCatching {
        runBlocking {
            withTimeout(90_000) {
                KcefSupport.ensureInitialized(dirs) { }
            }
        }
    }.getOrElse { KcefSupport.State.Error(it) }

    when (state) {
        is KcefSupport.State.Ready, is KcefSupport.State.RestartRequired -> {
            println(
                "NBT smoke: CEF engine ready (dev server port=${devServer.port}, " +
                    "runtime jcef=${KcefSupport.runtimeHasBundledJcef()})"
            )
            exitProcess(0)
        }
        is KcefSupport.State.Error -> {
            System.err.println("NBT smoke: engine failed to initialize")
            state.throwable.printStackTrace()
            exitProcess(3)
        }
        else -> {
            System.err.println("NBT smoke: engine never reached ready state ($state)")
            exitProcess(4)
        }
    }
}
