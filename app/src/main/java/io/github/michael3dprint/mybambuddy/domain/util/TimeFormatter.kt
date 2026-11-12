package io.github.michael3dprint.mybambuddy.domain.util

/** Formats seconds left as "Xh Ym left" or "Ym left"; "0m left" when unknown or zero. */
fun formatRemainingTime(seconds: Long?): String {
    if (seconds == null || seconds <= 0L) {
        return "0m left"
    }
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) {
        "${hours}h ${minutes}m left"
    } else {
        "${minutes}m left"
    }
}

/** The local clock time at which the print should finish, or "--" when the time left is unknown or zero. */
fun calculateEstimatedFinishTime(remainingSeconds: Long?, currentTimeMillis: Long = System.currentTimeMillis()): String {
    if (remainingSeconds == null || remainingSeconds <= 0L) {
        return "--"
    }
    val targetTime = currentTimeMillis + (remainingSeconds * 1000L)
    val sdf = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(targetTime))
}
