package id.my.baramarket.remotcanggih

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import id.my.baramarket.remotcanggih.ui.RemoteApp
import id.my.baramarket.remotcanggih.ui.RemoteViewModel
import id.my.baramarket.remotcanggih.ui.RemotTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: RemoteViewModel by viewModels()

    private val speech = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val text = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!text.isNullOrBlank()) vm.sendSpoken(text)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            RemotTheme {
                RemoteApp(
                    vm = vm,
                    onMic = ::startSpeech,
                    onOpenCast = ::openSystemCast,
                    onOpenUrl = ::openUrl,
                )
            }
        }
        lifecycleScope.launch {
            vm.toast.collect { Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show() }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
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
