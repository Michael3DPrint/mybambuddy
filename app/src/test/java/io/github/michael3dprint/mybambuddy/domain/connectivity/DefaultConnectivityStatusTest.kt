package io.github.michael3dprint.mybambuddy.domain.connectivity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultConnectivityStatusTest {
    @Test
    fun `by default one failure is enough`() {
        val status = DefaultConnectivityStatus()
        status.markOffline(1L)
        assertTrue(status.state.value.isOffline)
        assertEquals(1L, status.state.value.dataFrom)
    }

    @Test
    fun `with a threshold of two a single failure does not switch the app offline`() {
        val status = DefaultConnectivityStatus(failuresBeforeOffline = 2)
        status.markOffline(1L)
        assertFalse(status.state.value.isOffline)
        status.markOffline(1L)
        assertTrue(status.state.value.isOffline)
    }

    @Test
    fun `a success between failures starts the count again`() {
        val status = DefaultConnectivityStatus(failuresBeforeOffline = 2)
        status.markUnreachable()
        status.markOnline()
        status.markUnreachable()
        assertFalse(status.state.value.isOffline)
    }

    @Test
    fun `a success clears the offline state at once`() {
        val status = DefaultConnectivityStatus(failuresBeforeOffline = 2)
        status.markUnreachable()
        status.markUnreachable()
        assertTrue(status.state.value.isOffline)
        status.markOnline()
        assertFalse(status.state.value.isOffline)
    }
}
