package uz.bekobod.faolmfy.sync

import android.content.Context
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import uz.bekobod.faolmfy.data.Prefs
import uz.bekobod.faolmfy.data.local.AppDatabase
import uz.bekobod.faolmfy.data.local.NoteQueueEntity
import uz.bekobod.faolmfy.data.local.PhotoQueueEntity
import uz.bekobod.faolmfy.data.remote.ApiClient
import uz.bekobod.faolmfy.data.remote.PhotoConfirmRequest
import uz.bekobod.faolmfy.data.remote.PhotoPresignRequest
import uz.bekobod.faolmfy.data.remote.TimeNoteRequest
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Rasm va izohlarni yuklash — TZ F6.4.
 *
 * Rasm uch qadamda ketadi va har qadam bazada belgilanadi:
 *   1. presign  -> objectKey + uploadUrl olinadi
 *   2. PUT      -> fayl to'g'ridan-to'g'ri MinIO ga yuklanadi
 *   3. confirm  -> server metama'lumotni yozadi va 120 kunlik muddat belgilaydi
 *
 * Nima uchun qadamlar alohida saqlanadi: mahallada internet uzilib turadi.
 * Agar 5 MB fayl 90% yuklanib uzilsa, keyingi urinishda presign qaytadan
 * so'ralmaydi — faqat PUT takrorlanadi.
 */
class AttachmentManager(private val context: Context) {

    companion object {
        private const val TAG = "AttachmentManager"
        private const val MAX_ATTEMPTS = 20
    }

    private val db = AppDatabase.get(context)
    private val prefs = Prefs(context)
    private suspend fun api() = ApiClient.getReady(context)

