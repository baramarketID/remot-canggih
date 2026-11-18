package id.my.baramarket.remotcanggih

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.view.KeyEvent
import android.widget.Toast
import android.media.projection.MediaProjectionManager
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import id.my.baramarket.remotcanggih.atv.Keys
import id.my.baramarket.remotcanggih.cast.CastManager
import id.my.baramarket.remotcanggih.cast.MirrorService
import id.my.baramarket.remotcanggih.core.RemoteController
import id.my.baramarket.remotcanggih.core.RemoteWidget
import id.my.baramarket.remotcanggih.ui.AppActions
import id.my.baramarket.remotcanggih.ui.RemotTheme
import id.my.baramarket.remotcanggih.ui.RemoteApp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val rc by lazy { RemoteController.get(this) }

    private val speech = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val text = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!text.isNullOrBlank()) {
                rc.sendSpoken(text)
                Toast.makeText(this, "Dikirim: $text", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) rc.setNotifControls(true)
        else Toast.makeText(this, "Izinkan notifikasi supaya tombol remot bisa tampil", Toast.LENGTH_LONG).show()
    }

    private val projection = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val data = res.data
        if (res.resultCode == Activity.RESULT_OK && data != null) MirrorService.start(this, res.resultCode, data)
        else Toast.makeText(this, "Mirror dibatalkan", Toast.LENGTH_SHORT).show()
    }

    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) lifecycleScope.launch {
            val ok = CastManager.get(this@MainActivity).castFile(uri)
            Toast.makeText(this@MainActivity, if (ok) "Dikirim ke TV" else "Gagal mengirim ke TV", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        rc.start()
        val actions = AppActions(
            onMic = ::startSpeech,
            onOpenCast = ::openSystemCast,
            onOpenUrl = ::openUrl,
            onEnableNotifControls = ::enableNotifControls,
            onPinWidget = ::pinWidget,
            onStartMirror = ::startMirror,
            onPickMedia = {
                pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
            },
        )
        setContent {
            val dark by rc.dark.collectAsState()
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            RemotTheme(dark) {
                RemoteApp(rc, actions)
            }
        }
        lifecycleScope.launch {
            rc.toast.collect { Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show() }
        }
    }

    override fun onResume() {
        super.onResume()
        rc.reconnectIfNeeded()
    }

    /** Tombol volume HP mengatur volume TV (bisa dimatikan di Setelan). */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if ((code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN) &&
            rc.volumeKeys.value && rc.isConnected
        ) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                rc.key(if (code == KeyEvent.KEYCODE_VOLUME_UP) Keys.VOL_UP else Keys.VOL_DOWN)
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun startSpeech() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Ucapkan yang ingin diketik ke TV")
        try {
            speech.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Fitur suara tidak tersedia di HP ini", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startMirror() {
        val mpm = getSystemService(MediaProjectionManager::class.java)
        if (mpm == null) {
            Toast.makeText(this, "Mirror tidak didukung di HP ini", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            projection.launch(mpm.createScreenCaptureIntent())
        } catch (e: Exception) {
            Toast.makeText(this, "Mirror tidak didukung di HP ini", Toast.LENGTH_SHORT).show()
        }
    }

    private fun enableNotifControls() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            rc.setNotifControls(true)
        }
    }

    private fun pinWidget() {
        val mgr = getSystemService(AppWidgetManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && mgr != null && mgr.isRequestPinAppWidgetSupported) {
            mgr.requestPinAppWidget(ComponentName(this, RemoteWidget::class.java), null, null)
        } else {
            Toast.makeText(this, "Tahan layar utama → Widget → Remot Canggih", Toast.LENGTH_LONG).show()
        }
    }

    /** Membuka fitur cast / screen mirroring bawaan HP (Smart View, Cast, dll). */
    private fun openSystemCast() {
        val candidates = listOf(
            Intent(Settings.ACTION_CAST_SETTINGS),
            Intent("android.settings.WIFI_DISPLAY_SETTINGS"),
            Intent().setClassName("com.samsung.android.smartmirroring",
                "com.samsung.android.smartmirroring.CaptureActivity"),
            Intent(Settings.ACTION_WIRELESS_SETTINGS),
        )
        for (i in candidates) {
            try {
                startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: Exception) {
            }
        }
        Toast.makeText(this, "Buka menu Cast / Screen Mirroring dari panel notifikasi HP", Toast.LENGTH_LONG).show()
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            Toast.makeText(this, url, Toast.LENGTH_LONG).show()
        }
    }
}
