package uz.bekobod.faolmfy.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import uz.bekobod.faolmfy.data.remote.TrackingConfigDto
import java.util.UUID

private val Context.dataStore by preferencesDataStore("faolmfy")

class Prefs(private val context: Context) {

    private object K {
        val ACCESS = stringPreferencesKey("access_token")
        val REFRESH = stringPreferencesKey("refresh_token")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val FIO = stringPreferencesKey("fio")
        val ORG_NAME = stringPreferencesKey("org_name")
        val JOB_NAME = stringPreferencesKey("job_name")
        val ORG_ID = intPreferencesKey("org_id")
        val STATUS = stringPreferencesKey("status")

        val WIZARD_DONE = booleanPreferencesKey("wizard_done")
        val AUTOSTART_CONFIRMED = booleanPreferencesKey("autostart_confirmed")

        val WORK_START = stringPreferencesKey("work_start")
        val WORK_END = stringPreferencesKey("work_end")
        val WORK_DAYS = stringPreferencesKey("work_days")
        val DIST_FILTER = intPreferencesKey("dist_filter")
        val MIN_INTERVAL = intPreferencesKey("min_interval")
        val IDLE_INTERVAL = intPreferencesKey("idle_interval")
        val MAX_ACCURACY = intPreferencesKey("max_accuracy")
        val SYNC_INTERVAL = intPreferencesKey("sync_interval")
        val SYNC_BATCH = intPreferencesKey("sync_batch")

        val LAST_SYNC = longPreferencesKey("last_sync")
        val LAST_POINT = longPreferencesKey("last_point")
        val SERVICE_ALIVE = longPreferencesKey("service_alive")
        val HOLIDAYS = stringPreferencesKey("holidays")   // "2026-09-01,2026-03-21"
        val EXTRA_WORKDAYS = stringPreferencesKey("extra_workdays")
        val DISCOVERED_API = stringPreferencesKey("discovered_api")
        val DISCOVERED_S3 = stringPreferencesKey("discovered_s3")
    }

    // ------------------------------------------------------------ tokens

    suspend fun accessToken(): String? = context.dataStore.data.first()[K.ACCESS]
    suspend fun refreshToken(): String? = context.dataStore.data.first()[K.REFRESH]

    suspend fun saveTokens(access: String, refresh: String) {
        context.dataStore.edit { it[K.ACCESS] = access; it[K.REFRESH] = refresh }
    }

    suspend fun clearTokens() {
        context.dataStore.edit { it.remove(K.ACCESS); it.remove(K.REFRESH) }
    }

    val isLoggedIn: Flow<Boolean> = context.dataStore.data.map { !it[K.ACCESS].isNullOrBlank() }

    // ------------------------------------------------------------ device

    /** Qurilma identifikatori — birinchi ishga tushishda yaratiladi va o'zgarmaydi. */
    suspend fun deviceId(): String {
        val existing = context.dataStore.data.first()[K.DEVICE_ID]
        if (!existing.isNullOrBlank()) return existing
        val generated = UUID.randomUUID().toString()
        context.dataStore.edit { it[K.DEVICE_ID] = generated }
        return generated
    }

    // ------------------------------------------------------------ profil

    suspend fun saveProfile(fio: String, orgId: Int?, orgName: String?, jobName: String?, status: String) {
        context.dataStore.edit {
            it[K.FIO] = fio
            it[K.STATUS] = status
            orgId?.let { v -> it[K.ORG_ID] = v }
            orgName?.let { v -> it[K.ORG_NAME] = v }
            jobName?.let { v -> it[K.JOB_NAME] = v }
        }
    }

    val profile: Flow<Profile> = context.dataStore.data.map {
        Profile(
            fio = it[K.FIO].orEmpty(),
            orgName = it[K.ORG_NAME].orEmpty(),
            jobName = it[K.JOB_NAME].orEmpty(),
            status = it[K.STATUS].orEmpty(),
        )
    }

    // ------------------------------------------------------------ sehrgar

    val wizardDone: Flow<Boolean> = context.dataStore.data.map { it[K.WIZARD_DONE] ?: false }
    suspend fun setWizardDone(v: Boolean) = context.dataStore.edit { it[K.WIZARD_DONE] = v }.let { }
    suspend fun autostartConfirmed(): Boolean =
        context.dataStore.data.first()[K.AUTOSTART_CONFIRMED] ?: false
    suspend fun setAutostartConfirmed(v: Boolean) {
        context.dataStore.edit { it[K.AUTOSTART_CONFIRMED] = v }
    }

