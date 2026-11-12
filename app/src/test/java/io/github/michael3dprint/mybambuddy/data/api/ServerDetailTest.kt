package io.github.michael3dprint.mybambuddy.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerDetailTest {
    @Test
    fun `a string detail is returned as is`() {
        assertEquals("Spool not found", ServerDetail.from("""{"detail":"Spool not found"}"""))
    }

    @Test
    fun `a validation list becomes field and message`() {
        val body = """{"detail":[{"type":"x","loc":["body","label_weight"],"msg":"Input should be greater than or equal to 1"},
            {"loc":["body","rgba"],"msg":"bad hex"}]}"""
        assertEquals(
            "label_weight: Input should be greater than or equal to 1; rgba: bad hex",
            ServerDetail.from(body),
        )
    }

    @Test
    fun `a Spoolman refusal shows its message and upstream text`() {
        val body = """{"detail":{"message":"Spoolman rejected the request","upstream_status":400,"upstream_body":"Vendor missing"}}"""
        assertEquals("Spoolman rejected the request: Vendor missing", ServerDetail.from(body))
    }

    @Test
    fun `unreadable or empty bodies give null`() {
        assertNull(ServerDetail.from(null))
        assertNull(ServerDetail.from(""))
        assertNull(ServerDetail.from("<html>502</html>"))
        assertNull(ServerDetail.from("""{"other":1}"""))
        assertNull(ServerDetail.from("""{"detail":[]}"""))
    }

    @Test
    fun `a filament shortfall object is read as the slots that are short`() {
        val body = """{"detail":{"code":"insufficient_filament","deficit":[
            {"slot_id":1,"ams_id":0,"tray_id":2,"filament_type":"PLA","required_grams":31.2,"remaining_grams":12.0},
            {"slot_id":2,"filament_type":"PETG","required_grams":5.0,"remaining_grams":null}]}}"""
        assertEquals(
            "Not enough filament: filament 1 (PLA) needs about 31 g, the slot has about 12 g; " +
                "filament 2 (PETG) needs about 5 g, the slot has an amount that could not be read.",
            ServerDetail.from(body),
        )
        assertEquals("insufficient_filament", ServerDetail.codeOf(body))
    }

    @Test
    fun `a missing filament object names what no loaded slot has`() {
        val body = """{"detail":{"code":"unmatched_filament",
            "missing":[{"brand":"Bambu Lab","material":"PLA","subtype":"Basic","color_name":"Black"}]}}"""
        assertEquals("No loaded slot matches: Bambu Lab PLA Basic Black.", ServerDetail.from(body))
        assertEquals("unmatched_filament", ServerDetail.codeOf(body))
    }

    @Test
    fun `an object with only a code still says something and a plain string has no code`() {
        assertEquals("Bambuddy refused this: something_new.", ServerDetail.from("""{"detail":{"code":"something_new"}}"""))
        assertNull(ServerDetail.codeOf("""{"detail":"Spool not found"}"""))
        assertNull(ServerDetail.codeOf(""))
        assertEquals("Not enough filament for this job.", ServerDetail.from("""{"detail":{"code":"insufficient_filament","deficit":[]}}"""))
    }
}
