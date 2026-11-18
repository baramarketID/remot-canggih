package id.my.baramarket.remotcanggih.tv

import android.util.Base64
import id.my.baramarket.remotcanggih.atv.Keys
import id.my.baramarket.remotcanggih.core.TvApp
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Remot Samsung Smart TV (Tizen, 2016 ke atas) lewat WebSocket.
 * Pertama kali tersambung, TV menampilkan "Izinkan"; setelah itu token disimpan.
 */
class SamsungClient(
    private val host: String,
    private val savedToken: String?,
    private val onClosed: (Exception?) -> Unit,
) : TvClient {

    class DeniedException : IOException("Ditolak di TV")

    @Volatile
    private var ws: WebSocket? = null
    @Volatile
    private var open = false
    @Volatile
    private var closedByUs = false

    override val ready: Boolean get() = open

    /**
     * Menyambung (blocking). [onPrompt] dipanggil bila TV sedang meminta persetujuan.
     * Mengembalikan token baru (boleh null untuk TV lama tanpa token).
     */
    fun connect(onPrompt: () -> Unit): String? {
        return try {
            connectTo(secure = true, onPrompt = onPrompt)
        } catch (e: DeniedException) {
            throw e
        } catch (e: Exception) {
            connectTo(secure = false, onPrompt = onPrompt) // TV lama: ws://:8001 tanpa token
        }
    }

    private fun connectTo(secure: Boolean, onPrompt: () -> Unit): String? {
        val name = Base64.encodeToString("Remot Canggih".toByteArray(), Base64.NO_WRAP)
        val url = buildString {
            append(if (secure) "wss://$host:8002" else "ws://$host:8001")
            append("/api/v2/channels/samsung.remote.control?name=").append(name)
            if (secure && !savedToken.isNullOrEmpty()) append("&token=").append(savedToken)
        }
        val done = CountDownLatch(1)
        var token: String? = null
        var error: Exception? = null
        var connected = false

        val listener = object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val msg = try {
                    JSONObject(text)
                } catch (e: Exception) {
                    return
                }
                when (msg.optString("event")) {
                    "ms.channel.connect" -> {
                        token = msg.optJSONObject("data")?.optString("token")?.takeIf { it.isNotEmpty() } ?: savedToken
                        connected = true
                        open = true
                        done.countDown()
                    }
                    "ms.channel.unauthorized" -> {
                        error = DeniedException()
                        done.countDown()
                    }
                    "ms.channel.timeOut" -> {
                        error = IOException("Waktu persetujuan habis")
                        done.countDown()
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val wasOpen = open
                open = false
                if (!connected) {
                    error = IOException(t.message ?: "Gagal tersambung", t)
                    done.countDown()
                } else if (wasOpen && !closedByUs) {
                    onClosed(IOException(t.message, t))
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                val wasOpen = open
                open = false
                if (!connected) {
                    error = IOException("Ditutup TV ($code)")
                    done.countDown()
                } else if (wasOpen && !closedByUs) {
                    onClosed(null)
                }
            }
        }
        val socket = Http.client.newWebSocket(Request.Builder().url(url).build(), listener)
        // Bila dalam 2 detik belum tersambung, kemungkinan besar TV sedang menampilkan "Izinkan".
        if (!done.await(2, TimeUnit.SECONDS)) {
            onPrompt()
            if (!done.await(40, TimeUnit.SECONDS)) {
                socket.cancel()
                throw IOException("Tidak ada persetujuan dari TV")
            }
        }
        error?.let {
            socket.cancel()
            throw it
        }
        ws = socket
        return token
    }

    private fun send(json: JSONObject) {
        val s = ws ?: throw IOException("Tidak terhubung")
        if (!s.send(json.toString())) throw IOException("Gagal mengirim")
    }

    private fun sendKey(name: String) {
        val params = JSONObject()
            .put("Cmd", "Click")
            .put("DataOfCmd", name)
            .put("Option", "false")
            .put("TypeOfRemote", "SendRemoteKey")
        send(JSONObject().put("method", "ms.remote.control").put("params", params))
    }

    override fun key(code: Int) {
        val name = when (code) {
            Keys.UP -> "KEY_UP"
            Keys.DOWN -> "KEY_DOWN"
            Keys.LEFT -> "KEY_LEFT"
            Keys.RIGHT -> "KEY_RIGHT"
            Keys.OK, Keys.ENTER -> "KEY_ENTER"
            Keys.BACK -> "KEY_RETURN"
            Keys.HOME -> "KEY_HOME"
            Keys.MENU -> "KEY_MENU"
            Keys.VOL_UP -> "KEY_VOLUP"
            Keys.VOL_DOWN -> "KEY_VOLDOWN"
            Keys.MUTE -> "KEY_MUTE"
            Keys.POWER -> "KEY_POWER"
            Keys.CH_UP -> "KEY_CHUP"
            Keys.CH_DOWN -> "KEY_CHDOWN"
            Keys.INPUT -> "KEY_SOURCE"
            Keys.PLAY_PAUSE -> "KEY_PLAY"
            Keys.REWIND, Keys.PREV -> "KEY_REWIND"
            Keys.FORWARD, Keys.NEXT -> "KEY_FF"
            Keys.INFO -> "KEY_INFO"
            Keys.GUIDE -> "KEY_GUIDE"
            in Keys.NUM_0..(Keys.NUM_0 + 9) -> "KEY_${code - Keys.NUM_0}"
            else -> return
        }
        sendKey(name)
    }

    override fun text(old: String, new: String) {
        if (new.isEmpty()) return
        val b64 = Base64.encodeToString(new.toByteArray(), Base64.NO_WRAP)
        val params = JSONObject()
            .put("Cmd", b64)
            .put("DataOfCmd", "base64")
            .put("TypeOfRemote", "SendInputString")
        send(JSONObject().put("method", "ms.remote.control").put("params", params))
    }

    override fun launch(app: TvApp): Boolean {
        val id = app.samsungId ?: return false
        val data = JSONObject().put("appId", id).put("action_type", "DEEP_LINK")
        val params = JSONObject().put("event", "ed.apps.launch").put("to", "host").put("data", data)
        send(JSONObject().put("method", "ms.channel.emit").put("params", params))
        return true
    }

    override fun close() {
        closedByUs = true
        open = false
        ws?.close(1000, null)
        ws = null
    }
}
