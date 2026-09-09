package com.karzoun.synclattice.clock

import com.karzoun.synclattice.model.Dot

enum class ClockRelation {
    BEFORE,
    EQUAL,
    AFTER,
    CONCURRENT,
}

class VectorClock private constructor(
    private val counters: Map<String, Long>,
) {
    companion object {
        fun empty(): VectorClock = VectorClock(emptyMap())

        fun of(vararg entries: Pair<String, Long>): VectorClock =
            VectorClock(entries.toMap()).validated()
    }

    fun counter(actor: String): Long = counters[actor] ?: 0L

    fun observe(dot: Dot): VectorClock {
        val next = counters.toMutableMap()
        next[dot.actor] = maxOf(counter(dot.actor), dot.sequence)
        return VectorClock(next)
    }

    fun increment(actor: String): Pair<VectorClock, Dot> {
        require(actor.isNotBlank()) { "actor must not be blank" }
        val nextSequence = counter(actor) + 1L
        val dot = Dot(actor, nextSequence)
        return observe(dot) to dot
    }

    fun merge(other: VectorClock): VectorClock {
        val actors = counters.keys + other.counters.keys
        return VectorClock(actors.associateWith { actor -> maxOf(counter(actor), other.counter(actor)) })
    }

    fun relationTo(other: VectorClock): ClockRelation {
        val actors = counters.keys + other.counters.keys
        var less = false
        var greater = false

        for (actor in actors) {
            val mine = counter(actor)
            val theirs = other.counter(actor)
            less = less || mine < theirs
            greater = greater || mine > theirs
        }

        return when {
            less && greater -> ClockRelation.CONCURRENT
            less -> ClockRelation.BEFORE
            greater -> ClockRelation.AFTER
            else -> ClockRelation.EQUAL
        }
    }

    fun asMap(): Map<String, Long> = counters.toSortedMap()

    private fun validated(): VectorClock {
        require(counters.keys.none(String::isBlank)) { "clock actors must not be blank" }
        require(counters.values.all { it >= 0L }) { "clock counters must be non-negative" }
        return this
    }

    override fun equals(other: Any?): Boolean = other is VectorClock && counters == other.counters

    override fun hashCode(): Int = counters.hashCode()

    override fun toString(): String = asMap().toString()
}
