package com.enrpau.dualscreendex.parser.dataset.core.basestats

import com.enrpau.dualscreendex.parser.catalog.RecordMaterializers
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.ProfileTables
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import com.enrpau.dualscreendex.parser.model.TableLayout
import org.junit.Assert.assertEquals
import org.junit.Test

/** Characterizes the catalog contract while the new unit remains intentionally unintegrated. */
class BaseStatsCompatibilityTest {
    @Test
    fun retailCodecMatchesLegacyMaterializationForStatsTypesAbilitiesAndGrowth() {
        val bytes = ByteArray(256)
        encodeGbaName(bytes, 0, "NONE")
        encodeGbaName(bytes, 11, "BULBA")
        val root = 128
        putRetailBaseStats(bytes, root + 28)
        putU16(bytes, 240, 1)
        val rom = RomImage(bytes)
        val legacy = RecordMaterializers.species(
            rom,
            ResolvedRomLayout(
                family = EngineFamily.EMERALD,
                generation = 3,
                platform = Platform.GBA,
                speciesCount = 2,
                moveCount = 0,
                tables = ProfileTables(
                    speciesNames = TableLayout(0, 2, 11),
                    baseStats = TableLayout(root, 2, 28),
                ),
            ),
        ).getValue(1)
        val decoded = (
            (BaseStatsCodec().decode(
                baseStatsSession(bytes),
                BaseStatsTableLayout(root.toLong(), 2, BaseStatsAbi.RETAIL_28),
            ) as BaseStatsTableOutcome.Decoded).rows[1] as BaseStatsRowOutcome.Decoded
            ).record

        assertEquals(legacy.baseStats.value, decoded.stats)
        assertEquals(legacy.typeIds.value, decoded.typeIds)
        assertEquals(legacy.abilityIds.value, decoded.abilityIds)
        assertEquals(legacy.growthRate.value, decoded.growthRate)
    }

    private fun encodeGbaName(target: ByteArray, offset: Int, value: String) {
        value.forEachIndexed { index, char ->
            target[offset + index] = (0xBB + char.code - 'A'.code).toByte()
        }
        target[offset + value.length] = 0xFF.toByte()
    }

    private fun putU16(target: ByteArray, offset: Int, value: Int) {
        target[offset] = value.toByte()
        target[offset + 1] = (value ushr 8).toByte()
    }
}
