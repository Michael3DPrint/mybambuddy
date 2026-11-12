package io.github.michael3dprint.mybambuddy.data.store

import io.github.michael3dprint.mybambuddy.domain.widget.WidgetSnapshot
import io.github.michael3dprint.mybambuddy.domain.widget.WidgetSnapshotStore

/** Keeps the widget's last snapshot in a small JSON file (printer names and states only; no address, no key). */
class WidgetSnapshotFileStore(private val store: JsonCacheStore<WidgetSnapshot>) : WidgetSnapshotStore {
    override fun load(): WidgetSnapshot? = store.read(Long.MAX_VALUE)?.value

    override fun save(snapshot: WidgetSnapshot) = store.write(snapshot)
}
