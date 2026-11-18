package id.my.baramarket.remotcanggih.cast

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Surface
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import id.my.baramarket.remotcanggih.MainActivity
import id.my.baramarket.remotcanggih.R
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Merekam layar HP (seluruh layar atau satu aplikasi, sesuai pilihan di dialog Android),
 * mengubahnya jadi H.264, lalu menyiarkannya sebagai HLS ke TV lewat Chromecast.
 */
class MirrorService : Service() {

    companion object {
        private const val CHANNEL = "mirror"
        private const val NOTIF_ID = 9
        private const val EXTRA_CODE = "code"
        private const val EXTRA_DATA = "data"
        private const val ACTION_STOP = "id.my.baramarket.remotcanggih.MIRROR_STOP"

        fun start(context: Context, resultCode: Int, data: Intent) {
            val i = Intent(context, MirrorService::class.java)
                .putExtra(EXTRA_CODE, resultCode)
                .putExtra(EXTRA_DATA, data)
            ContextCompat.startForegroundService(context, i)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MirrorService::class.java))
        }
    }

    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var encoder: MediaCodec? = null
    private var surface: Surface? = null
    private var drainThread: Thread? = null
    @Volatile
    private var running = false
    private val main = Handler(Looper.getMainLooper())
    private lateinit var cast: CastManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        cast = CastManager.get(this)
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL, "Mirror layar", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION else 0
        try {
            ServiceCompat.startForeground(this, NOTIF_ID, notification(), type)
        } catch (e: Exception) {
            cast.setStatus("Mirror tidak bisa dimulai di HP ini.")
            stopSelf()
            return START_NOT_STICKY
        }
        if (running) return START_NOT_STICKY

        val code = intent?.getIntExtra(EXTRA_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        @Suppress("DEPRECATION")
        val data: Intent? = intent?.getParcelableExtra(EXTRA_DATA)
        if (code != Activity.RESULT_OK || data == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            begin(code, data)
        } catch (e: Exception) {
            cast.setStatus("Mirror gagal dimulai: ${e.message ?: "kesalahan encoder"}")
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun begin(code: Int, data: Intent) {
        val mpm = getSystemService(MediaProjectionManager::class.java)
        val proj = mpm.getMediaProjection(code, data) ?: throw IllegalStateException("izin ditolak")
        projection = proj
        proj.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                main.post { stopSelf() }
            }
        }, main)

        // Ukuran video: sisi terpanjang 1280 px, kelipatan 16.
        val dm = resources.displayMetrics
        val scale = minOf(1f, 1280f / max(dm.widthPixels, dm.heightPixels))
        val w = ((dm.widthPixels * scale) / 16f).roundToInt() * 16
        val h = ((dm.heightPixels * scale) / 16f).roundToInt() * 16

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, 4_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            // Ulangi frame terakhir saat layar diam, supaya siaran tetap mengalir.
            setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 100_000L)
        }
        val enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        enc.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val input = enc.createInputSurface()
        enc.start()
        encoder = enc
        surface = input

        display = proj.createVirtualDisplay(
            "RemotCanggih", w, h, dm.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, input, null, null,
        )

        val live = HlsLive(1_000_000L)
        running = true
        cast.setMirroring(true)
        cast.setStatus("Menyiapkan siaran…")
        drainThread = Thread({ drain(enc, live) }, "mirror-encoder").also { it.start() }
        Thread({
            try {
                if (live.awaitSegments(2, 15_000) && running) {
                    main.post {
                        if (cast.playLive(live)) cast.setStatus(null)
                        else cast.setStatus("TV belum tersambung. Pilih TV dulu.")
                    }
                } else if (running) {
                    main.post { cast.setStatus("Encoder layar tidak mengirim gambar.") }
                }
            } catch (_: InterruptedException) {
            }
        }, "mirror-start").start()
    }

    private fun drain(enc: MediaCodec, live: HlsLive) {
        val info = MediaCodec.BufferInfo()
        var firstPts = -1L
        while (running) {
            val idx = try {
                enc.dequeueOutputBuffer(info, 20_000)
            } catch (e: Exception) {
                break
            }
            if (idx < 0) continue
            try {
                val buf = enc.getOutputBuffer(idx)
                if (buf != null && info.size > 0) {
                    buf.position(info.offset)
                    buf.limit(info.offset + info.size)
                    val bytes = ByteArray(info.size)
                    buf.get(bytes)
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        live.setCodecConfig(bytes)
                    } else {
                        if (firstPts < 0) firstPts = info.presentationTimeUs
                        val key = info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0
                        live.addFrame(bytes, info.presentationTimeUs - firstPts, key)
                    }
                }
                enc.releaseOutputBuffer(idx, false)
            } catch (e: Exception) {
                break
            }
        }
    }

    override fun onDestroy() {
        running = false
        try {
            drainThread?.join(500)
        } catch (_: InterruptedException) {
        }
        try {
            display?.release()
        } catch (_: Exception) {
        }
        try {
            encoder?.stop()
        } catch (_: Exception) {
        }
        try {
            encoder?.release()
        } catch (_: Exception) {
        }
        surface?.release()
        try {
            projection?.stop()
        } catch (_: Exception) {
        }
        cast.stopLive()
        cast.setMirroring(false)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 2, Intent(this, MirrorService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Mirror layar ke TV")
            .setContentText("Layar HP sedang ditampilkan di TV")
            .setContentIntent(open)
            .addAction(0, "Hentikan", stop)
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}
