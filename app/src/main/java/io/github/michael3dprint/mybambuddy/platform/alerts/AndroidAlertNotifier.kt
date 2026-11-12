package io.github.michael3dprint.mybambuddy.platform.alerts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.michael3dprint.mybambuddy.MainActivity
import io.github.michael3dprint.mybambuddy.R
import io.github.michael3dprint.mybambuddy.domain.alerts.Alert
import io.github.michael3dprint.mybambuddy.domain.alerts.AlertNotifier
import io.github.michael3dprint.mybambuddy.domain.alerts.AlertType
import io.github.michael3dprint.mybambuddy.ui.navigation.DeepLinks

/**
 * Shows alerts as notifications, one channel per category so each can be muted in Android settings.
 * Notification text never contains the server address or API key. A tap opens the printer through an explicit,
 * immutable PendingIntent carrying only a numeric printer or spool id.
 */
class AndroidAlertNotifier(private val context: Context) : AlertNotifier {
    init {
        createChannels()
    }

    override fun post(alert: Alert): Boolean {
        if (!canPost()) return false
        val open = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        alert.printerId?.let { open.putExtra(DeepLinks.EXTRA_PRINTER_ID, it) }
        alert.spoolId?.let { open.putExtra(DeepLinks.EXTRA_SPOOL_ID, it) }
        val pending = PendingIntent.getActivity(
            context,
            alert.key.hashCode(),
            open,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, channelFor(alert.type))
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(alert.title)
            .setContentText(alert.text)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        return notify(alert.key.hashCode(), notification)
    }

    override fun postSummary(count: Int) {
        if (!canPost()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_PRINTS)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            // Android already shows the app's own name in the notification shade, so the title describes the
            // content instead.
            .setContentTitle("More alerts")
            .setContentText("$count more alerts. Open the app to see everything.")
            .setAutoCancel(true)
            .build()
        notify(SUMMARY_ID, notification)
    }

    private fun notify(id: Int, notification: android.app.Notification): Boolean {
        if (!canPost()) return false
        @Suppress("MissingPermission") // canPost() checked the permission just above
        NotificationManagerCompat.from(context).notify(id, notification)
        return true
    }

    private fun canPost(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun channelFor(type: AlertType): String = when (type) {
        AlertType.PRINT_FINISHED, AlertType.PRINT_FAILED -> CHANNEL_PRINTS
        AlertType.NEW_ERROR, AlertType.PRINTER_OFFLINE -> CHANNEL_ERRORS
        AlertType.LOW_FILAMENT -> CHANNEL_FILAMENT
        AlertType.MAINTENANCE_DUE -> CHANNEL_MAINTENANCE
    }

    private fun createChannels() {
        val manager = context.getSystemService(NotificationManager::class.java)
        listOf(
            CHANNEL_PRINTS to "Print results",
            CHANNEL_ERRORS to "Printer errors and offline",
            CHANNEL_FILAMENT to "Filament",
            CHANNEL_MAINTENANCE to "Maintenance",
        ).forEach { (id, name) -> manager.createNotificationChannel(NotificationChannel(id, name, NotificationManager.IMPORTANCE_DEFAULT)) }
    }

    private companion object {
        const val CHANNEL_PRINTS = "prints"
        const val CHANNEL_ERRORS = "errors"
        const val CHANNEL_FILAMENT = "filament"
        const val CHANNEL_MAINTENANCE = "maintenance"
        const val SUMMARY_ID = 1
    }
}
