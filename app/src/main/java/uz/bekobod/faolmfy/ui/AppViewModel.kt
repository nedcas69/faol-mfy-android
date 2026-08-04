package uz.bekobod.faolmfy.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uz.bekobod.faolmfy.BuildConfig
import uz.bekobod.faolmfy.data.Prefs
import uz.bekobod.faolmfy.data.Profile
import uz.bekobod.faolmfy.data.local.AppDatabase
import uz.bekobod.faolmfy.data.remote.ActivateRequest
import uz.bekobod.faolmfy.data.remote.ApiClient
import uz.bekobod.faolmfy.data.remote.DayResponse
import uz.bekobod.faolmfy.data.remote.JobTitleDto
import uz.bekobod.faolmfy.data.remote.OrgUnitDto
import uz.bekobod.faolmfy.data.remote.PermsStateRequest
import uz.bekobod.faolmfy.location.AlarmScheduler
import uz.bekobod.faolmfy.location.TrackingService
import uz.bekobod.faolmfy.sync.AttachmentManager
import uz.bekobod.faolmfy.sync.SyncManager
import uz.bekobod.faolmfy.sync.WorkScheduler
import uz.bekobod.faolmfy.util.DeviceInfo
import uz.bekobod.faolmfy.util.ImageUtils
import uz.bekobod.faolmfy.util.WorkWindow
import uz.bekobod.faolmfy.ui.screens.LocalPhoto
import java.io.File
import java.time.Instant
import java.time.LocalDate

enum class Screen { LOADING, ACTIVATION, PENDING, WIZARD, HOME }

