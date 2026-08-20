package com.enrpau.dualscreendex.parser.dataset.abilities.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImmediatePostDominatorTest {
    @Test
    fun `finds the join in a full-size decoded control-flow graph`() {
        val nodeCount = 4_096
        val exit = nodeCount
        val successors = buildMap<Int, Set<Int>> {
            put(0, setOf(1, 2))
            put(1, setOf(2))
            for (node in 2 until nodeCount - 1) {
                put(node, setOf(node + 1))
            }
            put(nodeCount - 1, emptySet())
        }

        assertEquals(2, ImmediatePostDominator.find(0, exit, successors))
    }

    @Test
    fun `fails closed when an explored path cannot reach an exit`() {
        val successors = mapOf(
            0 to setOf(1, 2),
            1 to setOf(3),
            2 to setOf(2),
            3 to emptySet(),
        )

        assertNull(ImmediatePostDominator.find(0, 4, successors))
    }
}
