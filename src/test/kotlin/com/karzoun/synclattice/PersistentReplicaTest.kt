package com.karzoun.synclattice

import com.karzoun.synclattice.clock.VectorClock
import com.karzoun.synclattice.model.Dot
import com.karzoun.synclattice.model.SetAdd
import com.karzoun.synclattice.model.SetRemove
import com.karzoun.synclattice.replica.PersistentReplica
import com.karzoun.synclattice.storage.OperationConflictException
import com.karzoun.synclattice.storage.SQLiteOperationStore
import com.karzoun.synclattice.sync.ReplicaSynchronizer
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PersistentReplicaTest {
    @Test
    fun `schema initialization is idempotent`() {
        withDatabase { database ->
            SQLiteOperationStore(database).use { first ->
                assertEquals(SQLiteOperationStore.SCHEMA_VERSION, first.schemaVersion())
            }
            SQLiteOperationStore(database).use { second ->
                assertEquals(SQLiteOperationStore.SCHEMA_VERSION, second.schemaVersion())
                assertTrue(second.loadAll().isEmpty())
            }
        }
    }

    @Test
    fun `restart reconstructs materialized state and local sequence`() = runBlocking {
        withDatabaseSuspend { database ->
            PersistentReplica.open("device-a", SQLiteOperationStore(database)).use { replica ->
                replica.add("alpha")
                replica.writeRegister("saved", 42)
            }

            PersistentReplica.open("device-a", SQLiteOperationStore(database)).use { restored ->
                val snapshot = restored.snapshot()
                assertEquals(setOf("alpha"), snapshot.setElements)
                assertEquals("saved", snapshot.registerValue)
                assertEquals(2, snapshot.operationCount)

                val next = restored.add("beta")
                assertEquals(3L, next.id.sequence)
            }
        }
    }

    @Test
    fun `duplicate id with different content is rejected`() {
        withDatabase { database ->
            SQLiteOperationStore(database).use { store ->
                val first = SetAdd(Dot("a", 1), VectorClock.empty(), "alpha")
                val conflicting = SetAdd(Dot("a", 1), VectorClock.empty(), "beta")

                assertTrue(store.append(first))
                assertFalse(store.append(first))
                assertFailsWith<OperationConflictException> { store.append(conflicting) }
                assertEquals(listOf(first), store.loadAll())
            }
        }
    }

    @Test
    fun `remove-before-add order remains removed after restart`() = runBlocking {
        withDatabaseSuspend { database ->
            val add = SetAdd(Dot("z-add", 1), VectorClock.empty(), "x")
            val remove = SetRemove(Dot("a-remove", 1), VectorClock.empty(), "x", setOf(add.id))

            SQLiteOperationStore(database).use { store ->
                assertTrue(store.append(remove))
                assertTrue(store.append(add))
            }

            PersistentReplica.open("reader", SQLiteOperationStore(database)).use { restored ->
                assertFalse("x" in restored.snapshot().setElements)
            }
        }
    }

    @Test
    fun `offline persistent replicas reconcile and survive restart`() = runBlocking {
        val directory = Files.createTempDirectory("synclattice-persistent-sync")
        val leftDatabase = directory.resolve("left.db")
        val rightDatabase = directory.resolve("right.db")
        try {
            val left = PersistentReplica.open("left", SQLiteOperationStore(leftDatabase))
            val right = PersistentReplica.open("right", SQLiteOperationStore(rightDatabase))
            left.use { leftReplica ->
                right.use { rightReplica ->
                    leftReplica.add("left-only")
                    leftReplica.writeRegister("left", 100)
                    rightReplica.add("right-only")
                    rightReplica.writeRegister("right", 100)

                    val report = ReplicaSynchronizer().reconcile(leftReplica, rightReplica, batchSize = 1)
                    assertTrue(report.rounds >= 2)
                    assertEquals(leftReplica.snapshot(), rightReplica.snapshot())
                }
            }

            PersistentReplica.open("left", SQLiteOperationStore(leftDatabase)).use { leftRestored ->
                PersistentReplica.open("right", SQLiteOperationStore(rightDatabase)).use { rightRestored ->
                    assertEquals(leftRestored.snapshot(), rightRestored.snapshot())
                    assertEquals(setOf("left-only", "right-only"), leftRestored.snapshot().setElements)
                    assertEquals("right", leftRestored.snapshot().registerValue)
                }
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    private fun withDatabase(block: (Path) -> Unit) {
        val directory = Files.createTempDirectory("synclattice-sqlite")
        val database = directory.resolve("replica.db")
        try {
            block(database)
        } finally {
            database.deleteIfExists()
            directory.toFile().deleteRecursively()
        }
    }

    private suspend fun withDatabaseSuspend(block: suspend (Path) -> Unit) {
        val directory = Files.createTempDirectory("synclattice-sqlite")
        val database = directory.resolve("replica.db")
        try {
            block(database)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
