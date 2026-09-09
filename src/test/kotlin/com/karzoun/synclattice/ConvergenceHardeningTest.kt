package com.karzoun.synclattice

import com.karzoun.synclattice.replica.Replica
import com.karzoun.synclattice.sync.ReplicaSynchronizer
import kotlinx.coroutines.runBlocking
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConvergenceHardeningTest {
    @Test
    fun `exact dots repair a delivery hole hidden by vector clock maximum`() = runBlocking {
        val source = Replica("a")
        val first = source.add("one")
        val second = source.add("two")
        val third = source.writeRegister("latest", 30)

        val target = Replica("target")
        target.applyRemote(third)

        val before = target.snapshot()
        assertEquals(3L, before.vectorClock.counter("a"))
        assertEquals(setOf(third.id), before.knownDots)
        assertTrue(first.id !in before.knownDots)
        assertTrue(second.id !in before.knownDots)

        val report = ReplicaSynchronizer().reconcile(source, target, batchSize = 1)

        assertEquals(2, report.transferredToRight)
        assertEquals(0, report.transferredToLeft)
        assertEquals(source.snapshot(), target.snapshot())
    }

    @Test
    fun `fixed-seed shuffled duplicate delivery always converges`() = runBlocking {
        val actorA = Replica("a")
        val aAdd = actorA.add("alpha")
        val aWrite = actorA.writeRegister("from-a", 100)
        val aRemove = actorA.remove("alpha")

        val actorB = Replica("b")
        val bAdd = actorB.add("beta")
        val bWrite = actorB.writeRegister("from-b", 100)
        val bGamma = actorB.add("gamma")
        val bRemove = actorB.remove("beta")

        val operations = listOf(aAdd, aWrite, aRemove, bAdd, bWrite, bGamma, bRemove)
        val baseline = Replica("baseline")
        for (operation in operations) {
            baseline.applyRemote(operation)
        }
        val expected = baseline.snapshot()
        assertEquals("from-b", expected.registerValue)
        assertEquals(setOf("gamma"), expected.setElements)

        for (seed in 0 until 32) {
            val target = Replica("target-$seed")
            val shuffled = operations.shuffled(Random(seed))
            for (operation in shuffled) {
                target.applyRemote(operation)
            }
            for (operation in shuffled.asReversed()) {
                target.applyRemote(operation)
            }

            assertEquals(expected, target.snapshot(), "convergence failed for deterministic seed $seed")
        }
    }
}
