package io.github.michael3dprint.mybambuddy.platform.alerts

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.michael3dprint.mybambuddy.PrintDeckApp
import java.util.concurrent.TimeUnit

/** Background entry point: one read-only check every ~15 minutes while the phone has a network. */
class AlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        (applicationContext as PrintDeckApp).container.alertRunner.run()
        Result.success()
    } catch (e: Exception) {
        // Never crash the background job; the next scheduled run tries again.
        Result.success()
    }
}

/** Switches the periodic check on or off. */
object AlertScheduler {
    private const val WORK_NAME = "printdeck-alerts"

    fun apply(context: Context, enabled: Boolean) {
        val manager = WorkManager.getInstance(context)
        if (!enabled) {
            manager.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<AlertWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
