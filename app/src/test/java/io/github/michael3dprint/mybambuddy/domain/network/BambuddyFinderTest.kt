package io.github.michael3dprint.mybambuddy.domain.network

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BambuddyFinderTest {
    @Test
    fun `finds only the addresses that answer, in order, and reports progress`() = runTest {
        val asked = mutableListOf<Pair<String, Int>>()
        val probe = HealthProbe { host, port ->
            asked += host to port
            host == "h2" || host == "h4"
        }
        val progress = mutableListOf<Pair<Int, Int>>()
        val found = BambuddyFinder(probe, parallelism = 2).find(listOf("h1", "h2", "h3", "h4")) { d, t -> progress += d to t }
        assertEquals(listOf("h2", "h4"), found)
        assertEquals(4, asked.size)
        assertTrue(asked.all { it.second == 8000 })
        assertEquals(4 to 4, progress.last())
    }

    @Test
    fun `an empty list finds nothing`() = runTest {
        assertEquals(emptyList<String>(), BambuddyFinder({ _, _ -> true }).find(emptyList()))
    }

    @Test
    fun `only Bambuddy's healthy reply matches`() {
        assertTrue(BambuddyFingerprint.matches("{\"status\":\"healthy\"}"))
        assertTrue(BambuddyFingerprint.matches("{ \"status\" : \"healthy\" }"))
        assertFalse(BambuddyFingerprint.matches("{\"status\":\"down\"}"))
        assertFalse(BambuddyFingerprint.matches("<html>healthy</html>"))
        assertFalse(BambuddyFingerprint.matches("{\"status\":\"healthy\"}" + " ".repeat(2000)))
    }
}
