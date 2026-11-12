package io.github.michael3dprint.mybambuddy.domain.policy

import io.github.michael3dprint.mybambuddy.data.api.ApiClient
import io.github.michael3dprint.mybambuddy.data.api.dto.PrinterStatusDto
import io.github.michael3dprint.mybambuddy.domain.mapper.mapToPrintStatus
import io.github.michael3dprint.mybambuddy.domain.model.HmsStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HmsExplainerTest {
    private val real = HmsStatus(
        code = "0x10006",
        description = "Failed to resume previous print",
        severity = "1",
        module = 5,
        fullCode = "0500040000010006",
        actions = listOf("RESUME_PRINTING", "STOP_PRINTING", "SOMETHING_NEW"),
    )

    @Test
    fun `explains the real error from the live printer`() {
        val advice = HmsExplainer.explain(real)
        assertEquals(HmsSeverity.ERROR, advice.severity)
        assertEquals("Motion controller", advice.area)
        assertEquals("Failed to resume previous print", advice.title)
        assertEquals(listOf("Resume the print", "Stop the print"), advice.options) // unknown keys are never shown
        assertEquals("0500-0400-0001-0006", advice.codeLabel)
        assertEquals("https://wiki.bambulab.com/en/hms/error-code?e=0500040000010006", advice.learnMoreUrl)
    }

    @Test
    fun `severity mapping and fallbacks`() {
        assertEquals(HmsSeverity.WARNING, HmsExplainer.explain(real.copy(severity = "2")).severity)
        assertEquals(HmsSeverity.NOTICE, HmsExplainer.explain(real.copy(severity = "3")).severity)
        assertEquals("Printer", HmsExplainer.explain(real.copy(module = 99)).area)
        assertEquals("The printer reported a problem", HmsExplainer.explain(real.copy(description = "")).title)
    }

    @Test
    fun `the link is only built from a valid sixteen digit hex code`() {
        listOf(
            "",
            "../x",
            "http://evil.example",
            "05000400000100",
            "0500040000010006/../..",
            "0500040000010006 ",
            "ZZZZ040000010006",
            "0500040000010006&x=1",
        )
            .forEach { assertNull(it, HmsExplainer.wikiUrl(it)) }
        assertNull(HmsExplainer.explain(real.copy(fullCode = "javascript:alert(1)")).learnMoreUrl)
        assertTrue(HmsExplainer.wikiUrl("0500040000010006")!!.startsWith("https://wiki.bambulab.com/"))
    }

    @Test
    fun `the live status fixture carries the new fields through the whole mapper`() {
        val json = javaClass.classLoader!!.getResource("fixtures/status.json")!!.readText()
        val dto = ApiClient.createMoshi().adapter(PrinterStatusDto::class.java).fromJson(json)!!
        val hms = mapToPrintStatus(dto).hmsErrors.single()
        assertEquals(5, hms.module)
        assertEquals("0500040000010006", hms.fullCode)
        assertEquals("Motion controller", HmsExplainer.explain(hms).area)
    }

    @Test
    fun `a hostile actions list is bounded`() {
        val actions = (1..500).joinToString(",") { "\"A$it\"" }
        val json = """{"hms_errors":[{"code":"x","severity":1,"module":5,"full_code":"0500040000010006","actions":[$actions]}]}"""
        val dto = ApiClient.createMoshi().adapter(PrinterStatusDto::class.java).fromJson(json)!!
        assertTrue(dto.hmsErrors!!.single().actions!!.size <= 16)
    }
}
