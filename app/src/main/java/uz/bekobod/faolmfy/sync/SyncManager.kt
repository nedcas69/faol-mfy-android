package uz.bekobod.faolmfy.sync

import android.content.Context
import android.util.Log
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import uz.bekobod.faolmfy.BuildConfig
import uz.bekobod.faolmfy.data.Prefs
import uz.bekobod.faolmfy.data.local.AppDatabase
import uz.bekobod.faolmfy.data.local.DeviceEventEntity
import uz.bekobod.faolmfy.data.remote.ApiClient
import uz.bekobod.faolmfy.data.remote.DeviceEventBatchRequest
import uz.bekobod.faolmfy.data.remote.DeviceEventDto
import uz.bekobod.faolmfy.data.remote.PositionBatchRequest
import uz.bekobod.faolmfy.data.remote.PositionDto
import uz.bekobod.faolmfy.util.DeviceInfo
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Outbox ni serverga bo'shatish — TZ F5.
 *
 * Idempotentlik: har nuqta `clientSeq` bilan ketadi, server `device_id:clientSeq`
 * kaliti bo'yicha dublikatni tashlaydi. Shuning uchun javob kelmasa
 * xotirjam qayta yuborsa bo'ladi.
 */
class SyncManager(private val context: Context) {

    companion object {
        private const val TAG = "SyncManager"
        private val ISO: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC)

        fun iso(millis: Long): String = ISO.format(Instant.ofEpochMilli(millis))
    }

    private val db = AppDatabase.get(context)
    private val prefs = Prefs(context)
    private suspend fun api() = ApiClient.getReady(context)

    /** Kutayotgan barcha nuqta va hodisalarni yuboradi. */
    suspend fun flush(): Boolean {
        val deviceId = prefs.deviceId()
        val cfg = prefs.config()
        var allOk = true

        // --- hodisalar (kichik, avval yuboriladi) ---
        val events = db.deviceEvents().pending(100)
        if (events.isNotEmpty()) {
            val ok = pushEvents(deviceId, events)
            if (ok) db.deviceEvents().markSynced(events.map { it.id }) else allOk = false
        }

        // --- nuqtalar, paket-paket ---
        while (true) {
            val batch = db.positions().pending(cfg.syncBatchMax)
            if (batch.isEmpty()) break

            val request = PositionBatchRequest(
                deviceId = deviceId,
                points = batch.map {
                    PositionDto(
                        ts = iso(it.ts),
                        lat = it.lat, lon = it.lon,
                        accuracy = it.accuracy, speed = it.speed,
                        bearing = it.bearing, altitude = it.altitude,
                        battery = it.battery, isCharging = it.isCharging,
                        isMock = it.isMock, provider = it.provider,
                        activity = it.activity, stepDelta = it.stepDelta,
                        clientSeq = it.clientSeq,
                    )
                }
            )

            val ok = try {
                val resp = api().pushPositions(request)
                if (resp.isSuccessful) {
                    val b = resp.body()
                    Log.i(TAG, "Yuborildi: qabul ${b?.accepted}, dublikat ${b?.duplicates}, " +
                               "rad ${b?.rejected}")
                    true
                } else {
                    // 401 — interceptor tokenni yangilashga urinib ko'rgan, foyda bermagan
                    Log.w(TAG, "Server javobi: ${resp.code()}")
                    false
                }
            } catch (e: Exception) {
                Log.w(TAG, "Tarmoq xatosi: ${e.message}")
                false
            }

            if (!ok) { allOk = false; break }

            db.positions().markSynced(batch.map { it.clientSeq })
            prefs.markSync()
            if (batch.size < cfg.syncBatchMax) break
        }

        purge()
        return allOk
    }

    private suspend fun pushEvents(deviceId: String, events: List<DeviceEventEntity>): Boolean = try {
        val resp = api().pushDeviceEvents(
            DeviceEventBatchRequest(
                deviceId = deviceId,
                events = events.map {
                    DeviceEventDto(
                        ts = iso(it.ts),
                        type = it.type,
                        details = it.detailsJson?.let { j ->
                            runCatching {
                                Json.decodeFromString<Map<String, String>>(j)
                            }.getOrNull()
                        },
                        manufacturer = DeviceInfo.manufacturer,
                        model = DeviceInfo.model,
                        androidVersion = DeviceInfo.androidVersion,
                        appVersion = BuildConfig.VERSION_NAME,
                    )
                }
            )
        )
        resp.isSuccessful
    } catch (e: Exception) {
        Log.w(TAG, "Hodisalar yuborilmadi: ${e.message}")
        false
    }

    /** Qurilma hodisasini lokal navbatga yozadi (keyin yuboriladi). */
    suspend fun logEvent(type: String, details: Map<String, String>? = null) {
        db.deviceEvents().insert(
            DeviceEventEntity(
                ts = System.currentTimeMillis(),
                type = type,
                detailsJson = details?.let { Json.encodeToString(it) },
            )
        )
    }

    /** Lokal bazani tozalash — TZ F5.7. */
    private suspend fun purge() {
        val now = System.currentTimeMillis()
        db.positions().purgeSynced(now - 48 * 3600_000L)
        db.positions().purgeAll(now - 14 * 24 * 3600_000L)
        db.deviceEvents().purge(now - 7 * 24 * 3600_000L)
    }
}
