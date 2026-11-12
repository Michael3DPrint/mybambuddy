package io.github.michael3dprint.mybambuddy.data.store

import io.github.michael3dprint.mybambuddy.domain.slicing.SliceMemory
import io.github.michael3dprint.mybambuddy.domain.slicing.SliceMemoryStore

/** Keeps the slice memory in a small JSON file (no secrets, size-capped, written atomically); it never expires. */
class SliceMemoryFileStore(private val store: JsonCacheStore<SliceMemory>) : SliceMemoryStore {
    override fun load(): SliceMemory = store.read(Long.MAX_VALUE)?.value ?: SliceMemory()

    override fun save(memory: SliceMemory) = store.write(memory)
}
