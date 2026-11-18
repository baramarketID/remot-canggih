package id.my.baramarket.remotcanggih.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.my.baramarket.remotcanggih.core.Conn
import id.my.baramarket.remotcanggih.core.RemoteController
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Panel { None, Devices, Keyboard, Numpad, Cast, AddApp }

/** Aksi yang perlu Activity (izin, intent sistem). */
class AppActions(
    val onMic: () -> Unit,
    val onOpenCast: () -> Unit,
    val onOpenUrl: (String) -> Unit,
    val onEnableNotifControls: () -> Unit,
    val onPinWidget: () -> Unit,
    val onStartMirror: () -> Unit,
    val onPickMedia: () -> Unit,
)

const val DOWNLOAD_URL = "https://baramarket.my.id/remot/download"

@Composable
fun RemoteApp(rc: RemoteController, actions: AppActions) {
    val c = LocalColors.current
    val conn by rc.conn.collectAsState()
    val update by rc.update.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var panel by rememberSaveable { mutableStateOf(Panel.None) }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.bg)
    ) {
        Column(
            Modifier
                .weight(1f)
                .statusBarsPadding()
        ) {
            Header(rc, onCast = { panel = Panel.Cast })
            TvCard(rc, conn, onClick = { panel = Panel.Devices })
            StatusStrip(conn, update, onRetry = { rc.reconnectIfNeeded() }, onPick = { panel = Panel.Devices },
                onUpdate = { actions.onOpenUrl(DOWNLOAD_URL) })
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    (fadeIn(tween(260)) +
                        slideInVertically(tween(420, easing = GlideEasing)) { 28 } +
                        scaleIn(tween(420, easing = GlideEasing), initialScale = 0.985f))
                        .togetherWith(fadeOut(tween(110)) + scaleOut(tween(110), targetScale = 0.99f))
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                label = "tab",
            ) { t ->
                when (t) {
                    0 -> RemoteTab(rc, onKeyboard = { panel = Panel.Keyboard }, onNumpad = { panel = Panel.Numpad },
                        onMic = actions.onMic)
                    1 -> TouchpadTab(rc)
                    2 -> AppsTab(rc, onAdd = { panel = Panel.AddApp })
                    else -> SettingsTab(rc, actions, onAddTv = { panel = Panel.Devices })
                }
            }
        }
        BottomNav(tab) { tab = it }
    }

    when (panel) {
        Panel.Devices -> DevicesSheet(rc, onDismiss = { panel = Panel.None })
        Panel.Keyboard -> KeyboardSheet(rc, onDismiss = { panel = Panel.None })
        Panel.Numpad -> NumpadSheet(rc, onDismiss = { panel = Panel.None })
        Panel.Cast -> CastSheet(onDismiss = { panel = Panel.None }, actions = actions)
        Panel.AddApp -> AddAppDialog(rc, onDismiss = { panel = Panel.None })
        Panel.None -> {}
    }
    val pairing = conn as? Conn.Pairing
    if (pairing != null) PairingDialog(pairing, rc)
    val approve = conn as? Conn.Approve
    if (approve != null) ApproveDialog(approve, rc)
}

// ---------------------------------------------------------------------- header

@Composable
private fun Header(rc: RemoteController, onCast: () -> Unit) {
    val c = LocalColors.current
    val dark by rc.dark.collectAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(c.accent),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.SettingsRemote, null, tint = c.accentInk, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(10.dp))
        Text("Remot Canggih", fontSize = 17.sp, fontWeight = FontWeight.Black, color = c.ink, modifier = Modifier.weight(1f))
        ThemeToggle(dark) { rc.setDark(!dark) }
        Spacer(Modifier.width(8.dp))
        CircleIconButton(Icons.Rounded.Cast, "Mirror / cast", onCast)
    }
}

@Composable
private fun CircleIconButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val c = LocalColors.current
    Box(
        Modifier
            .size(44.dp)
            .bouncyClick(onClickLabel = label, down = 0.92f, onClick = onClick)
            .clip(CircleShape)
            .background(c.surface)
            .border(1.dp, c.line, CircleShape),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = c.ink, modifier = Modifier.size(20.dp)) }
}