data class UiState(
    val screen: Screen = Screen.LOADING,
    val loading: Boolean = false,
    val error: String? = null,
    val profile: Profile = Profile("", "", "", ""),
    val today: DayResponse? = null,
    val pendingPoints: Int = 0,
    val pendingAttachments: Int = 0,
    val photosByStop: Map<String, List<LocalPhoto>> = emptyMap(),
    val notesByAnchor: Map<String, String> = emptyMap(),
    val toast: String? = null,
    val trackingActive: Boolean = false,
    val isWorkday: Boolean = true,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(app)
    private suspend fun api() = ApiClient.getReady(getApplication())
    private val db = AppDatabase.get(app)
    private val attachments = AttachmentManager(app)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    // Ro'yxatdan o'tish uchun spravochniklar
    private val _regions = MutableStateFlow<List<OrgUnitDto>>(emptyList())
    val regions: StateFlow<List<OrgUnitDto>> = _regions.asStateFlow()
    private val _districts = MutableStateFlow<List<OrgUnitDto>>(emptyList())
    val districts: StateFlow<List<OrgUnitDto>> = _districts.asStateFlow()
    private val _mfys = MutableStateFlow<List<OrgUnitDto>>(emptyList())
    val mfys: StateFlow<List<OrgUnitDto>> = _mfys.asStateFlow()
    private val _jobs = MutableStateFlow<List<JobTitleDto>>(emptyList())
    val jobs: StateFlow<List<JobTitleDto>> = _jobs.asStateFlow()

    init {
        viewModelScope.launch {
            // Avval backend manzilini aniqlaymiz (GitHub discovery).
            // Bu api klientni to'g'ri manzil bilan qayta quradi.
            runCatching { ApiClient.getReady(getApplication()) }
            decideStartScreen()
        }
        viewModelScope.launch {
            db.positions().pendingCountFlow().collect { n ->
                _state.value = _state.value.copy(pendingPoints = n)
            }
        }
        viewModelScope.launch {
            prefs.profile.collect { p -> _state.value = _state.value.copy(profile = p) }
        }
        viewModelScope.launch {
            db.photoQueue().pendingCountFlow().collect { n ->
                _state.value = _state.value.copy(pendingAttachments = n)
            }
        }
    }

    // ------------------------------------------------------ rasm va izoh

    /**
     * Kamera qaytargan faylni siqib navbatga qo'yadi.
     *
     * Rasm joyi sifatida HOZIRGI GPS nuqtasi yoziladi: rasm tizim kamerasi
     * orqali bizning faylimizga yozilgani uchun galereyadan tanlash imkoni
     * yo'q, ya'ni "eski rasm qo'yish" allaqachon oldini olingan. Koordinata
     * esa "rasm olayotganda telefon shu joyda edi" faktini qayd etadi.
     */
    fun onPhotoTaken(file: File, anchorTs: String) = viewModelScope.launch {
        val app = getApplication<Application>()
        val cfg = prefs.config()

        val size = ImageUtils.compressInPlace(app, file)
        if (size == null) {
            runCatching { file.delete() }
            _state.value = _state.value.copy(toast = "Rasmni saqlab bo'lmadi")
            return@launch
        }

        // Vaqt: EXIF dan olamiz, bo'lmasa fayl vaqtidan
        val takenAt = ImageUtils.readExifDateMillis(file) ?: System.currentTimeMillis()

        // Joy: EXIF da bo'lsa o'shani, aks holda oxirgi GPS nuqtasini
        val exif = ImageUtils.readExifLatLon(file)
        val point = exif ?: lastKnownPoint()

        attachments.enqueuePhoto(
            file = file,
            takenAt = takenAt,
            lat = point?.first,
            lon = point?.second,
            sizeBytes = size,
        )
        WorkScheduler.uploadAttachmentsNow(app)
        _state.value = _state.value.copy(
            toast = "Rasm saqlandi, internet paydo bo'lganda yuboriladi"
        )
        loadLocalAttachments()
    }

    private suspend fun lastKnownPoint(): Pair<Double, Double>? {
        val ts = db.positions().lastTs() ?: return null
        return db.positions().lastPoint(ts)?.let { it.lat to it.lon }
    }

    fun onNoteSaved(anchorTs: String, note: String) = viewModelScope.launch {
        val millis = runCatching { Instant.parse(anchorTs).toEpochMilli() }.getOrNull()
            ?: runCatching { Instant.parse(anchorTs + "Z").toEpochMilli() }.getOrNull()
        if (millis == null) {
            _state.value = _state.value.copy(toast = "Izoh saqlanmadi: vaqt formati xato")
            return@launch
        }
        attachments.enqueueNote(millis, note.trim())
        WorkScheduler.uploadAttachmentsNow(getApplication())
        _state.value = _state.value.copy(
            notesByAnchor = _state.value.notesByAnchor + (anchorTs to note.trim())
        )
        _state.value = _state.value.copy(toast = "Izoh saqlandi")
    }

    /** Lokal navbatdagi rasmlarni bugungi to'xtashlarga taqsimlaydi. */
    private suspend fun loadLocalAttachments() {
        val day = state.value.today ?: return
        val stops = day.stops
        if (stops.isEmpty()) {
            _state.value = _state.value.copy(photosByStop = emptyMap())
            return
        }
        val dayStart = runCatching {
            LocalDate.parse(day.day).atStartOfDay(WorkWindow.ZONE).toInstant().toEpochMilli()
        }.getOrNull() ?: return

        val queued = db.photoQueue().inRange(dayStart, dayStart + 24 * 3600_000L)
        val map = mutableMapOf<String, MutableList<LocalPhoto>>()
        for (photo in queued) {
            val stop = stops.firstOrNull { s ->
                val from = parseMillis(s.startedAt) ?: return@firstOrNull false
                val to = parseMillis(s.endedAt) ?: return@firstOrNull false
                photo.takenAt in from..to
            } ?: continue
            map.getOrPut(stop.startedAt) { mutableListOf() }.add(
                LocalPhoto(
                    id = photo.id,
                    path = photo.localPath,
                    uploaded = photo.uploaded,
                    failed = photo.attempts >= 5 && !photo.uploaded,
                )
            )
        }
        _state.value = _state.value.copy(photosByStop = map)
    }

    private fun parseMillis(iso: String): Long? =
        runCatching { Instant.parse(iso).toEpochMilli() }.getOrNull()
            ?: runCatching { Instant.parse(iso + "Z").toEpochMilli() }.getOrNull()

    fun clearToast() {
        _state.value = _state.value.copy(toast = null)
    }

    private suspend fun decideStartScreen() {
        val token = prefs.accessToken()
        when {
            token.isNullOrBlank() -> {
                _state.value = _state.value.copy(screen = Screen.ACTIVATION)
                loadRegions()
                loadJobs()
            }
            !prefs.wizardDoneOnce() -> _state.value = _state.value.copy(screen = Screen.WIZARD)
            else -> {
                _state.value = _state.value.copy(screen = Screen.HOME)
                refreshToday()
            }
        }
        refreshConfig()
    }

    private suspend fun Prefs.wizardDoneOnce(): Boolean =
        runCatching { autostartConfirmed() }.getOrDefault(false)

    // ------------------------------------------------------ spravochniklar

    fun loadRegions() = viewModelScope.launch {
        runCatching { api().orgUnits(level = 2) }.getOrNull()?.body()?.let { _regions.value = it }
    }

    fun loadDistricts(regionId: Int) = viewModelScope.launch {
        _districts.value = emptyList(); _mfys.value = emptyList()
        runCatching { api().orgUnits(parentId = regionId) }.getOrNull()?.body()
            ?.let { _districts.value = it }
    }

    fun loadMfys(districtId: Int) = viewModelScope.launch {
        _mfys.value = emptyList()
        runCatching { api().orgUnits(parentId = districtId) }.getOrNull()?.body()
            ?.let { _mfys.value = it }
    }

    fun loadJobs() = viewModelScope.launch {
        runCatching { api().jobTitles() }.getOrNull()?.body()?.let { _jobs.value = it }
    }

    // ------------------------------------------------------ faollashtirish

    fun activate(code: String, fio: String, phone: String, orgUnitId: Int, jobTitleId: Int) =
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val app = getApplication<Application>()
            try {
                val resp = api().activate(
                    ActivateRequest(
                        code = code.trim().uppercase(),
                        fio = fio.trim(),
                        phone = phone.trim().ifBlank { null },
                        orgUnitId = orgUnitId,
                        jobTitleId = jobTitleId,
                        deviceId = prefs.deviceId(),
                        deviceManufacturer = DeviceInfo.manufacturer,
                        deviceModel = DeviceInfo.model,
                        androidVersion = DeviceInfo.androidVersion,
                        appVersion = BuildConfig.VERSION_NAME,
                    )
                )
                when {
                    // 202 — admin tasdig'i kutilmoqda. Retrofit 202 ni ham
                    // "successful" deb hisoblaydi, shuning uchun bu shart
                    // isSuccessful dan OLDIN turishi shart, aks holda body()
                    // TokenResponse deb o'qilib deserializatsiya xatosi chiqadi.
                    resp.code() == 202 -> _state.value = _state.value.copy(
                        loading = false, screen = Screen.PENDING
                    )
                    resp.isSuccessful && resp.body() != null -> {
                        val body = resp.body()!!
                        prefs.saveTokens(body.accessToken, body.refreshToken)
                        prefs.saveProfile(
                            body.user.fio, body.user.orgUnitId,
                            body.user.orgUnitName, body.user.jobTitleName, body.user.status
                        )
                        refreshConfig()
                        WorkScheduler.scheduleAll(app)
                        _state.value = _state.value.copy(loading = false, screen = Screen.WIZARD)
                    }
                    else -> _state.value = _state.value.copy(
                        loading = false, error = readError(resp.errorBody()?.string(), resp.code())
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = "Serverga ulanib bo'lmadi. Internetni tekshiring.\n(${e.message})"
                )
            }
        }

    private fun readError(body: String?, code: Int): String {
        if (body.isNullOrBlank()) return "Xato: $code"
        return runCatching {
            ApiClient.json.parseToJsonElement(body)
                .let { el -> el.toString().substringAfter("\"detail\":\"").substringBefore("\"") }
        }.getOrNull()?.takeIf { it.isNotBlank() && it.length < 300 } ?: "Xato: $code"
    }

    // ------------------------------------------------------ sozlash sehrgari

    fun reportPerms(
        fine: Boolean, background: Boolean, notifications: Boolean,
        activity: Boolean, battery: Boolean, autostart: Boolean,
    ) = viewModelScope.launch {
        runCatching {
            api().pushPerms(
                PermsStateRequest(
                    fineLocation = fine, backgroundLocation = background,
                    notifications = notifications, activityRecognition = activity,
                    batteryUnrestricted = battery, autostartConfirmed = autostart,
                    manufacturer = DeviceInfo.manufacturer,
                )
            )
        }
    }

    fun finishWizard() = viewModelScope.launch {
        val app = getApplication<Application>()
        prefs.setAutostartConfirmed(true)
        prefs.setWizardDone(true)
        WorkScheduler.scheduleAll(app)
        AlarmScheduler.scheduleSuspend(app)

        val cfg = prefs.config()
        if (WorkWindow.isInsideWindow(cfg, prefs.holidays(), prefs.extraWorkdays())) {
            TrackingService.start(app)
        }
        _state.value = _state.value.copy(screen = Screen.HOME)
        refreshToday()
    }

    // ------------------------------------------------------ home

    fun refreshConfig() = viewModelScope.launch {
        runCatching { api().config() }.getOrNull()?.body()?.let { prefs.saveConfig(it) }
        runCatching { api().calendar(LocalDate.now().year) }.getOrNull()?.body()?.let { days ->
            val holidays = days.filter { it.kind != "workday" }.map { it.day }
            val extra = days.filter { it.kind == "workday" }
                .map { it.day }
                .filter { d ->
                    runCatching { LocalDate.parse(d).dayOfWeek.value > 5 }.getOrDefault(false)
                }
            prefs.saveCalendar(holidays, extra)
        }
        updateTrackingFlag()
    }

    private suspend fun updateTrackingFlag() {
        val app = getApplication<Application>()
        val cfg = prefs.config()
        val inside = WorkWindow.isInsideWindow(cfg, prefs.holidays(), prefs.extraWorkdays())
        _state.value = _state.value.copy(
            trackingActive = inside &&
                DeviceInfo.isServiceRunning(app, TrackingService::class.java),
            isWorkday = WorkWindow.isWorkday(
                WorkWindow.today(), cfg, prefs.holidays(), prefs.extraWorkdays()
            ),
        )
    }

    fun refreshToday() = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true)
        runCatching { SyncManager(getApplication()).flush() }
        val day = runCatching { api().today() }.getOrNull()?.body()
        updateTrackingFlag()
        _state.value = _state.value.copy(loading = false, today = day)
        loadLocalAttachments()
    }

    fun syncNow() = viewModelScope.launch {
        runCatching { SyncManager(getApplication()).flush() }
        runCatching { attachments.flush() }
        refreshToday()
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}
