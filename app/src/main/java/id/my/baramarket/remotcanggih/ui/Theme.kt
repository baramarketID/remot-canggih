package id.my.baramarket.remotcanggih.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import id.my.baramarket.remotcanggih.R

/** Warna aplikasi, mengikuti tema /portal BaraMarket (navy + oranye / putih krem + oranye). */
data class AppColors(
    val dark: Boolean,
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val key: Color,
    val keyLine: Color,
    val line: Color,
    val ink: Color,
    val muted: Color,
    val faint: Color,
    val accent: Color,
    val accentInk: Color,
    val accentSoft: Color,
    val power: Color,
    val ok: Color,
    val warn: Color,
)

val DarkColors = AppColors(
    dark = true,
    bg = Color(0xFF0E1828),
    surface = Color(0xFF142136),
    surface2 = Color(0xFF1A2A44),
    key = Color(0xFF1A2A44),
    keyLine = Color(0x14FFFFFF),
    line = Color(0x17FFFFFF),
    ink = Color(0xFFF2F0EC),
    muted = Color(0xFFA6B0C2),
    faint = Color(0xFF8A96AB),
    accent = Color(0xFFFF8A3D),
    accentInk = Color(0xFF1B0E05),
    accentSoft = Color(0x24FF8A3D),
    power = Color(0xFFD93A3F),
    ok = Color(0xFF3DD68C),
    warn = Color(0xFFE8C27A),
)

val LightColors = AppColors(
    dark = false,
    bg = Color(0xFFF7F5F2),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFF3F0EC),
    key = Color(0xFFFFFFFF),
    keyLine = Color(0xFFE4DED6),
    line = Color(0xFFECE7E1),
    ink = Color(0xFF17130F),
    muted = Color(0xFF5E6573),
    faint = Color(0xFF737A87),
    accent = Color(0xFFE8650F),
    accentInk = Color(0xFFFFFFFF),
    accentSoft = Color(0x1AE8650F),
    power = Color(0xFFC2361F),
    ok = Color(0xFF1E7F4F),
    warn = Color(0xFFA86500),
)

val LocalColors = staticCompositionLocalOf { DarkColors }

/** Kurva pegas dari /portal: cubic-bezier(.34,1.45,.5,1). */
val SpringEasing = CubicBezierEasing(0.34f, 1.45f, 0.5f, 1f)

/** Kurva "glide" untuk masuk halaman: cubic-bezier(.16,1,.3,1). */
val GlideEasing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

val Lato = FontFamily(
    Font(R.font.lato_regular, FontWeight.Normal),
    Font(R.font.lato_bold, FontWeight.Bold),
    Font(R.font.lato_bold, FontWeight.SemiBold),
    Font(R.font.lato_black, FontWeight.Black),
    Font(R.font.lato_black, FontWeight.ExtraBold),
)

@Composable
private fun animated(target: Color) =
    animateColorAsState(target, tween(450), label = "warna").value

@Composable
fun RemotTheme(dark: Boolean, content: @Composable () -> Unit) {
    val t = if (dark) DarkColors else LightColors
    val c = AppColors(
        dark = dark,
        bg = animated(t.bg),
        surface = animated(t.surface),
        surface2 = animated(t.surface2),
        key = animated(t.key),
        keyLine = animated(t.keyLine),
        line = animated(t.line),
        ink = animated(t.ink),
        muted = animated(t.muted),
        faint = animated(t.faint),
        accent = animated(t.accent),
        accentInk = animated(t.accentInk),
        accentSoft = animated(t.accentSoft),
        power = animated(t.power),
        ok = animated(t.ok),
        warn = animated(t.warn),
    )
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.accent, onPrimary = c.accentInk, background = c.bg, surface = c.surface,
            onSurface = c.ink, onBackground = c.ink, surfaceVariant = c.surface2, onSurfaceVariant = c.muted,
            outline = c.line, error = c.power, surfaceContainerLow = c.surface, surfaceContainerHigh = c.surface2,
        )
    } else {
        lightColorScheme(
            primary = c.accent, onPrimary = c.accentInk, background = c.bg, surface = c.surface,
            onSurface = c.ink, onBackground = c.ink, surfaceVariant = c.surface2, onSurfaceVariant = c.muted,
            outline = c.keyLine, error = c.power, surfaceContainerLow = c.surface, surfaceContainerHigh = c.surface2,
        )
    }
    val base = MaterialTheme.typography
    val typo = base.copy(
        bodyLarge = base.bodyLarge.copy(fontFamily = Lato),
        bodyMedium = base.bodyMedium.copy(fontFamily = Lato),
        bodySmall = base.bodySmall.copy(fontFamily = Lato),
        titleLarge = base.titleLarge.copy(fontFamily = Lato, fontWeight = FontWeight.Black),
        titleMedium = base.titleMedium.copy(fontFamily = Lato, fontWeight = FontWeight.Bold),
        titleSmall = base.titleSmall.copy(fontFamily = Lato),
        labelLarge = base.labelLarge.copy(fontFamily = Lato, fontWeight = FontWeight.Bold),
        labelMedium = base.labelMedium.copy(fontFamily = Lato),
        labelSmall = base.labelSmall.copy(fontFamily = Lato),
        headlineSmall = base.headlineSmall.copy(fontFamily = Lato, fontWeight = FontWeight.Black),
        headlineMedium = base.headlineMedium.copy(fontFamily = Lato, fontWeight = FontWeight.Black),
    )
    CompositionLocalProvider(LocalColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = typo) {
            androidx.compose.material3.ProvideTextStyle(TextStyle(fontFamily = Lato, color = c.ink)) {
                content()
            }
        }
    }
}
