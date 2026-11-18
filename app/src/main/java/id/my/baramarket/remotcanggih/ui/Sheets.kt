package id.my.baramarket.remotcanggih.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backspace
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.KeyboardReturn
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.ScreenShare
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.my.baramarket.remotcanggih.atv.Keys
import id.my.baramarket.remotcanggih.cast.CastManager
import id.my.baramarket.remotcanggih.cast.MirrorService
import id.my.baramarket.remotcanggih.core.Conn
import id.my.baramarket.remotcanggih.core.RemoteController
import id.my.baramarket.remotcanggih.data.TvDevice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Sheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalColors.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surface,
        contentColor = c.ink,
        scrimColor = Color(0x99000000),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
                .imePadding()
        ) { content() }
    }
}

@Composable
private fun SheetTitle(text: String) {
    Text(text, color = LocalColors.current.ink, fontSize = 20.sp, fontWeight = FontWeight.Black)
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun SectionLabel(text: String) {
    val c = LocalColors.current
    Text(
        text.uppercase(), color = c.faint, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
    )
}

@Composable
private fun fieldColors() = LocalColors.current.let { c ->
    OutlinedTextFieldDefaults.colors(
        focusedBorderColor = c.accent,
        unfocusedBorderColor = c.keyLine,
        cursorColor = c.accent,
        focusedTextColor = c.ink,
        unfocusedTextColor = c.ink,
        focusedPlaceholderColor = c.faint,
        unfocusedPlaceholderColor = c.faint,
        focusedLabelColor = c.accent,
        unfocusedLabelColor = c.muted,
    )
}

@Composable
private fun AccentButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = LocalColors.current
    Button(
        onClick = onClick, enabled = enabled, modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.accentInk),
    ) { Text(text, fontWeight = FontWeight.Bold) }
}

// ---------------------------------------------------------------------- pilih TV

