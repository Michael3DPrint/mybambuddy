package io.github.michael3dprint.mybambuddy.domain.policy

import io.github.michael3dprint.mybambuddy.domain.model.QueueItem

/** Pure reordering of waiting jobs. The server rejects duplicate positions, so results are always unique and contiguous. */
object QueueOrdering {
    /** (id, newPosition) pairs for every pending job of the same printer as [itemId] after moving it one step. */
    fun move(items: List<QueueItem>, itemId: Int, up: Boolean): List<Pair<Int, Int>>? {
        val target = items.firstOrNull { it.id == itemId && it.isPending } ?: return null
        val sameQueue = items.filter { it.isPending && it.printerId == target.printerId }.sortedBy { it.position }
        val index = sameQueue.indexOfFirst { it.id == itemId }
        val swapWith = if (up) index - 1 else index + 1
        if (swapWith !in sameQueue.indices) return null
        val ordered = sameQueue.toMutableList().also { java.util.Collections.swap(it, index, swapWith) }
        val base = sameQueue.minOf { it.position }
        return ordered.mapIndexed { i, item -> item.id to base + i }
    }
}
