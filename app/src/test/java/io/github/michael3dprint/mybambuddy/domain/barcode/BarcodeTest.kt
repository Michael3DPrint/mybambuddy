package io.github.michael3dprint.mybambuddy.domain.barcode

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BarcodeTest {
    private class FakeLookup(var result: EanLookupResult) : EanLookup {
        val asked = mutableListOf<String>()

        override suspend fun lookup(ean: String): EanLookupResult {
            asked += ean
            return result
        }
    }

    private class FakePrefs(override var onlineLookup: OnlineLookupConsent = OnlineLookupConsent.ASK) : BarcodePrefs

    @Test
    fun `digits are an ean and everything else is text`() {
        assertEquals(ParsedBarcode.Ean("6975337030163"), BarcodeParser.parse("6975337030163"))
        assertEquals(ParsedBarcode.Ean("12345678"), BarcodeParser.parse("12345678"))
        assertTrue(BarcodeParser.parse("1234567") is ParsedBarcode.Text)
        assertTrue(BarcodeParser.parse("123456789012345") is ParsedBarcode.Text)
        assertTrue(BarcodeParser.parse("https://example.com/x?a=1") is ParsedBarcode.Text)
    }

    @Test
    fun `openspool style json becomes a draft`() {
        val json = """{"protocol":"openspool","brand":"Bambu Lab","type":"PLA","color_hex":"#43403D","weight":750}"""
        val draft = (BarcodeParser.parse(json) as ParsedBarcode.Structured).draft
        assertEquals("Bambu Lab", draft.brand)
        assertEquals("PLA", draft.material)
        assertEquals("43403D", draft.colorHex)
        assertEquals(750, draft.labelWeightGrams)
    }

    @Test
    fun `hostile input never throws and never becomes a command`() {
        val attacks = listOf(
            "'; DROP TABLE spools;--", "../../etc/passwd", "<script>alert(1)</script>", "%00%0d%0aHost: evil",
            "{\"material\":", "{\"material\":\"PLA\",\"weight\":99999999999999999999}", "\u0000\u0007\u001b[31m", "\uD800", "{}", "[]",
        )
        attacks.forEach { BarcodeParser.parse(it) } // must simply not throw
        val capped = BarcodeParser.parse("x".repeat(100_000)) as ParsedBarcode.Text
        assertEquals(BarcodeParser.MAX_LENGTH, capped.text.length)
        val ctrl = BarcodeParser.parse("a\u0000b\u001bc") as ParsedBarcode.Text
        assertEquals("abc", ctrl.text)
        val heavy = (BarcodeParser.parse("""{"material":"PLA","weight":99999999999}""") as ParsedBarcode.Structured).draft
        assertEquals(100_000, heavy.labelWeightGrams)
    }

    @Test
    fun `ean without consent asks first and sends nothing`() = runBlocking {
        val lookup = FakeLookup(EanLookupResult.Found("Bambu PLA"))
        val resolver = BarcodeResolver(lookup, FakePrefs(OnlineLookupConsent.ASK))
        assertEquals(BarcodeOutcome.AskOnlineLookup("6975337030163"), resolver.resolve("6975337030163"))
        assertTrue(lookup.asked.isEmpty())
    }

    @Test
    fun `never means never`() = runBlocking {
        val lookup = FakeLookup(EanLookupResult.Found("x"))
        val outcome = BarcodeResolver(lookup, FakePrefs(OnlineLookupConsent.NEVER)).resolve("6975337030163")
        assertTrue(outcome is BarcodeOutcome.NotFound)
        assertTrue(lookup.asked.isEmpty())
    }

    @Test
    fun `always looks up and turns the title into a clean search phrase`() = runBlocking {
        val lookup = FakeLookup(EanLookupResult.Found("Bambu Lab PLA Basic - Jade White (1.75mm, 1kg) <b>!!</b> a b c d e f g h"))
        val outcome = BarcodeResolver(lookup, FakePrefs(OnlineLookupConsent.ALWAYS)).resolve("6975337030163")
        val query = (outcome as BarcodeOutcome.Search).query
        assertEquals(listOf("6975337030163"), lookup.asked)
        assertTrue(query, query.all { it.isLetterOrDigit() || it == ' ' })
        assertTrue(query.split(' ').size <= 8)
    }

    @Test
    fun `lookup failures become a friendly message`() = runBlocking {
        val resolver = BarcodeResolver(FakeLookup(EanLookupResult.Unavailable("daily limit reached")), FakePrefs())
        val outcome = resolver.resolveOnline("6975337030163") as BarcodeOutcome.NotFound
        assertTrue(outcome.message.contains("daily limit reached"))
        assertTrue(BarcodeResolver(FakeLookup(EanLookupResult.NotFound), FakePrefs()).resolveOnline("12345678") is BarcodeOutcome.NotFound)
    }

    @Test
    fun `structured codes need no network or consent`() = runBlocking {
        val lookup = FakeLookup(EanLookupResult.NotFound)
        val outcome = BarcodeResolver(lookup, FakePrefs()).resolve("""{"material":"PETG","brand":"Acme"}""")
        assertTrue(outcome is BarcodeOutcome.Draft)
        assertTrue(lookup.asked.isEmpty())
    }
}
