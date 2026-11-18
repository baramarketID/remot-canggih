package id.my.baramarket.remotcanggih.ui

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import id.my.baramarket.remotcanggih.BuildConfig
import id.my.baramarket.remotcanggih.atv.AtvCert
import id.my.baramarket.remotcanggih.atv.AtvPairing
import id.my.baramarket.remotcanggih.atv.AtvRemote
import id.my.baramarket.remotcanggih.atv.Keys
import id.my.baramarket.remotcanggih.data.DeviceStore
import id.my.baramarket.remotcanggih.data.TvDevice
import id.my.baramarket.remotcanggih.data.TvDiscovery
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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
}

class RemoteViewModel(app: Application) : AndroidViewModel(app) {
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
    /** Nama versi baru bila ada update. */
    val update: StateFlow<String?> = _update

    private val _toast = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val toast: SharedFlow<String> = _toast

    private var remote: AtvRemote? = null
    private var pairing: AtvPairing? = null
    private var job: Job? = null

    init {
        store.last()?.let { connect(it) }
        checkUpdate()
    }

    val currentDevice: TvDevice?
        get() = when (val c = _conn.value) {
            is Conn.Connecting -> c.device
            is Conn.Connected -> c.device
            is Conn.Pairing -> c.device
            is Conn.Failed -> c.device
            Conn.Idle -> null
        }

    // ------------------------------------------------------------------ koneksi

    fun connect(device: TvDevice) {
        job?.cancel()
        closeAll()
        _conn.value = Conn.Connecting(device)
        job = viewModelScope.launch(Dispatchers.IO) {
            try {
                openRemote(device)
            } catch (e: AtvRemote.NotPairedException) {
                startPairing(device)
            } catch (e: Exception) {
                _conn.value = Conn.Failed(device, "Tidak bisa terhubung ke TV. Pastikan TV menyala dan satu WiFi dengan HP.")
            }
        }
    }

    /** Dipanggil saat aplikasi dibuka lagi: sambung ulang bila koneksi terputus. */
    fun onResume() {
        val c = _conn.value
        if (c is Conn.Failed) connect(c.device)
        else if (c is Conn.Connected && remote?.isReady != true) connect(c.device)
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
                    if (_conn.value == Conn.Connected(device)) {
                        _conn.value = Conn.Failed(device, "Koneksi ke TV terputus.")
                    }
                }
            })
        remote = r
        r.connect()
        try {
            withTimeout(8000) { ready.await() }
        } catch (e: Exception) {
            r.close()
            throw AtvRemote.NotPairedException(e)
        }
        store.save(device)
        _saved.value = store.saved()
        _conn.value = Conn.Connected(device)
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
        job = viewModelScope.launch(Dispatchers.IO) {
            try {
                p.finish(code)
                p.close()
                pairing = null
                _conn.value = Conn.Connecting(c.device)
                openRemote(c.device)
            } catch (e: AtvPairing.WrongCodeException) {
                // TV membuat kode baru setiap percobaan
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

    fun forget(device: TvDevice) {
        store.remove(device)
        _saved.value = store.saved()
        if (currentDevice?.host == device.host) {
            closeAll()
            _conn.value = Conn.Idle
        }
    }

    /** Menghapus semua TV & sertifikat; semua TV harus di-pairing ulang. */
    fun resetAll() {
        closeAll()
        store.clear()
        AtvCert.delete(certFile)
        _saved.value = emptyList()
        _conn.value = Conn.Idle
    }

    private fun closeAll() {
        remote?.close()
        remote = null
        pairing?.close()
        pairing = null
    }

    // ------------------------------------------------------------------ perintah

    private fun withRemote(block: (AtvRemote) -> Unit) {
        val r = remote
        if (r == null || !r.isReady || _conn.value !is Conn.Connected) {
            _toast.tryEmit("Belum terhubung ke TV")
            return
        }
        viewModelScope.launch(sender) {
            try {
                block(r)
            } catch (e: Exception) {
                _toast.tryEmit("Gagal mengirim perintah")
            }
        }
    }

    fun key(code: Int) = withRemote { it.sendKey(code) }

    fun openApp(link: String) = withRemote { it.launchApp(link) }

    /** Menyamakan isi kolom teks di TV dengan teks di HP. */
    fun typeText(old: String, new: String) = withRemote { r ->
        if (r.canTypeDirectly()) {
            r.sendText(new)
        } else {
            var p = 0
            while (p < old.length && p < new.length && old[p] == new[p]) p++
            repeat(old.length - p) { r.sendKey(Keys.DEL) }
            new.substring(p).forEach { ch -> Keys.forChar(ch)?.let { r.sendKey(it) } }
        }
    }

    /** Hasil suara → diketik ke TV. */
    fun sendSpoken(text: String) = typeText("", text)

    // ------------------------------------------------------------------ update

    private fun checkUpdate() {
        viewModelScope.launch {
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

    override fun onCleared() {
        closeAll()
        discovery.stop()
        sender.close()
    }
}
