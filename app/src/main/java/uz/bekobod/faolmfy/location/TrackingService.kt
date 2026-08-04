package uz.bekobod.faolmfy.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uz.bekobod.faolmfy.R
import uz.bekobod.faolmfy.data.Prefs
import uz.bekobod.faolmfy.data.TrackingConfig
import uz.bekobod.faolmfy.data.local.AppDatabase
import uz.bekobod.faolmfy.data.local.PositionEntity
import uz.bekobod.faolmfy.sync.AttachmentManager
import uz.bekobod.faolmfy.sync.SyncManager
import uz.bekobod.faolmfy.ui.MainActivity
import uz.bekobod.faolmfy.util.DeviceInfo
import uz.bekobod.faolmfy.util.WorkWindow

/**
 * Kuzatuv servisi — TZ F3.
 *
 * Vazifalari:
 *  - GPS nuqtalarini yig'ib lokal outbox ga yozish
 *  - har 2 daqiqada yig'ilganini serverga yuborish
 *  - 18:00 da o'zini to'xtatish
 *  - "tirikman" belgisini yozib turish (watchdog shu bo'yicha o'lganini biladi)
 */
class TrackingService : Service(), SensorEventListener {

    companion object {
        const val ACTION_START = "uz.bekobod.faolmfy.START"
        const val ACTION_STOP = "uz.bekobod.faolmfy.STOP"
        const val CHANNEL_TRACKING = "tracking"
        const val CHANNEL_ALERTS = "alerts"
        private const val NOTIF_ID = 1001
        private const val TAG = "TrackingService"

        fun start(context: Context) {
            val i = Intent(context, TrackingService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        fun stop(context: Context) {
            context.startService(Intent(context, TrackingService::class.java).setAction(ACTION_STOP))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var prefs: Prefs
    private lateinit var db: AppDatabase
    private lateinit var fused: FusedLocationProviderClient
    private var sensorManager: SensorManager? = null
    private var stepSensor: Sensor? = null

    private var cfg: TrackingConfig? = null
    private var syncJob: Job? = null
    private var watchdogJob: Job? = null

    private var lastStepTotal: Float = -1f
    private var pendingSteps: Int = 0
    private var lastActivity: String = "unknown"
    private var pointsToday: Int = 0

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach { handleLocation(it) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        db = AppDatabase.get(this)
        fused = LocationServices.getFusedLocationProviderClient(this)
        createChannels()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        stepSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopTracking("manual")
                return START_NOT_STICKY
            }
            else -> startTracking()
        }
        // START_STICKY — tizim servisni o'ldirsa qayta ishga tushirishga urinadi.
        // Xiaomi/Honor da bu kafolat emas, shuning uchun watchdog ham bor.
        return START_STICKY
    }

    private fun startTracking() {
        startForegroundSafely(buildNotification("Ishga tushmoqda…", null))

        scope.launch {
            val c = prefs.config()
            cfg = c

            val holidays = prefs.holidays()
            val extra = prefs.extraWorkdays()
            if (!WorkWindow.isInsideWindow(c, holidays, extra)) {
                Log.i(TAG, "Ish oynasidan tashqarida — servis to'xtatiladi")
                AlarmScheduler.schedule(this@TrackingService)
                stopTracking("outside_window")
                return@launch
            }

            requestLocationUpdates(c)
            registerStepSensor()
            startSyncLoop(c)
            startWatchdogLoop(c)
            SyncManager(this@TrackingService).logEvent("service_started", mapOf("reason" to "start"))
        }
    }

    private fun startForegroundSafely(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(NOTIF_ID, notification)
            }
        } catch (e: Exception) {
            // Android 12+ da ilova fonda bo'lsa startForegroundService cheklanishi mumkin
            Log.e(TAG, "startForeground xatosi: ${e.message}")
            stopSelf()
        }
    }

    private fun requestLocationUpdates(c: TrackingConfig) {
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            c.minIntervalS * 1000L,
        )
            .setMinUpdateDistanceMeters(c.distanceFilterM.toFloat())
            .setMinUpdateIntervalMillis(c.minIntervalS * 1000L)
            // Harakatsiz bo'lsa ham shu oraliqda bitta nuqta keladi (heartbeat)
            .setMaxUpdateDelayMillis(c.idleIntervalS * 1000L)
            .setWaitForAccurateLocation(false)
            .build()

