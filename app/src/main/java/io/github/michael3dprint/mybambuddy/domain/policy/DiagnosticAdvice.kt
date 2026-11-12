package io.github.michael3dprint.mybambuddy.domain.policy

import io.github.michael3dprint.mybambuddy.domain.model.DiagnosticStatus

/** A readable title for a check and, when it failed or warned, what to try. */
data class Advice(val title: String, val fix: String?)

/**
 * The words for each diagnostics check. Bambuddy sends only a check id and a status, so the wording lives here, in one place
 * (docs/design/20-server-diagnostics.md). The advice is general ("check", "may") because the server does not say why a check failed.
 */
object DiagnosticAdvice {
    private val titles = mapOf(
        "port_mqtt" to "Printer reachable (control port)",
        "port_ftps" to "File upload port",
        "port_rtsps" to "Camera port",
        "macos_local_network" to "macOS local network access",
        "network_mode" to "Server network mode",
        "subnet" to "Server and printer on the same network",
        "external_storage" to "External storage for sent files",
        "mqtt_auth" to "Printer login (serial number and access code)",
        "developer_mode" to "Developer LAN mode",
        "printer_publishing" to "Printer sending status updates",
    )

    private val fixes = mapOf(
        "port_mqtt" to
            "Bambuddy can't reach the printer's control port. Check that the printer is on, on the same network as the server, " +
            "and that nothing blocks the connection.",
        "port_ftps" to "Sending prints may fail. If the port is open but not answering, restarting the printer can help.",
        "port_rtsps" to "The camera may not work. Check the printer's camera and LAN settings.",
        "macos_local_network" to
            "macOS may be blocking the server from reaching devices on the local network. Allow it in the system's privacy settings.",
        "network_mode" to
            "The server runs in a network mode that can block finding the printer. Host networking is usually the simplest choice.",
        "subnet" to "The server and the printer may be on different networks. Put them on the same one.",
        "external_storage" to
            "The printer's external storage may not be usable for sent files. Check that a card is inserted and the setting is on.",
        "mqtt_auth" to "The printer may have rejected Bambuddy's login. Check the serial number and access code saved in Bambuddy.",
        "developer_mode" to ToolAvailability.DEVELOPER_MODE_FIX,
        "printer_publishing" to
            "The printer isn't sending status updates to Bambuddy. Try restarting the printer or reconnecting it in Bambuddy.",
    )

    /** Title and fix for a check. The fix is only given for FAIL and WARN. An id this app doesn't know is shown by its id. */
    fun forCheck(id: String, status: DiagnosticStatus): Advice {
        val title = titles[id] ?: id.replace('_', ' ').replaceFirstChar { it.uppercase() }
        val fix = if (status == DiagnosticStatus.FAIL || status == DiagnosticStatus.WARN) fixes[id] else null
        return Advice(title, fix)
    }
}
