package io.github.michael3dprint.mybambuddy.testutil

import io.github.michael3dprint.mybambuddy.domain.slicing.SliceMemory
import io.github.michael3dprint.mybambuddy.domain.slicing.SliceMemoryStore

/** A slice memory kept in a field, counting saves, for tests. */
class InMemorySliceMemory(var memory: SliceMemory = SliceMemory()) : SliceMemoryStore {
    var saves = 0
        private set

    override fun load(): SliceMemory = memory

    override fun save(memory: SliceMemory) {
        this.memory = memory
        saves++
    }
}
