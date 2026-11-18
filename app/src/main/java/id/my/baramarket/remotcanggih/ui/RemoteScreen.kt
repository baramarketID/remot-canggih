package id.my.baramarket.remotcanggih.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Input
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.my.baramarket.remotcanggih.atv.Keys
import kotlin.math.abs
import kotlin.math.min

enum class Panel { None, Devices, Keyboard, Numpad, Settings, Cast }

@Composable
fun RemoteApp(vm: RemoteViewModel, onMic: () -> Unit, onOpenCast: () -> Unit, onOpenUrl: (String) -> Unit) {
    val conn by vm.conn.collectAsState()
    val update by vm.update.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var panel by rememberSaveable { mutableStateOf(Panel.None) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.Bg)
            .background(
                Brush.radialGradient(
                    listOf(Palette.BgGlow.copy(alpha = 0.55f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(0f, 0f),
                    radius = 900f,
                )
            )
            .background(
                Brush.radialGradient(
                    listOf(Palette.BgGlow2.copy(alpha = 0.45f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(1400f, 0f),
                    radius = 900f,
                )
            )
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 18.dp)
        ) {
            TopBar(
                conn = conn,
                onSettings = { panel = Panel.Settings },
                onDevices = { panel = Panel.Devices },
                onCast = { panel = Panel.Cast },
            )
            Spacer(Modifier.height(14.dp))
            Tabs(tab) { tab = it }
            StatusStrip(conn, update, onRetry = { vm.onResume() }, onPick = { panel = Panel.Devices },
                onUpdate = { onOpenUrl(DOWNLOAD_URL) })
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    0 -> RemoteTab(vm, onKeyboard = { panel = Panel.Keyboard }, onNumpad = { panel = Panel.Numpad }, onMic = onMic)
                    1 -> TouchpadTab(vm, onNumpad = { panel = Panel.Numpad })
                    else -> AppsTab(vm)
                }
            }
        }
    }

    when (panel) {
        Panel.Devices -> DevicesSheet(vm, onDismiss = { panel = Panel.None })
        Panel.Keyboard -> KeyboardSheet(vm, onDismiss = { panel = Panel.None })
        Panel.Numpad -> NumpadSheet(vm, onDismiss = { panel = Panel.None })
        Panel.Settings -> SettingsSheet(vm, onDismiss = { panel = Panel.None }, onOpenUrl = onOpenUrl)
        Panel.Cast -> CastSheet(onDismiss = { panel = Panel.None }, onOpenCast = onOpenCast)
        Panel.None -> {}
    }
    if (conn is Conn.Pairing) PairingDialog(conn as Conn.Pairing, vm)
}

const val DOWNLOAD_URL = "https://baramarket.my.id/remot/download"

@Composable
private fun TopBar(conn: Conn, onSettings: () -> Unit, onDevices: () -> Unit, onCast: () -> Unit) {
    val (name, color) = when (conn) {
        is Conn.Connected -> conn.device.name to Palette.Green
        is Conn.Connecting -> conn.device.name to Color(0xFFF5B83D)
        is Conn.Pairing -> conn.device.name to Color(0xFFF5B83D)
        is Conn.Failed -> conn.device.name to Palette.Red
        Conn.Idle -> "Pilih TV" to Palette.TextDim
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundButton(Icons.Outlined.Settings, "Pengaturan", onSettings, size = 56.dp)
        Row(
            Modifier
                .weight(1f)
                .padding(horizontal = 10.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onDevices)
                .padding(vertical = 10.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(10.dp))
            Text(
                name,
                color = Palette.Text,
                fontSize = 22.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(Icons.Rounded.ArrowDropDown, null, tint = Palette.Text, modifier = Modifier.size(30.dp))
        }
        RoundButton(Icons.Rounded.Cast, "Mirror / Cast", onCast, size = 56.dp)
    }
}

@Composable
private fun Tabs(selected: Int, onSelect: (Int) -> Unit) {
    val titles = listOf("Remot", "Touchpad", "Aplikasi")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x14FFFFFF))
    ) {
        titles.forEachIndexed { i, t ->
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (i == selected) Color(0x26FFFFFF) else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    t,
                    color = if (i == selected) Palette.Text else Palette.TextDim,
                    fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Normal,
                    fontSize = 16.sp,
                )
            }
        }
    }
}

