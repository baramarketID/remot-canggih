package id.my.baramarket.remotcanggih.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Getaran halus saat tombol ditekan. */
@Composable
fun rememberHaptic(): () -> Unit {
    val view = LocalView.current
    return remember(view) { { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) } }
}

/**
 * Animasi tekan seperti /portal: mengecil cepat (80 ms) saat ditekan,
 * lalu memantul kembali dengan kurva pegas saat dilepas.
 */
@Composable
fun pressScale(pressed: Boolean, down: Float = 0.95f): Float {
    val s by animateFloatAsState(
        targetValue = if (pressed) down else 1f,
        animationSpec = if (pressed) tween(80) else tween(420, easing = SpringEasing),
        label = "tekan",
    )
    return s
}

/** Klik biasa (aksi saat jari dilepas) dengan animasi tekan /portal. */
@Composable
fun Modifier.bouncyClick(
    onClickLabel: String? = null,
    enabled: Boolean = true,
    down: Float = 0.97f,
    onClick: () -> Unit,
): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val s = pressScale(pressed, down)
    val haptic = rememberHaptic()
    return this
        .graphicsLayer {
            scaleX = s
            scaleY = s
        }
        .clickable(
            interactionSource = source,
            indication = null,
            enabled = enabled,
            onClickLabel = onClickLabel,
            role = Role.Button,
        ) {
            haptic()
            onClick()
        }
}

/**
 * Tombol remot: aksi langsung saat ditekan (terasa instan), bisa diulang selama ditahan.
 */
@Composable
fun KeyButton(
    icon: ImageVector?,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    width: Dp = size,
    shape: Shape = RoundedCornerShape(18.dp),
    background: Color = LocalColors.current.key,
    tint: Color = LocalColors.current.ink,
    border: Color? = LocalColors.current.keyLine,
    repeat: Boolean = false,
    iconSize: Dp = 22.dp,
    text: String? = null,
) {
    var pressed by remember { mutableStateOf(false) }
    val haptic = rememberHaptic()
    val click by rememberUpdatedState(onClick)
    val scope = rememberCoroutineScope()
    val s = pressScale(pressed)
    val c = LocalColors.current
    Box(
        modifier
            .size(width, size)
            .graphicsLayer {
                scaleX = s
                scaleY = s
            }
            .clip(shape)
            .background(background)
            .then(if (pressed) Modifier.background(if (c.dark) Color(0x14FFFFFF) else Color(0x0F17130F)) else Modifier)
            .then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier)
            .semantics {
                contentDescription = label
                role = Role.Button
            }
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
        if (icon != null) Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
        if (text != null) Text(text, color = tint, fontWeight = FontWeight.Black, fontSize = 20.sp)
    }
}

/** Tombol pil dengan ikon + teks (Power, Sumber, 123, dll). */
@Composable
fun Chip(
    icon: ImageVector?,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = LocalColors.current.surface,
    tint: Color = LocalColors.current.ink,
    border: Color? = LocalColors.current.line,
) {
    var pressed by remember { mutableStateOf(false) }
    val haptic = rememberHaptic()
    val click by rememberUpdatedState(onClick)
    val s = pressScale(pressed)
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier
            .height(46.dp)
            .graphicsLayer {
                scaleX = s
                scaleY = s
            }
            .clip(shape)
            .background(background)
            .then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier)
            .semantics {
                contentDescription = text
                role = Role.Button
            }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    pressed = true
                    haptic()
                    click()
                    tryAwaitRelease()
                    pressed = false
                })
            }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
        }
        Text(text, color = tint, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

/** Bar mendatar dua tombol (VOL / CH): kiri – label – kanan. */
@Composable
fun Rocker(
    label: String,
    left: ImageVector,
    right: ImageVector,
    leftLabel: String,
    rightLabel: String,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalColors.current
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier
            .height(56.dp)
            .clip(shape)
            .background(c.key)
            .border(1.dp, c.keyLine, shape)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        KeyButton(left, leftLabel, onLeft, size = 48.dp, background = Color.Transparent, border = null,
            tint = c.accent, repeat = true, shape = RoundedCornerShape(14.dp))
        Text(label, color = c.muted, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 1.sp)
        KeyButton(right, rightLabel, onRight, size = 48.dp, background = Color.Transparent, border = null,
            tint = c.accent, repeat = true, shape = RoundedCornerShape(14.dp))
    }
}

/** Panel arah: kotak membulat berisi 4 tombol + OK bulat di tengah. */
@Composable
fun DPad(
    size: Dp,
    up: ImageVector,
    down: ImageVector,
    left: ImageVector,
    right: ImageVector,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onOk: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalColors.current
    val cell = size / 3.4f
    val shape = RoundedCornerShape(size / 6)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(c.surface)
            .border(1.dp, c.line, shape)
            .padding(10.dp),
    ) {
        val keyShape = RoundedCornerShape(cell / 3.8f)
        KeyButton(up, "Atas", onUp, Modifier.align(Alignment.TopCenter), size = cell, shape = keyShape,
            repeat = true, iconSize = 28.dp)
        KeyButton(down, "Bawah", onDown, Modifier.align(Alignment.BottomCenter), size = cell, shape = keyShape,
            repeat = true, iconSize = 28.dp)
        KeyButton(left, "Kiri", onLeft, Modifier.align(Alignment.CenterStart), size = cell, shape = keyShape,
            repeat = true, iconSize = 28.dp)
        KeyButton(right, "Kanan", onRight, Modifier.align(Alignment.CenterEnd), size = cell, shape = keyShape,
            repeat = true, iconSize = 28.dp)
        KeyButton(null, "OK", onOk, Modifier.align(Alignment.Center), size = cell * 1.05f, shape = CircleShape,
            background = c.accent, tint = c.accentInk, border = null, text = "OK")
    }
}

/** Kartu permukaan standar. */
@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = LocalColors.current
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .clip(shape)
            .background(c.surface)
            .border(1.dp, c.line, shape)
    ) { content() }
}

@Composable
fun FillBox(content: @Composable () -> Unit) = Box(Modifier.fillMaxSize()) { content() }
