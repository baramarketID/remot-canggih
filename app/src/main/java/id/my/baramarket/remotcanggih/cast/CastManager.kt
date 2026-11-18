package id.my.baramarket.remotcanggih.cast

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.HlsSegmentFormat
import com.google.android.gms.cast.HlsVideoSegmentFormat
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.UUID

data class CastTarget(val id: String, val name: String, val selected: Boolean)

/**
 * Kirim foto/video & mirror layar ke TV lewat Chromecast bawaan (Android TV / Google TV).
 * HP menjadi server HTTP kecil; TV mengambil file / siaran HLS langsung dari HP.
 */
class CastManager private constructor(private val app: Context) {

    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: CastManager? = null

        fun get(context: Context): CastManager =
            instance ?: synchronized(this) {
                instance ?: CastManager(context.applicationContext).also { instance = it }
            }

        private val PORTS = intArrayOf(8789, 8790, 8791, 8792, 8793)
    }

    private val castContext: CastContext? = try {
        @Suppress("DEPRECATION")
        CastContext.getSharedInstance(app)
    } catch (e: Throwable) {
        null
    }

    /** False bila HP tidak punya Google Play Services (Cast tidak bisa dipakai). */
    val available: Boolean get() = castContext != null

    private val router: MediaRouter? = if (castContext != null) MediaRouter.getInstance(app) else null
    private val selector = MediaRouteSelector.Builder()
        .addControlCategory(CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID))
        .build()

    private val _targets = MutableStateFlow<List<CastTarget>>(emptyList())
    val targets: StateFlow<List<CastTarget>> = _targets

    private val _connectedTo = MutableStateFlow<String?>(null)
    val connectedTo: StateFlow<String?> = _connectedTo

    private val _mirroring = MutableStateFlow(false)
    val mirroring: StateFlow<Boolean> = _mirroring

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status

    val server = MediaServer()
    private var port = -1

    private val routerCallback = object : MediaRouter.Callback() {
        override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
        override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
        override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
        override fun onRouteSelected(router: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) = refresh()
        override fun onRouteUnselected(router: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) = refresh()
    }

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) = onConnected(session)
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) = onConnected(session)
        override fun onSessionEnded(session: CastSession, error: Int) = onDisconnected()
        override fun onSessionStartFailed(session: CastSession, error: Int) {
            _status.value = "Gagal tersambung ke TV (kode $error)."
            onDisconnected()
        }

        override fun onSessionResumeFailed(session: CastSession, error: Int) = onDisconnected()
        override fun onSessionSuspended(session: CastSession, reason: Int) {}
        override fun onSessionStarting(session: CastSession) {
            _status.value = "Menyambungkan…"
        }

        override fun onSessionEnding(session: CastSession) {}
        override fun onSessionResuming(session: CastSession, sessionId: String) {}
    }

    init {
        castContext?.sessionManager?.addSessionManagerListener(sessionListener, CastSession::class.java)
        castContext?.sessionManager?.currentCastSession?.let { onConnected(it) }
    }

    private fun onConnected(session: CastSession) {
        _connectedTo.value = session.castDevice?.friendlyName ?: "TV"
        _status.value = null
        refresh()
    }

    private fun onDisconnected() {
        _connectedTo.value = null
        if (_mirroring.value) MirrorService.stop(app)
        refresh()
    }

    // ------------------------------------------------------------------ perangkat

    fun startDiscovery() {
        router?.addCallback(selector, routerCallback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)
        refresh()
    }

    fun stopDiscovery() {
        router?.removeCallback(routerCallback)
    }

    private fun refresh() {
        val r = router ?: return
        _targets.value = r.routes
            .filter { !it.isDefault && it.isEnabled && it.matchesSelector(selector) }
            .map { CastTarget(it.id, it.name, it.isSelected) }
    }

    fun select(id: String) {
        val r = router ?: return
        r.routes.firstOrNull { it.id == id }?.select()
        _status.value = "Menyambungkan…"
    }

    fun disconnect() {
        if (_mirroring.value) MirrorService.stop(app)
        castContext?.sessionManager?.endCurrentSession(true)
        router?.unselect(MediaRouter.UNSELECT_REASON_STOPPED)
        _connectedTo.value = null
        refresh()
    }

    // ------------------------------------------------------------------ server

    /** Alamat HP di WiFi (atau hotspot) yang bisa dihubungi TV. */
    private fun localIp(): InetAddress? {
        val ifaces = try {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        } catch (e: Exception) {
            emptyList()
        }
        fun rank(name: String) = when {
            name.startsWith("wlan") -> 0
            name.startsWith("swlan") || name.startsWith("ap") -> 1
            name.startsWith("eth") -> 2
            else -> 3
        }
        return ifaces
            .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
            .sortedBy { rank(it.name) }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
    }

    @Synchronized
    fun baseUrl(): String? {
        val ip = localIp() ?: return null
        if (port < 0) port = server.start(null, *PORTS)
        return "http://${ip.hostAddress}:$port"
    }

    // ------------------------------------------------------------------ kirim file

    /** Mengirim foto/video dari galeri ke TV yang sedang tersambung. */
    suspend fun castFile(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val session = castContext?.sessionManager?.currentCastSession
        if (session == null) {
            _status.value = "Pilih TV dulu."
            return@withContext false
        }
        val base = try {
            baseUrl()
        } catch (e: IOException) {
            null
        }
        if (base == null) {
            _status.value = "HP tidak terhubung ke WiFi."
            return@withContext false
        }
        val cr = app.contentResolver
        val mime = cr.getType(uri) ?: "application/octet-stream"
        val name = queryName(uri) ?: "Media"
        val token = UUID.randomUUID().toString().replace("-", "")
        val isImage = mime.startsWith("image/")
        val src: MediaServer.Source
        val servedMime: String
        val ext: String
        if (isImage) {
            val jpeg = toJpeg(uri) ?: run {
                _status.value = "Foto tidak bisa dibaca."
                return@withContext false
            }
            servedMime = "image/jpeg"
            ext = "jpg"
            src = object : MediaServer.Source {
                override fun length() = jpeg.size.toLong()
                override fun mime() = servedMime
                override fun open(offset: Long): InputStream =
                    ByteArrayInputStream(jpeg, offset.toInt(), (jpeg.size - offset).toInt())
            }
        } else {
            servedMime = mime
            ext = mime.substringAfter('/').substringBefore(';').ifEmpty { "bin" }
            val size = querySize(uri)
            src = object : MediaServer.Source {
                override fun length() = size
                override fun mime() = servedMime
                override fun open(offset: Long): InputStream {
                    val pfd = cr.openFileDescriptor(uri, "r") ?: throw IOException("Tidak bisa membuka file")
                    val stream = ParcelFileDescriptor.AutoCloseInputStream(pfd)
                    if (offset > 0) stream.channel.position(offset)
                    return stream
                }
            }
        }
        server.putFile(token, src)
        val url = "$base/m/$token.$ext"
        withContext(Dispatchers.Main) {
            load(session, url, servedMime, name, live = false, photo = isImage)
        }
        _status.value = null
        true
    }

    private fun queryName(uri: Uri): String? = try {
        app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    } catch (e: Exception) {
        null
    }

    private fun querySize(uri: Uri): Long {
        try {
            app.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use {
                if (it.moveToFirst() && !it.isNull(0)) return it.getLong(0)
            }
        } catch (_: Exception) {
        }
        return try {
            app.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
        } catch (e: Exception) {
            -1L
        }
    }

    /** Foto diubah ke JPEG maks 1920 px (HEIC dll. tidak didukung Chromecast). */
    private fun toJpeg(uri: Uri): ByteArray? = try {
        val max = 1920
        val bmp: Bitmap? = if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(app.contentResolver, uri)) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                val scale = minOf(1f, max.toFloat() / maxOf(w, h))
                decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            app.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > max * 2) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            app.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        }
        bmp?.let {
            val out = ByteArrayOutputStream()
            it.compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.toByteArray()
        }
    } catch (e: Exception) {
        null
    }

    private fun load(session: CastSession, url: String, mime: String, title: String, live: Boolean, photo: Boolean) {
        val meta = MediaMetadata(if (photo) MediaMetadata.MEDIA_TYPE_PHOTO else MediaMetadata.MEDIA_TYPE_MOVIE)
        meta.putString(MediaMetadata.KEY_TITLE, title)
        val b = MediaInfo.Builder(url)
            .setContentType(mime)
            .setStreamType(if (live) MediaInfo.STREAM_TYPE_LIVE else MediaInfo.STREAM_TYPE_BUFFERED)
            .setMetadata(meta)
        if (live) {
            b.setHlsSegmentFormat(HlsSegmentFormat.TS)
            b.setHlsVideoSegmentFormat(HlsVideoSegmentFormat.MPEG2_TS)
        }
        val req = MediaLoadRequestData.Builder().setMediaInfo(b.build()).setAutoplay(true).build()
        session.remoteMediaClient?.load(req)
    }

    // ------------------------------------------------------------------ mirror

    internal fun setMirroring(v: Boolean) {
        _mirroring.value = v
    }

    internal fun setStatus(msg: String?) {
        _status.value = msg
    }

    /** Dipanggil MirrorService saat siaran siap: suruh TV memutar HLS dari HP. */
    internal fun playLive(live: HlsLive): Boolean {
        val session = castContext?.sessionManager?.currentCastSession ?: return false
        val base = baseUrl() ?: return false
        server.setLive(live)
        load(session, "$base/live/index.m3u8?t=${System.currentTimeMillis()}", "application/x-mpegurl",
            "Layar HP", live = true, photo = false)
        return true
    }

    internal fun stopLive() {
        server.setLive(null)
        try {
            castContext?.sessionManager?.currentCastSession?.remoteMediaClient?.stop()
        } catch (_: Exception) {
        }
    }

    fun isConnected(): Boolean = castContext?.sessionManager?.currentCastSession?.isConnected == true
}
