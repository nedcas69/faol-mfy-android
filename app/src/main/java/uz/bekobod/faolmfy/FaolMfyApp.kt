package uz.bekobod.faolmfy

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import uz.bekobod.faolmfy.data.Prefs
import uz.bekobod.faolmfy.location.AlarmScheduler
import uz.bekobod.faolmfy.location.TrackingService
import uz.bekobod.faolmfy.sync.WorkScheduler
import uz.bekobod.faolmfy.util.WorkWindow

class FaolMfyApp : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            // Backend manzilini oldindan aniqlab olamiz
            runCatching { uz.bekobod.faolmfy.data.remote.ApiClient.getReady(this@FaolMfyApp) }

            val prefs = Prefs(this@FaolMfyApp)
            if (prefs.accessToken().isNullOrBlank()) return@launch

            WorkScheduler.scheduleAll(this@FaolMfyApp)
            AlarmScheduler.scheduleSuspend(this@FaolMfyApp)

            val cfg = prefs.config()
            if (WorkWindow.isInsideWindow(cfg, prefs.holidays(), prefs.extraWorkdays())) {
                TrackingService.start(this@FaolMfyApp)
            }
        }
    }
}
