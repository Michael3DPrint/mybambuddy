package io.github.michael3dprint.mybambuddy.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * Decodes untrusted JPEG/PNG bytes without letting a huge image exhaust memory ("decompression bomb"):
 * the size is read first, absurd dimensions are refused, and large images are decoded at reduced size.
 */
object SafeBitmap {
    const val MAX_DIMENSION = 8_192
    const val TARGET_MAX_PIXELS = 1_000_000L

    /** Power-of-two sample size so the decoded image has at most about [maxPixels] pixels; null if the size is not acceptable. */
    fun sampleSizeFor(width: Int, height: Int, maxPixels: Long = TARGET_MAX_PIXELS): Int? {
        if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION) return null
        var sample = 1
        while ((width.toLong() / sample) * (height.toLong() / sample) > maxPixels) sample *= 2
        return sample
    }

    fun decode(bytes: ByteArray, maxPixels: Long = TARGET_MAX_PIXELS): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val sample = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxPixels) ?: return null
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    fun decodeFile(path: String, maxPixels: Long = TARGET_MAX_PIXELS): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val sample = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxPixels) ?: return null
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
