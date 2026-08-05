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
    @Volatile
    private var cachedAt: Long = 0L

    // Keshning yashash muddati. Bu vaqtdan keyin GitHub'dan qayta olinadi.
    // Qisqa bo'lgani uchun tunnel o'zgarsa ilova bir daqiqada o'zi topadi,
    // lekin har so'rovda GitHub'ni bezovta qilmaydi.
    private const val TTL_MS = 60_000L

    /**
     * Joriy API manzilini qaytaradi.
     *
     * @param force true bo'lsa keshni butunlay chetlab, GitHub'dan yangisini
     *   oladi. So'rov 200 bermaganda ApiClient shuni chaqiradi.
     *
     * Tartib:
     *   1. Kesh yangi (TTL ichida) va force emas — o'shani
     *   2. GitHub'dan olishga urinadi, keshga yozadi
     *   3. Ulanmasa — oxirgi diskdagi manzil
     *   4. Hech narsa yo'q — APK ichidagi standart
     */
    suspend fun apiBaseUrl(context: Context, force: Boolean = false): String {
        val now = System.currentTimeMillis()
        if (!force) {
            cached?.let { if (now - cachedAt < TTL_MS) return it }
        }

        val prefs = Prefs(context)
        val fetched = fetch()
        if (fetched != null) {
            val url = normalize(fetched.apiBaseUrl)
            val changed = url != cached
            cached = url
            cachedAt = now
            prefs.saveDiscoveredEndpoint(url, fetched.s3Endpoint)
            if (changed) Log.i(TAG, "Manzil yangilandi: $url")
            return url
        }

        // GitHub'ga ulana olmadik — sessiyadagi keshni ishlatamiz
        cached?.let { return it }

        val saved = prefs.discoveredApi()
        if (!saved.isNullOrBlank()) {
            cached = saved
            cachedAt = now
            Log.i(TAG, "Manzil diskdan olindi: $saved")
            return saved
        }

        val fallback = normalize(BuildConfig.API_BASE_URL)
        cached = fallback
        cachedAt = now
        Log.w(TAG, "Discovery ishlamadi, standart manzil: $fallback")
        return fallback
    }

    /**
     * Manzilni GitHub'dan majburan qayta oladi (keshni tashlaydi).
     * So'rov muvaffaqiyatsiz bo'lganda ApiClient shuni chaqiradi:
     * ehtimol tunnel manzili o'zgargan.
     */
    suspend fun forceRefresh(context: Context): String {
        Log.i(TAG, "Majburiy yangilash — so'rov muvaffaqiyatsiz bo'ldi")
        return apiBaseUrl(context, force = true)
    }

    /** Keyingi so'rovda GitHub'dan qaytadan olishga majbur qiladi. */
    fun invalidate() {
        cached = null
        cachedAt = 0L
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
