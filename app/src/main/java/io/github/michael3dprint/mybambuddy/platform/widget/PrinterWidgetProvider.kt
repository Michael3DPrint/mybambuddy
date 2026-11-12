package io.github.michael3dprint.mybambuddy.platform.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.michael3dprint.mybambuddy.PrintDeckApp
import java.util.concurrent.TimeUnit

/**
 * The home-screen widget. It never reads the server itself: it draws the saved snapshot, and the refresh button or the
 * 15-minute worker run [WidgetUpdateWorker], which only reads. There are no control buttons.
 */
class PrinterWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val container = (context.applicationContext as PrintDeckApp).container
        container.widgetRenderer.render(container.widgetStore.load())
    }

    override fun onEnabled(context: Context) {
        WidgetScheduler.start(context)
    }

    override fun onDisabled(context: Context) {
        WidgetScheduler.stop(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) WidgetScheduler.refreshNow(context)
    }

    companion object {
        /** The refresh button's action. It only schedules the same read-only refresh. */
        const val ACTION_REFRESH = "io.github.michael3dprint.mybambuddy.widget.REFRESH"
    }
}

/** Background entry point for the widget: one read-only refresh. */
class WidgetUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        try {
            (applicationContext as PrintDeckApp).container.widgetRefresher.refresh()
        } catch (e: Exception) {
            // Never crash the background job; the next run tries again.
        }
        return Result.success()
    }
}

/** Starts and stops the widget's refreshes. */
object WidgetScheduler {
    private const val PERIODIC = "mybambuddy-widget"
    private const val ONCE = "mybambuddy-widget-now"
    private const val PERIOD_MINUTES = 15L

    private val needsNetwork = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Every 15 minutes while the phone has a network, and once right away. */
    fun start(context: Context) {
        val request = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(PERIOD_MINUTES, TimeUnit.MINUTES).setConstraints(needsNetwork).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
        refreshNow(context)
    }

    /** Stops the refreshes (the last widget was removed). */
    fun stop(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
    }

    /** One refresh as soon as there is a network. A second tap while one is waiting does nothing. */
    fun refreshNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<WidgetUpdateWorker>().setConstraints(needsNetwork).build()
        WorkManager.getInstance(context).enqueueUniqueWork(ONCE, ExistingWorkPolicy.KEEP, request)
    }
}
