package com.darkaxt.dualdex.battle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Gen3MainStateResolverTest {
    private val resolver = Gen3MainStateResolver()

    @Test
    fun resolvesTheUniqueMainStateAndObservesTheOverworldCallbackTransition() {
        val iwram = ByteArray(0x8000)
        mainState(iwram, 0x1574, callback1 = 0x0807B025, callback2 = 0x08078E01, counter = 5108)

        val active = requireNotNull(resolver.resolve(iwram))
        assertEquals(0x1574, active.layout.offset)
        assertEquals(Gen3MainCallbacks(0x0807B025, 0x08078E01), active.callbacks)

        mainState(iwram, 0x1574, callback1 = 0x0816086D, callback2 = 0x08160D3D, counter = 9499)

        val overworld = requireNotNull(resolver.resolveKnown(iwram, active.layout))
        assertEquals(Gen3MainCallbacks(0x0816086D, 0x08160D3D), overworld.callbacks)
    }

    @Test
    fun rejectsAmbiguousOrStructurallyInvalidMainHeaders() {
        val ambiguous = ByteArray(0x8000)
        mainState(ambiguous, 0x100, 0x08000101, 0x08000201, 1)
        mainState(ambiguous, 0x200, 0x08000301, 0x08000401, 1)
        assertNull(resolver.resolve(ambiguous))

        val invalid = ByteArray(0x8000)
        mainState(invalid, 0x100, 0x08000101, 0x08000201, 1)
        putU16(invalid, 0x100 + 0x28, 0x8000)
        assertNull(resolver.resolve(invalid))
    }

    private fun mainState(bytes: ByteArray, offset: Int, callback1: Int, callback2: Int, counter: Int) {
        listOf(callback1, callback2, 0x08000301, 0x08000401, 0, 0, 0x08000501)
            .forEachIndexed { index, value -> putU32(bytes, offset + index * 4, value) }
        putU32(bytes, offset + 0x20, counter)
        putU32(bytes, offset + 0x24, counter)
        putU16(bytes, offset + 0x32, 40)
    }

    private fun putU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }

    private fun putU32(bytes: ByteArray, offset: Int, value: Int) {
        repeat(4) { index -> bytes[offset + index] = (value ushr (index * 8)).toByte() }
    }
}
