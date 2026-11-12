package io.github.michael3dprint.mybambuddy.domain.policy

import io.github.michael3dprint.mybambuddy.domain.model.QueueItem
import io.github.michael3dprint.mybambuddy.domain.model.QueueStatus
import io.github.michael3dprint.mybambuddy.testutil.ScriptedQueue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StartCheckTest {
    private fun job(status: QueueStatus = QueueStatus.PENDING, reason: String? = null, error: String? = null) =
        QueueItem(1, 1, "X2D", "benchy", status, 1, reason, false, errorMessage = error)

    private val sdCard = "X2D refused to store the file — check its SD card or USB drive. Bambuddy will retry automatically"

    @Test
    fun `each job state gives its own verdict`() {
        assertEquals(StartVerdict.Printing, StartCheck.verdict(job(QueueStatus.PRINTING)))
        assertEquals(StartVerdict.Printing, StartCheck.verdict(job(QueueStatus.COMPLETED)))
        assertEquals(StartVerdict.Waiting, StartCheck.verdict(job()))
        assertEquals(StartVerdict.Blocked(sdCard), StartCheck.verdict(job(reason = sdCard)))
        assertEquals(StartVerdict.Failed("Out of filament"), StartCheck.verdict(job(QueueStatus.FAILED, error = "Out of filament")))
        assertEquals(StartVerdict.Failed("Bambuddy gave no reason."), StartCheck.verdict(job(QueueStatus.CANCELLED)))
        assertEquals(StartVerdict.Gone, StartCheck.verdict(null))
    }

    @Test
    fun `the words name the reason and say it will not print`() {
        val blocked = StartCheck.words(StartVerdict.Blocked(sdCard), "X2D")
        assertTrue(blocked.startsWith("It won't print yet."))
        assertTrue(blocked.contains("SD card"))
        assertTrue(StartCheck.words(StartVerdict.Failed("boom"), "X2D").startsWith("It won't print."))
        assertEquals("Printing has started on X2D.", StartCheck.words(StartVerdict.Printing, "X2D"))
        assertTrue(StartCheck.isProblem(StartVerdict.Blocked("x")) && StartCheck.isProblem(StartVerdict.Gone))
        assertFalse(StartCheck.isProblem(StartVerdict.Printing) || StartCheck.isProblem(StartVerdict.Waiting))
    }

    @Test
    fun `the watcher stops as soon as the job prints`() = runTest {
        val queue = ScriptedQueue(listOf(listOf(job()), listOf(job(QueueStatus.PRINTING))))
        assertEquals(StartVerdict.Printing, StartWatcher(queue).watch(1))
        assertEquals(2, queue.reads)
    }

    @Test
    fun `the watcher reports a reason that stays, without waiting out the full time`() = runTest {
        val queue = ScriptedQueue(listOf(listOf(job(reason = sdCard))))
        assertEquals(StartVerdict.Blocked(sdCard), StartWatcher(queue).watch(1))
        assertEquals(3, queue.reads)
    }

    @Test
    fun `a reason that clears is not reported and a failure is reported at once`() = runTest {
        val clears =
            ScriptedQueue(listOf(listOf(job(reason = "Waiting for the printer")), listOf(job()), listOf(job(QueueStatus.PRINTING))))
        assertEquals(StartVerdict.Printing, StartWatcher(clears).watch(1))
        val fails = ScriptedQueue(listOf(listOf(job(QueueStatus.FAILED, error = "Boom"))))
        assertEquals(StartVerdict.Failed("Boom"), StartWatcher(fails).watch(1))
        assertEquals(1, fails.reads)
    }

    @Test
    fun `with no news the last verdict is returned after the limit`() = runTest {
        val queue = ScriptedQueue(listOf(listOf(job())))
        assertEquals(StartVerdict.Waiting, StartWatcher(queue, maxPolls = 4).watch(1))
        assertEquals(4, queue.reads)
    }
}
