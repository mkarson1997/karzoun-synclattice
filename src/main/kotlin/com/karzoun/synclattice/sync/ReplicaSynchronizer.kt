package com.karzoun.synclattice.sync

import com.karzoun.synclattice.log.OperationLog
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class ReplicaSynchronizer {
    suspend fun reconcile(
        left: SyncPeer,
        right: SyncPeer,
        batchSize: Int = 128,
    ): SyncReport {
        require(batchSize in 1..OperationLog.MAX_BATCH_SIZE) {
            "batchSize must be between 1 and ${OperationLog.MAX_BATCH_SIZE}"
        }

        var rounds = 0
        var transferredToLeft = 0
        var transferredToRight = 0

        while (true) {
            rounds += 1
            val (leftKnown, rightKnown) = coroutineScope {
                val leftDeferred = async { left.knownDots() }
                val rightDeferred = async { right.knownDots() }
                leftDeferred.await() to rightDeferred.await()
            }

            val (toLeft, toRight) = coroutineScope {
                val leftDeferred = async { right.operationsMissingFrom(leftKnown, batchSize) }
                val rightDeferred = async { left.operationsMissingFrom(rightKnown, batchSize) }
                leftDeferred.await() to rightDeferred.await()
            }

            if (toLeft.isEmpty() && toRight.isEmpty()) break

            coroutineScope {
                launch {
                    for (operation in toLeft) {
                        left.applyRemote(operation)
                    }
                }
                launch {
                    for (operation in toRight) {
                        right.applyRemote(operation)
                    }
                }
            }

            transferredToLeft += toLeft.size
            transferredToRight += toRight.size
        }

        return SyncReport(
            rounds = rounds,
            transferredToLeft = transferredToLeft,
            transferredToRight = transferredToRight,
        )
    }
}

data class SyncReport(
    val rounds: Int,
    val transferredToLeft: Int,
    val transferredToRight: Int,
)
