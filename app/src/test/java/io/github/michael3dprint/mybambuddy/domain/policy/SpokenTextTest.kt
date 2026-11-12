package io.github.michael3dprint.mybambuddy.domain.policy

import io.github.michael3dprint.mybambuddy.domain.model.AmsTray
import org.junit.Assert.assertEquals
import org.junit.Test

class SpokenTextTest {
    @Test
    fun `amounts temperatures and percents are spoken in words`() {
        assertEquals("about 750 grams", SpokenText.of("~750 g"))
        assertEquals("humidity 45 percent", SpokenText.of("💧 45 %"))
        assertEquals("temperature 21.0 degrees Celsius", SpokenText.of("🌡 21.0 °C"))
        assertEquals("75 percent", SpokenText.of("75%"))
    }

    @Test
    fun `a lone dash is unknown and a middle dot becomes a pause`() {
        assertEquals("unknown", SpokenText.of("—"))
        assertEquals("unknown", SpokenText.of(" – "))
        assertEquals("Blue, PLA, 750 grams left of 1000 grams", SpokenText.of("Blue · PLA · 750 g left of 1000 g"))
    }

    @Test
    fun `warning signs, arrows and bullets are spoken or dropped`() {
        assertEquals("warning LOW, Bambu PLA", SpokenText.of("⚠ LOW · Bambu PLA"))
        assertEquals("Spool to slot", SpokenText.of("Spool → slot"))
        assertEquals("The inventory records this.", SpokenText.of("• The inventory records this."))
    }

    @Test
    fun `text without symbols is unchanged and letters after a number are not mistaken for grams`() {
        assertEquals("Print queue", SpokenText.of("Print queue"))
        assertEquals("AMS 1 gold", SpokenText.of("AMS 1 gold"))
    }

    private fun tray(material: String, sub: String = "", id: Int = 2) =
        AmsTray(id, material, sub, if (material.isBlank()) "" else "FF0000", 50, null)

    @Test
    fun `a slot is described with where it is, what is in it and how much is left`() {
        assertEquals(
            "AMS 1, slot 3, PLA Basic, about 750 grams left",
            SlotSpeech.describe("AMS 1", tray("PLA", "PLA Basic"), SlotFill(0.75f, 750), low = false),
        )
        assertEquals(
            "AMS 2, slot 1, PETG, about 80 grams left, low",
            SlotSpeech.describe("AMS 2", tray("PETG", id = 0), SlotFill(0.08f, 80), low = true),
        )
    }

    @Test
    fun `an empty slot and an unknown amount are said plainly`() {
        assertEquals("AMS 1, slot 4, empty", SlotSpeech.describe("AMS 1", tray("", id = 3), null, low = false))
        assertEquals("AMS 1, slot 3, PLA, amount unknown", SlotSpeech.describe("AMS 1", tray("PLA"), null, low = false))
    }
}
