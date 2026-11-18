package id.my.baramarket.remotcanggih.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.ArrayDeque

/**
 * Mencari Android TV / Google TV di jaringan WiFi yang sama (mDNS "_androidtvremote2._tcp").
 */
class TvDiscovery(context: Context) {
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val main = Handler(Looper.getMainLooper())
    private val _devices = MutableStateFlow<List<TvDevice>>(emptyList())
    val devices: StateFlow<List<TvDevice>> = _devices

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching

    private var listener: NsdManager.DiscoveryListener? = null
    private val queue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false

    fun start() {
        if (listener != null) return
        _devices.value = emptyList()
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                main.post { _searching.value = true }
            }

            override fun onDiscoveryStopped(serviceType: String) {
                main.post { _searching.value = false }
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                main.post {
                    _searching.value = false
                    listener = null
                }
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
                    _devices.value = _devices.value.filterNot { it.name == info.serviceName }
                }
            }
        }
        listener = l
        try {
            nsd.discoverServices(SERVICE, NsdManager.PROTOCOL_DNS_SD, l)
        } catch (e: Exception) {
            listener = null
        }
        // Berhenti otomatis supaya tidak boros baterai.
        main.removeCallbacks(autoStop)
        main.postDelayed(autoStop, 20_000)
    }

    private val autoStop = Runnable { stop() }

    fun stop() {
        main.removeCallbacks(autoStop)
        val l = listener ?: return
        listener = null
        try {
            nsd.stopServiceDiscovery(l)
        } catch (_: Exception) {
        }
        _searching.value = false
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
                        if (host != null) {
                            val dev = TvDevice(serviceInfo.serviceName, host, serviceInfo.port)
                            _devices.value = _devices.value.filterNot { it.host == host } + dev
                        }
                        resolveNext()
                    }
                }
            })
        } catch (e: Exception) {
            resolving = false
            main.post { resolveNext() }
        }
    }

    companion object {
        private const val SERVICE = "_androidtvremote2._tcp."
    }
}
