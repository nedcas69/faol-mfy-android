package uz.bekobod.faolmfy.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import uz.bekobod.faolmfy.data.Prefs
import uz.bekobod.faolmfy.data.remote.ApiClient
import uz.bekobod.faolmfy.location.TrackingService
import uz.bekobod.faolmfy.util.DeviceInfo
import uz.bekobod.faolmfy.util.WorkWindow

/**
 * Watchdog — TZ F2.7 / B11.
 *
 * Xiaomi (MIUI/HyperOS), Honor (MagicOS), Oppo, Vivo o'z "battery optimizer"i
 * bilan ForegroundService ni ham o'ldiradi. Ruxsat berilgan bo'lsa ham.
 * Ilova ichidan kod bilan buni to'liq oldini olib bo'lmaydi.
 *
 * Shuning uchun: har 15 daqiqada tekshiramiz. Ish vaqti bo'lsa-yu servis
 * "tirikman" belgisini 5 daqiqadan beri yangilamagan bo'lsa —
 *   1) `service_killed` hodisasini yozamiz (pilotning asosiy o'lchovi)
 *   2) servisni qayta ishga tushiramiz
 */
class WatchdogWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "Watchdog"
        private const val ALIVE_TIMEOUT_MS = 5 * 60 * 1000L
    }

    override suspend fun doWork(): Result {
        val prefs = Prefs(applicationContext)
        if (prefs.accessToken().isNullOrBlank()) return Result.success()
        runCatching { ApiClient.getReady(applicationContext) }

        val cfg = prefs.config()
        val inside = WorkWindow.isInsideWindow(cfg, prefs.holidays(), prefs.extraWorkdays())

        if (!inside) {
            // Ish vaqtidan tashqarida — faqat qolgan ma'lumotni yuboramiz
            runCatching { SyncManager(applicationContext).flush() }
            return Result.success()
        }

        val lastAlive = prefs.lastServiceAlive()
        val silentFor = System.currentTimeMillis() - lastAlive
        val running = DeviceInfo.isServiceRunning(applicationContext, TrackingService::class.java)

        if (!running || silentFor > ALIVE_TIMEOUT_MS) {
            Log.w(TAG, "Servis o'lgan (jim: ${silentFor / 1000}s, running=$running) — qayta yoqilmoqda")
            val sync = SyncManager(applicationContext)
            sync.logEvent(
                "service_killed",
                mapOf(
                    "silent_seconds" to (silentFor / 1000).toString(),
                    "manufacturer" to DeviceInfo.manufacturer,
                    "model" to DeviceInfo.model,
                    "battery_unrestricted" to
                        DeviceInfo.isBatteryUnrestricted(applicationContext).toString(),
                )
            )
            runCatching { TrackingService.start(applicationContext) }
            runCatching { sync.flush() }
        } else {
            runCatching { SyncManager(applicationContext).flush() }
        }
        return Result.success()
    }
}
