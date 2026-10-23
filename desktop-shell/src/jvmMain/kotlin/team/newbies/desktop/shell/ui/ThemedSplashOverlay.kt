package team.newbies.desktop.shell.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.useResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import team.newbies.desktop.shell.brand.Brand

/**
 * Animated brand splash — the Compose Desktop port of the APK's
 * `activity_main.xml` splash overlay (crimson radial glow pulse + logo + title
 * + subtitle) with the same 420 ms fade-out and 4.5 s safety timeout.
 */
private const val SPLASH_TIMEOUT_MS = 4500L
private const val FADE_OUT_MS = 420

@Composable
fun ThemedSplashOverlay(
    appName: String,
    appSubtitle: String,
    logoResource: String = "newbies/logo-512.png",
    onDismissed: () -> Unit,
    /** While true the splash stays visible up to the timeout. */
    show: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "glowPulse")
    val glowAlpha by transition.animateFloat(
        label = "glowAlpha",
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300),
            repeatMode = RepeatMode.Reverse
        ),
        initialValue = 0.35f,
        targetValue = 0.85f
    )

    var fading by remember { mutableStateOf(false) }
    val overlayAlpha = remember { Animatable(1f) }

    // The parent stops showing the splash when the engine finished loading; this
    // starts the same 420 ms fade the Android app plays on splash dismiss.
    LaunchedEffect(show) {
        if (!show && !fading) {
            fading = true
            overlayAlpha.animateTo(0f, tween(FADE_OUT_MS))
            onDismissed()
        }
    }

    // Safety fallback timer, mirrors SPLASH_TIMEOUT_MS from the Android app.
    LaunchedEffect(Unit) {
        delay(SPLASH_TIMEOUT_MS)
        if (!fading) {
            fading = true
            overlayAlpha.animateTo(0f, tween(FADE_OUT_MS))
            onDismissed()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .alpha(overlayAlpha.value)
            .background(Brand.SplashBackground),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(260.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Brand.AccentCrimson.copy(alpha = glowAlpha * 0.55f),
                            Color.Transparent
                        )
                    ),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(50)
                )
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
        ) {
            val logo: ImageBitmap = useResource(logoResource, ::loadImageBitmap)
            Image(
                bitmap = logo,
                contentDescription = appName,
                modifier = Modifier.size(140.dp)
            )

            Text(
                text = appName,
                fontSize = 20.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                letterSpacing = androidx.compose.ui.unit.TextUnit(0.08f, androidx.compose.ui.unit.TextUnitType.Em),
                color = Brand.TextInk,
                modifier = Modifier.padding(top = 18.dp)
            )
            Text(
                text = appSubtitle,
                fontSize = 12.sp,
                letterSpacing = androidx.compose.ui.unit.TextUnit(0.06f, androidx.compose.ui.unit.TextUnitType.Em),
                color = Brand.TextMuted,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}
