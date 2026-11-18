package id.my.baramarket.remotcanggih.tv

import id.my.baramarket.remotcanggih.atv.AtvRemote
import id.my.baramarket.remotcanggih.atv.Keys
import id.my.baramarket.remotcanggih.core.TvApp
import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/** Jenis TV yang didukung. */
object TvKind {
    const val ANDROID = "atv"
    const val SAMSUNG = "samsung"
    const val LG = "lg"
}

/** Satu koneksi remot ke TV, apa pun merknya. Semua fungsi boleh blocking (dipanggil di thread kirim). */
interface TvClient {
    val ready: Boolean

    /** Kode tombol Android (lihat [Keys]); tiap merk menerjemahkannya sendiri. */
    fun key(code: Int)

    /** Menyamakan isi kolom teks di TV dengan teks di HP. */
    fun text(old: String, new: String)

    /** False bila aplikasi ini tidak dikenal untuk merk TV ini. */
    fun launch(app: TvApp): Boolean

    fun close()
}

/** Pembungkus untuk Android TV / Google TV. */
class AtvClient(private val r: AtvRemote) : TvClient {
    override val ready: Boolean get() = r.isReady

    override fun key(code: Int) = r.sendKey(code)

    override fun text(old: String, new: String) {
        if (r.canTypeDirectly() && new.isNotEmpty()) {
            r.sendText(new)
        } else {
            var p = 0
            while (p < old.length && p < new.length && old[p] == new[p]) p++
            repeat(old.length - p) { r.sendKey(Keys.DEL) }
            new.substring(p).forEach { ch -> Keys.forChar(ch)?.let { r.sendKey(it) } }
        }
    }

    override fun launch(app: TvApp): Boolean {
        r.launchApp(app.link)
        return true
    }

    override fun close() = r.close()
}

/** OkHttp yang menerima sertifikat self-signed TV (Samsung wss:8002, LG wss:3001). */
internal object Http {
    private val trustAll = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }

    val client: OkHttpClient by lazy {
        val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustAll), SecureRandom()) }
        OkHttpClient.Builder()
            .sslSocketFactory(ssl.socketFactory, trustAll)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
    }
}
