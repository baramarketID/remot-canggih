package id.my.baramarket.remotcanggih.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class TvDevice(
    val name: String,
    val host: String,
    val port: Int = 6466,
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
                TvDevice(o.getString("name"), o.getString("host"), o.optInt("port", 6466))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(device: TvDevice) {
        val list = saved().filterNot { it.host == device.host } + device
        write(list)
        prefs.edit().putString("last", device.host).apply()
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
            arr.put(JSONObject().put("name", it.name).put("host", it.host).put("port", it.port))
        }
        prefs.edit().putString("devices", arr.toString()).apply()
    }
}
