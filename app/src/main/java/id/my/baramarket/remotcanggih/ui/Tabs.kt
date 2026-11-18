package id.my.baramarket.remotcanggih.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Input
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.my.baramarket.remotcanggih.BuildConfig
import id.my.baramarket.remotcanggih.atv.Keys
import id.my.baramarket.remotcanggih.core.RemoteController
import id.my.baramarket.remotcanggih.core.TvApp
import id.my.baramarket.remotcanggih.data.TvDevice
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.min

// ---------------------------------------------------------------------- tab Remot

@Composable
fun RemoteTab(rc: RemoteController, onKeyboard: () -> Unit, onNumpad: () -> Unit, onMic: () -> Unit) {
    val c = LocalColors.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val usable = maxHeight.value - 46f - 72f - 56f - 14f * 4 - 8f
        val dpad = min(maxWidth.value - 70f, usable).coerceIn(200f, 320f).dp
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Chip(Icons.Rounded.PowerSettingsNew, "Power", { rc.key(Keys.POWER) }, Modifier.weight(1f),
                    background = c.power, tint = Color.White, border = null)
                Chip(Icons.Rounded.Input, "Sumber", { rc.key(Keys.INPUT) }, Modifier.weight(1f))
                Chip(null, "123", onNumpad, Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))
            DPad(
                size = dpad,
                up = Icons.Rounded.KeyboardArrowUp,
                down = Icons.Rounded.KeyboardArrowDown,
                left = Icons.Rounded.KeyboardArrowLeft,
                right = Icons.Rounded.KeyboardArrowRight,
                onUp = { rc.key(Keys.UP) },
                onDown = { rc.key(Keys.DOWN) },
                onLeft = { rc.key(Keys.LEFT) },
                onRight = { rc.key(Keys.RIGHT) },
                onOk = { rc.key(Keys.OK) },
            )
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KeyButton(Icons.Rounded.Undo, "Kembali", { rc.key(Keys.BACK) })
                KeyButton(Icons.Rounded.Home, "Home", { rc.key(Keys.HOME) })
                KeyButton(
                    Icons.Rounded.Mic, "Bicara ke TV", onMic, size = 72.dp, shape = CircleShape,
                    background = c.accentSoft, tint = c.accent, border = c.accent, iconSize = 28.dp,
                )
                KeyButton(Icons.Rounded.Keyboard, "Ketik ke TV", onKeyboard)
                KeyButton(Icons.Rounded.VolumeOff, "Bisukan", { rc.key(Keys.MUTE) })
            }
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Rocker("VOL", Icons.Rounded.Remove, Icons.Rounded.Add, "Volume turun", "Volume naik",
                    { rc.key(Keys.VOL_DOWN) }, { rc.key(Keys.VOL_UP) }, Modifier.weight(1f))
                Rocker("CH", Icons.Rounded.KeyboardArrowDown, Icons.Rounded.KeyboardArrowUp, "Channel turun",
                    "Channel naik", { rc.key(Keys.CH_DOWN) }, { rc.key(Keys.CH_UP) }, Modifier.weight(1f))
            }
        }
    }
}

// ---------------------------------------------------------------------- tab Touchpad

@Composable
fun TouchpadTab(rc: RemoteController) {
    val c = LocalColors.current
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Chip(Icons.Rounded.Undo, "Kembali", { rc.key(Keys.BACK) }, Modifier.weight(1f))
            Chip(Icons.Rounded.Home, "Home", { rc.key(Keys.HOME) }, Modifier.weight(1f))
            Chip(Icons.Rounded.Menu, "Menu", { rc.key(Keys.MENU) }, Modifier.weight(1f))
        }
        Touchpad(rc, Modifier
            .padding(top = 14.dp)
            .weight(1f)
            .fillMaxWidth())
        val shape = RoundedCornerShape(35.dp)
        Row(
            Modifier
                .padding(top = 14.dp)
                .fillMaxWidth()
                .height(70.dp)
                .clip(shape)
                .background(c.surface)
                .border(1.dp, c.line, shape)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val clear = Color.Transparent
            KeyButton(Icons.Rounded.SkipPrevious, "Sebelumnya", { rc.key(Keys.PREV) }, size = 48.dp,
                shape = CircleShape, background = clear, border = null)
            KeyButton(Icons.Rounded.FastRewind, "Mundur", { rc.key(Keys.REWIND) }, size = 48.dp,
                shape = CircleShape, background = clear, border = null, repeat = true)
            KeyButton(Icons.Rounded.PlayArrow, "Putar / jeda", { rc.key(Keys.PLAY_PAUSE) }, size = 58.dp,
                shape = CircleShape, background = c.accent, tint = c.accentInk, border = null, iconSize = 26.dp)
            KeyButton(Icons.Rounded.FastForward, "Maju", { rc.key(Keys.FORWARD) }, size = 48.dp,
                shape = CircleShape, background = clear, border = null, repeat = true)
            KeyButton(Icons.Rounded.SkipNext, "Berikutnya", { rc.key(Keys.NEXT) }, size = 48.dp,
                shape = CircleShape, background = clear, border = null)
        }
        Rocker("VOLUME", Icons.Rounded.Remove, Icons.Rounded.Add, "Volume turun", "Volume naik",
            { rc.key(Keys.VOL_DOWN) }, { rc.key(Keys.VOL_UP) },
            Modifier
                .padding(vertical = 14.dp)
                .fillMaxWidth())
    }
}

