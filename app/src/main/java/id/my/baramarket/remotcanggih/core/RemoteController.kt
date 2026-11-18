package id.my.baramarket.remotcanggih.core

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import id.my.baramarket.remotcanggih.BuildConfig
import id.my.baramarket.remotcanggih.atv.AtvCert
import id.my.baramarket.remotcanggih.atv.AtvPairing
import id.my.baramarket.remotcanggih.atv.AtvRemote
import id.my.baramarket.remotcanggih.tv.AtvClient
import id.my.baramarket.remotcanggih.tv.LgClient
import id.my.baramarket.remotcanggih.tv.SamsungClient
import id.my.baramarket.remotcanggih.tv.TvClient
import id.my.baramarket.remotcanggih.tv.TvKind
import id.my.baramarket.remotcanggih.data.DeviceStore
import id.my.baramarket.remotcanggih.data.TvDevice
import id.my.baramarket.remotcanggih.data.TvDiscovery
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

sealed interface Conn {
    data object Idle : Conn
    data class Connecting(val device: TvDevice) : Conn
    data class Connected(val device: TvDevice) : Conn

    /** TV sedang menampilkan kode; pengguna perlu mengetiknya. */
    data class Pairing(val device: TvDevice, val error: String? = null, val busy: Boolean = false) : Conn
    data class Failed(val device: TvDevice, val message: String) : Conn

    /** Samsung / LG: TV sedang meminta persetujuan ("Izinkan"). */
    data class Approve(val device: TvDevice) : Conn
}

/**
 * Pusat kendali: satu koneksi ke TV yang dipakai bersama oleh layar aplikasi,
 * notifikasi, widget, dan timer tidur.
 */
class RemoteController private constructor(private val app: Context) {

    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: RemoteController? = null

