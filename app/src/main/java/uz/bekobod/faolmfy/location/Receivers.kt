package uz.bekobod.faolmfy.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import uz.bekobod.faolmfy.data.Prefs
import uz.bekobod.faolmfy.sync.SyncManager
import uz.bekobod.faolmfy.sync.WorkScheduler
import uz.bekobod.faolmfy.util.WorkWindow

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/** Telefon qayta yoqilganda yoki ilova yangilanganda kuzatuvni tiklaydi. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.i("BootReceiver", "Signal: $action")
        val pending = goAsync()
        val app = context.applicationContext

        receiverScope.launch {
            try {
                val prefs = Prefs(app)
                if (prefs.accessToken().isNullOrBlank()) return@launch

                WorkScheduler.scheduleAll(app)
                AlarmScheduler.scheduleSuspend(app)

                SyncManager(app).logEvent(
                    "device_boot", mapOf("action" to action)
                )

                val cfg = prefs.config()
                if (WorkWindow.isInsideWindow(cfg, prefs.holidays(), prefs.extraWorkdays())) {
                    TrackingService.start(app)
                }
            } catch (e: Exception) {
                Log.e("BootReceiver", "Xato: ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }
}

/** 09:00 va 18:00 alarmlari. */
class WindowAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val pending = goAsync()
        val app = context.applicationContext

        receiverScope.launch {
            try {
                val prefs = Prefs(app)
                if (prefs.accessToken().isNullOrBlank()) return@launch

                when (action) {
                    AlarmScheduler.ACTION_WINDOW_START -> {
                        val cfg = prefs.config()
                        if (WorkWindow.isInsideWindow(cfg, prefs.holidays(), prefs.extraWorkdays())) {
                            TrackingService.start(app)
                        }
                    }
                    AlarmScheduler.ACTION_WINDOW_END -> {
                        TrackingService.stop(app)
                        SyncManager(app).flush()
                    }
                }
                AlarmScheduler.scheduleSuspend(app)
            } catch (e: Exception) {
                Log.e("WindowAlarm", "Xato: ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }
}
