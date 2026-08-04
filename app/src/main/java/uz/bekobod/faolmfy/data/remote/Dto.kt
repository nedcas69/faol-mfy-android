package uz.bekobod.faolmfy.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---------------------------------------------------------------- Auth

@Serializable
data class ActivateRequest(
    val code: String,
    val fio: String,
    val phone: String? = null,
    @SerialName("org_unit_id") val orgUnitId: Int,
    @SerialName("job_title_id") val jobTitleId: Int,
    @SerialName("device_id") val deviceId: String,
    @SerialName("device_manufacturer") val deviceManufacturer: String? = null,
    @SerialName("device_model") val deviceModel: String? = null,
    @SerialName("android_version") val androidVersion: String? = null,
    @SerialName("app_version") val appVersion: String? = null,
)

@Serializable
data class RefreshRequest(@SerialName("refresh_token") val refreshToken: String)

@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in_days") val expiresInDays: Int,
    val user: MeDto,
)

@Serializable
data class MeDto(
    val id: Int,
    val fio: String,
    val role: String,
    val status: String,
    @SerialName("org_unit_id") val orgUnitId: Int? = null,
    @SerialName("org_unit_name") val orgUnitName: String? = null,
    @SerialName("org_path") val orgPath: List<String> = emptyList(),
    @SerialName("job_title_id") val jobTitleId: Int? = null,
    @SerialName("job_title_name") val jobTitleName: String? = null,
)

// ---------------------------------------------------------------- Refs

@Serializable
data class OrgUnitDto(
    val id: Int,
    @SerialName("name_uz") val nameUz: String,
    val level: Int,
    @SerialName("parent_id") val parentId: Int? = null,
    @SerialName("soato_code") val soatoCode: String? = null,
    @SerialName("has_geom") val hasGeom: Boolean = false,
)

@Serializable
data class JobTitleDto(
    val id: Int,
    val code: String,
    @SerialName("name_uz") val nameUz: String,
    val agency: String? = null,
    @SerialName("external_agency") val externalAgency: Boolean = false,
)

@Serializable
data class CalendarDayDto(
    val day: String,
    val kind: String,
    val name: String? = null,
)

/** Kuzatuv parametrlari serverdan keladi — APK qayta yig'ish shart emas (TZ). */
@Serializable
data class TrackingConfigDto(
    @SerialName("work_start") val workStart: String,
    @SerialName("work_end") val workEnd: String,
    @SerialName("work_days") val workDays: List<Int>,
    @SerialName("distance_filter_m") val distanceFilterM: Int,
    @SerialName("min_interval_s") val minIntervalS: Int,
    @SerialName("idle_interval_s") val idleIntervalS: Int,
    @SerialName("max_accuracy_m") val maxAccuracyM: Int,
    @SerialName("sync_interval_s") val syncIntervalS: Int,
    @SerialName("sync_batch_max") val syncBatchMax: Int,
    @SerialName("photo_max_side_px") val photoMaxSidePx: Int = 1280,
    @SerialName("photo_quality") val photoQuality: Int = 80,
    @SerialName("heartbeat_warn_min") val heartbeatWarnMin: Int = 30,
)

// ---------------------------------------------------------------- Sync

@Serializable
data class PositionDto(
    val ts: String,
    val lat: Double,
    val lon: Double,
    val accuracy: Float? = null,
    val speed: Float? = null,
    val bearing: Float? = null,
    val altitude: Double? = null,
    val battery: Int? = null,
    @SerialName("is_charging") val isCharging: Boolean? = null,
    @SerialName("is_mock") val isMock: Boolean = false,
    val provider: String? = null,
    val activity: String? = null,
    @SerialName("step_delta") val stepDelta: Int? = null,
    @SerialName("client_seq") val clientSeq: Long,
)

@Serializable
data class PositionBatchRequest(
    @SerialName("device_id") val deviceId: String,
    val points: List<PositionDto>,
)

@Serializable
data class PositionBatchResponse(
    val accepted: Int,
    val duplicates: Int,
    val rejected: Int,
    @SerialName("server_time") val serverTime: String,
    @SerialName("last_client_seq") val lastClientSeq: Long? = null,
)

@Serializable
data class DeviceEventDto(
    val ts: String,
    val type: String,
    val details: Map<String, String>? = null,
    val manufacturer: String? = null,
    val model: String? = null,
    @SerialName("android_version") val androidVersion: String? = null,
    @SerialName("app_version") val appVersion: String? = null,
)

