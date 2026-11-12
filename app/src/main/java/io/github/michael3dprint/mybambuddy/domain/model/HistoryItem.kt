package io.github.michael3dprint.mybambuddy.domain.model

/** One print from the archive, with its date and duration already formatted for display. */
data class HistoryItem(
    val id: String,
    val name: String,
    val printerName: String,
    val status: HistoryStatus,
    val formattedDate: String,
    val formattedDuration: String,
    val thumbnailUrl: String? = null,
    /** The printer the print ran on, when the server says (used to show one printer's previous prints). */
    val printerId: Int? = null,
)
