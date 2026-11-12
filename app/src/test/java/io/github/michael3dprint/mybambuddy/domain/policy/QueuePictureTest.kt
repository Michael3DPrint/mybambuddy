package io.github.michael3dprint.mybambuddy.domain.policy

import io.github.michael3dprint.mybambuddy.domain.model.QueueItem
import io.github.michael3dprint.mybambuddy.domain.model.QueueStatus
import io.github.michael3dprint.mybambuddy.domain.print.PrintEstimate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueuePictureTest {
    private fun job(lib: Int? = null, archive: Int? = null) =
        QueueItem(1, 1, "P", "j", QueueStatus.PENDING, 1, null, false, libraryFileId = lib, archiveId = archive)

    @Test
    fun `the library file wins over the archive and a job with neither has no picture`() {
        assertEquals(QueuePicture.Source.Library(4), QueuePicture.sourceOf(job(lib = 4, archive = 7)))
        assertEquals(QueuePicture.Source.Archive(7), QueuePicture.sourceOf(job(archive = 7)))
        assertNull(QueuePicture.sourceOf(job()))
    }

    @Test
    fun `the queue summary leaves out what is unknown`() {
        assertEquals("1 h 15 m · 31 g · PLA", PrintEstimate.summary(4496, 31.2, "PLA"))
        assertEquals("45 m", PrintEstimate.summary(2700, null, null))
        assertEquals("PLA", PrintEstimate.summary(null, 0.0, "PLA"))
        assertNull(PrintEstimate.summary(null, null, " "))
        assertNull(PrintEstimate.summary(0, 0.0, null))
    }
}
