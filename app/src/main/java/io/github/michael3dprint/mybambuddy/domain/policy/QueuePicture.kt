package io.github.michael3dprint.mybambuddy.domain.policy

import io.github.michael3dprint.mybambuddy.domain.model.QueueItem

/** Which picture a queue job shows (design 41): its library file's, else its archive's, else none. */
object QueuePicture {
    /** Where a job's picture comes from. */
    sealed interface Source {
        /** A library file's thumbnail. */
        data class Library(val id: Int) : Source

        /** An archive's thumbnail. */
        data class Archive(val id: Int) : Source
    }

    /** The picture source for [item], or null when it has no file. */
    fun sourceOf(item: QueueItem): Source? = item.libraryFileId?.let { Source.Library(it) } ?: item.archiveId?.let { Source.Archive(it) }
}
