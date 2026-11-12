package io.github.michael3dprint.mybambuddy.platform.print

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import io.github.michael3dprint.mybambuddy.domain.print.PrintSource
import java.io.IOException
import java.io.InputStream

/** A file picked with the system file picker. It is read through the picker's own grant, so no storage permission is needed. */
class UriPrintSource private constructor(
    private val resolver: ContentResolver,
    private val uri: Uri,
    override val name: String,
    override val size: Long?,
) : PrintSource {
    override fun open(): InputStream = resolver.openInputStream(uri) ?: throw IOException("The file could not be opened.")

    companion object {
        /** Reads the name and size the picker reports for [uri]. */
        fun from(resolver: ContentResolver, uri: Uri): UriPrintSource {
            var name = uri.lastPathSegment.orEmpty()
            var size: Long? = null
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameAt = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeAt = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameAt >= 0) cursor.getString(nameAt)?.let { name = it }
                    if (sizeAt >= 0 && !cursor.isNull(sizeAt)) size = cursor.getLong(sizeAt)
                }
            }
            return UriPrintSource(resolver, uri, name, size)
        }
    }
}
