package id.my.baramarket.remotcanggih.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import id.my.baramarket.remotcanggih.MainActivity
import id.my.baramarket.remotcanggih.R
import id.my.baramarket.remotcanggih.atv.Keys
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Layanan latar depan untuk: tombol remot di notifikasi, tombol widget, dan timer tidur.
 * Berjalan hanya selama salah satu fitur itu aktif.
 */
class RemoteService : Service() {

    companion object {
        const val ACTION_SYNC = "id.my.baramarket.remotcanggih.SYNC"
        const val ACTION_KEY = "id.my.baramarket.remotcanggih.KEY"
        const val EXTRA_KEY = "key"
        private const val CHANNEL = "remot"
        private const val NOTIF_ID = 7

        /** Menyalakan / mematikan layanan sesuai pengaturan. */
        fun sync(context: Context) {
            val c = RemoteController.get(context)
            val needed = c.prefs.notifControls || c.prefs.sleepAt > System.currentTimeMillis()
            val intent = Intent(context, RemoteService::class.java).setAction(ACTION_SYNC)
            if (needed) c.startServiceCompat(intent)
            else context.stopService(Intent(context, RemoteService::class.java))
        }

        fun keyIntent(context: Context, code: Int): PendingIntent {
            val i = Intent(context, RemoteService::class.java).setAction(ACTION_KEY).putExtra(EXTRA_KEY, code)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            return if (Build.VERSION.SDK_INT >= 26) PendingIntent.getForegroundService(context, code, i, flags)
            else PendingIntent.getService(context, code, i, flags)
        }
    }

    private lateinit var controller: RemoteController
    private var sleepJob: Job? = null
    private var idleJob: Job? = null
    private var watchJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        controller = RemoteController.get(this)
        createChannel()
        goForeground()
        watchJob = controller.scope.launch {
            combine(controller.conn, controller.sleepAt) { _, _ -> Unit }.collect { updateNotification() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground()
        when (intent?.action) {
            ACTION_KEY -> {
                val code = intent.getIntExtra(EXTRA_KEY, -1)
                if (code >= 0) controller.scope.launch {
                    val ok = controller.sendKeyNow(code)
                    if (!ok) controller.toast("TV belum tersambung. Buka Remot Canggih untuk menyambungkan.")
                    if (code == Keys.POWER) updateNotification()
                }
            }
        }
        scheduleSleep()
        scheduleIdleStop()
        return START_STICKY
    }

    private fun scheduleSleep() {
        sleepJob?.cancel()
        val at = controller.prefs.sleepAt
        if (at <= 0L) return
        sleepJob = controller.scope.launch {
            val wait = at - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            controller.sendKeyNow(Keys.POWER)
            controller.clearSleep()
            updateNotification()
            scheduleIdleStop()
        }
    }

    /** Bila notifikasi kontrol tidak diaktifkan & tidak ada timer, berhenti setelah 1 menit. */
    private fun scheduleIdleStop() {
        idleJob?.cancel()
        if (controller.prefs.notifControls || controller.prefs.sleepAt > System.currentTimeMillis()) return
        idleJob = controller.scope.launch {
            delay(60_000)
            ServiceCompat.stopForeground(this@RemoteService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        sleepJob?.cancel()
        idleJob?.cancel()
        watchJob?.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ notifikasi

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL, "Kontrol remot", NotificationManager.IMPORTANCE_LOW)
            ch.description = "Tombol remot TV di notifikasi dan timer tidur"
            ch.setShowBadge(false)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun goForeground() {
        val n = buildNotification()
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        try {
            ServiceCompat.startForeground(this, NOTIF_ID, n, type)
        } catch (e: Exception) {
            stopSelf()
        }
    }

    private fun updateNotification() {
        try {
            getSystemService(NotificationManager::class.java)?.notify(NOTIF_ID, buildNotification())
        } catch (_: Exception) {
        }
    }

    private fun buildNotification(): Notification {
        val tv = controller.currentDevice?.name ?: "Belum ada TV"
        val status = when {
            controller.isConnected -> "Terhubung"
            else -> "Ketuk tombol untuk menyambungkan"
        }
        val sleepAt = controller.prefs.sleepAt
        val sleepText = if (sleepAt > System.currentTimeMillis()) {
            val min = ((sleepAt - System.currentTimeMillis()) / 60_000L).coerceAtLeast(1)
            " · TV mati dalam $min mnt"
        } else ""

        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val b = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(tv)
            .setContentText(status + sleepText)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (controller.prefs.notifControls) {
            val small = RemoteViews(packageName, R.layout.notif_controls)
            small.setTextViewText(R.id.n_title, tv)
            small.setTextViewText(R.id.n_sub, status + sleepText)
            bind(small)
            b.setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(small)
                .setCustomBigContentView(small)
        }
        return b.build()
    }

    private fun bind(v: RemoteViews) {
        v.setOnClickPendingIntent(R.id.n_power, keyIntent(this, Keys.POWER))
        v.setOnClickPendingIntent(R.id.n_voldown, keyIntent(this, Keys.VOL_DOWN))
        v.setOnClickPendingIntent(R.id.n_mute, keyIntent(this, Keys.MUTE))
        v.setOnClickPendingIntent(R.id.n_play, keyIntent(this, Keys.PLAY_PAUSE))
        v.setOnClickPendingIntent(R.id.n_volup, keyIntent(this, Keys.VOL_UP))
    }
}
