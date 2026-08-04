package uz.bekobod.faolmfy.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object WorkScheduler {

    private const val SYNC = "faolmfy_sync"
    private const val WATCHDOG = "faolmfy_watchdog"
    private const val ATTACH = "faolmfy_attachments"
    private const val ATTACH_NOW = "faolmfy_attachments_now"

    fun scheduleAll(context: Context) {
        val wm = WorkManager.getInstance(context)

        // Zaxira sinxron — faqat internet bo'lganda
        wm.enqueueUniquePeriodicWork(
            SYNC,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
        )

        // Watchdog — internetsiz ham ishlashi kerak (servisni qayta yoqish uchun)
        wm.enqueueUniquePeriodicWork(
            WATCHDOG,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<WatchdogWorker>(15, TimeUnit.MINUTES).build()
        )

        // Rasm va izohlar — faqat internet bo'lganda
        wm.enqueueUniquePeriodicWork(
            ATTACH,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<AttachmentWorker>(20, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()
        )
    }

    /**
     * Rasm olingandan keyin darhol chaqiriladi. Internet bo'lsa bir necha
     * soniyada yuklanadi, bo'lmasa WorkManager o'zi kutadi.
     */
    fun uploadAttachmentsNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            ATTACH_NOW,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<AttachmentWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
        )
    }

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).apply {
            cancelUniqueWork(SYNC)
            cancelUniqueWork(WATCHDOG)
            cancelUniqueWork(ATTACH)
        }
    }
}
