package io.github.michael3dprint.mybambuddy.platform.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import io.github.michael3dprint.mybambuddy.MainActivity
import io.github.michael3dprint.mybambuddy.R
import io.github.michael3dprint.mybambuddy.domain.widget.WidgetRenderer
import io.github.michael3dprint.mybambuddy.domain.widget.WidgetRow
import io.github.michael3dprint.mybambuddy.domain.widget.WidgetSnapshot
import io.github.michael3dprint.mybambuddy.domain.widget.WidgetSnapshotMapper
import io.github.michael3dprint.mybambuddy.domain.widget.WidgetSpeech
import io.github.michael3dprint.mybambuddy.ui.navigation.DeepLinks

/** Draws the snapshot with [RemoteViews]. Every tap target is an explicit, immutable intent carrying at most a printer number. */
class AndroidWidgetRenderer(private val context: Context) : WidgetRenderer {
    private val rowIds = listOf(
        RowViews(R.id.row1, R.id.name1, R.id.state1, R.id.bar1, R.id.time1),
        RowViews(R.id.row2, R.id.name2, R.id.state2, R.id.bar2, R.id.time2),
        RowViews(R.id.row3, R.id.name3, R.id.state3, R.id.bar3, R.id.time3),
    )

    private class RowViews(val row: Int, val name: Int, val state: Int, val bar: Int, val time: Int)

    override fun render(snapshot: WidgetSnapshot?) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, PrinterWidgetProvider::class.java))
        if (ids.isEmpty()) return
        val views = RemoteViews(context.packageName, R.layout.widget_printers)
        val rows = snapshot?.rows.orEmpty()
        rowIds.forEachIndexed { index, slot -> bind(views, slot, rows.getOrNull(index)) }
        val stamp = snapshot?.let { WidgetSnapshotMapper.stamp(it) }.orEmpty()
        views.setTextViewText(R.id.stamp, stamp.ifEmpty { context.getString(R.string.widget_empty) })
        views.setOnClickPendingIntent(R.id.title, openApp(null, REQUEST_APP))
        views.setOnClickPendingIntent(R.id.refresh, refresh())
        manager.updateAppWidget(ids, views)
    }

    private fun bind(views: RemoteViews, slot: RowViews, row: WidgetRow?) {
        if (row == null) {
            views.setViewVisibility(slot.row, View.GONE)
            return
        }
        views.setViewVisibility(slot.row, View.VISIBLE)
        // One spoken summary per row; the texts inside are hidden from the reader in the layout (design 34).
        views.setContentDescription(slot.row, WidgetSpeech.row(row))
        views.setTextViewText(slot.name, row.name)
        views.setTextViewText(slot.state, row.stateText)
        views.setTextViewText(slot.time, row.timeLeft)
        if (row.progress >= 0) {
            views.setViewVisibility(slot.bar, View.VISIBLE)
            views.setProgressBar(slot.bar, PROGRESS_MAX, row.progress, false)
        } else {
            views.setViewVisibility(slot.bar, View.GONE)
        }
        views.setOnClickPendingIntent(slot.row, openApp(row.id.toIntOrNull(), slot.row))
    }

    private fun openApp(printerId: Int?, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        printerId?.takeIf { it > 0 }?.let { intent.putExtra(DeepLinks.EXTRA_PRINTER_ID, it) }
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun refresh(): PendingIntent {
        val intent = Intent(context, PrinterWidgetProvider::class.java).setAction(PrinterWidgetProvider.ACTION_REFRESH)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_REFRESH,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private companion object {
        const val PROGRESS_MAX = 100
        const val REQUEST_APP = 1
        const val REQUEST_REFRESH = 2
    }
}