        try {
            fused.requestLocationUpdates(request, locationCallback, mainLooper)
            Log.i(TAG, "GPS yoqildi: filtr ${c.distanceFilterM}m, interval ${c.minIntervalS}s")
        } catch (e: SecurityException) {
            Log.e(TAG, "Joylashuv ruxsati yo'q: ${e.message}")
            scope.launch {
                SyncManager(this@TrackingService).logEvent(
                    "permission_revoked", mapOf("permission" to "location")
                )
            }
            stopTracking("no_permission")
        }
    }

    private fun registerStepSensor() {
        val sensor = stepSensor
        if (sensor == null) {
            Log.w(TAG, "Qadam sanagich yo'q — bu qurilmada tekshiruv ishlamaydi")
            scope.launch {
                SyncManager(this@TrackingService).logEvent(
                    "no_step_sensor", mapOf("model" to DeviceInfo.model)
                )
            }
            return
        }
        sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_STEP_COUNTER) return
        val total = event.values.firstOrNull() ?: return
        if (lastStepTotal < 0f) {
            lastStepTotal = total
            return
        }
        val delta = (total - lastStepTotal).toInt()
        if (delta > 0) {
            pendingSteps += delta
            lastStepTotal = total
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun handleLocation(loc: Location) {
        val c = cfg ?: return

        // Aniqligi past nuqtalar tashlanadi (TZ F3.3)
        if (loc.hasAccuracy() && loc.accuracy > c.maxAccuracyM) return

        val isMock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            loc.isMock
        } else {
            @Suppress("DEPRECATION") loc.isFromMockProvider
        }

        val steps = pendingSteps
        pendingSteps = 0

        scope.launch {
            // Ish oynasidan chiqqan bo'lsa — yozmaymiz va to'xtaymiz
            if (!WorkWindow.isInsideWindow(c, prefs.holidays(), prefs.extraWorkdays())) {
                AlarmScheduler.schedule(this@TrackingService)
                stopTracking("window_closed")
                return@launch
            }

            db.positions().insert(
                PositionEntity(
                    ts = loc.time.takeIf { it > 0 } ?: System.currentTimeMillis(),
                    lat = loc.latitude,
                    lon = loc.longitude,
                    accuracy = if (loc.hasAccuracy()) loc.accuracy else null,
                    speed = if (loc.hasSpeed()) loc.speed else null,
                    bearing = if (loc.hasBearing()) loc.bearing else null,
                    altitude = if (loc.hasAltitude()) loc.altitude else null,
                    battery = DeviceInfo.batteryPercent(this@TrackingService),
                    isCharging = DeviceInfo.isCharging(this@TrackingService),
                    isMock = isMock,
                    provider = loc.provider,
                    activity = lastActivity,
                    stepDelta = steps,
                )
            )
            pointsToday++
            prefs.markPoint()
            updateNotification()
        }
    }

    /** Har 2 daqiqada yuborish — TZ F5.3. */
    private fun startSyncLoop(c: TrackingConfig) {
        syncJob?.cancel()
        syncJob = scope.launch {
            val sync = SyncManager(this@TrackingService)
            val attachments = AttachmentManager(this@TrackingService)
            while (true) {
                delay(c.syncIntervalS * 1000L)
                try {
                    sync.flush()
                    // Rasm va izohlar ham shu yerda yuboriladi — xodim
                    // ilovani ochmasa ham navbat bo'shab boradi
                    attachments.flush()
                    updateNotification()
                } catch (e: Exception) {
                    Log.w(TAG, "Sinxron xatosi: ${e.message}")
                }
            }
        }
    }

    /**
     * "Tirikman" belgisi. Watchdog worker shu vaqtga qarab servis o'lganini
     * aniqlaydi va `service_killed` hodisasini yozadi — bu pilotning
     * asosiy o'lchovi (qaysi brendda ilova necha marta o'lgan).
     */
    private fun startWatchdogLoop(c: TrackingConfig) {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            while (true) {
                prefs.markServiceAlive()
                // 18:00 dan keyin o'zini to'xtatadi
                if (!WorkWindow.isInsideWindow(c, prefs.holidays(), prefs.extraWorkdays())) {
                    AlarmScheduler.schedule(this@TrackingService)
                    stopTracking("window_closed")
                    return@launch
                }
                delay(60_000)
            }
        }
    }

    private fun stopTracking(reason: String) {
        Log.i(TAG, "Kuzatuv to'xtatildi: $reason")
        try {
            fused.removeLocationUpdates(locationCallback)
        } catch (_: Exception) {}
        sensorManager?.unregisterListener(this)
        syncJob?.cancel()
        watchdogJob?.cancel()

        scope.launch {
            try {
                SyncManager(this@TrackingService).apply {
                    logEvent("service_stopped", mapOf("reason" to reason))
                    flush()
                }
            } catch (_: Exception) {}
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ------------------------------------------------------------ bildirishnoma

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_TRACKING, getString(R.string.notif_channel_tracking),
                NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERTS, getString(R.string.notif_channel_alerts),
                NotificationManager.IMPORTANCE_HIGH
            )
        )
    }

    private fun buildNotification(title: String, sub: String?): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_TRACKING)
            .setContentTitle(title)
            .setContentText(sub)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private suspend fun updateNotification() {
        val pending = db.positions().pendingCount()
        val sub = if (pending > 0) "Yuborilmagan: $pending · Nuqta: $pointsToday"
                  else "Nuqta: $pointsToday · Barchasi yuborilgan"
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(getString(R.string.tracking_active), sub))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /**
     * Foydalanuvchi ilovani "recent apps" dan surib tashlaganda ba'zi OEM lar
     * servisni ham o'ldiradi. Qayta ishga tushirishga urinamiz.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val restart = Intent(applicationContext, TrackingService::class.java)
            .setAction(ACTION_START)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                applicationContext.startForegroundService(restart)
            } else {
                applicationContext.startService(restart)
            }
        } catch (e: Exception) {
            Log.w(TAG, "onTaskRemoved qayta ishga tushirish muvaffaqiyatsiz: ${e.message}")
        }
        super.onTaskRemoved(rootIntent)
    }
}
