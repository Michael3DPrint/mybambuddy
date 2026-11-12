package io.github.michael3dprint.mybambuddy.ui.setup

import io.github.michael3dprint.mybambuddy.domain.network.BambuddyFinder
import io.github.michael3dprint.mybambuddy.domain.network.HealthProbe
import io.github.michael3dprint.mybambuddy.domain.network.NetworkInfo
import io.github.michael3dprint.mybambuddy.domain.network.Subnet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WelcomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private class FakeNetwork(var subnet: Subnet?, var vpn: Boolean? = false, var installed: Boolean? = true) : NetworkInfo {
        override fun wifiSubnet() = subnet

        override fun vpnActive() = vpn

        override fun isInstalled(packageName: String) = installed
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(net: FakeNetwork, probe: HealthProbe = HealthProbe { _, _ -> false }) = WelcomeViewModel(net, BambuddyFinder(probe))

    @Test
    fun `the checks show what the phone can see`() {
        val state = vm(FakeNetwork(Subnet("192.168.1.20", 24), vpn = true, installed = false)).uiState.value.checks
        assertEquals(true, state.vpnActive)
        assertEquals(false, state.tailscaleInstalled)
        assertEquals("192.168.1.20", state.wifi?.address)
    }

    @Test
    fun `a sweep that finds Bambuddy reports it`() = runTest(dispatcher) {
        val model = vm(FakeNetwork(Subnet("192.168.1.20", 24))) { host, _ -> host == "192.168.1.50" }
        model.find(true)
        advanceUntilIdle()
        assertEquals(FindState.Found(listOf("192.168.1.50")), model.uiState.value.find)
    }

    @Test
    fun `a sweep that finds nothing says so`() = runTest(dispatcher) {
        val model = vm(FakeNetwork(Subnet("192.168.1.20", 24)))
        model.find(true)
        advanceUntilIdle()
        assertEquals(FindState.NotFound, model.uiState.value.find)
    }

    @Test
    fun `no sweep runs when the permission was refused, off Wi-Fi, or on a public range`() = runTest(dispatcher) {
        var asked = 0
        val probe = HealthProbe { _, _ ->
            asked++
            false
        }
        val denied = vm(FakeNetwork(Subnet("192.168.1.20", 24)), probe).also { it.find(false) }
        assertTrue(denied.uiState.value.find is FindState.CouldNotScan)
        val offWifi = vm(FakeNetwork(null), probe).also { it.find(true) }
        assertTrue(offWifi.uiState.value.find is FindState.CouldNotScan)
        val publicRange = vm(FakeNetwork(Subnet("8.8.8.8", 24)), probe).also { it.find(true) }
        assertTrue(publicRange.uiState.value.find is FindState.CouldNotScan)
        advanceUntilIdle()
        assertEquals(0, asked)
    }

    @Test
    fun `stopping a sweep returns to idle`() = runTest(dispatcher) {
        val model = vm(FakeNetwork(Subnet("192.168.1.20", 24)))
        model.find(true)
        model.stopFind()
        advanceUntilIdle()
        assertEquals(FindState.Idle, model.uiState.value.find)
    }
}
