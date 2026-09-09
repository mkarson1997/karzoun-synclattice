package com.karzoun.synclattice

import com.karzoun.synclattice.clock.ClockRelation
import com.karzoun.synclattice.clock.VectorClock
import com.karzoun.synclattice.crdt.ObservedRemoveSet
import com.karzoun.synclattice.model.Dot
import com.karzoun.synclattice.model.SetAdd
import com.karzoun.synclattice.model.SetRemove
import com.karzoun.synclattice.replica.Replica
import com.karzoun.synclattice.sync.ReplicaSynchronizer
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncLatticeCoreTest {
    @Test
    fun `vector clocks expose causal and concurrent relations`() {
        val base = VectorClock.empty()
        val (a1, _) = base.increment("a")
        val (a2, _) = a1.increment("a")
        val (b1, _) = base.increment("b")

        assertEquals(ClockRelation.BEFORE, a1.relationTo(a2))
        assertEquals(ClockRelation.AFTER, a2.relationTo(a1))
        assertEquals(ClockRelation.CONCURRENT, a1.relationTo(b1))
        assertEquals(ClockRelation.EQUAL, a2.merge(b1).relationTo(b1.merge(a2)))
    }

    @Test
    fun `LWW tie break is deterministic across opposite delivery order`() = runBlocking {
        val left = Replica("a")
        val right = Replica("b")
        val aWrite = left.writeRegister("from-a", 100)
        val bWrite = right.writeRegister("from-b", 100)

        left.applyRemote(bWrite)
        right.applyRemote(aWrite)

        assertEquals("from-b", left.snapshot().registerValue)
        assertEquals(left.snapshot().registerValue, right.snapshot().registerValue)
    }

    @Test
    fun `OR-Set remove only removes observed adds`() {
        val set = ObservedRemoveSet()
        val a1 = SetAdd(Dot("a", 1), VectorClock.empty(), "x")
        val b1 = SetAdd(Dot("b", 1), VectorClock.empty(), "x")
        set.apply(a1)
        val remove = SetRemove(Dot("a", 2), VectorClock.of("a" to 1), "x", setOf(a1.id))
        set.apply(b1)
        set.apply(remove)

        assertTrue(set.contains("x"), "concurrent add must survive observed remove")
        assertEquals(setOf(b1.id), set.observedDots("x"))
    }

    @Test
    fun `OR-Set tombstone survives remove-before-add delivery`() {
        val set = ObservedRemoveSet()
        val add = SetAdd(Dot("a", 1), VectorClock.empty(), "x")
        val remove = SetRemove(Dot("a", 2), VectorClock.of("a" to 1), "x", setOf(add.id))

        set.apply(remove)
        set.apply(add)

        assertFalse(set.contains("x"))
    }

    @Test
    fun `duplicate remote operation is idempotent`() = runBlocking {
        val source = Replica("source")
        val target = Replica("target")
        val operation = source.add("alpha")

        assertTrue(target.applyRemote(operation))
        assertFalse(target.applyRemote(operation))
        assertEquals(1, target.snapshot().operationCount)
        assertEquals(setOf("alpha"), target.snapshot().setElements)
    }

    @Test
    fun `opposite remote delivery orders converge`() = runBlocking {
        val origin = Replica("origin")
        val first = origin.add("alpha")
        val second = origin.add("beta")
        val remove = origin.remove("alpha")
        val write1 = origin.writeRegister("old", 10)
        val write2 = origin.writeRegister("new", 20)

        val operations = listOf(first, second, remove, write1, write2)
        val left = Replica("left")
        val right = Replica("right")

        operations.forEach { left.applyRemote(it) }
        operations.asReversed().forEach { right.applyRemote(it) }

        val leftSnapshot = left.snapshot()
        val rightSnapshot = right.snapshot()
        assertEquals(leftSnapshot.registerValue, rightSnapshot.registerValue)
        assertEquals(leftSnapshot.setElements, rightSnapshot.setElements)
        assertEquals(leftSnapshot.knownDots, rightSnapshot.knownDots)
        assertEquals("new", leftSnapshot.registerValue)
        assertEquals(setOf("beta"), leftSnapshot.setElements)
    }

    @Test
    fun `offline divergent replicas reconcile to one state`() = runBlocking {
        val left = Replica("left")
        val right = Replica("right")

        left.add("shared")
        left.add("left-only")
        left.writeRegister("left-value", 50)

        right.add("shared")
        right.add("right-only")
        right.writeRegister("right-value", 50)

        val report = ReplicaSynchronizer().reconcile(left, right, batchSize = 2)
        val leftSnapshot = left.snapshot()
        val rightSnapshot = right.snapshot()

        assertTrue(report.transferredToLeft > 0)
        assertTrue(report.transferredToRight > 0)
        assertTrue(report.rounds >= 2, "small batch must require multiple reconciliation rounds")
        assertEquals(leftSnapshot.registerValue, rightSnapshot.registerValue)
        assertEquals(leftSnapshot.setElements, rightSnapshot.setElements)
        assertEquals(leftSnapshot.knownDots, rightSnapshot.knownDots)
        assertEquals(setOf("left-only", "right-only", "shared"), leftSnapshot.setElements)
        assertEquals("right-value", leftSnapshot.registerValue)
    }

    @Test
    fun `reconciliation is idempotent after convergence`() = runBlocking {
        val left = Replica("left")
        val right = Replica("right")
        left.add("x")
        right.add("y")

        val synchronizer = ReplicaSynchronizer()
        synchronizer.reconcile(left, right, batchSize = 1)
        val second = synchronizer.reconcile(left, right, batchSize = 1)

        assertEquals(0, second.transferredToLeft)
        assertEquals(0, second.transferredToRight)
        assertEquals(left.snapshot(), right.snapshot().copy(vectorClock = left.snapshot().vectorClock))
    }
}
