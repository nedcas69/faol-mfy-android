package uz.bekobod.faolmfy.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Rasm va izohlarni yuklash. Alohida worker, chunki:
 *  - internet talab qiladi (nuqtalardan farqli, ular offline yig'iladi)
 *  - fayllar og'ir, uzoq davom etishi mumkin
 *  - muvaffaqiyatsizlikda alohida qayta urinish jadvali kerak
 */
class AttachmentWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        val ok = AttachmentManager(applicationContext).flush()
        if (ok) Result.success() else Result.retry()
    } catch (e: Exception) {
        Result.retry()
    }
}
