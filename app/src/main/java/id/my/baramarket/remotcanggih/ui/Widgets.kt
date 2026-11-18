package id.my.baramarket.remotcanggih.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

object Palette {
    val Bg = Color(0xFF141619)
    val BgGlow = Color(0xFF1B3A2A)
    val BgGlow2 = Color(0xFF1A2540)
    val Surface = Color(0xFF212429)
    val SurfacePressed = Color(0xFF2C3036)
    val Border = Color(0xFF2E3238)
    val Blue = Color(0xFF2F80ED)
    val BlueLight = Color(0xFF6FA8F7)
    val Red = Color(0xFFF2453D)
    val Green = Color(0xFF2ECC71)
    val Text = Color(0xFFEDEFF2)
    val TextDim = Color(0xFF8C9199)
}

@Composable
fun RemotTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.Blue,
            onPrimary = Color.White,
            background = Palette.Bg,
            surface = Palette.Surface,
            onSurface = Palette.Text,
            onBackground = Palette.Text,
            surfaceVariant = Palette.SurfacePressed,
            error = Palette.Red,
        ),
        content = content,
    )
}

/** Getaran halus saat tombol ditekan. */
@Composable
fun rememberHaptic(): () -> Unit {
    val view = LocalView.current
    return remember(view) { { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) } }
}

/** Tombol bulat gelap seperti di remot. */
@Composable
fun RoundButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    background: Color = Palette.Surface,
    tint: Color = Palette.Text,
    repeat: Boolean = false,
    shape: Shape = CircleShape,
) {
    var pressed by remember { mutableStateOf(false) }
    val haptic = rememberHaptic()
    val click by rememberUpdatedState(onClick)
    val scope = rememberCoroutineScope()
    Box(
        modifier
            .size(size)
            .scale(if (pressed) 0.94f else 1f)
            .clip(shape)
            .background(
                when {
                    !pressed -> background
                    background.alpha == 0f -> Color(0x1FFFFFFF)
                    else -> lighten(background)
                }
            )
            .then(if (background.alpha > 0f) Modifier.border(1.dp, Palette.Border, shape) else Modifier)
            .pointerInput(repeat) {
                detectTapGestures(onPress = {
                    pressed = true
                    haptic()
                    click()
                    val rep = if (repeat) scope.launch {
                        delay(450)
                        while (isActive) {
                            click()
                            delay(110)
                        }
                    } else null
                    tryAwaitRelease()
                    rep?.cancel()
                    pressed = false
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(size * 0.42f))
    }
}

private fun lighten(c: Color): Color = Color(
    red = min(1f, c.red + 0.07f),
    green = min(1f, c.green + 0.07f),
    blue = min(1f, c.blue + 0.07f),
    alpha = c.alpha,
)

/** Pad arah dengan tombol OK di tengah. */
@Composable
fun DPad(
    size: Dp,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onOk: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 0 = tidak ada, 1 atas, 2 kanan, 3 bawah, 4 kiri, 5 OK
    var pressed by remember { mutableStateOf(0) }
    val haptic = rememberHaptic()
    val scope = rememberCoroutineScope()
    val actions by rememberUpdatedState(listOf(onUp, onRight, onDown, onLeft, onOk))

    Box(
        modifier
            .size(size)
            .pointerInput(Unit) {
                detectTapGestures(onPress = { pos ->
                    val cx = this@pointerInput.size.width / 2f
                    val cy = this@pointerInput.size.height / 2f
                    val dx = pos.x - cx
                    val dy = pos.y - cy
                    val r = hypot(dx, dy)
                    val outer = min(cx, cy)
                    if (r > outer) return@detectTapGestures
                    val zone = if (r < outer * 0.42f) 5 else {
                        val deg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble()))
                        when {
                            deg >= -135 && deg < -45 -> 1
                            deg >= -45 && deg < 45 -> 2
                            deg >= 45 && deg < 135 -> 3
                            else -> 4
                        }
                    }
                    pressed = zone
                    haptic()
                    actions[zone - 1]()
                    val rep = if (zone != 5) scope.launch {
                        delay(450)
                        while (isActive) {
                            actions[zone - 1]()
                            delay(120)
                        }
                    } else null
                    tryAwaitRelease()
                    rep?.cancel()
                    pressed = 0
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSizeCompat(size)) {
            val c = Offset(this.size.width / 2, this.size.height / 2)
            val outer = this.size.minDimension / 2
            drawCircle(Palette.Surface, outer, c)
            drawCircle(Palette.Border, outer - 1f, c, style = Stroke(2f))
            if (pressed in 1..4) {
                val start = when (pressed) {
                    1 -> -135f; 2 -> -45f; 3 -> 45f; else -> 135f
                }
                drawArc(
                    Palette.SurfacePressed, start, 90f, true,
                    topLeft = Offset(c.x - outer, c.y - outer), size = Size(outer * 2, outer * 2),
                )
            }
            // garis pemisah diagonal
            val inner = outer * 0.5f
            for (a in listOf(45.0, 135.0, 225.0, 315.0)) {
                val rad = Math.toRadians(a)
                val cos = kotlin.math.cos(rad).toFloat()
                val sin = kotlin.math.sin(rad).toFloat()
                drawLine(
                    Palette.Bg,
                    Offset(c.x + cos * inner, c.y + sin * inner),
                    Offset(c.x + cos * outer, c.y + sin * outer),
                    strokeWidth = 10f,
                )
            }
            drawCircle(Palette.Bg, inner, c)
            // segitiga arah
            val tri = outer * 0.075f
            val d = outer * 0.76f
            fun triangle(cx: Float, cy: Float, angle: Float) {
                val p = androidx.compose.ui.graphics.Path()
                val rad = Math.toRadians(angle.toDouble())
                val ux = kotlin.math.cos(rad).toFloat()
                val uy = kotlin.math.sin(rad).toFloat()
                p.moveTo(cx + ux * tri, cy + uy * tri)
                p.lineTo(cx - ux * tri - uy * tri * 1.3f, cy - uy * tri + ux * tri * 1.3f)
                p.lineTo(cx - ux * tri + uy * tri * 1.3f, cy - uy * tri - ux * tri * 1.3f)
                p.close()
                drawPath(p, Palette.Blue)
            }
            triangle(c.x, c.y - d, -90f)
            triangle(c.x + d, c.y, 0f)
            triangle(c.x, c.y + d, 90f)
            triangle(c.x - d, c.y, 180f)
            // tombol OK
            val okR = outer * 0.4f * (if (pressed == 5) 0.95f else 1f)
            drawCircle(
                Brush.linearGradient(
                    listOf(Palette.BlueLight, Palette.Blue),
                    start = Offset(c.x - okR, c.y - okR), end = Offset(c.x + okR, c.y + okR),
                ),
                okR, c,
            )
        }
        androidx.compose.material3.Text(
            "OK",
            color = Color.White,
            style = MaterialTheme.typography.headlineMedium,
        )
    }
}

private fun Modifier.matchParentSizeCompat(size: Dp): Modifier = this.size(size)
