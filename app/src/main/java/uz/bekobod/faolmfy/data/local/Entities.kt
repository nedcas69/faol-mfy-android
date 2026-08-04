package uz.bekobod.faolmfy.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Outbox: har bir GPS nuqta avval SHU YERGA yoziladi, keyin yuboriladi.
 * Internet bor-yo'qligidan qat'i nazar kod yo'li bir xil (TZ F5.2).
 */
@Entity(tableName = "positions", indices = [Index("synced"), Index("ts")])
data class PositionEntity(
    @PrimaryKey(autoGenerate = true) val clientSeq: Long = 0,
    val ts: Long,
    val lat: Double,
    val lon: Double,
    val accuracy: Float?,
    val speed: Float?,
    val bearing: Float?,
    val altitude: Double?,
    val battery: Int?,
    val isCharging: Boolean?,
    val isMock: Boolean,
    val provider: String?,
    val activity: String?,
    val stepDelta: Int?,
    val synced: Boolean = false,
)

@Entity(tableName = "device_events", indices = [Index("synced")])
data class DeviceEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long,
    val type: String,
    val detailsJson: String?,
    val synced: Boolean = false,
)

/**
 * Rasm navbati — TZ F6.4.
 *
 * Rasm avval telefonda siqilib saqlanadi, keyin internet paydo bo'lganda
 * uch qadamda yuklanadi: presign -> PUT -> confirm. Har qadam alohida
 * saqlanadi, shuning uchun yarim yo'lda uzilsa qaytadan boshlamaydi.
 */
@Entity(tableName = "photo_queue", indices = [Index("uploaded")])
data class PhotoQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val localPath: String,
    val takenAt: Long,
    val lat: Double?,
    val lon: Double?,
    val sizeBytes: Int,
    val objectKey: String? = null,
    val uploadUrl: String? = null,
    val putDone: Boolean = false,
    val uploaded: Boolean = false,
    val attempts: Int = 0,
    val lastError: String? = null,
)

/** Izoh navbati — internetsiz yozilgan izoh yo'qolmasligi uchun. */
@Entity(tableName = "note_queue", indices = [Index("synced")])
data class NoteQueueEntity(
    @PrimaryKey val anchorTs: Long,
    val text: String,
    val updatedAt: Long,
    val synced: Boolean = false,
)