@Composable
private fun Touchpad(rc: RemoteController, modifier: Modifier) {
    val c = LocalColors.current
    val haptic = rememberHaptic()
    var accX by remember { mutableFloatStateOf(0f) }
    var accY by remember { mutableFloatStateOf(0f) }
    var pressed by remember { mutableStateOf(false) }
    val s = pressScale(pressed, 0.985f)
    val ripple = remember { Animatable(0f) }
    var tapAt by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(tapAt) {
        if (tapAt != Offset.Zero) {
            ripple.snapTo(0f)
            ripple.animateTo(1f, tween(450, easing = GlideEasing))
        }
    }
    val shape = RoundedCornerShape(28.dp)
    val dotColor = if (c.dark) Color(0x1AFFFFFF) else Color(0x1A17130F)
    Box(
        modifier
            .graphicsLayer {
                scaleX = s
                scaleY = s
            }
            .clip(shape)
            .background(c.surface)
            .border(1.dp, c.line, shape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { pos ->
                        haptic()
                        tapAt = pos
                        rc.key(Keys.OK)
                    },
                )
            }
            .pointerInput(Unit) {
                val step = 42.dp.toPx()
                detectDragGestures(
                    onDragStart = {
                        accX = 0f
                        accY = 0f
                    },
                ) { change, drag ->
                    change.consume()
                    accX += drag.x
                    accY += drag.y
                    if (abs(accX) > step && abs(accX) > abs(accY)) {
                        rc.key(if (accX > 0) Keys.RIGHT else Keys.LEFT)
                        haptic()
                        accX = 0f
                        accY = 0f
                    } else if (abs(accY) > step) {
                        rc.key(if (accY > 0) Keys.DOWN else Keys.UP)
                        haptic()
                        accX = 0f
                        accY = 0f
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val gap = 18.dp.toPx()
            var y = gap / 2
            while (y < size.height) {
                var x = gap / 2
                while (x < size.width) {
                    drawCircle(dotColor, 1.2.dp.toPx(), Offset(x, y))
                    x += gap
                }
                y += gap
            }
            if (ripple.value in 0.001f..0.999f) {
                drawCircle(
                    c.accent.copy(alpha = 0.25f * (1f - ripple.value)),
                    radius = 24.dp.toPx() + 80.dp.toPx() * ripple.value,
                    center = tapAt,
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(c.accentSoft),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.TouchApp, null, tint = c.accent, modifier = Modifier.size(34.dp)) }
            Spacer(Modifier.height(14.dp))
            Text("Geser untuk arah", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = c.ink)
            Spacer(Modifier.height(4.dp))
            Text("Ketuk sekali untuk OK", fontSize = 14.sp, color = c.muted)
        }
    }
}

// ---------------------------------------------------------------------- tab Aplikasi

@Composable
fun AppsTab(rc: RemoteController, onAdd: () -> Unit) {
    val c = LocalColors.current
    val apps by rc.apps.collectAsState()
    var editing by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 18.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Buka di TV", fontSize = 20.sp, fontWeight = FontWeight.Black, color = c.ink, modifier = Modifier.weight(1f))
            Text(
                if (editing) "Selesai" else "Ubah",
                color = c.accent, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                modifier = Modifier
                    .bouncyClick { editing = !editing }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f),
        ) {
            itemsIndexed(apps, key = { _, a -> a.id }) { i, app ->
                AppTile(app, i, editing, onOpen = { rc.openApp(app) }, onRemove = { rc.removeApp(app) })
            }
            item(key = "tambah") {
                AddTile(apps.size, onAdd)
            }
        }
        Text(
            if (editing) "Ketuk × untuk menyembunyikan. Aplikasi bawaan bisa dikembalikan di Setelan."
            else "Aplikasi harus sudah terpasang di TV.",
            color = c.muted, fontSize = 12.sp, textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
        )
    }
}

