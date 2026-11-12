package io.github.michael3dprint.mybambuddy.data.store

import io.github.michael3dprint.mybambuddy.domain.alerts.AlertMemory
import io.github.michael3dprint.mybambuddy.domain.alerts.AlertMemoryStore

/** Saves what the alert check saw last time in a small JSON file (no secrets, size-capped, atomic). */
class AlertMemoryFileStore(private val store: JsonCacheStore<AlertMemory>) : AlertMemoryStore {
    override fun load(): AlertMemory = store.read(Long.MAX_VALUE)?.value ?: AlertMemory()

    override fun save(memory: AlertMemory) = store.write(memory)
}
