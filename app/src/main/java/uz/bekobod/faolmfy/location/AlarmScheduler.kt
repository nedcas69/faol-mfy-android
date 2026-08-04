package uz.bekobod.faolmfy.location

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import uz.bekobod.faolmfy.data.Prefs
import uz.bekobod.faolmfy.util.WorkWindow

/**
 * 09:00 da servisni yoqadi, 18:00 da o'chiradi (TZ F4.1, F4.2).
 *
 * 18:00 da to'liq to'xtash — bu shunchaki texnik detal emas, xodimlarga
 * berilgan va'da. Shuning uchun ikki qatlamli: alarm ham, servis ichidagi
 * tekshiruv ham.
 */
object AlarmScheduler {

    private const val TAG = "AlarmScheduler"
    const val ACTION_WINDOW_START = "uz.bekobod.faolmfy.WINDOW_START"
    const val ACTION_WINDOW_END = "uz.bekobod.faolmfy.WINDOW_END"

    private fun pi(context: Context, action: String, code: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, code,
            Intent(context, WindowAlarmReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    suspend fun scheduleSuspend(context: Context) {
        val prefs = Prefs(context)
        if (prefs.accessToken().isNullOrBlank()) return

        val cfg = prefs.config()
        val holidays = prefs.holidays()
        val extra = prefs.extraWorkdays()
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val nextStart = WorkWindow.nextStart(cfg, holidays, extra)
        setAlarm(am, context, nextStart.toInstant().toEpochMilli(), ACTION_WINDOW_START, 101)

        WorkWindow.todayEnd(cfg, holidays, extra)?.let { end ->
            setAlarm(am, context, end.toInstant().toEpochMilli(), ACTION_WINDOW_END, 102)
        }
        Log.i(TAG, "Keyingi boshlanish: $nextStart")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Chaqiruvchi coroutine ichida bo'lmagan joylardan ishlatish uchun. */
    fun schedule(context: Context) {
        scope.launch { scheduleSuspend(context.applicationContext) }
    }

    private fun setAlarm(am: AlarmManager, context: Context, at: Long, action: String, code: Int) {
        val intent = pi(context, action, code)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                // Aniq alarm ruxsati yo'q — taxminiy alarm ham yetarli,
                // chunki watchdog har 15 daqiqada tekshiradi
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        }
    }
}
