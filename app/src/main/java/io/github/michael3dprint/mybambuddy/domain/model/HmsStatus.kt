package io.github.michael3dprint.mybambuddy.domain.model

/** One printer error (HMS) as reported: its code, text, severity and any offered actions. */
data class HmsStatus(
    val code: String = "",
    val description: String = "",
    val severity: String = "",
    val attr: Long? = null,
    val module: Int? = null,
    val fullCode: String = "",
    /** Button names the printer offers for this error (e.g. RESUME_PRINTING). */
    val actions: List<String> = emptyList(),
) {
    val severityLabel: String
        get() = when (severity) {
            "1" -> "Error"
            "2" -> "Warning"
            "3" -> "Notice"
            else -> severity.ifBlank { "Warning" }
        }

    val displayMessage: String
        get() = description.ifBlank { code.ifBlank { "HMS Warning" } }
}
