package io.github.michael3dprint.mybambuddy.domain.nfc

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenTag3dTest {
    private val sample = OpenTag3dData(
        material = "PLA",
        modifiers = "Metal",
        manufacturer = "Bambu Lab",
        colorName = "Iron Gray Metallic",
        rgba = "43403DFF",
        diameterMm = 1.75,
        weightGrams = 1000,
        printTempC = 190,
    )

    /** Produced by Bambuddy's own encoder logic (backend/app/services/opentag3d.py) for the same input. */
    private val bambuddyHex =
        "03E8504C4120204D6574616C00000000000000000000000000000042616D6275204C61622020202020202049726F6E2047726179204D6574616C6C6963202020202020202020202020202043403DFF0000000000000000000000000006D603E8260000000000"

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02X".format(it.toInt() and 0xFF) }

    @Test
    fun `encoding is byte for byte what Bambuddy writes`() {
        assertEquals(bambuddyHex, hex(OpenTag3d.encode(sample)))
        assertEquals(OpenTag3d.PAYLOAD_SIZE, OpenTag3d.encode(sample).size)
    }

    @Test
    fun `decode returns what was encoded`() {
        assertEquals(sample, OpenTag3d.decode(OpenTag3d.encode(sample)))
    }

    @Test
    fun `payload that is too short is rejected`() {
        assertNull(OpenTag3d.decode(ByteArray(OpenTag3d.PAYLOAD_SIZE - 1)))
        assertNull(OpenTag3d.decode(ByteArray(0)))
    }

    @Test
    fun `long text is cut to its field and never spills into the next one`() {
        val data = sample.copy(material = "POLYCARBONATE", manufacturer = "Äääääääääääääääääääää", colorName = "x".repeat(200))
        val decoded = OpenTag3d.decode(OpenTag3d.encode(data))!!
        assertEquals("POLYC", decoded.material)
        assertTrue(decoded.manufacturer.length <= 16)
        assertEquals(32, decoded.colorName.length)
        assertEquals("Metal", decoded.modifiers)
        assertEquals("43403DFF", decoded.rgba)
    }

    @Test
    fun `control characters from a hostile tag are stripped`() {
        val raw = OpenTag3d.encode(sample)
        raw[0x2B] = 0x07 // bell
        raw[0x2C] = 0x1B // escape
        val decoded = OpenTag3d.decode(raw)!!
        assertTrue(decoded.colorName.none { it.isISOControl() })
    }

    @Test
    fun `numbers are clamped instead of overflowing`() {
        val data = sample.copy(weightGrams = 10_000_000, printTempC = 99_999, diameterMm = 500.0)
        val decoded = OpenTag3d.decode(OpenTag3d.encode(data))!!
        assertEquals(65_535, decoded.weightGrams)
        assertEquals(255 * 5, decoded.printTempC)
        assertEquals(65.535, decoded.diameterMm, 0.0001)
        assertArrayEquals(OpenTag3d.encode(data), OpenTag3d.encode(decoded))
    }

    @Test
    fun `whole message fits an NTAG215`() {
        // NDEF bytes: the payload plus the record, TLV, capability container and terminator.
        val totalTagBytes = 4 + 2 + 3 + 21 + OpenTag3d.PAYLOAD_SIZE + 1
        assertTrue(totalTagBytes < 504)
    }

    @Test
    fun `tag text sanitiser trims and caps`() {
        assertEquals("abc", TagText.sanitize("  abc\u0000\n ", 10))
        assertEquals("ab", TagText.sanitize("abcdef", 2))
    }

    @Test
    fun `decode ignores a payload larger than the cap`() {
        assertNull(OpenTag3d.decode(ByteArray(OpenTag3d.MAX_PAYLOAD_SIZE + 1)))
        assertNotNull(OpenTag3d.decode(ByteArray(OpenTag3d.MAX_PAYLOAD_SIZE)))
    }
}