@Composable
fun DevicesSheet(rc: RemoteController, onDismiss: () -> Unit) {
    val c = LocalColors.current
    val found by rc.discovery.devices.collectAsState()
    val searching by rc.discovery.searching.collectAsState()
    val saved by rc.saved.collectAsState()
    var ip by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        rc.discovery.start()
        onDispose { rc.discovery.stop() }
    }

    fun pick(d: TvDevice) {
        rc.connect(d)
        onDismiss()
    }

    Sheet(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Pilih TV", color = c.ink, fontSize = 20.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            if (searching) {
                CircularProgressIndicator(Modifier.size(22.dp), color = c.accent, strokeWidth = 2.dp)
            } else {
                Box(
                    Modifier
                        .size(44.dp)
                        .bouncyClick(onClickLabel = "Cari ulang", down = 0.9f) { rc.discovery.start() },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Refresh, "Cari ulang", tint = c.ink) }
            }
        }
        Text("HP dan TV harus terhubung ke WiFi yang sama.", color = c.muted, fontSize = 13.sp)

        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .weight(1f, fill = false)
        ) {
            if (saved.isNotEmpty()) {
                SectionLabel("TV kamu")
                saved.forEach { d ->
                    DeviceRow(d, if (found.any { it.host == d.host }) "Ada di WiFi ini" else d.host) { pick(d) }
                }
            }
            SectionLabel("Ditemukan di WiFi ini")
            val fresh = found.filter { f -> saved.none { it.host == f.host } }
            if (fresh.isEmpty()) {
                Text(
                    if (searching) "Mencari TV…" else "Tidak ada TV baru. Pastikan TV menyala.",
                    color = c.muted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            fresh.forEach { d -> DeviceRow(d, d.host) { pick(d) } }

            SectionLabel("Sambungkan manual (alamat IP TV)")
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    placeholder = { Text("contoh: 192.168.1.20") },
                    singleLine = true,
                    colors = fieldColors(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { if (ip.count { it == '.' } == 3) pick(TvDevice("TV $ip", ip)) }),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                AccentButton("Sambung", { pick(TvDevice("TV $ip", ip)) }, enabled = ip.count { it == '.' } == 3)
            }
            Text(
                "IP TV ada di TV: Setelan → Jaringan → Status / Tentang.",
                color = c.faint, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun DeviceRow(d: TvDevice, subtitle: String, onClick: () -> Unit) {
    val c = LocalColors.current
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .bouncyClick(onClickLabel = "Sambungkan ${d.name}", onClick = onClick)
            .clip(shape)
            .background(c.surface2)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(c.accentSoft),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Tv, null, tint = c.accent, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(d.name, color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = c.muted, fontSize = 12.sp)
        }
    }
}

// ---------------------------------------------------------------------- pairing

@Composable
fun PairingDialog(state: Conn.Pairing, rc: RemoteController) {
    val c = LocalColors.current
    var code by remember(state.device.host) { mutableStateOf("") }
    LaunchedEffect(state.error) { if (state.error != null) code = "" }
    AlertDialog(
        onDismissRequest = {},
        containerColor = c.surface,
        title = { Text("Masukkan kode dari TV", fontWeight = FontWeight.Black, color = c.ink) },
        text = {
            Column {
                Text(
                    "Lihat layar ${state.device.name}. Ketik 6 karakter kode yang muncul di TV.",
                    color = c.muted, fontSize = 14.sp,
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { v ->
                        code = v.uppercase().filter { it in "0123456789ABCDEF" }.take(6)
                        if (code.length == 6) rc.submitCode(code)
                    },
                    singleLine = true,
                    enabled = !state.busy,
                    colors = fieldColors(),
                    shape = RoundedCornerShape(12.dp),
                    textStyle = TextStyle(
                        fontSize = 26.sp, letterSpacing = 6.sp, textAlign = TextAlign.Center, color = c.ink,
                        fontWeight = FontWeight.Black, fontFamily = Lato,
                    ),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        keyboardType = KeyboardType.Ascii,
                        autoCorrectEnabled = false,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(state.error, color = c.power, fontSize = 13.sp)
                }
                if (state.busy) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = c.accent, strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Menyambungkan…", color = c.muted, fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = { AccentButton("Sambungkan", { rc.submitCode(code) }, enabled = code.length == 6 && !state.busy) },
        dismissButton = { TextButton(onClick = { rc.cancelPairing() }) { Text("Batal", color = c.ink) } },
    )
}

// ---------------------------------------------------------------------- keyboard

@Composable
fun KeyboardSheet(rc: RemoteController, onDismiss: () -> Unit) {
    val c = LocalColors.current
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    fun update(new: String) {
        val old = text
        text = new
        if (old != new) rc.typeText(old, new)
    }

    fun enter() {
        rc.key(Keys.ENTER)
        text = ""
    }

    Sheet(onDismiss) {
        SheetTitle("Ketik ke TV")
        Text("Pilih kolom pencarian / teks di TV dulu, lalu ketik di sini.", color = c.muted, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { update(it) },
            placeholder = { Text("Ketik di sini…") },
            singleLine = true,
            colors = fieldColors(),
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { enter() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus),
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(Icons.Rounded.Backspace, "Hapus", { if (text.isNotEmpty()) update(text.dropLast(1)) else rc.key(Keys.DEL) },
                Modifier.weight(1f), background = c.surface2)
            Chip(null, "Kosongkan", { update("") }, Modifier.weight(1f), background = c.surface2)
            Chip(Icons.Rounded.KeyboardReturn, "Enter", { enter() }, Modifier.weight(1f),
                background = c.accent, tint = c.accentInk, border = null)
        }
    }
}

// ---------------------------------------------------------------------- angka

@Composable
fun NumpadSheet(rc: RemoteController, onDismiss: () -> Unit) {
    val c = LocalColors.current
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("Menu", "0", "OK"),
    )
    Sheet(onDismiss) {
        SheetTitle("Tombol angka")
        rows.forEach { row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { label ->
                    val code = when (label) {
                        "Menu" -> Keys.MENU
                        "OK" -> Keys.OK
                        else -> Keys.number(label.toInt())
                    }
                    KeyButton(
                        null, label, { rc.key(code) }, Modifier.weight(1f), size = 58.dp, width = 1000.dp,
                        shape = RoundedCornerShape(14.dp), text = label,
                        background = if (label == "OK") c.accent else c.surface2,
                        tint = if (label == "OK") c.accentInk else c.ink,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------- tambah aplikasi

@Composable
fun AddAppDialog(rc: RemoteController, onDismiss: () -> Unit) {
    val c = LocalColors.current
    var name by remember { mutableStateOf("") }
    var link by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text("Tambah aplikasi", fontWeight = FontWeight.Black, color = c.ink) },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(30) }, singleLine = true,
                    label = { Text("Nama") }, placeholder = { Text("mis. Kodi") },
                    colors = fieldColors(), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = link, onValueChange = { link = it.trim() }, singleLine = true,
                    label = { Text("Nama paket atau tautan") }, placeholder = { Text("mis. org.xbmc.kodi") },
                    colors = fieldColors(), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Nama paket ada di alamat halaman Play Store aplikasinya, setelah \"id=\".",
                    color = c.muted, fontSize = 12.sp,
                )
            }
        },
        confirmButton = {
            AccentButton("Tambah", {
                rc.addApp(name, link)
                onDismiss()
            }, enabled = name.isNotBlank() && link.isNotBlank())
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal", color = c.ink) } },
    )
}

// ---------------------------------------------------------------------- cast

@Composable
fun CastSheet(onDismiss: () -> Unit, actions: AppActions) {
    val c = LocalColors.current
    val ctx = LocalContext.current
    val cast = remember { CastManager.get(ctx) }
    val targets by cast.targets.collectAsState()
    val connectedTo by cast.connectedTo.collectAsState()
    val mirroring by cast.mirroring.collectAsState()
    val status by cast.status.collectAsState()

    DisposableEffect(Unit) {
        cast.startDiscovery()
        onDispose { cast.stopDiscovery() }
    }

    Sheet(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.accentSoft),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Cast, null, tint = c.accent) }
            Spacer(Modifier.width(12.dp))
            Text("Cast & mirror", color = c.ink, fontSize = 20.sp, fontWeight = FontWeight.Black)
        }

        if (!cast.available) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Fitur ini butuh Google Play Services, yang tidak ada di HP ini. Pakai fitur cast bawaan HP.",
                color = c.muted, fontSize = 14.sp,
            )
            Spacer(Modifier.height(14.dp))
            AccentButton("Buka cast bawaan HP", {
                actions.onOpenCast()
                onDismiss()
            }, Modifier.fillMaxWidth())
            return@Sheet
        }

        SectionLabel("TV dengan Chromecast")
        if (targets.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                CircularProgressIndicator(Modifier.size(18.dp), color = c.accent, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Mencari… TV harus Android TV / Google TV dan satu WiFi.", color = c.muted, fontSize = 13.sp)
            }
        }
        targets.forEach { t ->
            val shape = RoundedCornerShape(14.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .bouncyClick(onClickLabel = "Pilih ${t.name}") { if (!t.selected) cast.select(t.id) }
                    .clip(shape)
                    .background(if (t.selected) c.accentSoft else c.surface2)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Tv, null, tint = c.accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(t.name, color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (t.selected) {
                    Text("Putuskan", color = c.power, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                        modifier = Modifier
                            .bouncyClick { cast.disconnect() }
                            .padding(horizontal = 6.dp, vertical = 4.dp))
                }
            }
        }

        val ready = connectedTo != null
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (mirroring) {
                Button(
                    onClick = { MirrorService.stop(ctx) },
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = c.power, contentColor = Color.White),
                ) { Text("Hentikan mirror", fontWeight = FontWeight.Bold) }
            } else {
                Button(
                    onClick = actions.onStartMirror, enabled = ready,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.accentInk),
                ) {
                    Icon(Icons.Rounded.ScreenShare, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Mirror layar", fontWeight = FontWeight.Bold)
                }
            }
            Button(
                onClick = actions.onPickMedia, enabled = ready,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = c.surface2, contentColor = c.ink),
            ) {
                Icon(Icons.Rounded.PhotoLibrary, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Foto / video", fontWeight = FontWeight.Bold)
            }
        }
        val info = status ?: when {
            !ready -> "Pilih TV di atas dulu."
            mirroring -> "Sedang mirror. Ada jeda sekitar 3–5 detik; suara tetap keluar dari HP."
            else -> "Mirror: pilih seluruh layar atau satu aplikasi (Android 14+). Foto/video dikirim dengan kualitas penuh."
        }
        Text(info, color = if (status != null) c.warn else c.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
        TextButton(onClick = {
            actions.onOpenCast()
            onDismiss()
        }) { Text("TV tidak muncul? Pakai cast bawaan HP", color = c.accent) }
    }
}
