package io.github.michael3dprint.mybambuddy.domain.policy

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyTest {
    @Test
    fun `backoff doubles per failure is capped and resets on success`() {
        val backoff = Backoff(maxMillis = 60_000, jitter = 0.0)
        assertEquals(5_000, backoff.delayMillis(5_000, 0))
        assertEquals(10_000, backoff.delayMillis(5_000, 1))
        assertEquals(20_000, backoff.delayMillis(5_000, 2))
        assertEquals(40_000, backoff.delayMillis(5_000, 3))
        assertEquals(60_000, backoff.delayMillis(5_000, 4))
        assertEquals(60_000, backoff.delayMillis(5_000, 1_000)) // no overflow with huge failure counts
        assertEquals(5_000, backoff.delayMillis(5_000, 0))
    }

    @Test
    fun `jitter stays within twenty percent`() {
        val backoff = Backoff(maxMillis = 60_000, jitter = 0.2, random = Random(42))
        repeat(200) {
            val delay = backoff.delayMillis(5_000, 2)
            assertTrue(delay in 16_000..24_000)
        }
        assertEquals(5_000, backoff.delayMillis(5_000, 0))
    }

    @Test
    fun `private and tailnet hosts get no warning`() {
        listOf(
            "http://100.64.0.10:8000", "http://192.168.1.5", "http://10.0.0.2:8000", "http://172.16.0.1", "https://example.com",
            "http://my-host.tailnet-name.ts.net:8000", "http://localhost:8000", "bambuddy:8000", "", "  ",
        ).forEach { assertNull(it, ServerUrlPolicy.warning(it)) }
    }

    @Test
    fun `public plain http gets a warning`() {
        listOf("http://example.com", "http://8.8.8.8:8000", "http://172.32.0.1", "http://100.128.0.1", "http://203.0.113.9/x")
            .forEach { assertNotNull(it, ServerUrlPolicy.warning(it)) }
    }
}
