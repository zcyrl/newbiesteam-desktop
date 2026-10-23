package team.newbies.desktop.publicapp

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
import team.newbies.core.router.UrlRouter
import team.newbies.desktop.shell.brand.Brand
import team.newbies.desktop.shell.kcef.KcefSupport
import team.newbies.desktop.shell.os.AppDirs
import team.newbies.desktop.shell.os.DesktopPlatform
import team.newbies.desktop.shell.os.TrayService
import team.newbies.desktop.shell.update.UpdateService
import team.newbies.desktop.shell.window.ShellSpec
import team.newbies.desktop.shell.window.ShellWindowContent
import team.newbies.platform.JvmHttpFetcher
import kotlin.system.exitProcess

/** NewbiesTeam Desktop — version parity with the Android app (data.js appReleases). */
const val APP_VERSION_NAME = "3.1.1"
const val APP_VERSION_CODE = 38L

private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
private val dirs = AppDirs("NewbiesTeamDesktop")
private val updateService = UpdateService(
    dirs = dirs,
    repository = UpdatesRepository(
        fetcher = JvmHttpFetcher,
        userAgent = "NewbiesTeamDesktop/$APP_VERSION_NAME"
    )
)

private fun initialUrl(): String = when (System.getenv("NBT_OPEN_TARGET")?.lowercase()) {
    "updates" -> UrlRouter.UPDATES_SECTION_URL
    else -> UrlRouter.DEFAULT_HUB_URL
}

private val shellSpec = ShellSpec(
    appName = Brand.PUBLIC_APP_NAME,
    appSubtitle = Brand.PUBLIC_APP_SUBTITLE,
    windowTitle = Brand.PUBLIC_WINDOW_TITLE,
    versionName = APP_VERSION_NAME,
    versionCode = APP_VERSION_CODE,
    isDevApp = false,
    startUrl = initialUrl(),
    dirs = dirs
)

fun main() {
    // macOS niceties (dock/app name take effect before AWT boots).
    System.setProperty("apple.awt.application.name", "NewbiesTeam")
    if (DesktopPlatform.isMac) {
        System.setProperty("apple.awt.application.appearance", "system")
    }

    // CI smoke-test mode: no window, boot the CEF engine, prove the packaged
    // runtime can initialize it, exit green. Used by the release pipelines.
    if (System.getenv("NBT_DESKTOP_SMOKE") == "1") {
        runSmokeTest()
        return
    }

    try {
        application {
            Window(
                onCloseRequest = ::exitApplication,
                title = Brand.PUBLIC_WINDOW_TITLE,
                state = WindowState(width = 1200.dp, height = 820.dp)
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
    TrayService.remove()
    appScope.cancel()
    KcefSupport.disposeBlocking()
}

/**
 * Headless CI proof: KCEF (a full Chromium) boots without a visible window.
 * Exit 0 = engine ready (or needs the one-shot restart), anything else fails CI.
 */
private fun runSmokeTest() {
    val state = runCatching {
        runBlocking {
            withTimeout(90_000) {
                KcefSupport.ensureInitialized(dirs) { }
            }
        }
    }.getOrElse { KcefSupport.State.Error(it) }

    when (state) {
        is KcefSupport.State.Ready, is KcefSupport.State.RestartRequired -> {
            println("NBT smoke: CEF engine ready (runtime jcef=${KcefSupport.runtimeHasBundledJcef()})")
            // halt, not exitProcess: KCEF's background zygote threads can trap
            // (SIGTRAP) between exit() and JVM teardown, mangling the smoke
            // exit code (CI saw exit 133 after "engine ready"). Halt is an
            // immediate OS-level exit and immune to that race.
            Runtime.getRuntime().halt(0)
        }
        is KcefSupport.State.Error -> {
            System.err.println("NBT smoke: engine failed to initialize")
            state.throwable.printStackTrace()
            Runtime.getRuntime().halt(3)
        }
        else -> {
            System.err.println("NBT smoke: engine never reached ready state ($state)")
            Runtime.getRuntime().halt(4)
        }
    }
}
