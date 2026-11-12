package io.github.michael3dprint.mybambuddy.data.repo

/** How old a saved copy may be before it is ignored, chosen from how fast the data really changes (design 16, F5). */
object CacheAges {
    private const val HOUR_MS = 60L * 60 * 1000
    private const val DAY_MS = 24 * HOUR_MS

    /** Printer state, AMS contents and the queue: older than an hour is misleading. */
    const val LIVE_STATE = HOUR_MS

    /** Finished prints do not change. */
    const val ARCHIVE = 30 * DAY_MS

    /** The printer list is re-emitted every poll; save it at most this often. */
    const val PRINTER_WRITE_GAP = 30_000L
}
