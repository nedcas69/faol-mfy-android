package uz.bekobod.faolmfy.data.remote

import android.content.Context
import android.util.Log
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import uz.bekobod.faolmfy.BuildConfig
import uz.bekobod.faolmfy.data.Prefs
import java.util.concurrent.TimeUnit

/**
 * Backend manzilini topish — "discovery URL" patterni.
 *
 * Muammo: quick tunnel manzili (trycloudflare.com) har qayta ishga
 * tushganda o'zgaradi. Agar u APK ichiga qattiq yozilsa, tunnel o'zgarganda
 * barcha telefonlarda APK ni qayta o'rnatish kerak bo'lardi.
 *
 * Yechim: APK ichida faqat GitHub'dagi JSON manzili turadi (u hech qachon
 * o'zgarmaydi). Ilova ishga tushganda shu JSON'dan haqiqiy backend manzilini
 * oladi. Tunnel o'zgarsa — server skripti JSON'ni yangilaydi, ilova o'zi topadi.
 *
 * Domen olingandan keyin JSON'ga doimiy manzil yoziladi va bu mexanizm
 * shunchaki bir xil qiymatni qaytaraveradi — hech narsa buzilmaydi.
 */
object EndpointProvider {

    private const val TAG = "EndpointProvider"

    // Bu manzil HECH QACHON o'zgarmaydi. raw.githubusercontent.com keshlanadi,
    // shuning uchun cache-buster (?t=) qo'shamiz.
    private const val CONFIG_URL =
        "https://raw.githubusercontent.com/nedcas69/faol-mfy-android/main/config/endpoint.json"

    @Serializable
    data class Endpoint(
        @SerialName("api_base_url") val apiBaseUrl: String,
        @SerialName("s3_endpoint") val s3Endpoint: String = "",
        @SerialName("updated_at") val updatedAt: String = "",
        @SerialName("min_app_version") val minAppVersion: Int = 0,
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var cached: String? = null

    /**
     * Joriy API manzilini qaytaradi. Tartib:
     *   1. Shu sessiyada allaqachon olingan bo'lsa — o'shani
     *   2. GitHub'dan yangisini olishga urinadi va keshga yozadi
     *   3. Internet yo'q bo'lsa — oxirgi saqlangan manzilni
     *   4. Hech narsa bo'lmasa — APK ichidagi standart (BuildConfig)
     */
    suspend fun apiBaseUrl(context: Context): String {
        cached?.let { return it }

        val prefs = Prefs(context)
        val fetched = fetch()
        if (fetched != null) {
            val url = normalize(fetched.apiBaseUrl)
            cached = url
            prefs.saveDiscoveredEndpoint(url, fetched.s3Endpoint)
            Log.i(TAG, "Manzil GitHub'dan olindi: $url")
            return url
        }

        val saved = prefs.discoveredApi()
        if (!saved.isNullOrBlank()) {
            cached = saved
            Log.i(TAG, "Manzil keshdan olindi: $saved")
            return saved
        }

        val fallback = normalize(BuildConfig.API_BASE_URL)
        cached = fallback
        Log.w(TAG, "Discovery ishlamadi, standart manzil: $fallback")
        return fallback
    }

    /** Keyingi so'rovda GitHub'dan qaytadan olishga majbur qiladi. */
    fun invalidate() {
        cached = null
    }

    private fun fetch(): Endpoint? = try {
        val req = Request.Builder()
            .url("$CONFIG_URL?t=${System.currentTimeMillis()}")
            .header("Cache-Control", "no-cache")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                Log.w(TAG, "Config olinmadi: ${resp.code}")
                return null
            }
            val body = resp.body?.string() ?: return null
            ApiClient.json.decodeFromString<Endpoint>(body)
        }
    } catch (e: Exception) {
        Log.w(TAG, "Config xatosi: ${e.message}")
        null
    }

    private fun normalize(url: String): String =
        if (url.endsWith("/")) url else "$url/"
}
