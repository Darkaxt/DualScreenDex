package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.ProfileTables
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import com.enrpau.dualscreendex.parser.model.TableLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LearnsetRulesetMaterializerTest {
    @Test
    fun discoversPrimaryAndExpandedGbaPointerTables() {
        val bytes = ByteArray(0x1000)
        val primaryOffset = 0x100
        val expandedOffset = 0x140
        writeRuleset(bytes, primaryOffset, listOf(
            listOf(1 to 10),
            listOf(1 to 10),
            listOf(7 to 20),
            listOf(9 to 30),
        ), 0x300)
        writeRuleset(bytes, expandedOffset, listOf(
            listOf(1 to 10),
            listOf(1 to 10, 12 to 40),
            listOf(7 to 20),
            listOf(9 to 30),
        ), 0x500)
        val layout = layout(primaryOffset)
        val rom = RomImage(bytes)
        val primary = RelationshipMaterializers.learnsets(rom, layout)

        val result = LearnsetRulesetMaterializer.materialize(rom, layout, primary)

        assertEquals(listOf(primaryOffset, expandedOffset), result.map { it.sourceOffset }.sorted())
        assertTrue(result.single { it.sourceOffset == primaryOffset }.primary)
        assertEquals(
            listOf(LearnsetEntry(1, 10), LearnsetEntry(12, 40)),
            result.single { it.sourceOffset == expandedOffset }.entriesBySpecies.getValue(1),
        )
    }

    @Test
    fun rejectsPointerRunsWhoseEntriesAreNotLegalLearnsets() {
        val bytes = ByteArray(0x800)
        val primaryOffset = 0x100
        writeRuleset(bytes, primaryOffset, List(4) { listOf(5 to 10) }, 0x300)
        repeat(4) { putGbaPointer(bytes, 0x180 + it * 4, 0x700 + it * 4) }
        val layout = layout(primaryOffset)
        val rom = RomImage(bytes)

        val result = LearnsetRulesetMaterializer.materialize(
            rom,
            layout,
            RelationshipMaterializers.learnsets(rom, layout),
        )

        assertEquals(listOf(primaryOffset), result.map { it.sourceOffset })
    }

    private fun layout(primaryOffset: Int) = ResolvedRomLayout(
        family = EngineFamily.EMERALD,
        generation = 3,
        platform = Platform.GBA,
        speciesCount = 4,
        moveCount = 100,
        tables = ProfileTables(learnsets = TableLayout(primaryOffset, 4, 4)),
    )

    private fun writeRuleset(
        target: ByteArray,
        tableOffset: Int,
        entries: List<List<Pair<Int, Int>>>,
        dataOffset: Int,
    ) {
        var cursor = dataOffset
        entries.forEachIndexed { speciesId, learnset ->
            putGbaPointer(target, tableOffset + speciesId * 4, cursor)
            learnset.forEach { (level, move) ->
                putU16(target, cursor, (level shl 9) or move)
                cursor += 2
            }
            putU16(target, cursor, 0xFFFF)
            cursor += 2
        }
    }

    private fun putU16(target: ByteArray, offset: Int, value: Int) {
        target[offset] = value.toByte()
        target[offset + 1] = (value ushr 8).toByte()
    }

    private fun putGbaPointer(target: ByteArray, offset: Int, targetOffset: Int) {
        val value = 0x08000000 + targetOffset
        repeat(4) { index -> target[offset + index] = (value ushr (index * 8)).toByte() }
    }
}
