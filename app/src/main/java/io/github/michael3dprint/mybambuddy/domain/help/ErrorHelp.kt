package io.github.michael3dprint.mybambuddy.domain.help

import io.github.michael3dprint.mybambuddy.domain.model.ErrorKind

/** Which troubleshooting page an error card links to (design 47). One table, so a new error kind cannot be left without help. */
object ErrorHelp {
    /** The page for [kind]; null when the right place is the Setup help index. */
    fun topicFor(kind: ErrorKind): HelpTopicId? = when (kind) {
        ErrorKind.NETWORK_UNREACHABLE -> HelpTopicId.T_CANNOT_REACH
        ErrorKind.NOT_FOUND -> HelpTopicId.T_CANNOT_REACH
        ErrorKind.PARSE_ERROR -> HelpTopicId.T_CANNOT_REACH
        ErrorKind.UNAUTHORIZED -> HelpTopicId.T_KEY_REJECTED
        ErrorKind.FORBIDDEN -> HelpTopicId.T_KEY_REJECTED
        ErrorKind.PRINTER_UNAVAILABLE -> HelpTopicId.T_PRINTER_OFFLINE
        ErrorKind.UNSUPPORTED -> HelpTopicId.T_DEVELOPER_MODE
        ErrorKind.ACTION_REJECTED -> HelpTopicId.T_PRINTER_REFUSES
        ErrorKind.SERVER_ERROR -> HelpTopicId.T_SERVER_ERROR
        ErrorKind.UNKNOWN -> null
    }
}
