package io.github.michael3dprint.mybambuddy.domain.model

/**
 * What Bambuddy said about a slot assignment. The server saves the record first and tries to configure the printer after, and a
 * printer that refuses the change does not make the request fail, so a success here is "saved", not "the printer took it".
 *
 * [pendingConfig] is true when Bambuddy found the slot empty and will configure it when a spool is inserted, false when it
 * sent the configuration, and null when the server does not say (the Spoolman route).
 */
data class SlotLoadResult(val pendingConfig: Boolean?)
