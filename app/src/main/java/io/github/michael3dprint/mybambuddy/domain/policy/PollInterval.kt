package io.github.michael3dprint.mybambuddy.domain.policy

/** Turns the Settings choice into a poll delay, kept inside sane bounds so a bad stored value can never hammer or starve the server. */
object PollInterval {
    /** The poll period used until a choice is saved. */
    const val DEFAULT_MS = 5_000L
    private const val MIN_SECONDS = 2
    private const val MAX_SECONDS = 60

    /** The poll period in milliseconds for a number of seconds, kept within 2 to 60 seconds. */
    fun toMillis(seconds: Int): Long = seconds.coerceIn(MIN_SECONDS, MAX_SECONDS) * 1_000L
}
