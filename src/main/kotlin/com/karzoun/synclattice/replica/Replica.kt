package com.karzoun.synclattice.replica

import com.karzoun.synclattice.clock.VectorClock
import com.karzoun.synclattice.crdt.LwwRegister
import com.karzoun.synclattice.crdt.ObservedRemoveSet
import com.karzoun.synclattice.log.OperationLog
import com.karzoun.synclattice.model.Dot
import com.karzoun.synclattice.model.Operation
import com.karzoun.synclattice.model.RegisterWrite
import com.karzoun.synclattice.model.SetAdd
import com.karzoun.synclattice.model.SetRemove
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class Replica(
    val actor: String,
) {
    init {
        require(actor.isNotBlank()) { "actor must not be blank" }
    }

    private val mutex = Mutex()
    private val log = OperationLog()
    private val register = LwwRegister()
    private val set = ObservedRemoveSet()
    private var clock = VectorClock.empty()
    private var localSequence = 0L

    suspend fun writeRegister(value: String, logicalTime: Long): RegisterWrite = mutex.withLock {
        val operation = RegisterWrite(nextDot(), clock, logicalTime, value)
        applyLocked(operation)
        operation
    }

    suspend fun add(element: String): SetAdd = mutex.withLock {
        val operation = SetAdd(nextDot(), clock, element)
        applyLocked(operation)
        operation
    }

    suspend fun remove(element: String): SetRemove = mutex.withLock {
        val operation = SetRemove(nextDot(), clock, element, set.observedDots(element))
        applyLocked(operation)
        operation
    }

    suspend fun applyRemote(operation: Operation): Boolean = mutex.withLock {
        applyLocked(operation)
    }

    suspend fun knownDots(): Set<Dot> = mutex.withLock { log.knownDots() }

    suspend fun operationsMissingFrom(known: Set<Dot>, limit: Int): List<Operation> =
        mutex.withLock { log.missingFrom(known, limit) }

    suspend fun snapshot(): ReplicaSnapshot = mutex.withLock {
        ReplicaSnapshot(
            registerValue = register.value(),
            setElements = set.elements(),
            vectorClock = clock,
            knownDots = log.knownDots(),
            operationCount = log.size(),
        )
    }

    private fun nextDot(): Dot {
        val floor = maxOf(localSequence, clock.counter(actor))
        localSequence = floor + 1L
        return Dot(actor, localSequence)
    }

    private fun applyLocked(operation: Operation): Boolean {
        if (!log.append(operation)) return false

        when (operation) {
            is RegisterWrite -> register.apply(operation)
            is SetAdd -> set.apply(operation)
            is SetRemove -> set.apply(operation)
        }

        clock = clock.merge(operation.context).observe(operation.id)
        if (operation.id.actor == actor) {
            localSequence = maxOf(localSequence, operation.id.sequence)
        }
        return true
    }
}

data class ReplicaSnapshot(
    val registerValue: String?,
    val setElements: Set<String>,
    val vectorClock: VectorClock,
    val knownDots: Set<Dot>,
    val operationCount: Int,
)
