package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.EngineFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Gen3SaveBlock2EngineDiscriminatorTest {
    private val tie = setOf(EngineFamily.FIRERED_LEAFGREEN, EngineFamily.EMERALD)

    // pokefirered GetMoney: ldr r1,=ptr; ldr r1,[r1]; movs r2,#0xF2; lsls r2,#4; adds r1,r2;
    // ldr r0,[r0]; ldr r1,[r1]; eors r0,r1; bx lr
    private val fireRedGetMoney = intArrayOf(0x4904, 0x6809, 0x22F2, 0x0112, 0x1889, 0x6800, 0x6809, 0x4048, 0x4770)

    // pokeemerald GetMoney: ldr r1,=ptr; ldr r1,[r1]; adds r1,#0xAC; ldr r0,[r0]; ldr r1,[r1]; eors r0,r1; bx lr
    private val emeraldGetMoney = intArrayOf(0x4903, 0x6809, 0x31AC, 0x6800, 0x6809, 0x4048, 0x4770)

    @Test
    fun fireRedKeyOffsetSelectsFireRed() {
        val rom = rom(fireRedGetMoney to 0x100)
        assertEquals(setOf(0xF20), Gen3SaveBlock2EngineDiscriminator.keyOffsets(rom))
        assertEquals(EngineFamily.FIRERED_LEAFGREEN, Gen3SaveBlock2EngineDiscriminator.resolve(rom, tie))
    }

    @Test
    fun emeraldKeyOffsetSelectsEmerald() {
        val rom = rom(emeraldGetMoney to 0x100, emeraldGetMoney to 0x200)
        assertEquals(EngineFamily.EMERALD, Gen3SaveBlock2EngineDiscriminator.resolve(rom, tie))
    }

    @Test
    fun conflictingOrMissingConsumersKeepTheTie() {
        assertNull(Gen3SaveBlock2EngineDiscriminator.resolve(rom(fireRedGetMoney to 0x100, emeraldGetMoney to 0x200), tie))
        assertNull(Gen3SaveBlock2EngineDiscriminator.resolve(rom(), tie))
        // A non-IWRAM literal is not a save-block pointer.
        assertNull(Gen3SaveBlock2EngineDiscriminator.resolve(rom(fireRedGetMoney to 0x100, literal = 0x02000000), tie))
        // An offset outside both source layouts gives no answer.
        val otherOffset = emeraldGetMoney.copyOf().also { it[2] = 0x3150 }
        assertNull(Gen3SaveBlock2EngineDiscriminator.resolve(rom(otherOffset to 0x100), tie))
    }

    @Test
    fun answersOnlyAnExactFireRedEmeraldTie() {
        val rom = rom(fireRedGetMoney to 0x100)
        assertNull(Gen3SaveBlock2EngineDiscriminator.resolve(rom, setOf(EngineFamily.FIRERED_LEAFGREEN, EngineFamily.RUBY_SAPPHIRE)))
        assertNull(Gen3SaveBlock2EngineDiscriminator.resolve(rom, tie + EngineFamily.RUBY_SAPPHIRE))
    }

    /** Places each Thumb sequence and points its first literal load at an IWRAM save-block pointer. */
    private fun rom(vararg code: Pair<IntArray, Int>, literal: Int = 0x0300500C): RomImage {
        val bytes = ByteArray(0x1000)
        for ((halfwords, at) in code) {
            halfwords.forEachIndexed { index, value ->
                bytes[at + index * 2] = value.toByte()
                bytes[at + index * 2 + 1] = (value ushr 8).toByte()
            }
            val first = halfwords[0]
            val target = ((at + 4) and 3.inv()) + (first and 0xFF) * 4
            repeat(4) { bytes[target + it] = (literal ushr (it * 8)).toByte() }
        }
        return RomImage(bytes)
    }
}
