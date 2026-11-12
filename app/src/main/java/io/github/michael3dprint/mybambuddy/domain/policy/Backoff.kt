package io.github.michael3dprint.mybambuddy.domain.policy

import kotlin.random.Random

/**
 * Delay before the next poll: the normal period while things work, doubling per consecutive failure
 * (max [maxMillis]) with +/-[jitter] randomisation so many clients never retry in lockstep.
 */
class Backoff(private val maxMillis: Long = 60_000L, private val jitter: Double = 0.2, private val random: Random = Random.Default) {
    /**
     * The wait before the next attempt: [baseMillis] while things work, doubling with each consecutive failure up to the maximum,
     * with random spread.
     */
    fun delayMillis(baseMillis: Long, consecutiveFailures: Int): Long {
        val exponent = consecutiveFailures.coerceIn(0, MAX_DOUBLINGS)
        val raw = (baseMillis shl exponent).coerceAtMost(maxMillis.coerceAtLeast(baseMillis))
        if (consecutiveFailures == 0 || jitter <= 0.0) return raw
        val spread = (raw * jitter).toLong()
        return (raw + random.nextLong(-spread, spread + 1)).coerceAtLeast(baseMillis)
    }

    private companion object {
        const val MAX_DOUBLINGS = 6
    }
}
