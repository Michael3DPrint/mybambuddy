package io.github.michael3dprint.mybambuddy.domain.print

import java.io.InputStream

/** A file the user picked on the phone. It is read as a stream, so a large file is never held in memory. */
interface PrintSource {
    /** The file name, with its extension. */
    val name: String

    /** The size in bytes, or null when the phone does not say. */
    val size: Long?

    /** Opens a new stream over the file; the caller closes it. */
    fun open(): InputStream
}