        fun get(context: Context): RemoteController =
            instance ?: synchronized(this) {
                instance ?: RemoteController(context.applicationContext).also { instance = it }
            }
    }

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val prefs = Prefs(app)
    private val store = DeviceStore(app)
    val discovery = TvDiscovery(app)
    private val certFile = File(app.filesDir, "atv_client.p12")

    /** Satu thread untuk semua kiriman, supaya urutan tombol terjaga. */
    private val sender = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    private val _conn = MutableStateFlow<Conn>(Conn.Idle)
    val conn: StateFlow<Conn> = _conn

    private val _saved = MutableStateFlow(store.saved())
    val saved: StateFlow<List<TvDevice>> = _saved

    private val _update = MutableStateFlow<String?>(null)
    val update: StateFlow<String?> = _update

    private val _dark = MutableStateFlow(prefs.darkTheme)
    val dark: StateFlow<Boolean> = _dark

    private val _volumeKeys = MutableStateFlow(prefs.volumeKeys)
    val volumeKeys: StateFlow<Boolean> = _volumeKeys

    private val _notifControls = MutableStateFlow(prefs.notifControls)
    val notifControls: StateFlow<Boolean> = _notifControls

    private val _sleepAt = MutableStateFlow(prefs.sleepAt.takeIf { it > System.currentTimeMillis() } ?: 0L)
    val sleepAt: StateFlow<Long> = _sleepAt

    private val _apps = MutableStateFlow(prefs.visibleApps())
    val apps: StateFlow<List<TvApp>> = _apps

    private val _toast = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val toast: SharedFlow<String> = _toast

    private var client: TvClient? = null
    private var pairing: AtvPairing? = null
    private var job: Job? = null
    private var started = false

    /** Dipanggil saat aplikasi dibuka: sambung ke TV terakhir & cek update. */
    fun start() {
        if (started) return
        started = true
        store.last()?.let { connect(it) }
        checkUpdate()
        if (_notifControls.value || _sleepAt.value > 0) RemoteService.sync(app)
    }

    val currentDevice: TvDevice?
        get() = when (val c = _conn.value) {
            is Conn.Connecting -> c.device
            is Conn.Connected -> c.device
            is Conn.Pairing -> c.device
            is Conn.Failed -> c.device
            is Conn.Approve -> c.device
            Conn.Idle -> null
        }

    val isConnected: Boolean
        get() = _conn.value is Conn.Connected && client?.ready == true

    // ------------------------------------------------------------------ koneksi

    fun connect(device: TvDevice) {
        job?.cancel()
        closeAll()
        _conn.value = Conn.Connecting(device)
        job = scope.launch(Dispatchers.IO) {
            val dev = if (device.kind.isEmpty()) detectKind(device) else device
            if (dev == null) {
                _conn.value = Conn.Failed(device, "TV tidak ditemukan di alamat itu, atau merk TV belum didukung.")
                return@launch
            }
            when (dev.kind) {
                TvKind.SAMSUNG, TvKind.LG -> openWebSocketTv(dev)
                else -> try {
                    openRemote(dev)
                } catch (e: AtvRemote.NotPairedException) {
                    startPairing(dev)
                } catch (e: Exception) {
                    _conn.value = Conn.Failed(dev, "Tidak bisa terhubung. Pastikan TV menyala dan satu WiFi dengan HP.")
                }
            }
        }
    }

    /** IP manual: tebak merk dari port yang terbuka. */
    private fun detectKind(device: TvDevice): TvDevice? {
        fun open(port: Int) = try {
            java.net.Socket().use { it.connect(java.net.InetSocketAddress(device.host, port), 1500) }
            true
        } catch (e: Exception) {
            false
        }
        return when {
            open(AtvRemote.PORT) -> device.copy(kind = TvKind.ANDROID, port = AtvRemote.PORT)
            open(8002) || open(8001) -> device.copy(kind = TvKind.SAMSUNG, name = "Samsung TV", port = 0)
            open(3001) || open(3000) -> device.copy(kind = TvKind.LG, name = "LG TV", port = 0)
            else -> null
        }
    }

    /** Samsung / LG: sambung lewat WebSocket, simpan token/kunci dari TV. */
    private fun openWebSocketTv(device: TvDevice) {
        val saved = store.saved().firstOrNull { it.host == device.host }
        val secret = device.secret ?: saved?.secret
        val onClosed: (Exception?) -> Unit = {
            scope.launch {
                val c = _conn.value
                if (c is Conn.Connected && c.device.host == device.host) {
                    _conn.value = Conn.Failed(c.device, "Koneksi ke TV terputus.")
                }
            }
        }
        val onPrompt = { _conn.value = Conn.Approve(device) }
        try {
            val newSecret: String?
            val tv: TvClient
            if (device.kind == TvKind.SAMSUNG) {
                val sc = SamsungClient(device.host, secret, onClosed)
                newSecret = sc.connect(onPrompt)
                tv = sc
            } else {
                val lc = LgClient(device.host, secret, onClosed)
                newSecret = lc.connect(onPrompt)
                tv = lc
            }
            client = tv
            val kept = store.save(device.copy(secret = newSecret ?: secret))
            _saved.value = store.saved()
            _conn.value = Conn.Connected(kept)
        } catch (e: SamsungClient.DeniedException) {
            _conn.value = Conn.Failed(device, "Ditolak di TV. Coba lagi lalu pilih Izinkan.")
        } catch (e: LgClient.DeniedException) {
            _conn.value = Conn.Failed(device, "Ditolak di TV. Coba lagi lalu pilih Terima.")
        } catch (e: Exception) {
            _conn.value = Conn.Failed(device, "Tidak bisa terhubung ke TV. Pastikan TV menyala dan satu WiFi dengan HP.")
        }
    }

    /** Sambung ulang bila koneksi terputus (mis. setelah HP dikunci). */
    fun reconnectIfNeeded() {
        val c = _conn.value
        if (c is Conn.Failed) connect(c.device)
        else if (c is Conn.Connected && client?.ready != true) connect(c.device)
        else if (c is Conn.Idle) store.last()?.let { connect(it) }
    }

    /** Untuk notifikasi/widget/timer: pastikan tersambung, tunggu maksimal 10 detik. */
    suspend fun ensureConnected(): Boolean {
        if (isConnected) return true
        withContext(Dispatchers.Main) {
            if (_conn.value !is Conn.Connecting) {
                val dev = currentDevice ?: store.last()
                if (dev != null) connect(dev)
            }
        }
        val result = withTimeoutOrNull(10_000) {
            conn.first { it is Conn.Connected || it is Conn.Failed || it is Conn.Pairing || it is Conn.Idle || it is Conn.Approve }
        }
        return result is Conn.Connected && isConnected
    }

    private suspend fun openRemote(device: TvDevice) {
        val cert = AtvCert.loadOrCreate(certFile)
        val ready = CompletableDeferred<Unit>()
        val r = AtvRemote(cert, device.host, device.port, Build.MODEL ?: "HP", Build.MANUFACTURER ?: "Android",
            BuildConfig.VERSION_NAME, object : AtvRemote.Listener {
                override fun onReady() {
                    ready.complete(Unit)
                }

                override fun onPowerChanged(on: Boolean) {}

                override fun onDisconnected(error: Exception?) {
                    if (!ready.isCompleted) {
                        ready.completeExceptionally(error ?: Exception("Terputus"))
                        return
                    }
                    val c = _conn.value
                    if (c is Conn.Connected && c.device.host == device.host) {
                        _conn.value = Conn.Failed(c.device, "Koneksi ke TV terputus.")
                    }
                }
            })
        client = AtvClient(r)
        r.connect()
        try {
            withTimeout(8000) { ready.await() }
        } catch (e: Exception) {
            r.close()
            throw AtvRemote.NotPairedException(e)
        }
        val kept = store.save(device)
        _saved.value = store.saved()
        _conn.value = Conn.Connected(kept)
    }

    private fun startPairing(device: TvDevice, error: String? = null) {
        try {
            pairing?.close()
            val p = AtvPairing(AtvCert.loadOrCreate(certFile), device.host, AtvPairing.PORT, "Remot Canggih (${Build.MODEL})")
            pairing = p
            p.start()
            _conn.value = Conn.Pairing(device, error)
        } catch (e: Exception) {
            _conn.value = Conn.Failed(
                device,
                "TV tidak merespons permintaan pairing. Pastikan ini Android TV / Google TV dan layar TV menyala."
            )
        }
    }

    fun submitCode(code: String) {
        val c = _conn.value as? Conn.Pairing ?: return
        val p = pairing ?: return
        _conn.value = c.copy(busy = true, error = null)
        job = scope.launch(Dispatchers.IO) {
            try {
                p.finish(code)
                p.close()
                pairing = null
                _conn.value = Conn.Connecting(c.device)
                openRemote(c.device)
            } catch (e: AtvPairing.WrongCodeException) {
                startPairing(c.device, "Kode salah. Coba lagi dengan kode baru di TV.")
            } catch (e: Exception) {
                _conn.value = Conn.Failed(c.device, "Pairing gagal. Coba sambungkan ulang.")
            }
        }
    }

    fun cancelPairing() {
        pairing?.close()
        pairing = null
        job?.cancel()
        _conn.value = Conn.Idle
    }

    fun rename(device: TvDevice, name: String) {
        val n = name.trim().ifEmpty { return }
        store.rename(device.host, n)
        _saved.value = store.saved()
        when (val c = _conn.value) {
            is Conn.Connected -> if (c.device.host == device.host) _conn.value = c.copy(device = c.device.copy(name = n))
            is Conn.Failed -> if (c.device.host == device.host) _conn.value = c.copy(device = c.device.copy(name = n))
            else -> {}
        }
    }

    fun forget(device: TvDevice) {
        store.remove(device)
        _saved.value = store.saved()
        if (currentDevice?.host == device.host) {
            closeAll()
            _conn.value = Conn.Idle
        }
    }

    fun resetAll() {
        closeAll()
        store.clear()
        AtvCert.delete(certFile)
        _saved.value = emptyList()
        _conn.value = Conn.Idle
    }

    private fun closeAll() {
        client?.close()
        client = null
        pairing?.close()
        pairing = null
    }

    // ------------------------------------------------------------------ perintah

    private fun withRemote(block: (TvClient) -> Unit) {
        val r = client
        if (r == null || !r.ready || _conn.value !is Conn.Connected) {
            _toast.tryEmit("Belum terhubung ke TV")
            reconnectIfNeeded()
            return
        }
        scope.launch(sender) {
            try {
                block(r)
            } catch (e: Exception) {
                _toast.tryEmit("Gagal mengirim perintah")
            }
        }
    }

    fun key(code: Int) = withRemote { it.key(code) }

    fun openApp(app: TvApp) = withRemote {
        if (!it.launch(app)) _toast.tryEmit("${app.name} belum tersedia untuk TV ini")
    }

    /** Menyamakan isi kolom teks di TV dengan teks di HP. */
    fun typeText(old: String, new: String) = withRemote { it.text(old, new) }

    fun sendSpoken(text: String) = typeText("", text)

    /** Info teknis koneksi (ditampilkan di Setelan untuk membantu mencari masalah). */
    fun debugInfo(): String {
        val c = client
        val d = currentDevice
        return buildString {
            append("Status: ").append(
                when (_conn.value) {
                    is Conn.Connected -> "terhubung"
                    is Conn.Connecting -> "menghubungkan"
                    is Conn.Pairing -> "menunggu kode"
                    is Conn.Approve -> "menunggu persetujuan"
                    is Conn.Failed -> "terputus"
                    Conn.Idle -> "belum ada TV"
                }
            )
            if (d != null) append("\nTV: ").append(d.name).append(" (").append(d.kind).append(", ").append(d.host).append(")")
            append("\nAplikasi: ").append(BuildConfig.VERSION_NAME)
            if (c != null) append("\n").append(c.debug().ifEmpty { "Tersambung: " + c.ready })
        }
    }

    /** Untuk notifikasi/widget/timer: sambung dulu bila perlu, lalu kirim tombol. */
    suspend fun sendKeyNow(code: Int): Boolean {
        if (!ensureConnected()) return false
        val r = client ?: return false
        return withContext(sender) {
            try {
                r.key(code)
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    // ------------------------------------------------------------------ pengaturan

    fun setDark(v: Boolean) {
        prefs.darkTheme = v
        _dark.value = v
    }

    fun setVolumeKeys(v: Boolean) {
        prefs.volumeKeys = v
        _volumeKeys.value = v
    }

    fun setNotifControls(v: Boolean) {
        prefs.notifControls = v
        _notifControls.value = v
        RemoteService.sync(app)
    }

    /** minutes = 0 mematikan timer. */
    fun setSleep(minutes: Int) {
        val at = if (minutes <= 0) 0L else System.currentTimeMillis() + minutes * 60_000L
        prefs.sleepAt = at
        _sleepAt.value = at
        RemoteService.sync(app)
    }

    /** Dipanggil service saat timer habis. */
    internal fun clearSleep() {
        prefs.sleepAt = 0L
        _sleepAt.value = 0L
    }

    fun addApp(name: String, input: String) {
        if (name.isBlank() || input.isBlank()) return
        prefs.customApps = prefs.customApps + prefs.newCustomApp(name, input)
        _apps.value = prefs.visibleApps()
    }

    fun removeApp(app: TvApp) {
        if (app.custom) prefs.customApps = prefs.customApps.filterNot { it.id == app.id }
        else prefs.hiddenApps = prefs.hiddenApps + app.id
        _apps.value = prefs.visibleApps()
    }

    fun restoreDefaultApps() {
        prefs.hiddenApps = emptySet()
        _apps.value = prefs.visibleApps()
    }

    fun toast(msg: String) {
        _toast.tryEmit(msg)
    }

    // ------------------------------------------------------------------ update

    private fun checkUpdate() {
        scope.launch {
            val latest = withContext(Dispatchers.IO) {
                try {
                    val c = URL("https://api.github.com/repos/baramarketID/remot-canggih/releases/latest")
                        .openConnection() as HttpURLConnection
                    c.connectTimeout = 6000
                    c.readTimeout = 6000
                    c.setRequestProperty("Accept", "application/vnd.github+json")
                    c.inputStream.bufferedReader().use { JSONObject(it.readText()).optString("tag_name") }
                } catch (e: Exception) {
                    null
                }
            } ?: return@launch
            val build = latest.substringAfterLast('.').toIntOrNull() ?: return@launch
            if (build > BuildConfig.VERSION_CODE) _update.value = latest.removePrefix("v")
        }
    }

    internal fun startServiceCompat(intent: Intent) {
        try {
            ContextCompat.startForegroundService(app, intent)
        } catch (_: Exception) {
        }
    }
}
