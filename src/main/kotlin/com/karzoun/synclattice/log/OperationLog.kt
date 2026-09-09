package com.karzoun.synclattice.log

import com.karzoun.synclattice.model.Dot
import com.karzoun.synclattice.model.Operation

class OperationLog {
    private val entries = linkedMapOf<Dot, Operation>()

    fun append(operation: Operation): Boolean {
        if (entries.containsKey(operation.id)) return false
        entries[operation.id] = operation
        return true
    }

    fun contains(dot: Dot): Boolean = entries.containsKey(dot)

    fun knownDots(): Set<Dot> = entries.keys.toSet()

    fun all(): List<Operation> = entries.values.sortedBy(Operation::id)

    fun missingFrom(known: Set<Dot>, limit: Int): List<Operation> {
        require(limit in 1..MAX_BATCH_SIZE) { "limit must be between 1 and $MAX_BATCH_SIZE" }
        return entries.values
            .asSequence()
            .filter { it.id !in known }
            .sortedBy(Operation::id)
            .take(limit)
            .toList()
    }

    fun size(): Int = entries.size

    companion object {
        const val MAX_BATCH_SIZE: Int = 1024
    }
}
