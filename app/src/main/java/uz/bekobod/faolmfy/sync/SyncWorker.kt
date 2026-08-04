package uz.bekobod.faolmfy.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Zaxira sinxronizatsiya. Asosiy yuborish servis ichida har 2 daqiqada
 * bo'ladi; bu worker servis o'lgan yoki internet keyin kelgan holatlar uchun.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val ok = SyncManager(applicationContext).flush()
            if (ok) Result.success() else Result.retry()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
