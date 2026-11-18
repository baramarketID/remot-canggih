package id.my.baramarket.remotcanggih.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.net.DatagramPacket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.SocketTimeoutException
import java.net.URL
import java.util.ArrayDeque

/**
 * Mencari TV di WiFi yang sama:
 * - Android TV / Google TV lewat mDNS ("_androidtvremote2._tcp")
 * - Samsung & LG lewat SSDP
 */
class TvDiscovery(context: Context) {
    private val app = context.applicationContext
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val main = Handler(Looper.getMainLooper())
    private val _devices = MutableStateFlow<List<TvDevice>>(emptyList())
    val devices: StateFlow<List<TvDevice>> = _devices

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching

    private var listener: NsdManager.DiscoveryListener? = null
    private val queue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false
    @Volatile
    private var ssdpRunning = false

    fun start() {
        if (listener != null) return
        _devices.value = emptyList()
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                main.post { _searching.value = true }
            }

            override fun onDiscoveryStopped(serviceType: String) {}

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                main.post { listener = null }
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}

            override fun onServiceFound(info: NsdServiceInfo) {
                main.post {
                    queue.add(info)
                    resolveNext()
                }
            }

            override fun onServiceLost(info: NsdServiceInfo) {
                main.post {
                    _devices.value = _devices.value.filterNot { it.kind == "atv" && it.name == info.serviceName }
                }
            }
        }
        listener = l
        _searching.value = true
        try {
            nsd.discoverServices(SERVICE, NsdManager.PROTOCOL_DNS_SD, l)
        } catch (e: Exception) {
            listener = null
        }
        startSsdp()
        main.removeCallbacks(autoStop)
        main.postDelayed(autoStop, 20_000)
    }

    private val autoStop = Runnable { stop() }

    fun stop() {
        main.removeCallbacks(autoStop)
        ssdpRunning = false
        val l = listener
        listener = null
        if (l != null) {
            try {
                nsd.stopServiceDiscovery(l)
            } catch (_: Exception) {
            }
        }
        _searching.value = false
    }

    private fun add(dev: TvDevice) {
        main.post {
            val cur = _devices.value
            val existing = cur.firstOrNull { it.host == dev.host }
            // Android TV diutamakan (beberapa Android TV juga menjawab SSDP).
            if (existing != null && (existing.kind == "atv" || dev.kind != "atv")) return@post
            _devices.value = cur.filterNot { it.host == dev.host } + dev
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveNext() {
        if (resolving) return
        val info = queue.poll() ?: return
        resolving = true
        try {
            nsd.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    main.post {
                        resolving = false
                        resolveNext()
                    }
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    val host = serviceInfo.host?.hostAddress
                    main.post {
                        resolving = false
                        if (host != null) add(TvDevice(serviceInfo.serviceName, host, serviceInfo.port, "atv"))
                        resolveNext()
                    }
                }
            })
        } catch (e: Exception) {
            resolving = false
            main.post { resolveNext() }
        }
    }

    // ------------------------------------------------------------------ SSDP (Samsung & LG)

    private fun startSsdp() {
        if (ssdpRunning) return
        ssdpRunning = true
        Thread({ runSsdp() }, "ssdp").apply { isDaemon = true }.start()
    }

    private fun runSsdp() {
        val wifi = app.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock = wifi?.createMulticastLock("remot-ssdp")?.apply {
            setReferenceCounted(false)
            try {
                acquire()
            } catch (_: Exception) {
            }
        }
        val seen = HashSet<String>()
        try {
            MulticastSocket(null).use { sock ->
                sock.reuseAddress = true
                sock.bind(java.net.InetSocketAddress(0))
                sock.soTimeout = 1000
                val group = InetAddress.getByName("239.255.255.250")
                val targets = listOf(
                    "urn:samsung.com:device:RemoteControlReceiver:1",
                    "urn:lge-com:service:webos-second-screen:1",
                )
                val buf = ByteArray(2048)
                repeat(3) { round ->
                    if (!ssdpRunning) return@use
                    for (st in targets) {
                        val msg = "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\n" +
                            "MX: 2\r\nST: $st\r\n\r\n"
                        val bytes = msg.toByteArray()
                        try {
                            sock.send(DatagramPacket(bytes, bytes.size, group, 1900))
                        } catch (_: Exception) {
                        }
                    }
                    val end = System.currentTimeMillis() + if (round == 2) 3000 else 2000
                    while (ssdpRunning && System.currentTimeMillis() < end) {
                        val pkt = DatagramPacket(buf, buf.size)
                        try {
                            sock.receive(pkt)
                        } catch (_: SocketTimeoutException) {
                            continue
                        }
                        val host = pkt.address?.hostAddress ?: continue
                        val text = String(pkt.data, 0, pkt.length)
                        val headers = text.split("\r\n").mapNotNull { line ->
                            val i = line.indexOf(':')
                            if (i > 0) line.substring(0, i).trim().lowercase() to line.substring(i + 1).trim() else null
                        }.toMap()
                        val st = (headers["st"] ?: headers["nt"] ?: "").lowercase()
                        val kind = when {
                            "samsung" in st -> "samsung"
                            "lge" in st || "webos" in (headers["server"] ?: "").lowercase() -> "lg"
                            else -> null
                        } ?: continue
                        if (!seen.add("$kind@$host")) continue
                        val name = headers["location"]?.let { friendlyName(it) }
                            ?: if (kind == "samsung") "Samsung TV" else "LG TV"
                        add(TvDevice(name, host, 0, kind))
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            try {
                lock?.release()
            } catch (_: Exception) {
            }
            ssdpRunning = false
        }
    }

    private fun friendlyName(location: String): String? = try {
        val c = URL(location).openConnection() as HttpURLConnection
        c.connectTimeout = 2000
        c.readTimeout = 2000
        val xml = c.inputStream.bufferedReader().use { it.readText() }
        Regex("<friendlyName>([^<]+)</friendlyName>").find(xml)?.groupValues?.get(1)
            ?.replace("&amp;", "&")?.replace("&quot;", "\"")?.trim()
    } catch (e: Exception) {
        null
    }

    companion object {
        private const val SERVICE = "_androidtvremote2._tcp."
    }
}
