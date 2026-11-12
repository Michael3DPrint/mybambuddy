package io.github.michael3dprint.mybambuddy.util

import android.content.Context
import android.graphics.Bitmap
import androidx.collection.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Keeps the last camera photo of each printer, in memory and in the app's private cache folder,
 * so it is still shown after leaving the screen or restarting the app. A photo is only replaced
 * when a new one has been fetched successfully.
 */
object SnapshotCache {
    private var dir: File? = null
    private val memory = LruCache<String, ImageBitmap>(12)

    fun init(context: Context) {
        dir = File(context.cacheDir, "snapshots").apply { mkdirs() }
    }

    fun getMemory(key: String): ImageBitmap? = memory.get(key)

    suspend fun loadFromDisk(key: String): ImageBitmap? = withContext(Dispatchers.IO) {
        val file = fileFor(key) ?: return@withContext null
        if (!file.exists()) return@withContext null
        val image = SafeBitmap.decodeFile(file.absolutePath)?.asImageBitmap() ?: return@withContext null
        memory.put(key, image)
        image
    }

    suspend fun put(key: String, bitmap: Bitmap) {
        memory.put(key, bitmap.asImageBitmap())
        withContext(Dispatchers.IO) {
            val file = fileFor(key) ?: return@withContext
            try {
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            } catch (_: Exception) {
                // Disk copy is best effort; the memory copy is already stored.
            }
        }
    }

    private fun fileFor(key: String): File? = dir?.let { File(it, key.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".jpg") }
}