    private val uploadClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)   // sekin tarmoq uchun
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    // ------------------------------------------------------------ navbatga qo'shish

    /** Kameradan kelgan rasmni navbatga qo'yadi. Yuklash keyin bo'ladi. */
    suspend fun enqueuePhoto(
        file: File,
        takenAt: Long,
        lat: Double?,
        lon: Double?,
        sizeBytes: Int,
    ): Long = db.photoQueue().insert(
        PhotoQueueEntity(
            localPath = file.absolutePath,
            takenAt = takenAt,
            lat = lat,
            lon = lon,
            sizeBytes = sizeBytes,
        )
    )

    /** Izohni navbatga qo'yadi (langar — to'xtashning boshlanish vaqti). */
    suspend fun enqueueNote(anchorTs: Long, text: String) {
        db.noteQueue().upsert(
            NoteQueueEntity(
                anchorTs = anchorTs,
                text = text,
                updatedAt = System.currentTimeMillis(),
                synced = false,
            )
        )
    }

    suspend fun localNote(anchorTs: Long): String? =
        db.noteQueue().byAnchor(anchorTs)?.text

    // ------------------------------------------------------------ yuklash

    suspend fun flush(): Boolean {
        var allOk = flushNotes()
        if (!flushPhotos()) allOk = false
        purge()
        return allOk
    }

    private suspend fun flushNotes(): Boolean {
        val pending = db.noteQueue().pending()
        if (pending.isEmpty()) return true
        var ok = true
        val done = mutableListOf<Long>()
        for (n in pending) {
            val sent = try {
                api().pushNote(
                    TimeNoteRequest(anchorTs = SyncManager.iso(n.anchorTs), note = n.text)
                ).isSuccessful
            } catch (e: Exception) {
                Log.w(TAG, "Izoh yuborilmadi: ${e.message}")
                false
            }
            if (sent) done += n.anchorTs else ok = false
        }
        if (done.isNotEmpty()) db.noteQueue().markSynced(done)
        return ok
    }

    private suspend fun flushPhotos(): Boolean {
        val pending = db.photoQueue().pending()
        if (pending.isEmpty()) return true
        var ok = true

        for (photo in pending) {
            val file = File(photo.localPath)
            if (!file.exists()) {
                // Fayl yo'q — navbatdan olib tashlaymiz, aks holda abadiy urinadi
                Log.w(TAG, "Fayl topilmadi, navbatdan chiqarildi: ${photo.localPath}")
                db.photoQueue().update(photo.copy(uploaded = true, lastError = "file_missing"))
                continue
            }
            if (!uploadOne(photo, file)) ok = false
        }
        return ok
    }

    private suspend fun uploadOne(photo: PhotoQueueEntity, file: File): Boolean {
        var current = photo
        try {
            // --- 1-qadam: presign ---
            if (current.objectKey == null || current.uploadUrl == null) {
                val resp = api().presignPhoto(
                    PhotoPresignRequest(
                        takenAt = SyncManager.iso(current.takenAt),
                        filename = file.name,
                    )
                )
                if (!resp.isSuccessful) {
                    // 400 — rasm ish vaqtidan tashqarida yoki juda eski.
                    // Bu qayta urinishda tuzalmaydi, shuning uchun to'xtatamiz.
                    val fatal = resp.code() in 400..499 && resp.code() != 429
                    current = current.copy(
                        attempts = current.attempts + 1,
                        lastError = "presign ${resp.code()}",
                        uploaded = fatal,
                    )
                    db.photoQueue().update(current)
                    if (fatal) Log.w(TAG, "Rasm rad etildi (${resp.code()}): ${file.name}")
                    return false
                }
                val body = resp.body()!!
                current = current.copy(objectKey = body.objectKey, uploadUrl = body.uploadUrl)
                db.photoQueue().update(current)
            }

            // --- 2-qadam: faylni MinIO ga PUT ---
            if (!current.putDone) {
                val request = Request.Builder()
                    .url(current.uploadUrl!!)
                    .put(file.asRequestBody("image/jpeg".toMediaType()))
                    .build()
                val success = uploadClient.newCall(request).execute().use { it.isSuccessful }
                if (!success) {
                    // Presigned URL 15 daqiqada eskiradi — tozalab qaytadan olamiz
                    current = current.copy(
                        objectKey = null, uploadUrl = null,
                        attempts = current.attempts + 1, lastError = "put_failed",
                    )
                    db.photoQueue().update(current)
                    return false
                }
                current = current.copy(putDone = true)
                db.photoQueue().update(current)
            }

            // --- 3-qadam: confirm ---
            val confirm = api().confirmPhoto(
                PhotoConfirmRequest(
                    objectKey = current.objectKey!!,
                    takenAt = SyncManager.iso(current.takenAt),
                    lat = current.lat,
                    lon = current.lon,
                    sizeBytes = current.sizeBytes,
                )
            )
            if (!confirm.isSuccessful) {
                current = current.copy(
                    attempts = current.attempts + 1,
                    lastError = "confirm ${confirm.code()}",
                )
                db.photoQueue().update(current)
                return false
            }

            db.photoQueue().update(current.copy(uploaded = true, lastError = null))
            prefs.markSync()
            Log.i(TAG, "Rasm yuklandi: ${file.name}")
            return true

        } catch (e: Exception) {
            Log.w(TAG, "Rasm yuklash xatosi: ${e.message}")
            db.photoQueue().update(
                current.copy(attempts = current.attempts + 1, lastError = e.message?.take(120))
            )
            return false
        }
    }

    /**
     * Yuklangan rasmlarning lokal nusxasini o'chiradi (48 soatdan keyin).
     * Xodim kun oxirida rasmini ko'rishi mumkin bo'lishi uchun darhol o'chirmaymiz.
     */
    private suspend fun purge() {
        val cutoff = System.currentTimeMillis() - 48 * 3600_000L
        val old = db.photoQueue().uploadedBefore(cutoff)
        if (old.isEmpty()) return
        old.forEach { runCatching { File(it.localPath).delete() } }
        db.photoQueue().deleteByIds(old.map { it.id })
        db.noteQueue().purge(cutoff)
        Log.i(TAG, "Lokal rasmlar tozalandi: ${old.size}")
    }
}
