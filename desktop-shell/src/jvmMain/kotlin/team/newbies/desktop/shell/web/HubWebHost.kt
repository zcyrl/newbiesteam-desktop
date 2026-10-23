package team.newbies.desktop.shell.web

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.multiplatform.webview.jsbridge.rememberWebViewJsBridge
import com.multiplatform.webview.web.LoadingState
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.WebViewNavigator
import com.multiplatform.webview.web.WebViewState
import com.multiplatform.webview.web.rememberWebViewNavigator
import com.multiplatform.webview.web.rememberWebViewState
import team.newbies.desktop.shell.brand.Brand
import team.newbies.desktop.shell.kcef.KcefSupport
import team.newbies.desktop.shell.os.AppDirs
import java.io.File

/**
 * One WebView window shell (the desktop twin of the Android `MainActivity`
 * `SwipeRefreshLayout`+`WebView` stack):
 *
 * 1. Waits for KCEF/Chromium (auto-downloading the JCEF bundle when the runtime
 *    doesn't ship it and showing a progress splash meanwhile).
 * 2. Renders the WebView with the same settings contract as the APK WebViews
 *    (JS on, no zoom controls, hub UA, browser popups suppressed).
 * 3. On every page load injects the `AndroidBridge`-compatible bridge shim +
 *    the internal/external link router so both hub and dev portal behave
 *    exactly like their Android counterparts.
 */
@Composable
fun HubWebHost(
    startUrl: String,
    dirs: AppDirs,
    versionName: String,
    versionCode: Long,
    isDevApp: Boolean,
    onPageLoaded: () -> Unit,
    background: Color,
    modifier: Modifier = Modifier,
    devUiServer: DevUiServer? = null,
    onRouteLink: (RouteLinkRequest) -> Unit = {}
) {
    var kcefState by remember(dirs) { mutableStateOf<KcefSupport.State>(KcefSupport.State.Initializing) }
    var downloadPercent by remember(dirs) { mutableFloatStateOf(0f) }

    LaunchedEffect(dirs) {
        kcefState = KcefSupport.ensureInitialized(dirs) { percent ->
            downloadPercent = percent
            kcefState = KcefSupport.State.Downloading(percent)
        }
    }
    if (kcefState !is KcefSupport.State.Ready) {
        KcefFallbackPanel(kcefState, downloadPercent, background)
        return
    }
    WebViewPanel(
        startUrl = startUrl,
        versionName = versionName,
        versionCode = versionCode,
        isDevApp = isDevApp,
        dirs = dirs,
        devUiServer = devUiServer,
        onPageLoaded = onPageLoaded,
        onRouteLink = onRouteLink,
        background = background,
        modifier = modifier
    )
}

/** Full-window WebView with shell bridge injection. */
@Composable
private fun WebViewPanel(
    startUrl: String,
    versionName: String,
    versionCode: Long,
    isDevApp: Boolean,
    dirs: AppDirs,
    devUiServer: DevUiServer?,
    onPageLoaded: () -> Unit,
    onRouteLink: (RouteLinkRequest) -> Unit,
    background: Color,
    modifier: Modifier = Modifier
) {
    val state = rememberWebViewState(startUrl)
    val navigator = rememberWebViewNavigator()
    // The bridge gets the navigator wired in so shell handlers (`reloadPage`)
    // can drive the WebView directly (`dispatch` passes the bridge's navigator).
    val jsBridge = rememberWebViewJsBridge(navigator)

    // Desktop settings parity of the APK `webView.settings` block: JS enabled by
    // default, zoom UI off, popups suppressed (they are routed via bridge), and
    // an opaque dark backdrop so first paint never flashes white.
    state.webSettings.desktopWebSettings.apply {
        offScreenRendering = false
        transparent = false
        disablePopupWindows = true
    }

    LaunchedEffect(jsBridge) {
        jsBridge.registerShellBridgeHandlers(
            isDevApp = isDevApp,
            dirs = dirs,
            devUiServer = devUiServer,
            onRouteLink = onRouteLink
        )
    }

    // Every finished main-frame load: inject the shell shims (idempotent JS).
    LaunchedEffect(state.loadingState) {
        if (state.loadingState is LoadingState.Finished) {
            val deviceJson = runCatching {
                File(dirs.stateDir, "dev-state.json").takeIf { it.isFile }?.readText()?.trim().orEmpty()
            }.getOrDefault("")
            val shim = if (isDevApp) {
                JsShims.devBridgeShim(
                    versionName = versionName,
                    versionCode = versionCode.toLong(),
                    deviceId = readDeviceId(dirs),
                    diskStateJson = deviceJson
                )
            } else {
                JsShims.publicBridgeShim(versionName = versionName, versionCode = versionCode.toLong())
            }

            navigator.evaluateJavaScript(shim, null)
            navigator.evaluateJavaScript(JsShims.routeInterceptorShim(), null)
            onPageLoaded()
        }
    }

    WebView(
        state = state,
        navigator = navigator,
        webViewJsBridge = jsBridge,
        modifier = modifier.fillMaxSize()
    )
}

private fun readDeviceId(dirs: AppDirs): String {
    val file = dirs.deviceIdFile
    return if (file.isFile) {
        file.readText().trim().ifBlank { createAndStoreDeviceId(file) }
    } else {
        createAndStoreDeviceId(file)
    }
}

private fun createAndStoreDeviceId(file: File): String = java.util.UUID.randomUUID().toString().also { value ->
    try {
        file.parentFile?.mkdirs()
        file.writeText(value)
    } catch (_: Exception) {
    }
}

/** Download-progress / error splash shown while CEF is not ready yet. */
@Composable
private fun KcefFallbackPanel(
    state: KcefSupport.State,
    percent: Float,
    background: Color
) {
    Column(
        modifier = Modifier.fillMaxSize().background(background),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (state) {
            is KcefSupport.State.Downloading, KcefSupport.State.Initializing -> {
                Text(
                    text = if (state is KcefSupport.State.Downloading) {
                        "Preparing the NewbiesTeam browser engine — ${state.percent.toInt()}%"
                    } else {
                        "Preparing the NewbiesTeam browser engine…"
                    },
                    fontSize = 14.sp,
                    color = Brand.TextMuted,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                LinearProgressIndicator(
                    progress = {
                        if (state is KcefSupport.State.Downloading) (state.percent / 100f).coerceIn(0f, 1f) else 0f
                    },
                    modifier = Modifier.size(width = 320.dp, height = 5.dp),
                    color = Brand.AccentCrimson,
                    trackColor = Brand.BlackCard
                )
            }
            is KcefSupport.State.Error -> {
                Text(
                    text = "Browser engine failed to start",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Brand.TextInk
                )
                Text(
                    text = state.throwable.message ?: state.throwable::class.java.simpleName,
                    fontSize = 12.sp,
                    color = Brand.TextMuted,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            is KcefSupport.State.RestartRequired -> {
                Text(
                    text = "Engine installed — restart the app once to finish setup.",
                    fontSize = 14.sp,
                    color = Brand.TextInk
                )
            }
            else -> Unit
        }
    }
}
