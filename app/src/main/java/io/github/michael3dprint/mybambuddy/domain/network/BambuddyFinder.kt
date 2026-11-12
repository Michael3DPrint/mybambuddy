package io.github.michael3dprint.mybambuddy.domain.network

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Asks one address whether a Bambuddy server answers there. Implementations send only a health request and never an API key. */
fun interface HealthProbe {
    /** True only when [host] answers on [port] with Bambuddy's health reply. */
    suspend fun isBambuddy(host: String, port: Int): Boolean
}

/** What a Bambuddy health reply looks like: JSON with status "healthy" (as served by Bambuddy 1.3.0). */
object BambuddyFingerprint {
    private val healthy = Regex("\"status\"\\s*:\\s*\"healthy\"")

    /** True when [body] is a small JSON reply whose status is "healthy". */
    fun matches(body: String): Boolean = body.length <= MAX_BODY && healthy.containsMatchIn(body)

    /** Longer replies are not a health reply. */
    const val MAX_BODY = 1024
}

/** Sweeps a list of addresses for Bambuddy, a few at a time, and reports progress. */
class BambuddyFinder(private val probe: HealthProbe, private val parallelism: Int = 40, private val port: Int = DEFAULT_PORT) {
    /** The addresses in [hosts] where Bambuddy answered, in the order given. [onProgress] gets (checked, total) as the sweep goes. */
    suspend fun find(hosts: List<String>, onProgress: (Int, Int) -> Unit = { _, _ -> }): List<String> = coroutineScope {
        val gate = Semaphore(parallelism)
        val done = AtomicInteger(0)
        hosts.map { host ->
            async {
                val hit = gate.withPermit { probe.isBambuddy(host, port) }
                onProgress(done.incrementAndGet(), hosts.size)
                host.takeIf { hit }
            }
        }.awaitAll().filterNotNull()
    }

    companion object {
        /** Bambuddy's default port. */
        const val DEFAULT_PORT = 8000
    }
}
