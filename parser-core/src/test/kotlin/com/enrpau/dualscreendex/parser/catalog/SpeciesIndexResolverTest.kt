package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.ProfileTables
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import com.enrpau.dualscreendex.parser.model.TableLayout
import org.junit.Assert.assertEquals
import org.junit.Test

class SpeciesIndexResolverTest {
    @Test
    fun findsGenOneInternalToDexPermutationWithMissingSlots() {
        val bytes = byteArrayOf(0x7F, 0x7F, 1, 0, 3, 2, 0x7F)
        val layout = ResolvedRomLayout(
            EngineFamily.RED_BLUE,
            generation = 1,
            platform = Platform.GB,
            speciesCount = 4,
            moveCount = 1,
            tables = ProfileTables(
                speciesNames = TableLayout(0, 4, 10),
                baseStats = TableLayout(0, 3, 28),
            ),
        )

        assertEquals(
            mapOf(1 to 1, 2 to 0, 3 to 3, 4 to 2),
            SpeciesIndexResolver.resolve(RomImage(bytes), layout),
        )
    }

    @Test
    fun findsGbaU16SpeciesToNationalDexTableWithReservedSlot() {
        val bytes = ByteArray(32) { 0x7F }
        val values = intArrayOf(1, 2, 0, 3)
        values.forEachIndexed { index, value ->
            bytes[4 + index * 2] = value.toByte()
            bytes[5 + index * 2] = (value ushr 8).toByte()
        }
        val layout = ResolvedRomLayout(
            EngineFamily.EMERALD,
            generation = 3,
            platform = Platform.GBA,
            speciesCount = 5,
            moveCount = 1,
            tables = ProfileTables(),
        )

        assertEquals(
            mapOf(0 to 0, 1 to 1, 2 to 2, 3 to 0, 4 to 3),
            SpeciesIndexResolver.resolve(RomImage(bytes), layout),
        )
    }
}
