package io.github.michael3dprint.mybambuddy.data.store

import com.squareup.moshi.JsonAdapter
import java.io.File
import java.io.IOException

/** What is written to the file: schema version + save time + the data. */
data class CacheEnvelope<T>(val version: Int, val savedAt: Long, val data: T)

/** A cached value plus when it was saved (epoch millis). */
data class Cached<T>(val value: T, val savedAt: Long)

/**
 * One JSON file holding the last good copy of something. Safe against crashes and tampering:
 * writes go to a temp file then an atomic rename (no half-written file), files above [maxBytes] are never written
 * or read, and anything unreadable is treated as "no cache" instead of crashing. Holds no secrets.
 */
class JsonCacheStore<T : Any>(
    private val file: File,
    private val adapter: JsonAdapter<CacheEnvelope<T>>,
    private val maxBytes: Long = 1_048_576L,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    @Synchronized
    fun write(value: T) {
        try {
            val json = adapter.toJson(CacheEnvelope(VERSION, clock(), value))
            if (json.length > maxBytes) return
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.writeText(json)
            if (!temp.renameTo(file)) {
                temp.delete()
                throw IOException("rename failed")
            }
        } catch (_: IOException) {
            // The cache is best effort; the live data was already delivered.
        }
    }

    /** The cached value, or null if there is none, it is older than [maxAgeMs], too big, from another version or unreadable. */
    @Synchronized
    fun read(maxAgeMs: Long = DEFAULT_MAX_AGE_MS): Cached<T>? {
        if (!file.exists() || file.length() > maxBytes) return null
        return try {
            val envelope = adapter.fromJson(file.readText()) ?: return null
            if (envelope.version != VERSION || clock() - envelope.savedAt > maxAgeMs) null else Cached(envelope.data, envelope.savedAt)
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        /** The cache file format version; a file with a different version is ignored. */
        const val VERSION = 1

        /** The default age limit for a saved copy: 7 days. */
        const val DEFAULT_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
    }
}

/** Builds a store for [type] using [moshi]; the file lives under [dir]. */
fun <T : Any> cacheStore(dir: File, name: String, moshi: com.squareup.moshi.Moshi, type: java.lang.reflect.Type): JsonCacheStore<T> {
    val envelopeType = com.squareup.moshi.Types.newParameterizedType(CacheEnvelope::class.java, type)
    val adapter: JsonAdapter<CacheEnvelope<T>> = moshi.adapter(envelopeType)
    return JsonCacheStore(File(dir, "$name.json"), adapter)
}
