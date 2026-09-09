package com.karzoun.synclattice.crdt

import com.karzoun.synclattice.model.RegisterWrite

class LwwRegister {
    private var current: RegisterWrite? = null

    fun apply(write: RegisterWrite) {
        val existing = current
        if (existing == null || compare(write, existing) > 0) {
            current = write
        }
    }

    fun value(): String? = current?.value

    fun winner(): RegisterWrite? = current

    private fun compare(left: RegisterWrite, right: RegisterWrite): Int {
        val time = left.logicalTime.compareTo(right.logicalTime)
        if (time != 0) return time

        val actor = left.id.actor.compareTo(right.id.actor)
        if (actor != 0) return actor

        return left.id.sequence.compareTo(right.id.sequence)
    }
}
