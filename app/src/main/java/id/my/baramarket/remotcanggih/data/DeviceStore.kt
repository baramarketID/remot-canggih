package id.my.baramarket.remotcanggih.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class TvDevice(
    val name: String,
    val host: String,
    val port: Int = 6466,
    /** "atv", "samsung", "lg", atau "" bila belum diketahui (IP manual). */
    val kind: String = "atv",
    /** Token Samsung / client-key LG yang didapat setelah disetujui di TV. */
    val secret: String? = null,
)

/** Menyimpan daftar TV yang pernah tersambung (di HP, tanpa akun/server). */
class DeviceStore(context: Context) {
    private val prefs = context.getSharedPreferences("tv", Context.MODE_PRIVATE)

    fun saved(): List<TvDevice> {
        val raw = prefs.getString("devices", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                TvDevice(
                    o.getString("name"), o.getString("host"), o.optInt("port", 6466),
                    o.optString("kind", "atv"), o.optString("secret", "").ifEmpty { null },
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Menyimpan TV; nama yang sudah diganti pengguna tetap dipakai. */
    fun save(device: TvDevice): TvDevice {
        val old = saved()
        val existing = old.firstOrNull { it.host == device.host }
        val keep = if (existing != null) device.copy(name = existing.name, secret = device.secret ?: existing.secret) else device
        write(old.map { if (it.host == device.host) keep else it } + if (existing == null) listOf(keep) else emptyList())
        prefs.edit().putString("last", device.host).apply()
        return keep
    }

    fun rename(host: String, name: String) {
        write(saved().map { if (it.host == host) it.copy(name = name) else it })
    }

    fun remove(device: TvDevice) {
        write(saved().filterNot { it.host == device.host })
        if (prefs.getString("last", null) == device.host) prefs.edit().remove("last").apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun last(): TvDevice? {
        val host = prefs.getString("last", null) ?: return null
        return saved().firstOrNull { it.host == host }
    }

    private fun write(list: List<TvDevice>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject().put("name", it.name).put("host", it.host).put("port", it.port)
                    .put("kind", it.kind).put("secret", it.secret ?: "")
            )
        }
        prefs.edit().putString("devices", arr.toString()).apply()
    }
}
