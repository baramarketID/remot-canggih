package id.my.baramarket.remotcanggih.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardReturn
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.my.baramarket.remotcanggih.BuildConfig
import id.my.baramarket.remotcanggih.atv.Keys
import id.my.baramarket.remotcanggih.data.TvDevice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Sheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF1A1D21),
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
    Text(text, color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = Palette.TextDim, fontSize = 13.sp, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
}

// ---------------------------------------------------------------------- pilih TV

@Composable
fun DevicesSheet(vm: RemoteViewModel, onDismiss: () -> Unit) {
    val found by vm.discovery.devices.collectAsState()
    val searching by vm.discovery.searching.collectAsState()
    val saved by vm.saved.collectAsState()
    var ip by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        vm.discovery.start()
        onDispose { vm.discovery.stop() }
    }

    fun pick(d: TvDevice) {
        vm.connect(d)
        onDismiss()
    }

    Sheet(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Pilih TV", color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f))
            if (searching) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = { vm.discovery.start() }) {
                    Icon(Icons.Rounded.Refresh, "Cari ulang", tint = Palette.Text)
                }
            }
        }
        Text("HP dan TV harus terhubung ke WiFi yang sama.", color = Palette.TextDim, fontSize = 13.sp)

        Column(Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false)) {
            SectionLabel("Ditemukan di WiFi ini")
            if (found.isEmpty()) {
                Text(
                    if (searching) "Mencari TV…" else "Tidak ada TV ditemukan. Pastikan TV menyala.",
                    color = Palette.TextDim, fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            found.forEach { DeviceRow(it, onClick = { pick(it) }) }

            val savedOnly = saved.filter { s -> found.none { it.host == s.host } }
            if (savedOnly.isNotEmpty()) {
                SectionLabel("Pernah tersambung")
                savedOnly.forEach { DeviceRow(it, onClick = { pick(it) }) }
            }

            SectionLabel("Sambungkan manual (alamat IP TV)")
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it.filter { c -> c.isDigit() || c == '.' } },
                    placeholder = { Text("contoh: 192.168.1.20") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { if (ip.count { it == '.' } == 3) pick(TvDevice("TV $ip", ip)) }),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                Button(
                    onClick = { pick(TvDevice("TV $ip", ip)) },
                    enabled = ip.count { it == '.' } == 3,
                ) { Text("Sambung") }
            }
            Text(
                "IP TV bisa dilihat di TV: Pengaturan → Jaringan / Tentang → Status.",
                color = Palette.TextDim, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun DeviceRow(d: TvDevice, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.Surface)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Tv, null, tint = Palette.BlueLight)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(d.name, color = Palette.Text, fontSize = 16.sp)
            Text(d.host, color = Palette.TextDim, fontSize = 12.sp)
        }
    }
}

// ---------------------------------------------------------------------- pairing

@Composable
fun PairingDialog(state: Conn.Pairing, vm: RemoteViewModel) {
    var code by remember(state.device.host) { mutableStateOf("") }
    LaunchedEffect(state.error) { if (state.error != null) code = "" }
    AlertDialog(
        onDismissRequest = {},
        containerColor = Color(0xFF1A1D21),
        title = { Text("Masukkan kode dari TV") },
        text = {
            Column {
                Text(
                    "Lihat layar ${state.device.name}. Ketik 6 karakter kode yang muncul di TV.",
                    color = Palette.TextDim, fontSize = 14.sp,
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { v ->
                        code = v.uppercase().filter { it in "0123456789ABCDEF" }.take(6)
                        if (code.length == 6) vm.submitCode(code)
                    },
                    singleLine = true,
                    enabled = !state.busy,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 26.sp, letterSpacing = 6.sp, textAlign = TextAlign.Center, color = Palette.Text,
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
                    Text(state.error, color = Palette.Red, fontSize = 13.sp)
                }
                if (state.busy) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Menyambungkan…", color = Palette.TextDim, fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { vm.submitCode(code) }, enabled = code.length == 6 && !state.busy) { Text("Sambungkan") }
        },
        dismissButton = { TextButton(onClick = { vm.cancelPairing() }) { Text("Batal") } },
    )
}

// ---------------------------------------------------------------------- keyboard

@Composable
fun KeyboardSheet(vm: RemoteViewModel, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    fun update(new: String) {
        val old = text
        text = new
        if (old != new) vm.typeText(old, new)
    }

    Sheet(onDismiss) {
        SheetTitle("Ketik ke TV")
        Text(
            "Pilih kolom pencarian / teks di TV dulu, lalu ketik di sini.",
            color = Palette.TextDim, fontSize = 13.sp,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = text,
            onValueChange = ::update,
            placeholder = { Text("Ketik di sini…") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                vm.key(Keys.ENTER)
                text = ""
            }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus),
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { if (text.isNotEmpty()) update(text.dropLast(1)) else vm.key(Keys.DEL) },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Rounded.Backspace, null)
                Spacer(Modifier.width(6.dp))
                Text("Hapus")
            }
            OutlinedButton(onClick = { update("") }, modifier = Modifier.weight(1f)) { Text("Kosongkan") }
            Button(
                onClick = {
                    vm.key(Keys.ENTER)
                    text = ""
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Rounded.KeyboardReturn, null)
                Spacer(Modifier.width(6.dp))
                Text("Enter")
            }
        }
    }
}

