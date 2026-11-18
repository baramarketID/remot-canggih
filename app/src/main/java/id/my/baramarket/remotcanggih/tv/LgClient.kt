package id.my.baramarket.remotcanggih.tv

import id.my.baramarket.remotcanggih.atv.Keys
import id.my.baramarket.remotcanggih.core.TvApp
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Remot LG Smart TV (webOS) lewat WebSocket SSAP.
 * Pertama kali tersambung, TV menampilkan permintaan izin; setelah itu client-key disimpan.
 * Tombol arah memakai "pointer input socket" kedua dari TV.
 */
class LgClient(
    private val host: String,
    private val savedKey: String?,
    private val onClosed: (Exception?) -> Unit,
) : TvClient {

    class DeniedException : IOException("Ditolak di TV")

    private val ids = AtomicInteger(1)
    private val pending = ConcurrentHashMap<String, (JSONObject) -> Unit>()

    @Volatile
    private var ws: WebSocket? = null
    @Volatile
    private var pointer: WebSocket? = null
    @Volatile
    private var open = false
    @Volatile
    private var closedByUs = false
    @Volatile
    private var muted = false
    @Volatile
    private var playing = true

    override val ready: Boolean get() = open

    fun connect(onPrompt: () -> Unit): String? =
        try {
            connectTo("wss://$host:3001", onPrompt)
        } catch (e: DeniedException) {
            throw e
        } catch (e: Exception) {
            connectTo("ws://$host:3000", onPrompt)
        }

    private fun connectTo(url: String, onPrompt: () -> Unit): String? {
        val done = CountDownLatch(1)
        var key: String? = null
        var error: Exception? = null
        var registered = false

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(registerMessage().toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val msg = try {
                    JSONObject(text)
                } catch (e: Exception) {
                    return
                }
                val id = msg.optString("id")
                when (msg.optString("type")) {
                    "registered" -> {
                        key = msg.optJSONObject("payload")?.optString("client-key")?.takeIf { it.isNotEmpty() } ?: savedKey
                        registered = true
                        open = true
                        done.countDown()
                    }
                    "response" -> {
                        if (id == "register_0" && msg.optJSONObject("payload")?.optString("pairingType") == "PROMPT") {
                            onPrompt()
                        }
                        pending.remove(id)?.invoke(msg.optJSONObject("payload") ?: JSONObject())
                    }
                    "error" -> {
                        if (id == "register_0") {
                            error = DeniedException()
                            done.countDown()
                        } else {
                            pending.remove(id)
                        }
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val wasOpen = open
                open = false
                if (!registered) {
                    error = IOException(t.message ?: "Gagal tersambung", t)
                    done.countDown()
                } else if (wasOpen && !closedByUs) {
                    onClosed(IOException(t.message, t))
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                val wasOpen = open
                open = false
                if (!registered) {
                    error = IOException("Ditutup TV ($code)")
                    done.countDown()
                } else if (wasOpen && !closedByUs) {
                    onClosed(null)
                }
            }
        }
        val socket = Http.client.newWebSocket(Request.Builder().url(url).build(), listener)
        if (!done.await(45, TimeUnit.SECONDS)) {
            socket.cancel()
            throw IOException("Tidak ada persetujuan dari TV")
        }
        error?.let {
            socket.cancel()
            throw it
        }
        ws = socket
        openPointer()
        return key
    }

    private fun registerMessage(): JSONObject {
        val permissions = JSONArray(
            listOf(
                "LAUNCH", "LAUNCH_WEBAPP", "APP_TO_APP", "CONTROL_AUDIO", "CONTROL_DISPLAY",
                "CONTROL_INPUT_JOYSTICK", "CONTROL_INPUT_MEDIA_PLAYBACK", "CONTROL_INPUT_TV", "CONTROL_POWER",
                "CONTROL_INPUT_TEXT", "CONTROL_MOUSE_AND_KEYBOARD", "READ_APP_STATUS", "READ_CURRENT_CHANNEL",
                "READ_INPUT_DEVICE_LIST", "READ_INSTALLED_APPS", "READ_TV_CHANNEL_LIST", "WRITE_NOTIFICATION_TOAST",
                "READ_RUNNING_APPS", "READ_NETWORK_STATE", "READ_POWER_STATE", "CONTROL_TV_SCREEN",
            )
        )
        val manifest = JSONObject()
            .put("manifestVersion", 1)
            .put("appVersion", "1.0")
            .put("permissions", permissions)
            .put("signed", JSONObject()
                .put("appId", "id.my.baramarket.remotcanggih")
                .put("created", "20261118")
                .put("vendorId", "baramarket")
                .put("localizedAppNames", JSONObject().put("", "Remot Canggih"))
                .put("localizedVendorNames", JSONObject().put("", "BaraMarket"))
                .put("permissions", permissions)
                .put("serial", "remotcanggih"))
        val payload = JSONObject()
            .put("forcePairing", false)
            .put("pairingType", "PROMPT")
            .put("manifest", manifest)
        if (!savedKey.isNullOrEmpty()) payload.put("client-key", savedKey)
        return JSONObject().put("type", "register").put("id", "register_0").put("payload", payload)
    }

    private fun request(uri: String, payload: JSONObject? = null, onResponse: ((JSONObject) -> Unit)? = null) {
        val s = ws ?: throw IOException("Tidak terhubung")
        val id = "req_" + ids.getAndIncrement()
        if (onResponse != null) pending[id] = onResponse
        val msg = JSONObject().put("type", "request").put("id", id).put("uri", uri)
        if (payload != null) msg.put("payload", payload)
        if (!s.send(msg.toString())) throw IOException("Gagal mengirim")
    }

    /** Membuka socket kedua untuk tombol arah/OK/Back/Home. */
    private fun openPointer() {
        val latch = CountDownLatch(1)
        request("ssap://com.webos.service.networkinput/getPointerInputSocket") { payload ->
            val path = payload.optString("socketPath")
            if (path.isNotEmpty()) {
                pointer = Http.client.newWebSocket(Request.Builder().url(path).build(), object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        latch.countDown()
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        pointer = null
                        latch.countDown()
                    }
                })
            } else {
                latch.countDown()
            }
        }
        latch.await(4, TimeUnit.SECONDS)
    }

    private fun button(name: String) {
        var p = pointer
        if (p == null) {
            openPointer()
            p = pointer ?: throw IOException("Tombol arah tidak tersedia")
        }
        if (!p.send("type:button\nname:$name\n\n")) throw IOException("Gagal mengirim")
    }

    override fun key(code: Int) {
        when (code) {
            Keys.UP -> button("UP")
            Keys.DOWN -> button("DOWN")
            Keys.LEFT -> button("LEFT")
            Keys.RIGHT -> button("RIGHT")
            Keys.OK -> button("ENTER")
            Keys.BACK -> button("BACK")
            Keys.HOME -> button("HOME")
            Keys.MENU -> button("MENU")
            Keys.INFO -> button("INFO")
            in Keys.NUM_0..(Keys.NUM_0 + 9) -> button((code - Keys.NUM_0).toString())
            Keys.ENTER -> request("ssap://com.webos.service.ime/sendEnterKey")
            Keys.DEL -> request("ssap://com.webos.service.ime/deleteCharacters", JSONObject().put("count", 1))
            Keys.VOL_UP -> request("ssap://audio/volumeUp")
            Keys.VOL_DOWN -> request("ssap://audio/volumeDown")
            Keys.MUTE -> {
                muted = !muted
                request("ssap://audio/setMute", JSONObject().put("mute", muted))
            }
            Keys.POWER -> request("ssap://system/turnOff")
            Keys.CH_UP -> request("ssap://tv/channelUp")
            Keys.CH_DOWN -> request("ssap://tv/channelDown")
            Keys.PLAY_PAUSE -> {
                playing = !playing
                request(if (playing) "ssap://media.controls/play" else "ssap://media.controls/pause")
            }
            Keys.REWIND, Keys.PREV -> request("ssap://media.controls/rewind")
            Keys.FORWARD, Keys.NEXT -> request("ssap://media.controls/fastForward")
            Keys.INPUT -> request("ssap://system.launcher/launch", JSONObject().put("id", "com.webos.app.inputpicker"))
        }
    }

    override fun text(old: String, new: String) {
        var p = 0
        while (p < old.length && p < new.length && old[p] == new[p]) p++
        val del = old.length - p
        if (del > 0) request("ssap://com.webos.service.ime/deleteCharacters", JSONObject().put("count", del))
        val add = new.substring(p)
        if (add.isNotEmpty()) {
            request("ssap://com.webos.service.ime/insertText", JSONObject().put("text", add).put("replace", 0))
        }
    }

    override fun launch(app: TvApp): Boolean {
        val id = app.lgId ?: return false
        request("ssap://system.launcher/launch", JSONObject().put("id", id))
        return true
    }

    override fun close() {
        closedByUs = true
        open = false
        pointer?.close(1000, null)
        ws?.close(1000, null)
        pointer = null
        ws = null
    }
}
