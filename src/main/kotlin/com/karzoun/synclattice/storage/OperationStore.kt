package com.karzoun.synclattice.storage

import com.karzoun.synclattice.model.Operation

interface OperationStore : AutoCloseable {
    fun append(operation: Operation): Boolean

    fun loadAll(): List<Operation>
}

class OperationConflictException(message: String) : IllegalStateException(message)
