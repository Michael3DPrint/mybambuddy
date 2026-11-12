package io.github.michael3dprint.mybambuddy.data.api

import io.github.michael3dprint.mybambuddy.data.api.dto.PrintersResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PrintersResponseAdapterTest {

    private val moshi = ApiClient.createMoshi()
    private val adapter = moshi.adapter(PrintersResponse::class.java)

    @Test
    fun `ingests printers from JSON array`() {
        val json = """
            [
                {"id": "p1", "name": "Bambu X1C", "model": "X1C"},
                {"id": "p2", "name": "Bambu P1S", "model": "P1S"}
            ]
        """.trimIndent()

        val response = adapter.fromJson(json)

        assertNotNull(response)
        val printers = response?.effectivePrinters ?: emptyList()
        assertEquals(2, printers.size)
        assertEquals("p1", printers[0].id)
        assertEquals("Bambu X1C", printers[0].name)
        assertEquals("p2", printers[1].id)
        assertEquals("Bambu P1S", printers[1].name)
    }

    @Test
    fun `ingests printers from JSON map of printers`() {
        val json = """
            {
                "printer_01": {"name": "X1 Carbon", "model": "X1C"},
                "printer_02": {"name": "P1P Lab", "model": "P1P"}
            }
        """.trimIndent()

        val response = adapter.fromJson(json)

        assertNotNull(response)
        val printers = response?.effectivePrinters ?: emptyList()
        assertEquals(2, printers.size)
        assertEquals("printer_01", printers[0].id)
        assertEquals("X1 Carbon", printers[0].name)
        assertEquals("printer_02", printers[1].id)
        assertEquals("P1P Lab", printers[1].name)
    }

    @Test
    fun `ingests printers from wrapper map object`() {
        val json = """
            {
                "printers": {
                    "p10": {"name": "A1 Mini", "model": "A1M"},
                    "p20": {"name": "X1E Enterprise", "model": "X1E"}
                }
            }
        """.trimIndent()

        val response = adapter.fromJson(json)

        assertNotNull(response)
        val printers = response?.effectivePrinters ?: emptyList()
        assertEquals(2, printers.size)
        assertEquals("p10", printers[0].id)
        assertEquals("A1 Mini", printers[0].name)
        assertEquals("p20", printers[1].id)
        assertEquals("X1E Enterprise", printers[1].name)
    }

    @Test
    fun `ingests printers from wrapper array object`() {
        val json = """
            {
                "printers": [
                    {"id": "p100", "name": "X1C 100", "model": "X1C"}
                ],
                "count": 1
            }
        """.trimIndent()

        val response = adapter.fromJson(json)

        assertNotNull(response)
        val printers = response?.effectivePrinters ?: emptyList()
        assertEquals(1, printers.size)
        assertEquals("p100", printers[0].id)
    }

    @Test
    fun `retains valid items when array contains malformed item`() {
        val json = """
            [
                {"id": "p1", "name": "Valid Printer 1"},
                "this_is_an_invalid_string_item",
                {"id": "p2", "name": "Valid Printer 2"}
            ]
        """.trimIndent()

        val response = adapter.fromJson(json)

        assertNotNull(response)
        val printers = response?.effectivePrinters ?: emptyList()
        assertEquals(2, printers.size)
        assertEquals("p1", printers[0].id)
        assertEquals("p2", printers[1].id)
    }

    @Test
    fun `ingests single printer object`() {
        val json = """
            {
                "id": "single_p1",
                "name": "Single Printer",
                "model": "X1C"
            }
        """.trimIndent()

        val response = adapter.fromJson(json)

        assertNotNull(response)
        val printers = response?.effectivePrinters ?: emptyList()
        assertEquals(1, printers.size)
        assertEquals("single_p1", printers[0].id)
        assertEquals("Single Printer", printers[0].name)
    }

    @Test
    fun `handles empty JSON array`() {
        val json = "[]"

        val response = adapter.fromJson(json)

        assertNotNull(response)
        val printers = response?.effectivePrinters ?: emptyList()
        assertEquals(0, printers.size)
    }

    @Test
    fun `handles empty JSON object`() {
        val json = "{}"

        val response = adapter.fromJson(json)

        assertNotNull(response)
        val printers = response?.effectivePrinters ?: emptyList()
        assertEquals(0, printers.size)
    }
}