/** Tombol tema dengan efek "squish" seperti saklar di /portal. */
@Composable
private fun ThemeToggle(dark: Boolean, onToggle: () -> Unit) {
    val c = LocalColors.current
    val sx = remember { Animatable(1f) }
    val sy = remember { Animatable(1f) }
    val rot = remember { Animatable(0f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(dark) {
        if (first) {
            first = false
            return@LaunchedEffect
        }
        rot.snapTo(-90f)
        coroutineScope {
            launch { rot.animateTo(0f, tween(520, easing = SpringEasing)) }
            launch {
                sx.animateTo(1f, keyframes { durationMillis = 420; 1.18f at 150; 1f at 420 using SpringEasing })
            }
            launch {
                sy.animateTo(1f, keyframes { durationMillis = 420; 0.86f at 150; 1f at 420 using SpringEasing })
            }
        }
    }
    Box(
        Modifier
            .size(44.dp)
            .bouncyClick(onClickLabel = if (dark) "Mode terang" else "Mode gelap", down = 0.92f, onClick = onToggle)
            .clip(CircleShape)
            .background(c.surface)
            .border(1.dp, c.line, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (dark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
            contentDescription = if (dark) "Mode terang" else "Mode gelap",
            tint = c.accent,
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer {
                    scaleX = sx.value
                    scaleY = sy.value
                    rotationZ = rot.value
                },
        )
    }
}

@Composable
private fun TvCard(rc: RemoteController, conn: Conn, onClick: () -> Unit) {
    val c = LocalColors.current
    val sleepAt by rc.sleepAt.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(sleepAt) {
        while (sleepAt > 0) {
            now = System.currentTimeMillis()
            delay(20_000)
        }
    }
    val (name, status, dot) = when (conn) {
        is Conn.Connected -> Triple(conn.device.name, "Terhubung", c.ok)
        is Conn.Connecting -> Triple(conn.device.name, "Menghubungkan…", c.warn)
        is Conn.Pairing -> Triple(conn.device.name, "Masukkan kode dari TV", c.warn)
        is Conn.Failed -> Triple(conn.device.name, "Terputus", c.power)
        is Conn.Approve -> Triple(conn.device.name, "Setujui di TV", c.warn)
        Conn.Idle -> Triple("Belum ada TV", "Ketuk untuk mencari TV", c.faint)
    }
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .padding(start = 20.dp, end = 20.dp, top = 14.dp)
            .fillMaxWidth()
            .bouncyClick(onClickLabel = "Ganti TV", onClick = onClick)
            .clip(shape)
            .background(c.surface)
            .border(1.dp, c.line, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(c.accentSoft),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Tv, null, tint = c.accent, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.ink, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
                Spacer(Modifier.width(6.dp))
                Text(status, fontSize = 13.sp, color = c.muted, maxLines = 1)
                if (sleepAt > now) {
                    val min = ((sleepAt - now) / 60_000L).coerceAtLeast(1)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Rounded.Timer, null, tint = c.accent, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("$min mnt", fontSize = 13.sp, color = c.accent, fontWeight = FontWeight.Bold)
                }
            }
        }
        Text("Ganti", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.accent)
        Icon(Icons.Rounded.ExpandMore, null, tint = c.accent, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun StatusStrip(conn: Conn, update: String?, onRetry: () -> Unit, onPick: () -> Unit, onUpdate: () -> Unit) {
    val c = LocalColors.current
    val (msg, action) = when {
        conn is Conn.Failed -> conn.message to ("Coba lagi" to onRetry)
        conn is Conn.Idle -> "Pastikan HP dan TV satu WiFi, lalu pilih TV." to ("Cari TV" to onPick)
        update != null -> "Versi baru $update tersedia." to ("Update" to onUpdate)
        else -> null to null
    }
    if (msg == null) return
    Row(
        Modifier
            .padding(start = 20.dp, end = 20.dp, top = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(c.surface2)
            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(msg, color = c.muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
        if (action != null) {
            Text(
                action.first,
                color = c.accent,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier
                    .bouncyClick(onClick = action.second)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------- menu bawah

@Composable
private fun BottomNav(selected: Int, onSelect: (Int) -> Unit) {
    val c = LocalColors.current
    val items = listOf(
        "Remot" to Icons.Rounded.SettingsRemote,
        "Touchpad" to Icons.Rounded.TouchApp,
        "Aplikasi" to Icons.Rounded.Apps,
        "Setelan" to Icons.Rounded.Tune,
    )
    Column(Modifier.background(c.surface)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            items.forEachIndexed { i, (label, icon) ->
                NavItem(label, icon, i == selected) { onSelect(i) }
            }
        }
    }
}

/** Item menu bawah dengan animasi "pop" seperti bnpop di /portal. */
@Composable
private fun NavItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val c = LocalColors.current
    val pop = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(selected) {
        if (first) {
            first = false
            return@LaunchedEffect
        }
        if (selected) {
            pop.snapTo(0.7f)
            pop.animateTo(1f, keyframes {
                durationMillis = 550
                1.12f at 275 using SpringEasing
                0.97f at 430
            })
        }
    }
    val tint = if (selected) c.accent else c.faint
    Column(
        Modifier
            .width(80.dp)
            .bouncyClick(onClickLabel = label, down = 0.94f, onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(width = 58.dp, height = 30.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(if (selected) c.accentSoft else androidx.compose.ui.graphics.Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon, null, tint = tint,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer {
                        scaleX = pop.value
                        scaleY = pop.value
                    },
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = tint, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}
