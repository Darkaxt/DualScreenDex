package com.enrpau.dualscreendex.parser.family

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomHeader
import com.enrpau.dualscreendex.parser.model.TableLayout
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PublishedUnifiedFallbackIsolationTest {
    @Test
    fun resolvedPublishedBlockDoesNotRestoreInheritedMoveNames() {
        val session = session(fixture())
        val identity = resolveIdentity(session)

        assertNotNull(identity.headerlessUnifiedSpecies)
        assertNull(identity.tableResolution.tables.moveNames)
    }

    @Test
    fun resolvedPublishedBlockPreservesPublishedMoveNames() {
        val bytes = fixture().also { writePointer(it, 0x148, MOVE_NAMES_ROOT) }
        val identity = resolveIdentity(session(bytes))

        assertEquals(MOVE_NAMES_ROOT, identity.tableResolution.tables.moveNames?.offset)
    }

    @Test
    fun unresolvedEmbeddedEvolutionsDoNotFallThroughToInheritedTable() {
        val bytes = fixture().also(::writeLegacyEvolutions)
        val session = session(bytes)
        val definition = EngineFamilyDefinitions.byFamily.getValue(EngineFamily.EMERALD)
        val identity = resolveIdentity(session)
        val isolatedIdentity = IdentityRootsPhaseResult.Resolved(
            exactProfile = identity.exactProfile,
            baseProfile = identity.baseProfile,
            identityMatched = identity.identityMatched,
            scoreEvidence = identity.scoreEvidence,
            expansion = identity.expansion,
            headerlessUnifiedSpecies = identity.headerlessUnifiedSpecies,
            expandedSplitCaptureBalls = identity.expandedSplitCaptureBalls,
            compiledGbaReferences = identity.compiledGbaReferences,
            tableResolution = identity.tableResolution.copy(
                tables = identity.tableResolution.tables.copy(
                    evolutions = TableLayout(
                        offset = LEGACY_EVOLUTION_ROOT,
                        count = 40,
                        recordSize = 40,
                        elementSize = 8,
                    ),
                ),
            ),
            codec = identity.codec,
        )
        var state = FamilyProbeState.empty().withIdentityRoots(isolatedIdentity)
        state = CoreDatasetsStrategy().execute(session, definition, state)
        state = SemanticDomainStrategy().execute(session, definition, state)
        state = DependentDatasetsStrategy().execute(session, definition, state)
        val dependent = state.dependentDatasets as DependentDatasetsPhaseResult.Resolved

        assertFalse(dependent.evolutions.compatible)
        assertNull(dependent.resolvedEvolutions)
    }

    private fun resolveIdentity(session: RomAnalysisSession): IdentityRootsPhaseResult.Resolved {
        val definition = EngineFamilyDefinitions.byFamily.getValue(EngineFamily.EMERALD)
        val state = IdentityRootsStrategy().execute(session, definition, FamilyProbeState.empty())
        return state.identityRoots as IdentityRootsPhaseResult.Resolved
    }

    private fun session(bytes: ByteArray) = RomAnalysisSession(
        RomImage(bytes),
        RomHeader(Platform.GBA, "POKEMON EMER", "BPEE"),
    )

    private fun fixture(): ByteArray {
        val bytes = ByteArray(0x20000)
        writePointer(bytes, 0x1BC, SPECIES_ROOT)
        listOf(0x8000, 0x9000, 0xA000, 0xB000, 0xC000, 0xD000).forEachIndexed { index, root ->
            writePointer(bytes, 0x1C0 + index * 4, root)
        }
        repeat(11) { bytes[SPECIES_ROOT + 44 + it] = 0xAC.toByte() }
        bytes[SPECIES_ROOT + 55] = 0xFF.toByte()
        for (id in 1 until 40) {
            if (id in 30..33) continue
            val row = SPECIES_ROOT + id * 152
            repeat(6) { bytes[row + it] = (40 + id).toByte() }
            bytes[row + 6] = 12
            bytes[row + 7] = 3
            encodeGba(bytes, row + 31, "SEED")
            encodeGba(bytes, row + 44, if (id == 1) "BULBA" else "MON")
            writeU16(bytes, row + 56, if (id <= 3) id else 1)
            writeU16(bytes, row + 58, id)
            writeU16(bytes, row + 60, 7)
            writeU16(bytes, row + 62, 69)
            writePointer(bytes, row + 72, DESCRIPTION_ROOT)
            writePointer(bytes, row + 84, GRAPHICS_ROOT)
            writePointer(bytes, row + 100, PALETTE_ROOT)
        }
        bytes[SPECIES_ROOT + 41 * 152] = 40
        encodeGba(bytes, DESCRIPTION_ROOT, "A SEED POKEMON")
        Base64.getDecoder().decode("ASAIAAAAKAABAAAAAAH+BwEAAAA=").copyInto(bytes, GRAPHICS_ROOT)
        bytes[PALETTE_ROOT + 2] = 0x1F
        return bytes
    }

    private fun writeLegacyEvolutions(bytes: ByteArray) {
        writeU16(bytes, LEGACY_EVOLUTION_ROOT + 40, 4)
        writeU16(bytes, LEGACY_EVOLUTION_ROOT + 42, 16)
        writeU16(bytes, LEGACY_EVOLUTION_ROOT + 44, 2)
        writeU16(bytes, LEGACY_EVOLUTION_ROOT + 80, 4)
        writeU16(bytes, LEGACY_EVOLUTION_ROOT + 82, 32)
        writeU16(bytes, LEGACY_EVOLUTION_ROOT + 84, 3)
    }

    private fun encodeGba(target: ByteArray, offset: Int, value: String) {
        value.forEachIndexed { index, char ->
            target[offset + index] = when (char) {
                in 'A'..'Z' -> (0xBB + char.code - 'A'.code).toByte()
                ' ' -> 0
                else -> error("unsupported fixture character $char")
            }
        }
        target[offset + value.length] = 0xFF.toByte()
    }

    private fun writePointer(target: ByteArray, offset: Int, romOffset: Int) =
        writeU32(target, offset, 0x08000000 + romOffset)

    private fun writeU16(target: ByteArray, offset: Int, value: Int) {
        target[offset] = value.toByte()
        target[offset + 1] = (value ushr 8).toByte()
    }

    private fun writeU32(target: ByteArray, offset: Int, value: Int) {
        repeat(4) { index -> target[offset + index] = (value ushr (index * 8)).toByte() }
    }

    private companion object {
        const val SPECIES_ROOT = 0x1000
        const val DESCRIPTION_ROOT = 0x7000
        const val GRAPHICS_ROOT = 0xE000
        const val PALETTE_ROOT = 0xF000
        const val MOVE_NAMES_ROOT = 0x16000
        const val LEGACY_EVOLUTION_ROOT = 0x18000
    }
}
