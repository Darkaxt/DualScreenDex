package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.family.EngineFamilyDefinitions
import com.enrpau.dualscreendex.parser.family.FamilyProbeState
import com.enrpau.dualscreendex.parser.family.IdentityRootsPhaseResult
import com.enrpau.dualscreendex.parser.family.IdentityRootsStrategy
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomHeader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class Gen2ClassicCoreContractTest {
    @Test fun acceptsOddStrideOnlyThroughCompleteClassicCopyAndEpilogue() {
        val result = requireNotNull(resolve(fixture(35)))
        assertEquals(251, result.speciesCount)
        assertEquals(35, result.tables.baseStats?.recordSize)
    }

    @Test fun runtimeOverwriteAuthorizesStoredPrefixMismatchWithIndependentAdjacentExtent() {
        val bytes = fixture(32, count = 253)
        bytes[BASE + 201 * 32] = 201.toByte()
        val result = requireNotNull(resolve(bytes))
        assertEquals(253, result.speciesCount)
        assertEquals(BASE + 253 * 32, result.tables.speciesNames?.offset)
    }

    @Test fun admitsSameCountCompiledGeometryDuringFamilyIdentityPhase() {
        val bytes = fixture(32)
        val state = IdentityRootsStrategy().execute(
            RomAnalysisSession(RomImage(bytes), RomHeader(Platform.GBC, "CRYSTAL")),
            EngineFamilyDefinitions.byFamily.getValue(EngineFamily.CRYSTAL), FamilyProbeState.empty(),
        )
        val identity = state.identityRoots as IdentityRootsPhaseResult.Resolved
        assertEquals(BASE, identity.tableResolution.tables.baseStats?.offset)
        assertEquals(BASE + 251 * 32, identity.tableResolution.tables.speciesNames?.offset)
    }

    @Test fun retainsSequentialExtentInferenceWhenTablesAreNotAdjacent() {
        val bytes = fixture(32, gap = 7)
        assertEquals(251, requireNotNull(resolve(bytes)).speciesCount)
        bytes[BASE + 201 * 32] = 201.toByte()
        assertEquals(201, requireNotNull(resolve(bytes)).speciesCount)
    }

    @Test fun doesNotWaivePrefixUnderIncompleteOverwriteOrHelperAuthority() {
        val original = fixture(32, count = 253).also { it[BASE + 201 * 32] = 201.toByte() }
        listOf(BASE_GETTER + 58, BASE_GETTER + 61, BASE_GETTER + 65,
            COPY + 7, MULTIPLY + 4, 0x11, BASE_GETTER + 37, BASE_GETTER + 56).forEach { offset ->
            assertNull("incomplete overwrite authority $offset", resolve(original.copyOf().also {
                it[offset] = (it[offset].toInt() xor 1).toByte()
            }))
        }
    }

    @Test fun rejectsOddStrideWithBrokenHelpersAndMismatchedCopies() {
        val original = fixture(35)
        listOf(COPY + 7, MULTIPLY + 4, BASE_GETTER + 30,
            BASE_GETTER + 15, BASE_GETTER + 36, BASE_GETTER + 62).forEach { offset ->
            assertNull("incomplete odd-stride authority $offset", resolve(original.copyOf().also {
                it[offset] = (it[offset].toInt() xor 1).toByte()
            }))
        }
    }

    @Test fun preservesEvenStrideSequentialClassicControl() {
        val result = resolve(fixture(32))
        assertNotNull(result)
        assertEquals(251, result?.speciesCount)
    }

    @Test fun rejectsBankCrossingOrTruncatedNameExtent() {
        val bytes = fixture(35)
        assertNull(resolve(bytes.copyOf(BASE + 251 * 35 + 250 * 10)))
        bytes.word(NAME_GETTER + 22, 0x7FF0)
        assertNull(resolve(bytes))
    }

    private fun resolve(bytes: ByteArray) = Gen2CompiledCoreResolver.resolve(RomImage(bytes))

    private fun fixture(width: Int, count: Int = 251, gap: Int = 0): ByteArray = ByteArray(0xC000).also { bytes ->
        bytes.put(0x10, "e0 9d ea 00 20 c9")
        bytes.put(MULTIPLY, "a7 c8 09 3d 20 fc c9")
        bytes.put(COPY, "04 0c 18 03 2a 12 13 0d 20 fa 05 20 f7 c9")
        bytes.put(NAME_GETTER, "f0 9d f5 e5 3e 01 d7 fa 00 c1 3d 16 00 5f 26 00 6f 29 29 19 29 11 00 60 19 11 00 c8 d5 01 0a 00 cd 00 06 21 0a c8 36 50 d1 e1 f1 d7 c9")
        bytes.put(BASE_GETTER, "c5 d5 e5 f0 9d f5 3e 01 d7 fa 00 c1 fe fd 28 15 3d 01 20 00 21 00 42 cd 00 05 11 00 c5 01 20 00 cd 00 06 18 15 11 00 70 06 55 21 11 c5 70 21 12 c5 73 23 72 23 73 23 72 18 00 fa 00 c1 ea 00 c5 f1 d7 e1 d1 c1 c9")
        bytes[BASE_GETTER + 18] = width.toByte()
        bytes.word(BASE_GETTER + 30, width)
        val names = BASE + count * width + gap
        bytes.word(NAME_GETTER + 22, names)
        repeat(count) { index ->
            bytes.put(BASE + index * width, "00 0a 14 1e 28 32 3c 00 00")
            bytes[BASE + index * width] = (index + 1).toByte()
            bytes.put(names + index * 10, "94 8d 88 93 50")
        }
    }

    private fun ByteArray.put(offset: Int, value: String) {
        value.split(' ').map { it.toInt(16).toByte() }.toByteArray().copyInto(this, offset)
    }

    private fun ByteArray.word(offset: Int, value: Int) {
        this[offset] = value.toByte()
        this[offset + 1] = (value ushr 8).toByte()
    }

    private companion object {
        const val NAME_GETTER = 0x800
        const val BASE_GETTER = 0x900
        const val MULTIPLY = 0x500
        const val COPY = 0x600
        const val BASE = 0x4200
    }
}
