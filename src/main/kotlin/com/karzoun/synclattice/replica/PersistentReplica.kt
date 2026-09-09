package com.karzoun.synclattice.replica

import com.karzoun.synclattice.model.Dot
import com.karzoun.synclattice.model.Operation
import com.karzoun.synclattice.model.RegisterWrite
import com.karzoun.synclattice.model.SetAdd
import com.karzoun.synclattice.model.SetRemove
import com.karzoun.synclattice.storage.OperationStore
import com.karzoun.synclattice.sync.SyncPeer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class PersistentReplica private constructor(
    val actor: String,
    private val delegate: Replica,
    private val store: OperationStore,
) : SyncPeer, AutoCloseable {
    private val mutationMutex = Mutex()

    companion object {
        suspend fun open(actor: String, store: OperationStore): PersistentReplica {
            require(actor.isNotBlank()) { "actor must not be blank" }
            val delegate = Replica(actor)
            try {
                val persisted = withContext(Dispatchers.IO) { store.loadAll() }
                for (operation in persisted) {
                    check(delegate.applyRemote(operation)) { "durable store contained duplicate operation ids" }
                }
                return PersistentReplica(actor, delegate, store)
            } catch (failure: Throwable) {
                store.close()
                throw failure
            }
        }
    }

    suspend fun writeRegister(value: String, logicalTime: Long): RegisterWrite = mutationMutex.withLock {
        val snapshot = delegate.snapshot()
        val operation = RegisterWrite(nextDot(snapshot), snapshot.vectorClock, logicalTime, value)
        persistThenApply(operation)
        operation
    }

    suspend fun add(element: String): SetAdd = mutationMutex.withLock {
        val snapshot = delegate.snapshot()
        val operation = SetAdd(nextDot(snapshot), snapshot.vectorClock, element)
        persistThenApply(operation)
        operation
    }

    suspend fun remove(element: String): SetRemove = mutationMutex.withLock {
        val snapshot = delegate.snapshot()
        val operation = SetRemove(
            id = nextDot(snapshot),
            context = snapshot.vectorClock,
            element = element,
            removedDots = delegate.observedDots(element),
        )
        persistThenApply(operation)
        operation
    }

    override suspend fun applyRemote(operation: Operation): Boolean = mutationMutex.withLock {
        val persisted = withContext(Dispatchers.IO) { store.append(operation) }
        val applied = delegate.applyRemote(operation)
        check(persisted == applied) {
            "durable and materialized operation sets diverged for ${operation.id}"
        }
        applied
    }

    override suspend fun knownDots(): Set<Dot> = delegate.knownDots()

    override suspend fun operationsMissingFrom(known: Set<Dot>, limit: Int): List<Operation> =
        delegate.operationsMissingFrom(known, limit)

    suspend fun snapshot(): ReplicaSnapshot = mutationMutex.withLock { delegate.snapshot() }

    override fun close() {
        store.close()
    }

    private fun nextDot(snapshot: ReplicaSnapshot): Dot =
        Dot(actor, snapshot.vectorClock.counter(actor) + 1L)

    private suspend fun persistThenApply(operation: Operation) {
        val persisted = withContext(Dispatchers.IO) { store.append(operation) }
        check(persisted) { "new local operation unexpectedly already existed: ${operation.id}" }
        check(delegate.applyRemote(operation)) { "new durable local operation was already materialized: ${operation.id}" }
    }
}