// ---------------------------------------------------------------------- angka

@Composable
fun NumpadSheet(vm: RemoteViewModel, onDismiss: () -> Unit) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("Menu", "0", "OK"),
    )
    val haptic = rememberHaptic()
    Sheet(onDismiss) {
        SheetTitle("Tombol angka")
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { label ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(58.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Palette.Surface)
                            .clickable {
                                haptic()
                                when (label) {
                                    "Menu" -> vm.key(Keys.MENU)
                                    "OK" -> vm.key(Keys.OK)
                                    else -> vm.key(Keys.number(label.toInt()))
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, color = Palette.Text, fontSize = if (label.length > 1) 16.sp else 24.sp)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------- pengaturan

@Composable
fun SettingsSheet(vm: RemoteViewModel, onDismiss: () -> Unit, onOpenUrl: (String) -> Unit) {
    val saved by vm.saved.collectAsState()
    val update by vm.update.collectAsState()
    var confirmReset by remember { mutableStateOf(false) }

    Sheet(onDismiss) {
        SheetTitle("Pengaturan")
        Text("Remot Canggih versi ${BuildConfig.VERSION_NAME}", color = Palette.Text, fontSize = 15.sp)
        Text(
            if (update != null) "Versi baru $update tersedia." else "Gratis, tanpa iklan, tanpa login.",
            color = if (update != null) Palette.BlueLight else Palette.TextDim, fontSize = 13.sp,
        )
        Spacer(Modifier.height(10.dp))
        Button(onClick = { onOpenUrl(DOWNLOAD_URL) }, modifier = Modifier.fillMaxWidth()) {
            Text(if (update != null) "Download update" else "Buka halaman download")
        }

        SectionLabel("TV tersimpan")
        if (saved.isEmpty()) Text("Belum ada.", color = Palette.TextDim, fontSize = 14.sp)
        saved.forEach { d ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Palette.Surface)
                    .padding(start = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(d.name, color = Palette.Text, fontSize = 15.sp)
                    Text(d.host, color = Palette.TextDim, fontSize = 12.sp)
                }
                IconButton(onClick = { vm.forget(d) }) { Icon(Icons.Rounded.Delete, "Hapus", tint = Palette.TextDim) }
            }
        }
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = Palette.Border)
        TextButton(onClick = { confirmReset = true }) {
            Text("Reset: lupakan semua TV", color = Palette.Red)
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Lupakan semua TV?") },
            text = { Text("Semua TV harus disambungkan ulang dan memasukkan kode lagi.") },
            confirmButton = {
                Button(
                    onClick = {
                        vm.resetAll()
                        confirmReset = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Red),
                ) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Batal") } },
        )
    }
}

// ---------------------------------------------------------------------- cast

@Composable
fun CastSheet(onDismiss: () -> Unit, onOpenCast: () -> Unit) {
    Sheet(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Cast, null, tint = Palette.BlueLight)
            Spacer(Modifier.width(10.dp))
            Text("Mirror layar ke TV", color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Mirror langsung dari aplikasi ini (pilih seluruh layar atau satu aplikasi) sedang disiapkan " +
                "untuk update berikutnya.\n\nSementara itu, pakai fitur cast bawaan HP. HP dan TV harus satu WiFi.",
            color = Palette.TextDim, fontSize = 14.sp, lineHeight = 21.sp,
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                onOpenCast()
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Buka cast bawaan HP") }
    }
}
