package com.darkaxt.dualdex.catalog

import java.nio.file.Path
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.AssumptionViolatedException
import org.junit.Test

class OriginalRomTestAccessTest {
    @Test
    fun `missing opt-in skips before invoking reader`() {
        var calls = 0
        assertThrows(AssumptionViolatedException::class.java) {
            OriginalRomTestAccess.readIfEnabled(null) { calls++; byteArrayOf(1) }
        }
        assertEquals(0, calls)
    }

    @Test
    fun `non-exact opt-ins skip before invoking reader`() {
        var calls = 0
        for (flag in listOf("", "false", "TRUE", "1", " true", "true ")) {
            assertThrows(AssumptionViolatedException::class.java) {
                OriginalRomTestAccess.readIfEnabled(flag) { calls++; byteArrayOf(1) }
            }
        }
        assertEquals(0, calls)
    }

    @Test
    fun `explicit opt-in invokes generated reader exactly once`() {
        var calls = 0
        val bytes = OriginalRomTestAccess.readIfEnabled("true") { calls++; byteArrayOf(1, 2) }
        assertArrayEquals(byteArrayOf(1, 2), bytes)
        assertEquals(1, calls)
    }

    @Test
    fun `explicit opt-in preserves reader failure`() {
        val expected = IllegalStateException("generated read failure")
        val actual = assertThrows(IllegalStateException::class.java) {
            OriginalRomTestAccess.readIfEnabled("true") { throw expected }
        }
        assertSame(expected, actual)
    }

    @Test
    fun `filesystem access is guarded before opening a nonexistent path`() {
        assertThrows(AssumptionViolatedException::class.java) {
            OriginalRomTestAccess.readAllBytes(Path.of("original-read-must-not-be-attempted.gba"), null)
        }
    }
}