    // ------------------------------------------------------------ config

    suspend fun saveConfig(c: TrackingConfigDto) {
        context.dataStore.edit {
            it[K.WORK_START] = c.workStart
            it[K.WORK_END] = c.workEnd
            it[K.WORK_DAYS] = c.workDays.joinToString(",")
            it[K.DIST_FILTER] = c.distanceFilterM
            it[K.MIN_INTERVAL] = c.minIntervalS
            it[K.IDLE_INTERVAL] = c.idleIntervalS
            it[K.MAX_ACCURACY] = c.maxAccuracyM
            it[K.SYNC_INTERVAL] = c.syncIntervalS
            it[K.SYNC_BATCH] = c.syncBatchMax
        }
    }

    suspend fun config(): TrackingConfig {
        val d = context.dataStore.data.first()
        return TrackingConfig(
            workStart = d[K.WORK_START] ?: "09:00",
            workEnd = d[K.WORK_END] ?: "18:00",
            workDays = (d[K.WORK_DAYS] ?: "1,2,3,4,5").split(",")
                .mapNotNull { it.trim().toIntOrNull() }.toSet(),
            distanceFilterM = d[K.DIST_FILTER] ?: 25,
            minIntervalS = d[K.MIN_INTERVAL] ?: 30,
            idleIntervalS = d[K.IDLE_INTERVAL] ?: 180,
            maxAccuracyM = d[K.MAX_ACCURACY] ?: 100,
            syncIntervalS = d[K.SYNC_INTERVAL] ?: 120,
            syncBatchMax = d[K.SYNC_BATCH] ?: 200,
        )
    }

    // ------------------------------------------------------------ kalendar

    suspend fun saveCalendar(holidays: List<String>, extraWorkdays: List<String>) {
        context.dataStore.edit {
            it[K.HOLIDAYS] = holidays.joinToString(",")
            it[K.EXTRA_WORKDAYS] = extraWorkdays.joinToString(",")
        }
    }

    suspend fun holidays(): Set<String> =
        (context.dataStore.data.first()[K.HOLIDAYS] ?: "").split(",").filter { it.isNotBlank() }.toSet()

    suspend fun extraWorkdays(): Set<String> =
        (context.dataStore.data.first()[K.EXTRA_WORKDAYS] ?: "").split(",").filter { it.isNotBlank() }.toSet()

    // ------------------------------------------------------------ holat

    suspend fun markSync() = context.dataStore.edit { it[K.LAST_SYNC] = System.currentTimeMillis() }.let { }
    suspend fun markPoint() = context.dataStore.edit { it[K.LAST_POINT] = System.currentTimeMillis() }.let { }
    suspend fun markServiceAlive() =
        context.dataStore.edit { it[K.SERVICE_ALIVE] = System.currentTimeMillis() }.let { }

    suspend fun lastServiceAlive(): Long = context.dataStore.data.first()[K.SERVICE_ALIVE] ?: 0L

    val runtime: Flow<Runtime> = context.dataStore.data.map {
        Runtime(
            lastSync = it[K.LAST_SYNC] ?: 0L,
            lastPoint = it[K.LAST_POINT] ?: 0L,
        )
    }

    // ------------------------------------------------------------ discovery

    suspend fun saveDiscoveredEndpoint(api: String, s3: String) {
        context.dataStore.edit {
            it[K.DISCOVERED_API] = api
            if (s3.isNotBlank()) it[K.DISCOVERED_S3] = s3
        }
    }

    suspend fun discoveredApi(): String? = context.dataStore.data.first()[K.DISCOVERED_API]

    suspend fun wipe() = context.dataStore.edit { it.clear() }.let { }
}

data class Profile(val fio: String, val orgName: String, val jobName: String, val status: String)
data class Runtime(val lastSync: Long, val lastPoint: Long)

data class TrackingConfig(
    val workStart: String,
    val workEnd: String,
    val workDays: Set<Int>,
    val distanceFilterM: Int,
    val minIntervalS: Int,
    val idleIntervalS: Int,
    val maxAccuracyM: Int,
    val syncIntervalS: Int,
    val syncBatchMax: Int,
)