@Composable
private fun StatusStrip(conn: Conn, update: String?, onRetry: () -> Unit, onPick: () -> Unit, onUpdate: () -> Unit) {
    val (msg, action) = when {
        conn is Conn.Connecting -> "Menghubungkan ke ${conn.device.name}…" to null
        conn is Conn.Failed -> conn.message to ("Coba lagi" to onRetry)
        conn is Conn.Idle -> "Belum ada TV. Ketuk untuk mencari TV di WiFi ini." to ("Cari" to onPick)
        update != null -> "Versi baru $update tersedia." to ("Update" to onUpdate)
        else -> null to null
    }
    if (msg == null) {
        Spacer(Modifier.height(14.dp))
        return
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.Surface)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(msg, color = Palette.TextDim, fontSize = 13.sp, modifier = Modifier.weight(1f))
        if (action != null) {
            Text(
                action.first,
                color = Palette.BlueLight,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = action.second)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------- tab Remot

@Composable
private fun RemoteTab(vm: RemoteViewModel, onKeyboard: () -> Unit, onNumpad: () -> Unit, onMic: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val btn = min(maxWidth.value / 5.3f, 76f).dp
        val dpad = min(maxWidth.value * 0.88f, maxHeight.value - btn.value * 3.1f).coerceAtLeast(200f).dp
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                RoundButton(Icons.Rounded.Dialpad, "Angka", onNumpad, size = btn * 1.1f)
                RoundButton(
                    Icons.Rounded.PowerSettingsNew, "Power", { vm.key(Keys.POWER) }, size = btn * 1.1f,
                    background = Palette.Red, tint = Color.White,
                )
            }
            DPad(
                size = dpad,
                onUp = { vm.key(Keys.UP) },
                onDown = { vm.key(Keys.DOWN) },
                onLeft = { vm.key(Keys.LEFT) },
                onRight = { vm.key(Keys.RIGHT) },
                onOk = { vm.key(Keys.OK) },
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RockerVertical("VOL", Icons.Rounded.Add, Icons.Rounded.Remove, btn,
                    { vm.key(Keys.VOL_UP) }, { vm.key(Keys.VOL_DOWN) })
                Column(verticalArrangement = Arrangement.spacedBy(btn * 0.18f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(btn * 0.14f)) {
                        RoundButton(Icons.Rounded.Keyboard, "Keyboard", onKeyboard, size = btn)
                        RoundButton(Icons.Outlined.Home, "Home", { vm.key(Keys.HOME) }, size = btn)
                        RoundButton(Icons.Rounded.Input, "Input / Source", { vm.key(Keys.INPUT) }, size = btn)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(btn * 0.14f)) {
                        RoundButton(Icons.Rounded.VolumeOff, "Bisukan", { vm.key(Keys.MUTE) }, size = btn)
                        RoundButton(Icons.Rounded.Mic, "Suara", onMic, size = btn, tint = Palette.BlueLight)
                        RoundButton(Icons.Rounded.Undo, "Kembali", { vm.key(Keys.BACK) }, size = btn)
                    }
                }
                RockerVertical("CH", Icons.Rounded.KeyboardArrowUp, Icons.Rounded.KeyboardArrowDown, btn,
                    { vm.key(Keys.CH_UP) }, { vm.key(Keys.CH_DOWN) })
            }
        }
    }
}

/** Tombol panjang (VOL / CH) dengan atas & bawah. */
@Composable
private fun RockerVertical(
    label: String,
    up: ImageVector,
    down: ImageVector,
    btn: Dp,
    onUp: () -> Unit,
    onDown: () -> Unit,
) {
    Column(
        Modifier
            .width(btn)
            .clip(RoundedCornerShape(btn / 2))
            .background(Palette.Surface)
            .border(1.dp, Palette.Border, RoundedCornerShape(btn / 2)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RoundButton(up, "$label naik", onUp, size = btn, tint = Palette.Blue, repeat = true,
            background = Color.Transparent)
        Text(label, color = Palette.TextDim, fontSize = 15.sp, modifier = Modifier.padding(vertical = btn * 0.12f))
        RoundButton(down, "$label turun", onDown, size = btn, tint = Palette.Blue, repeat = true,
            background = Color.Transparent)
    }
}

// ---------------------------------------------------------------------- tab Touchpad

@Composable
private fun TouchpadTab(vm: RemoteViewModel, onNumpad: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val btn = min(maxWidth.value / 5.6f, 72f).dp
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                RoundButton(Icons.Rounded.Dialpad, "Angka", onNumpad, size = btn * 1.15f)
                RoundButton(
                    Icons.Rounded.PowerSettingsNew, "Power", { vm.key(Keys.POWER) }, size = btn * 1.15f,
                    background = Palette.Red, tint = Color.White,
                )
            }
            Touchpad(vm, Modifier.weight(1f).fillMaxWidth())
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundButton(Icons.Rounded.Undo, "Kembali", { vm.key(Keys.BACK) }, size = btn)
                Row(
                    Modifier
                        .padding(horizontal = 8.dp)
                        .weight(1f)
                        .clip(RoundedCornerShape(btn / 2))
                        .background(Palette.Surface)
                        .border(1.dp, Palette.Border, RoundedCornerShape(btn / 2)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    RoundButton(Icons.Rounded.Remove, "Volume turun", { vm.key(Keys.VOL_DOWN) }, size = btn,
                        tint = Palette.Blue, background = Color.Transparent, repeat = true)
                    Text("VOL", color = Palette.TextDim, fontSize = 15.sp)
                    RoundButton(Icons.Rounded.Add, "Volume naik", { vm.key(Keys.VOL_UP) }, size = btn,
                        tint = Palette.Blue, background = Color.Transparent, repeat = true)
                }
                RoundButton(Icons.Outlined.Home, "Home", { vm.key(Keys.HOME) }, size = btn)
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                RoundButton(Icons.Rounded.SkipPrevious, "Sebelumnya", { vm.key(Keys.PREV) }, size = btn)
                RoundButton(Icons.Rounded.FastRewind, "Mundur", { vm.key(Keys.REWIND) }, size = btn, repeat = true)
                RoundButton(Icons.Rounded.PlayArrow, "Putar / Jeda", { vm.key(Keys.PLAY_PAUSE) }, size = btn)
                RoundButton(Icons.Rounded.FastForward, "Maju", { vm.key(Keys.FORWARD) }, size = btn, repeat = true)
                RoundButton(Icons.Rounded.SkipNext, "Berikutnya", { vm.key(Keys.NEXT) }, size = btn)
            }
        }
    }
}

