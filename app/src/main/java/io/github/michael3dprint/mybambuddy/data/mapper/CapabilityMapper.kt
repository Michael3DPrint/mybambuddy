package io.github.michael3dprint.mybambuddy.data.mapper

import io.github.michael3dprint.mybambuddy.data.api.dto.PrinterAmsStatusDto
import io.github.michael3dprint.mybambuddy.data.api.dto.PrinterStatusDto
import io.github.michael3dprint.mybambuddy.domain.model.PrintActivity
import io.github.michael3dprint.mybambuddy.domain.model.PrinterCapabilities

/** Maps Bambuddy's status payloads to [PrinterCapabilities]. A missing field stays null, never "yes". */
fun PrinterStatusDto.toCapabilities(): PrinterCapabilities = PrinterCapabilities(
    connected = connected,
    developerMode = developerMode,
    hasAms = amsExists,
    supportsDrying = supportsDrying,
    dryingWhilePrinting = supportsDryingWhilePrinting,
    dryingScreenOnly = dryingScreenOnly,
    hasChamberHeater = supportsChamberHeater,
    activity = PrintActivity.from(effectiveState, connected),
)

/**
 * Reads the capability flags (connection, developer mode, AMS, drying, activity) from an AMS status reply. A flag the server did
 * not send stays unknown.
 */
fun PrinterAmsStatusDto.toCapabilities(): PrinterCapabilities = PrinterCapabilities(
    connected = connected,
    developerMode = developerMode,
    hasAms = amsExists ?: ams?.isNotEmpty(),
    supportsDrying = supportsDrying,
    dryingWhilePrinting = supportsDryingWhilePrinting,
    dryingScreenOnly = dryingScreenOnly,
    hasChamberHeater = supportsChamberHeater,
    activity = PrintActivity.from(state, connected),
)

/** True when the AMS read an RFID tag: a non-blank tag UID or Bambu spool id that is not all zeros. */
fun isRfidTag(tagUid: String?, trayUuid: String?): Boolean = listOf(tagUid, trayUuid).any { id ->
    !id.isNullOrBlank() && id.any { it != '0' }
}
