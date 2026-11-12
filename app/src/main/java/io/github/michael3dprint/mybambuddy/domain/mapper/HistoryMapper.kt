package io.github.michael3dprint.mybambuddy.domain.mapper

import io.github.michael3dprint.mybambuddy.data.api.dto.ArchiveItemDto
import io.github.michael3dprint.mybambuddy.domain.model.HistoryItem
import io.github.michael3dprint.mybambuddy.domain.model.HistoryStatus
import io.github.michael3dprint.mybambuddy.util.PrintDeckLogger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * Maps an archive row to a [HistoryItem]. Missing fields get safe defaults; a row that cannot be mapped still gives an item
 * (status unknown) instead of throwing.
 */
fun ArchiveItemDto.toHistoryItem(): HistoryItem = try {
    HistoryItem(
        id = id ?: UUID.randomUUID().toString(),
        name = effectiveName,
        printerName = effectivePrinterName,
        status = HistoryStatus.fromString(effectiveStatus),
        formattedDate = formatHistoryDate(effectiveDate),
        formattedDuration = formatHistoryDuration(effectiveDurationSeconds),
        thumbnailUrl = effectiveThumbnailUrl,
        printerId = printerId?.toIntOrNull(),
    )
} catch (e: Exception) {
    PrintDeckLogger.e("PrintDeckTrace", "Failed to map ArchiveItemDto to HistoryItem", e)
    HistoryItem(
        id = id ?: UUID.randomUUID().toString(),
        name = effectiveName,
        printerName = effectivePrinterName,
        status = HistoryStatus.UNKNOWN,
        formattedDate = "Unknown date",
        formattedDuration = "--",
    )
}

/**
 * Turns an archive timestamp (epoch seconds, epoch milliseconds or ISO text) into a short local date, or "Unknown date" when it is
 * blank or unreadable.
 */
fun formatHistoryDate(dateStr: String?): String {
    if (dateStr.isNullOrBlank()) return "Unknown date"

    val epoch = dateStr.toLongOrNull()
    if (epoch != null) {
        val millis = if (epoch < 10_000_000_000L) epoch * 1000L else epoch
        val date = Date(millis)
        val sdf = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault())
        return sdf.format(date)
    }

    val instant = runCatching { java.time.OffsetDateTime.parse(dateStr).toInstant() }.getOrNull()
        ?: runCatching {
            java.time.LocalDateTime.parse(dateStr.replace(' ', 'T')).toInstant(java.time.ZoneOffset.UTC)
        }.getOrNull()
    if (instant != null) {
        return java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a")
            .withZone(java.time.ZoneId.systemDefault()).format(instant)
    }

    val isoFormats = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd HH:mm",
    )
    for (format in isoFormats) {
        try {
            val sdfInput = SimpleDateFormat(format, Locale.US)
            sdfInput.timeZone = TimeZone.getTimeZone("UTC")
            val parsedDate = sdfInput.parse(dateStr)
            if (parsedDate != null) {
                val sdfOutput = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault())
                return sdfOutput.format(parsedDate)
            }
        } catch (_: Exception) {}
    }

    return dateStr
}

/** Formats a print duration as hours and minutes, minutes and seconds, or seconds; "--" when it is missing or zero. */
fun formatHistoryDuration(seconds: Long?): String {
    if (seconds == null || seconds <= 0L) return "--"
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remainingSecs = seconds % 60

    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${remainingSecs}s"
        else -> "${remainingSecs}s"
    }
}
