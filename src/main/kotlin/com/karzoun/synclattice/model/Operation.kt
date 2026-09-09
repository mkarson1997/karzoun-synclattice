package com.karzoun.synclattice.model

import com.karzoun.synclattice.clock.VectorClock

sealed interface Operation {
    val id: Dot
    val context: VectorClock
}

data class RegisterWrite(
    override val id: Dot,
    override val context: VectorClock,
    val logicalTime: Long,
    val value: String,
) : Operation {
    init {
        require(logicalTime >= 0L) { "logicalTime must be non-negative" }
    }
}

data class SetAdd(
    override val id: Dot,
    override val context: VectorClock,
    val element: String,
) : Operation {
    init {
        require(element.isNotBlank()) { "element must not be blank" }
    }
}

data class SetRemove(
    override val id: Dot,
    override val context: VectorClock,
    val element: String,
    val removedDots: Set<Dot>,
) : Operation {
    init {
        require(element.isNotBlank()) { "element must not be blank" }
    }
}
