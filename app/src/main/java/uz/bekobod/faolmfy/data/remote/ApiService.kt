package uz.bekobod.faolmfy.data.remote

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    @POST("api/v1/auth/activate")
    suspend fun activate(@Body body: ActivateRequest): Response<TokenResponse>

    @POST("api/v1/auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): Response<TokenResponse>

    @GET("api/v1/auth/me")
    suspend fun me(): Response<MeDto>

    @GET("api/v1/refs/org-units")
    suspend fun orgUnits(
        @Query("parent_id") parentId: Int? = null,
        @Query("level") level: Int? = null,
    ): Response<List<OrgUnitDto>>

    @GET("api/v1/refs/job-titles")
    suspend fun jobTitles(): Response<List<JobTitleDto>>

    @GET("api/v1/refs/calendar")
    suspend fun calendar(@Query("year") year: Int): Response<List<CalendarDayDto>>

    @GET("api/v1/refs/config")
    suspend fun config(): Response<TrackingConfigDto>

    @POST("api/v1/sync/positions")
    suspend fun pushPositions(@Body body: PositionBatchRequest): Response<PositionBatchResponse>

    @POST("api/v1/sync/device-events")
    suspend fun pushDeviceEvents(@Body body: DeviceEventBatchRequest): Response<Map<String, Int>>

    @POST("api/v1/sync/perms")
    suspend fun pushPerms(@Body body: PermsStateRequest): Response<ResponseBody>

    @GET("api/v1/sync/status")
    suspend fun syncStatus(@Query("device_id") deviceId: String): Response<SyncStatusResponse>

    @GET("api/v1/me/today")
    suspend fun today(): Response<DayResponse>

    @GET("api/v1/me/day/{day}")
    suspend fun day(@Path("day") day: String): Response<DayResponse>

    @PATCH("api/v1/me/stops/{id}/note")
    suspend fun setNote(@Path("id") id: Long, @Body body: NoteRequest): Response<Map<String, Boolean>>

    // ---------------------------------------------------------- rasmlar

    @POST("api/v1/photos/presign")
    suspend fun presignPhoto(@Body body: PhotoPresignRequest): Response<PhotoPresignResponse>

    @POST("api/v1/photos/confirm")
    suspend fun confirmPhoto(@Body body: PhotoConfirmRequest): Response<PhotoResponse>

    @POST("api/v1/photos/note")
    suspend fun pushNote(@Body body: TimeNoteRequest): Response<ResponseBody>

    @GET("api/v1/photos/{id}/url")
    suspend fun photoUrl(@Path("id") id: Long): Response<PhotoUrlResponse>
}