/** Animasi muncul bertahap (rise di /portal). */
@Composable
private fun Modifier.rise(index: Int): Modifier {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(30L * index.coerceAtMost(12))
        a.animateTo(1f, tween(380, easing = GlideEasing))
    }
    return this.graphicsLayer {
        alpha = a.value
        translationY = (1f - a.value) * 8.dp.toPx()
    }
}

@Composable
private fun AppTile(app: TvApp, index: Int, editing: Boolean, onOpen: () -> Unit, onRemove: () -> Unit) {
    val c = LocalColors.current
    val shape = RoundedCornerShape(18.dp)
    Box(Modifier.rise(index)) {
        Column(
            Modifier
                .fillMaxWidth()
                .bouncyClick(onClickLabel = "Buka ${app.name}") { if (editing) onRemove() else onOpen() }
                .clip(shape)
                .background(c.surface)
                .border(1.dp, c.line, shape)
                .padding(top = 14.dp, bottom = 12.dp, start = 6.dp, end = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppIcon(app)
            Spacer(Modifier.height(8.dp))
            Text(app.name, color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
        if (editing) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(c.power),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Close, "Hapus ${app.name}", tint = Color.White, modifier = Modifier.size(16.dp)) }
        }
    }
}

@Composable
private fun AddTile(index: Int, onAdd: () -> Unit) {
    val c = LocalColors.current
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .rise(index)
            .fillMaxWidth()
            .bouncyClick(onClickLabel = "Tambah aplikasi", onClick = onAdd)
            .clip(shape)
            .border(1.5.dp, c.keyLine, shape)
            .padding(top = 14.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(c.accentSoft),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Add, null, tint = c.accent, modifier = Modifier.size(26.dp)) }
        Spacer(Modifier.height(8.dp))
        Text("Tambah", color = c.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

// ---------------------------------------------------------------------- tab Setelan

@Composable
fun SettingsTab(rc: RemoteController, actions: AppActions, onAddTv: () -> Unit) {
    val c = LocalColors.current
    val dark by rc.dark.collectAsState()
    val volumeKeys by rc.volumeKeys.collectAsState()
    val notif by rc.notifControls.collectAsState()
    val sleepAt by rc.sleepAt.collectAsState()
    val saved by rc.saved.collectAsState()
    val update by rc.update.collectAsState()
    var renaming by remember { mutableStateOf<TvDevice?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(sleepAt) {
        while (sleepAt > 0) {
            now = System.currentTimeMillis()
            delay(15_000)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
    ) {
        Section("Tampilan")
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Segment("Gelap", dark, Modifier.weight(1f)) { rc.setDark(true) }
                Segment("Terang", !dark, Modifier.weight(1f)) { rc.setDark(false) }
            }
        }

        Section("TV tersimpan")
        Card(Modifier.fillMaxWidth()) {
            Column {
                if (saved.isEmpty()) {
                    Text("Belum ada TV.", color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(16.dp))
                }
                saved.forEachIndexed { i, d ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .bouncyClick(onClickLabel = "Sambungkan ${d.name}") { rc.connect(d) }
                            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(d.name, color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                            Text(d.host, color = c.muted, fontSize = 12.sp)
                        }
                        IconAction(Icons.Rounded.Edit, "Ganti nama") { renaming = d }
                        IconAction(Icons.Rounded.Delete, "Hapus") { rc.forget(d) }
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .bouncyClick(onClickLabel = "Tambah TV", onClick = onAddTv)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Add, null, tint = c.accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Tambah / ganti TV", color = c.accent, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }

        Section("Kontrol")
        Card(Modifier.fillMaxWidth()) {
            Column {
                ToggleRow("Tombol volume HP", "Atur volume TV pakai tombol volume di samping HP.", volumeKeys) {
                    rc.setVolumeKeys(it)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
                ToggleRow("Remot di notifikasi", "Power, volume, bisukan, putar/jeda tanpa membuka aplikasi.", notif) {
                    if (it) actions.onEnableNotifControls() else rc.setNotifControls(false)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Widget layar utama", color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("Tombol remot langsung di layar utama HP.", color = c.muted, fontSize = 13.sp)
                    }
                    Text(
                        "Pasang", color = c.accent, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                        modifier = Modifier
                            .bouncyClick(onClick = actions.onPinWidget)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
            }
        }

        Section("Timer tidur")
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    if (sleepAt > now) "TV akan dimatikan dalam ${((sleepAt - now) / 60_000L).coerceAtLeast(1)} menit."
                    else "Matikan TV otomatis setelah waktu tertentu.",
                    color = if (sleepAt > now) c.accent else c.muted, fontSize = 13.sp,
                    fontWeight = if (sleepAt > now) FontWeight.Bold else FontWeight.Normal,
                )
                Spacer(Modifier.height(12.dp))
                val options = listOf(0, 15, 30, 60, 90, 120)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { m ->
                                val active = if (m == 0) sleepAt <= now else false
                                Segment(if (m == 0) "Mati" else "$m mnt", active, Modifier.weight(1f)) {
                                    rc.setSleep(m)
                                    if (m > 0) rc.toast("TV akan mati dalam $m menit")
                                }
                            }
                        }
                    }
                }
                Text(
                    "Biarkan HP tetap tersambung WiFi yang sama sampai waktunya.",
                    color = c.faint, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp),
                )
            }
        }

        Section("Aplikasi")
        Card(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .bouncyClick(onClickLabel = "Kembalikan aplikasi bawaan") {
                        rc.restoreDefaultApps()
                        rc.toast("Aplikasi bawaan dikembalikan")
                    }
                    .padding(16.dp),
            ) {
                Text("Kembalikan aplikasi bawaan yang disembunyikan", color = c.ink, fontSize = 15.sp)
            }
        }

        Section("Tentang")
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Remot Canggih ${BuildConfig.VERSION_NAME}", color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (update != null) "Versi baru $update tersedia." else "Gratis, tanpa iklan, tanpa login.",
                    color = if (update != null) c.accent else c.muted, fontSize = 13.sp,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { actions.onOpenUrl(DOWNLOAD_URL) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.accentInk),
                ) { Text(if (update != null) "Download update" else "Halaman download", fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = { confirmReset = true }) {
                    Text("Reset: lupakan semua TV", color = c.power)
                }
            }
        }
    }

    renaming?.let { d ->
        RenameDialog(d, onDismiss = { renaming = null }) { name ->
            rc.rename(d, name)
            renaming = null
        }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            containerColor = c.surface,
            title = { Text("Lupakan semua TV?") },
            text = { Text("Semua TV harus disambungkan ulang dan memasukkan kode lagi.") },
            confirmButton = {
                Button(
                    onClick = {
                        rc.resetAll()
                        confirmReset = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = c.power, contentColor = Color.White),
                ) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Batal", color = c.ink) } },
        )
    }
}

