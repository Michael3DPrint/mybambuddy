package io.github.michael3dprint.mybambuddy.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PrintDeckLoggerTest {
    private data class Line(val level: Char, val message: String, val throwable: Throwable?)

    private val lines = mutableListOf<Line>()
    private lateinit var original: LogSink

    @Before
    fun setUp() {
        original = PrintDeckLogger.sink
        PrintDeckLogger.sink = LogSink { level, _, message, throwable -> lines += Line(level, message, throwable) }
    }

    @After
    fun tearDown() {
        PrintDeckLogger.sink = original
        LogPolicy.verbose = false
    }

    @Test
    fun `release logging has no stack trace and no exception text`() {
        LogPolicy.verbose = false
        PrintDeckLogger.e(message = "Failed to parse", throwable = IllegalStateException("secret server body"))
        val line = lines.single()
        assertNull(line.throwable)
        assertEquals("Failed to parse (IllegalStateException)", line.message)
        assertTrue(!line.message.contains("secret"))
    }

    @Test
    fun `release logging drops debug and info lines`() {
        LogPolicy.verbose = false
        PrintDeckLogger.d(message = "request details")
        PrintDeckLogger.i(message = "GET /api/v1/printers")
        assertTrue(lines.isEmpty())
    }

    @Test
    fun `debug logging keeps everything`() {
        LogPolicy.verbose = true
        val boom = IllegalStateException("boom")
        PrintDeckLogger.e(message = "Failed", throwable = boom)
        PrintDeckLogger.i(message = "info")
        assertEquals(2, lines.size)
        assertEquals(boom, lines[0].throwable)
    }

    @Test
    fun `logging is off unless something turns it on`() {
        assertEquals(false, LogPolicy.verbose)
    }
}
