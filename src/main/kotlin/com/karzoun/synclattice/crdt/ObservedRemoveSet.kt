package com.karzoun.synclattice.crdt

import com.karzoun.synclattice.model.Dot
import com.karzoun.synclattice.model.SetAdd
import com.karzoun.synclattice.model.SetRemove

class ObservedRemoveSet {
    private val adds = mutableMapOf<String, MutableSet<Dot>>()
    private val tombstones = mutableSetOf<Dot>()

    fun apply(add: SetAdd) {
        if (add.id in tombstones) return
        adds.getOrPut(add.element) { mutableSetOf() }.add(add.id)
    }

    fun apply(remove: SetRemove) {
        tombstones.addAll(remove.removedDots)
        val dots = adds[remove.element] ?: return
        dots.removeAll(remove.removedDots)
        if (dots.isEmpty()) {
            adds.remove(remove.element)
        }
    }

    fun observedDots(element: String): Set<Dot> = adds[element]?.toSet() ?: emptySet()

    fun contains(element: String): Boolean = !adds[element].isNullOrEmpty()

    fun elements(): Set<String> = adds.filterValues { it.isNotEmpty() }.keys.toSortedSet()
}
