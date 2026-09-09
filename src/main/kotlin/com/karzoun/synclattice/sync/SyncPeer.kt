package com.karzoun.synclattice.sync

import com.karzoun.synclattice.model.Dot
import com.karzoun.synclattice.model.Operation

interface SyncPeer {
    suspend fun knownDots(): Set<Dot>

    suspend fun operationsMissingFrom(known: Set<Dot>, limit: Int): List<Operation>

    suspend fun applyRemote(operation: Operation): Boolean
}
