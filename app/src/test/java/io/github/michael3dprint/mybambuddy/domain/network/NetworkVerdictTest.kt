package io.github.michael3dprint.mybambuddy.domain.network

import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkVerdictTest {
    private val tail = "100.64.0.9"

    private fun v(host: String, installed: Boolean?, vpn: Boolean?, reachable: Boolean?) =
        NetworkVerdictRule.decide(host, installed, vpn, reachable)

    @Test
    fun `a home address does not need Tailscale`() {
        assertEquals(NetworkVerdict.NOT_NEEDED, v("192.168.1.5", false, false, false))
    }

    @Test
    fun `a name that cannot be classified is not judged`() {
        assertEquals(NetworkVerdict.COULD_NOT_CHECK, v("my-host", true, true, true))
    }

    @Test
    fun `a tailnet address walks through every combination`() {
        assertEquals(NetworkVerdict.TAILSCALE_NOT_INSTALLED, v(tail, false, null, null))
        assertEquals(NetworkVerdict.COULD_NOT_CHECK, v(tail, null, true, true))
        assertEquals(NetworkVerdict.COULD_NOT_CHECK, v(tail, true, null, true))
        assertEquals(NetworkVerdict.INSTALLED_NO_VPN, v(tail, true, false, false))
        assertEquals(NetworkVerdict.VPN_REACHABLE, v(tail, true, true, true))
        assertEquals(NetworkVerdict.VPN_UNREACHABLE, v(tail, true, true, false))
        assertEquals(NetworkVerdict.COULD_NOT_CHECK, v(tail, true, true, null))
    }

    @Test
    fun `no verdict claims that Tailscale itself is on`() {
        NetworkVerdict.values().forEach { assert(!it.text.contains("Tailscale is on", ignoreCase = true)) { it.name } }
    }
}
