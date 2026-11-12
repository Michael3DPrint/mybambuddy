package io.github.michael3dprint.mybambuddy.domain.model

/** The kind of problem an error card describes; it decides the title and wording. */
enum class ErrorKind {
    NETWORK_UNREACHABLE,
    UNAUTHORIZED,
    FORBIDDEN,
    ACTION_REJECTED,
    PRINTER_UNAVAILABLE,
    UNSUPPORTED,
    NOT_FOUND,
    SERVER_ERROR,
    PARSE_ERROR,
    UNKNOWN,
}

/** An error ready to show: title, message, and the labels of its two buttons (null hides a button). */
data class UiErrorState(
    val kind: ErrorKind,
    val title: String,
    val message: String,
    val primaryActionLabel: String? = "Retry",
    val secondaryActionLabel: String? = null,
)