@Serializable
data class DeviceEventBatchRequest(
    @SerialName("device_id") val deviceId: String,
    val events: List<DeviceEventDto>,
)

@Serializable
data class PermsStateRequest(
    @SerialName("fine_location") val fineLocation: Boolean = false,
    @SerialName("background_location") val backgroundLocation: Boolean = false,
    val notifications: Boolean = false,
    @SerialName("activity_recognition") val activityRecognition: Boolean = false,
    @SerialName("battery_unrestricted") val batteryUnrestricted: Boolean = false,
    @SerialName("autostart_confirmed") val autostartConfirmed: Boolean = false,
    val manufacturer: String? = null,
)

@Serializable
data class SyncStatusResponse(
    @SerialName("server_time") val serverTime: String,
    val today: String,
    @SerialName("day_kind") val dayKind: String,
    @SerialName("day_name") val dayName: String? = null,
    @SerialName("tracking_enabled") val trackingEnabled: Boolean,
    @SerialName("points_today") val pointsToday: Int,
)

// ---------------------------------------------------------------- Me

@Serializable
data class StopDto(
    val id: Long,
    @SerialName("started_at") val startedAt: String,
    @SerialName("ended_at") val endedAt: String,
    @SerialName("duration_s") val durationS: Int,
    val lat: Double,
    val lon: Double,
    val address: String? = null,
    @SerialName("inside_mfy") val insideMfy: Boolean,
    val note: String? = null,
    @SerialName("photo_count") val photoCount: Int = 0,
    @SerialName("photo_ids") val photoIds: List<Long> = emptyList(),
)

@Serializable
data class DayEventDto(
    val type: String,
    val severity: String,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("ended_at") val endedAt: String? = null,
)

@Serializable
data class DayResponse(
    val day: String,
    val kind: String,
    @SerialName("day_status") val dayStatus: String,
    val total: Double,
    @SerialName("score_inside") val scoreInside: Double,
    @SerialName("score_coverage") val scoreCoverage: Double,
    @SerialName("score_stops") val scoreStops: Double,
    @SerialName("score_discipline") val scoreDiscipline: Double,
    @SerialName("tracked_s") val trackedS: Int,
    @SerialName("gap_s") val gapS: Int,
    @SerialName("inside_s") val insideS: Int,
    @SerialName("distance_m") val distanceM: Double,
    @SerialName("cells_total") val cellsTotal: Int,
    @SerialName("cells_visited") val cellsVisited: Int,
    @SerialName("stops_count") val stopsCount: Int,
    @SerialName("documented_stops") val documentedStops: Int,
    val stops: List<StopDto> = emptyList(),
    val events: List<DayEventDto> = emptyList(),
)

@Serializable
data class NoteRequest(val note: String)

// ---------------------------------------------------------------- Rasmlar

@Serializable
data class PhotoPresignRequest(
    @SerialName("taken_at") val takenAt: String,
    val filename: String,
    @SerialName("content_type") val contentType: String = "image/jpeg",
)

@Serializable
data class PhotoPresignResponse(
    @SerialName("object_key") val objectKey: String,
    @SerialName("upload_url") val uploadUrl: String,
    @SerialName("expires_in") val expiresIn: Int,
)

@Serializable
data class PhotoConfirmRequest(
    @SerialName("object_key") val objectKey: String,
    @SerialName("taken_at") val takenAt: String,
    val lat: Double? = null,
    val lon: Double? = null,
    @SerialName("size_bytes") val sizeBytes: Int? = null,
)

@Serializable
data class PhotoResponse(
    val id: Long,
    @SerialName("stop_id") val stopId: Long? = null,
    @SerialName("taken_at") val takenAt: String? = null,
    @SerialName("object_key") val objectKey: String,
    @SerialName("delete_after") val deleteAfter: String,
    @SerialName("file_deleted") val fileDeleted: Boolean = false,
    @SerialName("distance_m") val distanceM: Double? = null,
)

@Serializable
data class TimeNoteRequest(
    @SerialName("anchor_ts") val anchorTs: String,
    val note: String,
)

@Serializable
data class PhotoUrlResponse(
    val url: String,
    @SerialName("expires_in") val expiresIn: Int,
)
