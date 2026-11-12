package io.github.michael3dprint.mybambuddy.domain.policy

import io.github.michael3dprint.mybambuddy.domain.model.DiagnosticStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DiagnosticAdviceTest {
    @Test
    fun `every known check has a title and a fix when it fails`() {
        listOf(
            "port_mqtt", "port_ftps", "port_rtsps", "macos_local_network", "network_mode", "subnet",
            "external_storage", "mqtt_auth", "developer_mode", "printer_publishing",
        ).forEach { id ->
            assertNotNull(id, DiagnosticAdvice.forCheck(id, DiagnosticStatus.FAIL).fix)
            assertNotNull(id, DiagnosticAdvice.forCheck(id, DiagnosticStatus.WARN).fix)
        }
    }

    @Test
    fun `a fix is given only for a failure or a warning`() {
        listOf(DiagnosticStatus.PASS, DiagnosticStatus.SKIP, DiagnosticStatus.UNKNOWN).forEach {
            assertNull(DiagnosticAdvice.forCheck("port_mqtt", it).fix)
        }
    }

    @Test
    fun `developer mode uses the standard sentence`() {
        assertEquals(ToolAvailability.DEVELOPER_MODE_FIX, DiagnosticAdvice.forCheck("developer_mode", DiagnosticStatus.FAIL).fix)
    }

    @Test
    fun `an id this app does not know is shown by its id with no invented advice`() {
        val advice = DiagnosticAdvice.forCheck("brand_new_check", DiagnosticStatus.FAIL)
        assertEquals("Brand new check", advice.title)
        assertNull(advice.fix)
    }
}
