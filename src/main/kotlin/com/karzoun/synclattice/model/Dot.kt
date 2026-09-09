package com.karzoun.synclattice.model

data class Dot(
    val actor: String,
    val sequence: Long,
) : Comparable<Dot> {
    init {
        require(actor.isNotBlank()) { "actor must not be blank" }
        require(sequence > 0) { "sequence must be positive" }
    }

    override fun compareTo(other: Dot): Int {
        val actorComparison = actor.compareTo(other.actor)
        return if (actorComparison != 0) actorComparison else sequence.compareTo(other.sequence)
    }
}
