package uz.bekobod.faolmfy.data.remote

import android.content.Context
import android.util.Log
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import uz.bekobod.faolmfy.BuildConfig
import uz.bekobod.faolmfy.data.Prefs
import java.util.concurrent.TimeUnit

object ApiClient {

    private const val TAG = "ApiClient"

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    @Volatile private var instance: ApiService? = null
    @Volatile private var builtFor: String? = null

    /**
     * Diqqat: bu funksiya endpoint'ni bilgan holda chaqirilishi kerak.
     * Birinchi marta suspend `getReady()` orqali quriladi. Keyingi
     * chaqiruvlar (interceptor kabi) uchun sinxron `get()` keshdan qaytaradi.
     */
    fun get(context: Context): ApiService = instance ?: synchronized(this) {
        instance ?: build(context.applicationContext, fallbackBaseUrl(context)).also {
            instance = it
        }
    }

    /**
     * Manzilni GitHub discovery'dan olib, shu manzil uchun klient quradi.
     * Ilova ishga tushganda va manzil o'zgargan bo'lishi mumkin bo'lganda
     * chaqiriladi.
     */
    suspend fun getReady(context: Context): ApiService {
        val baseUrl = EndpointProvider.apiBaseUrl(context.applicationContext)
        val current = instance
        if (current != null && builtFor == baseUrl) return current
        return synchronized(this) {
            build(context.applicationContext, baseUrl).also {
                instance = it
                builtFor = baseUrl
            }
        }
    }

    /** Diskdagi oxirgi manzil yoki BuildConfig — bloklashsiz. */
    private fun fallbackBaseUrl(context: Context): String {
        return runBlocking {
            val prefs = Prefs(context)
            prefs.discoveredApi()?.takeIf { it.isNotBlank() } ?: BuildConfig.API_BASE_URL
        }
    }

    private fun build(context: Context, baseUrl: String): ApiService {
        val prefs = Prefs(context)

        val auth = Interceptor { chain ->
            val original = chain.request()
            val path = original.url.encodedPath
            // Faollashtirish va yangilash so'rovlariga token qo'shilmaydi
            if (path.contains("/auth/activate") || path.contains("/auth/refresh")) {
                return@Interceptor chain.proceed(original)
            }
            val token = runBlocking { prefs.accessToken() }
            val req = if (token.isNullOrBlank()) original
                      else original.newBuilder()
                          .header("Authorization", "Bearer $token").build()

            var response = chain.proceed(req)
            if (response.code == 401 && !token.isNullOrBlank()) {
                response.close()
                val refreshed = runBlocking { tryRefresh(context, prefs) }
                if (refreshed != null) {
                    response = chain.proceed(
                        original.newBuilder()
                            .header("Authorization", "Bearer $refreshed").build()
                    )
                }
            }
            response
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .writeTimeout(40, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(auth)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ApiService::class.java)
    }

    /** Token muddati tugaganda bir marta yangilashga urinish. */
    private suspend fun tryRefresh(context: Context, prefs: Prefs): String? {
        val refresh = prefs.refreshToken() ?: return null
        return try {
            val baseUrl = EndpointProvider.apiBaseUrl(context.applicationContext)
            val bare = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(OkHttpClient.Builder().build())
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build().create(ApiService::class.java)
            val resp = bare.refresh(RefreshRequest(refresh))
            val body = resp.body()
            if (resp.isSuccessful && body != null) {
                prefs.saveTokens(body.accessToken, body.refreshToken)
                body.accessToken
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "Token yangilanmadi: ${e.message}")
            null
        }
    }
}
