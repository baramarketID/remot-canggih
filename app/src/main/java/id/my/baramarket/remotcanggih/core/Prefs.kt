package id.my.baramarket.remotcanggih.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Aplikasi TV yang bisa dibuka dari tab Aplikasi. */
data class TvApp(
    val id: String,
    val name: String,
    val link: String,
    val color: Long,
    val custom: Boolean = false,
    /** Paket aplikasi yang sama di HP, untuk menampilkan ikon aslinya bila terpasang. */
    val phonePkgs: List<String> = emptyList(),
)

val DEFAULT_APPS = listOf(
    TvApp("youtube", "YouTube", "https://www.youtube.com", 0xFFE62117, phonePkgs = listOf("com.google.android.youtube")),
    TvApp("netflix", "Netflix", "https://www.netflix.com/title", 0xFFB20710, phonePkgs = listOf("com.netflix.mediaclient")),
    TvApp("prime", "Prime Video", "https://app.primevideo.com", 0xFF0F79AF,
        phonePkgs = listOf("com.amazon.avod.thirdpartyclient")),
    TvApp("disney", "Disney+", "https://www.disneyplus.com", 0xFF0E2A7A,
        phonePkgs = listOf("com.disney.disneyplus", "in.startv.hotstar.dplus", "in.startv.hotstar")),
    TvApp("spotify", "Spotify", "market://launch?id=com.spotify.tv.android", 0xFF168D40,
        phonePkgs = listOf("com.spotify.music")),
    TvApp("vidio", "Vidio", "https://www.vidio.com", 0xFFC41E25, phonePkgs = listOf("com.vidio.android")),
    TvApp("viu", "Viu", "https://www.viu.com", 0xFFB88600, phonePkgs = listOf("com.vuclip.viu")),
    TvApp("wetv", "WeTV", "https://wetv.vip", 0xFFD35400, phonePkgs = listOf("com.tencent.qqlivei18n")),
    TvApp("ytkids", "YouTube Kids", "market://launch?id=com.google.android.youtube.tvkids", 0xFFD93A2F,
        phonePkgs = listOf("com.google.android.apps.youtube.kids")),
)

/** Semua paket HP yang perlu terlihat (dipakai juga di <queries> manifest). */
val ICON_PACKAGES = DEFAULT_APPS.flatMap { it.phonePkgs }

private val CUSTOM_COLORS = listOf(0xFF6D4AFF, 0xFF0E8A7E, 0xFFB5306E, 0xFF2F6FDB, 0xFF8A5A00, 0xFF3C7D22)

/** Pengaturan yang disimpan di HP. */
class Prefs(context: Context) {
    private val p = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var darkTheme: Boolean
        get() = p.getString("theme", "dark") != "light"
        set(v) = p.edit().putString("theme", if (v) "dark" else "light").apply()

    var volumeKeys: Boolean
        get() = p.getBoolean("volume_keys", true)
        set(v) = p.edit().putBoolean("volume_keys", v).apply()

    var notifControls: Boolean
        get() = p.getBoolean("notif_controls", false)
        set(v) = p.edit().putBoolean("notif_controls", v).apply()

    /** Waktu (epoch ms) TV dimatikan otomatis; 0 = timer mati. */
    var sleepAt: Long
        get() = p.getLong("sleep_at", 0L)
        set(v) = p.edit().putLong("sleep_at", v).apply()

    var hiddenApps: Set<String>
        get() = p.getStringSet("hidden_apps", emptySet())?.toSet() ?: emptySet()
        set(v) = p.edit().putStringSet("hidden_apps", v).apply()

    var customApps: List<TvApp>
        get() {
            val raw = p.getString("custom_apps", null) ?: return emptyList()
            return try {
                val arr = JSONArray(raw)
                (0 until arr.length()).map {
                    val o = arr.getJSONObject(it)
                    val link = o.getString("link")
                    TvApp(o.getString("id"), o.getString("name"), link, o.getLong("color"), true,
                        listOfNotNull(link.substringAfter("market://launch?id=", "").ifEmpty { null }))
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
        set(v) {
            val arr = JSONArray()
            v.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name).put("link", it.link).put("color", it.color)) }
            p.edit().putString("custom_apps", arr.toString()).apply()
        }

    fun visibleApps(): List<TvApp> {
        val hidden = hiddenApps
        return DEFAULT_APPS.filterNot { it.id in hidden } + customApps
    }

    fun newCustomApp(name: String, input: String): TvApp {
        val trimmed = input.trim()
        val link = if (trimmed.contains("://")) trimmed else "market://launch?id=$trimmed"
        val color = CUSTOM_COLORS[(customApps.size) % CUSTOM_COLORS.size]
        val pkg = link.substringAfter("market://launch?id=", "").ifEmpty { null }
        return TvApp("c" + System.currentTimeMillis(), name.trim(), link, color, true, listOfNotNull(pkg))
    }
}
