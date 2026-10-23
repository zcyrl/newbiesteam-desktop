package team.newbies.desktop.shell.kcef

import dev.datlag.kcef.KCEF
import team.newbies.desktop.shell.os.AppDirs
import team.newbies.desktop.shell.os.DesktopPlatform
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Bootstraps KCEF/Chromium exactly like the upstream README.desktop.md sample:
 *
 * - When the app runs on the JetBrains Runtime (the `jbrsdk_jcef` bundles shipped
 *   by our release pipelines), JCEF loads straight out of the runtime: zero
 *   first-run downloads, even offline.
 * - When running from a plain Gradle `run` on a system JDK, KCEF transparently
 *   downloads the matching JCEF package with progress reporting.
 */
object KcefSupport {

    sealed class State {
        data class Downloading(val percent: Float) : State()
        data object Initializing : State()
        data object Ready : State()
        data object RestartRequired : State()
        data class Error(val throwable: Throwable) : State()
    }

    /** Pinned JBR-with-JCEF runtime packages (same tag KCEF's README pins: 17.0.10 b1087.23). */
    fun fallbackPackageUrl(): String {
        return when {
            DesktopPlatform.isWindows ->
                "https://cache-redirector.jetbrains.com/intellij-jbr/jbr_jcef-17.0.10-windows-x64-b1087.23.tar.gz"
            DesktopPlatform.isMac ->
                "https://cache-redirector.jetbrains.com/intellij-jbr/jbr_jcef-17.0.10-osx-aarch64-b1087.23.tar.gz"
            else ->
                "https://cache-redirector.jetbrains.com/intellij-jbr/jbr_jcef-17.0.10-linux-x64-b1087.23.tar.gz"
        }
    }

    /** Extra CEF/Chromium command line switches provided by the user or packaging scripts. */
    val userCefArgs: List<String>
        get() = System.getenv("NBT_DESKTOP_CEF_ARGS")
            ?.split(" ", ",", ";")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()

    /**
     * Performs the full KCEF init (optional download + build) and suspends until
     * the CEF runtime reports ready (or an error appears).
     */
    suspend fun ensureInitialized(dirs: AppDirs, onDownloading: (Float) -> Unit): State {
        return withContext(Dispatchers.IO) {
            dirs.ensureAllCreated()
            try {
                KCEF.init(
                    builder = {
                        installDir(dirs.kcefBundleDir)
                        addArgs(*userCefArgs.toTypedArray())
                        progress {
                            onDownloading { percent ->
                                onDownloading(maxOf(percent, 0F))
                            }
                            onInitialized { }
                        }
                        settings {
                            cachePath = File(dirs.kcefCacheDir, "cache").absolutePath
                        }
                    },
                    onError = { error -> error?.let { throw it } },
                    onRestartRequired = { }
                )
                // init() completes when CEF reports INITIALIZED.
                State.Ready
            } catch (t: Throwable) {
                State.Error(t)
            }
        }
    }

    fun disposeBlocking() {
        try {
            KCEF.disposeBlocking()
        } catch (_: Exception) {
        }
    }

    /** CI smoke-test helper: does this java runtime already ship JCEF (jbrsdk_jcef)?
     *
     * Layouts: Linux/macOS JDK put native libs in `lib/`, Windows in `bin/`.
     */
    fun runtimeHasBundledJcef(): Boolean {
        val home = System.getProperty("java.home") ?: return false
        val hf = File(home)
        val dirs = listOf(hf.resolve("lib"), hf.resolve("bin"), hf.resolve("lib").resolve("server"))
        return dirs.any { dir ->
            dir.isDirectory && dir.listFiles { f ->
                f.name.startsWith("libcef") || f.name.startsWith("libjcef")
            }?.isNotEmpty() == true
        }
    }
}