@Composable
private fun Touchpad(vm: RemoteViewModel, modifier: Modifier) {
    val haptic = rememberHaptic()
    var accX by remember { mutableFloatStateOf(0f) }
    var accY by remember { mutableFloatStateOf(0f) }
    var flash by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .clip(shape)
            .background(if (flash) Palette.SurfacePressed else Palette.Surface)
            .border(1.dp, Palette.Border, shape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        flash = true
                        tryAwaitRelease()
                        flash = false
                    },
                    onTap = {
                        haptic()
                        vm.key(Keys.OK)
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
                        vm.key(if (accX > 0) Keys.RIGHT else Keys.LEFT)
                        haptic()
                        accX = 0f
                        accY = 0f
                    } else if (abs(accY) > step) {
                        vm.key(if (accY > 0) Keys.DOWN else Keys.UP)
                        haptic()
                        accX = 0f
                        accY = 0f
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.KeyboardArrowUp, null, tint = Color(0x33FFFFFF),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp).size(40.dp))
        Icon(Icons.Rounded.KeyboardArrowDown, null, tint = Color(0x33FFFFFF),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp).size(40.dp))
        Text(
            "Geser untuk navigasi\nketuk untuk OK",
            color = Palette.TextDim,
            textAlign = TextAlign.Center,
            fontSize = 18.sp,
            lineHeight = 28.sp,
        )
    }
}

// ---------------------------------------------------------------------- tab Aplikasi

private data class TvApp(val name: String, val link: String, val color: Color)

private val APPS = listOf(
    TvApp("YouTube", "https://www.youtube.com", Color(0xFFFF0033)),
    TvApp("Netflix", "https://www.netflix.com/title", Color(0xFFE50914)),
    TvApp("Prime Video", "https://app.primevideo.com", Color(0xFF00A8E1)),
    TvApp("Disney+ Hotstar", "https://www.hotstar.com", Color(0xFF113CCF)),
    TvApp("Spotify", "spotify://", Color(0xFF1DB954)),
    TvApp("Vidio", "https://www.vidio.com", Color(0xFFD2232A)),
    TvApp("Viu", "https://www.viu.com", Color(0xFFF5B400)),
    TvApp("WeTV", "https://wetv.vip", Color(0xFFFF6A00)),
    TvApp("YouTube Kids", "https://www.youtubekids.com", Color(0xFFFF4E45)),
)

@Composable
private fun AppsTab(vm: RemoteViewModel) {
    Column(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(APPS) { app ->
                val haptic = rememberHaptic()
                Column(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Palette.Surface)
                        .border(1.dp, Palette.Border, RoundedCornerShape(16.dp))
                        .clickable {
                            haptic()
                            vm.openApp(app.link)
                        }
                        .padding(vertical = 16.dp, horizontal = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .size(52.dp)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(app.color),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(app.name.take(1), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        app.name, color = Palette.Text, fontSize = 13.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                    )
                }
            }
        }
        Text(
            "Aplikasi harus sudah terpasang di TV. Fitur ini khusus Android TV / Google TV.",
            color = Palette.TextDim,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
        )
    }
}