@Composable
private fun Section(title: String) {
    val c = LocalColors.current
    Text(
        title.uppercase(), color = c.faint, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp, start = 4.dp),
    )
}

@Composable
private fun Segment(label: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = LocalColors.current
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .height(42.dp)
            .bouncyClick(onClickLabel = label, onClick = onClick)
            .clip(shape)
            .background(if (active) c.accent else c.surface2),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (active) c.accentInk else c.ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
private fun ToggleRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .bouncyClick(onClickLabel = title, down = 0.985f) { onChange(!checked) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(desc, color = c.muted, fontSize = 13.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = c.accent,
                checkedThumbColor = c.accentInk,
                uncheckedTrackColor = c.surface2,
                uncheckedThumbColor = c.faint,
                uncheckedBorderColor = c.keyLine,
            ),
        )
    }
}

@Composable
private fun IconAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    val c = LocalColors.current
    Box(
        Modifier
            .size(44.dp)
            .bouncyClick(onClickLabel = label, down = 0.9f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = c.muted, modifier = Modifier.size(20.dp)) }
}

@Composable
private fun RenameDialog(device: TvDevice, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val c = LocalColors.current
    var name by remember { mutableStateOf(device.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text("Ganti nama TV") },
        text = {
            OutlinedTextField(
                value = name, onValueChange = { name = it.take(40) }, singleLine = true,
                placeholder = { Text("mis. TV Ruang Tamu") }, modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                onClick = { onSave(name) }, enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.accentInk),
            ) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal", color = c.ink) } },
    )
}

/** Ikon asli dari aplikasi yang sama di HP (bila terpasang), selain itu kotak huruf berwarna. */
@Composable
private fun AppIcon(app: TvApp) {
    val ctx = LocalContext.current
    val icon = remember(app.id, app.phonePkgs) {
        app.phonePkgs.firstNotNullOfOrNull { pkg ->
            try {
                ctx.packageManager.getApplicationIcon(pkg).toBitmap(144, 144).asImageBitmap()
            } catch (_: Exception) {
                null
            }
        }
    }
    if (icon != null) {
        Image(
            icon, contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp)),
        )
    } else {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(app.color)),
            contentAlignment = Alignment.Center,
        ) {
            Text(app.name.take(1).uppercase(), color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Black)
        }
    }
}
