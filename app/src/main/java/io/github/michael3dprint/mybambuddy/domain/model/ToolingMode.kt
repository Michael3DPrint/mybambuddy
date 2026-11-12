package io.github.michael3dprint.mybambuddy.domain.model

/** Which version of PrintDeck a printer gets, decided by Developer LAN mode and reachability (docs/design/11-readiness-check.md). */
enum class ToolingMode {
    /** Connected and Developer LAN mode on: every tool. */
    FULL,

    /** Connected and Developer LAN mode off: monitoring and local features only; every command tool is off. */
    LIMITED,

    /** Connected but the printer does not report Developer LAN mode: tools stay on, each marked as possibly refused. */
    UNVERIFIED,
    OFFLINE,

    /** The status could not be read, so nothing is assumed. */
    UNKNOWN,
}
