package com.enrpau.dualscreendex.parser.family

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.dataset.moves.MoveDetailsAbi
import com.enrpau.dualscreendex.parser.dataset.moves.putPackedMove
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.ProfileTables
import com.enrpau.dualscreendex.parser.model.RomHeader
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.model.TableRecordFormat
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompiledPackedCoreIntegrationTest {
    @Test
    fun failedInheritedMoveDataIsReplacedOnlyByTheCompleteNativeContract() {
        val core = resolve(fixture())
        assertTrue(core.moveData.compatible)
        assertEquals(TableRecordFormat.PACKED_FLAGS_MOVE_20, core.moveDataLayout?.format)
        assertEquals(MoveDetailsAbi.PACKED_FLAGS_MOVE_20, core.resolvedMoveDetails?.table?.abi)
        assertEquals(3, core.moveCount)
        assertEquals(setOf(1, 2), core.resolvedMoveDetails?.materializedRecords?.keys)
        assertEquals(-7, core.resolvedMoveDetails?.materializedRecords?.get(2)?.priority)
        assertEquals(0x3800, core.speciesNamesLayout?.offset)
        assertEquals(0x3900, core.baseStatsLayout?.offset)
    }

    @Test
    fun incompleteOptionalMoveContractPreservesIndependentCoreTables() {
        val bytes = fixture()
        bytes.fill(0, 0x100, 0x160)
        val core = resolve(bytes)
        assertNull(core.resolvedMoveDetails)
        assertNotNull(core.speciesNamesLayout)
        assertNotNull(core.baseStatsLayout)
        assertEquals(0x3800, core.speciesNamesLayout?.offset)
        assertEquals(0x3900, core.baseStatsLayout?.offset)
    }

    private fun resolve(bytes: ByteArray): CoreDatasetsPhaseResult.Resolved {
        val session = RomAnalysisSession(RomImage(bytes), RomHeader(Platform.GBA, "PACKED CORE TEST"))
        val identity = IdentityRootsPhaseResult.Resolved(
            exactProfile = null, baseProfile = null, identityMatched = true, scoreEvidence = emptyList(),
            expansion = null, compiledGbaReferences = null, probeCodec = PokemonTextCodec.gbaEnglish,
            tableResolution = ProfileTableResolution(ProfileTables(
                speciesNames = TableLayout(0x3800, 2, 11), baseStats = TableLayout(0x3900, 2, 28),
                moveNames = TableLayout(0x4000, 3, 13), moveData = TableLayout(0x3000, 3, 12),
            )),
        )
        return CoreDatasetsStrategy().execute(
            session, EngineFamilyDefinitions.byFamily.getValue(EngineFamily.EMERALD),
            FamilyProbeState.empty().withIdentityRoots(identity),
        ).coreDatasets as CoreDatasetsPhaseResult.Resolved
    }

    private fun fixture(): ByteArray = ByteArray(0x6000).also { bytes ->
        val fields = listOf(0 to 2, 2 to 1, 3 to 1, 4 to 1, 5 to 1, 6 to 1, 8 to 2,
            10 to 1, 11 to 1, 12 to 2, 14 to 1, 15 to 1, 16 to 1, 17 to 1, 18 to 1, 19 to 1)
        fields.forEachIndexed { index, (field, width) ->
            val site = 0x100 + index * 0x60
            val words = mutableListOf(0x4b0f, 0x0088, 0x1840, 0x0080, 0x18c2,
                (if (width == 1) 0x7800 else 0x8800) or ((field / width) shl 6) or 0x15)
            if (field == 10) words += listOf(0x0628, 0x1600)
            words += 0x4770
            words.forEachIndexed { wordIndex, word ->
                bytes[site + wordIndex * 2] = word.toByte()
                bytes[site + wordIndex * 2 + 1] = (word ushr 8).toByte()
            }
            repeat(4) { bytes[site + 0x40 + it] = (0x08003000 ushr (it * 8)).toByte() }
        }
        putPackedMove(bytes, 0x3000 + 20)
        putPackedMove(bytes, 0x3000 + 40)
        listOf("NONE", "FIRST", "SECOND").forEachIndexed { index, name -> encode(bytes, 0x4000 + index * 13, name) }
        listOf("NONE", "SPECIES").forEachIndexed { index, name -> encode(bytes, 0x3800 + index * 11, name) }
        for (id in 0..1) {
            val at = 0x3900 + id * 28
            repeat(6) { bytes[at + it] = 50 }
            bytes[at + 6] = 1
            bytes[at + 7] = 1
            bytes[at + 8] = 45
            bytes[at + 9] = 64
            bytes[at + 16] = 127
            bytes[at + 17] = 20
            bytes[at + 18] = 70
            bytes[at + 20] = 1
            bytes[at + 21] = 1
            bytes[at + 22] = 1
        }
    }

    private fun encode(bytes: ByteArray, at: Int, text: String) {
        text.forEachIndexed { index, char -> bytes[at + index] = (0xbb + char.code - 'A'.code).toByte() }
        bytes[at + text.length] = 0xff.toByte()
    }
}
