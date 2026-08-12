package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.dataset.learnsets.LearnsetCodec
import com.enrpau.dualscreendex.parser.dataset.learnsets.LearnsetFormat
import com.enrpau.dualscreendex.parser.dataset.learnsets.LearnsetTableLayout
import com.enrpau.dualscreendex.parser.dataset.learnsets.LearnsetTableOutcome
import com.enrpau.dualscreendex.parser.dataset.learnsets.ResolvedLearnsetLayout
import com.enrpau.dualscreendex.parser.dataset.learnsets.ResolvedLearnsetSet
import com.enrpau.dualscreendex.parser.dataset.learnsets.ResolvedSelectedLearnsetTable
import com.enrpau.dualscreendex.parser.detect.RomHeaderReader
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Gen3LearnsetTableLayout
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.ProfileTables
import com.enrpau.dualscreendex.parser.model.ResolvedDatasetLayouts
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.model.TableRecordFormat
import com.enrpau.dualscreendex.parser.parse.DatasetResolvers
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
        val layout = layout(primaryOffset, listOf(primaryOffset, expandedOffset)).withTypedLearnsets(bytes)
        val rom = RomImage(bytes)
        val primary = RelationshipMaterializers.learnsets(rom, layout)

        val result = LearnsetRulesetMaterializer.materialize(rom, layout, primary)

        assertEquals(listOf(primaryOffset, expandedOffset), result.map { it.sourceOffset }.sorted())
        assertTrue(result.none { it.primary })
        assertEquals(
            listOf(LearnsetEntry(1, 10), LearnsetEntry(12, 40)),
            result.single { it.sourceOffset == expandedOffset }.entriesBySpecies.getValue(1),
        )
    }

    @Test
    fun retainsAdjacentAlternateRulesetAfterResolverSelectsThePrimaryWindow() {
        val bytes = ByteArray(0x1000)
        val speciesCount = 4
        val primaryOffset = 0x100
        val alternateOffset = primaryOffset + speciesCount * 4
        writeRuleset(
            bytes,
            primaryOffset,
            List(speciesCount) { species -> listOf((1 + species) to (10 + species)) },
            0x300,
        )
        writeRuleset(
            bytes,
            alternateOffset,
            List(speciesCount) { species -> listOf((11 + species) to (20 + species)) },
            0x500,
        )
        repeat(4) { index ->
            bytes[primaryOffset + index] = bytes[primaryOffset + 4 + index]
            bytes[alternateOffset + index] = bytes[alternateOffset + 4 + index]
        }
        putThumbLiteralReferences(bytes, 0x20, 0x80, primaryOffset)
        putThumbLiteralReferences(bytes, 0x30, 0x88, alternateOffset)
        val rom = RomImage(bytes)
        val resolution = DatasetResolvers.gen3LearnsetResolution(
            rom, speciesCount = speciesCount, moveCount = 100, inherited = null,
        )
        assertTrue(resolution.evidence.ambiguous)
        val resolved = layout(primaryOffset).copy(learnsetTables = resolution.tables).withTypedLearnsets(bytes)
        val primary = RelationshipMaterializers.learnsets(rom, resolved)

        val result = LearnsetRulesetMaterializer.materialize(rom, resolved, primary)

        assertEquals(listOf(primaryOffset, alternateOffset), result.map { it.sourceOffset }.sorted())
        assertTrue(result.none { it.primary })
    }

    @Test
    fun preservesValidatedAlternativesWhenAutoHasNoSelectorProof() {
        val bytes = ByteArray(0x1000)
        val firstOffset = 0x100
        val secondOffset = 0x140
        writeRuleset(bytes, firstOffset, List(4) { species -> listOf((1 + species) to (10 + species)) }, 0x300)
        writeRuleset(bytes, secondOffset, List(4) { species -> listOf((11 + species) to (20 + species)) }, 0x500)
        val first = TableLayout(
            firstOffset, 4, 4, elementSize = 2, format = TableRecordFormat.GEN3_PACKED_U16,
        )
        val second = TableLayout(
            secondOffset, 4, 4, elementSize = 2, format = TableRecordFormat.GEN3_PACKED_U16,
        )
        val layout = ResolvedRomLayout(
            family = EngineFamily.EMERALD,
            generation = 3,
            platform = Platform.GBA,
            speciesCount = 4,
            moveCount = 100,
            tables = ProfileTables(learnsets = null),
            learnsetTables = listOf(
                Gen3LearnsetTableLayout(first, 1.0, 8),
                Gen3LearnsetTableLayout(second, 1.0, 8),
            ),
            learnsetSelector = null,
        ).withTypedLearnsets(bytes)

        val result = LearnsetRulesetMaterializer.materialize(RomImage(bytes), layout, emptyMap())

        assertEquals(listOf("ruleset-00000100", "ruleset-00000140"), result.map { it.id })
        assertEquals(listOf(firstOffset, secondOffset), result.map { it.sourceOffset })
        assertTrue(result.none { it.primary })
        assertTrue(result.all { it.levelUpSelector == null })
        assertEquals(4, result.single { it.sourceOffset == firstOffset }.entriesBySpecies.size)
        assertEquals(4, result.single { it.sourceOffset == secondOffset }.entriesBySpecies.size)
    }

    @Test
    fun rejectsPointerRunsWhoseEntriesAreNotLegalLearnsets() {
        val bytes = ByteArray(0x800)
        val primaryOffset = 0x100
        writeRuleset(bytes, primaryOffset, List(4) { listOf(5 to 10) }, 0x300)
        repeat(4) { putGbaPointer(bytes, 0x180 + it * 4, 0x700 + it * 4) }
        val layout = layout(primaryOffset).withTypedLearnsets(bytes)
        val rom = RomImage(bytes)

        val result = LearnsetRulesetMaterializer.materialize(
            rom,
            layout,
            RelationshipMaterializers.learnsets(rom, layout),
        )

        assertEquals(listOf(primaryOffset), result.map { it.sourceOffset })
    }

    @Test
    fun discoversNineBitRulesetsWithExactly512MoveDefinitions() {
        val bytes = ByteArray(0x1000)
        val primaryOffset = 0x100
        val alternateOffset = 0x140
        writeRuleset(bytes, primaryOffset, List(4) { listOf(20 to 489) }, 0x300)
        writeRuleset(bytes, alternateOffset, List(4) { listOf(21 to 490) }, 0x500)
        val layout = layout(primaryOffset, listOf(primaryOffset, alternateOffset))
            .copy(moveCount = 512)
            .withTypedLearnsets(bytes)
        val primary = mapOf(0 to listOf(LearnsetEntry(20, 489)))

        val result = LearnsetRulesetMaterializer.materialize(RomImage(bytes), layout, primary)

        assertEquals(listOf(primaryOffset, alternateOffset), result.map { it.sourceOffset }.sorted())
    }

    @Test
    fun decodesEachResolverProvidedAlternateWithItsOwnAbiFormat() {
        val bytes = ByteArray(0x1000)
        val primaryOffset = 0x100
        val alternateOffset = 0x140
        writeRuleset(bytes, primaryOffset, List(4) { listOf(20 to 40) }, 0x300)
        repeat(4) { species ->
            val target = 0x600 + species * 0x20
            putGbaPointer(bytes, alternateOffset + species * 4, target)
            bytes[target] = 30
            putU16(bytes, target + 1, 60 + species)
            bytes[target + 3] = 5
            putU16(bytes, target + 4, 70 + species)
            bytes[target + 6] = 0xFE.toByte()
        }
        val primaryTable = TableLayout(
            primaryOffset, 4, 4, elementSize = 2, format = TableRecordFormat.GEN3_PACKED_U16,
        )
        val alternateTable = TableLayout(
            alternateOffset, 4, 4, elementSize = 3, format = TableRecordFormat.GEN3_LEVEL_U8_MOVE_U16,
        )
        val layout = ResolvedRomLayout(
            family = EngineFamily.EMERALD,
            generation = 3,
            platform = Platform.GBA,
            speciesCount = 4,
            moveCount = 100,
            tables = ProfileTables(learnsets = primaryTable),
            learnsetTables = listOf(
                Gen3LearnsetTableLayout(primaryTable, 1.0, 2),
                Gen3LearnsetTableLayout(alternateTable, 1.0, 2),
            ),
        ).withTypedLearnsets(bytes)
        val rom = RomImage(bytes)

        val result = LearnsetRulesetMaterializer.materialize(
            rom, layout, RelationshipMaterializers.learnsets(rom, layout),
        )

        assertEquals(listOf(primaryOffset, alternateOffset), result.map { it.sourceOffset })
        assertEquals(
            listOf(LearnsetEntry(30, 60), LearnsetEntry(5, 70)),
            result.single { it.sourceOffset == alternateOffset }.entriesBySpecies.getValue(0),
        )
    }

    private fun layout(primaryOffset: Int, allOffsets: List<Int> = listOf(primaryOffset)) = ResolvedRomLayout(
        family = EngineFamily.EMERALD,
        generation = 3,
        platform = Platform.GBA,
        speciesCount = 4,
        moveCount = 100,
        tables = ProfileTables(learnsets = TableLayout(primaryOffset, 4, 4)),
        learnsetTables = allOffsets.map { offset ->
            Gen3LearnsetTableLayout(
                TableLayout(offset, 4, 4, elementSize = 2, format = TableRecordFormat.GEN3_PACKED_U16),
                1.0,
                1,
            )
        },
    )

    private fun ResolvedRomLayout.withTypedLearnsets(bytes: ByteArray): ResolvedRomLayout {
        val moveDomain = requireNotNull(moveCount)
        val rom = RomImage(bytes)
        val session = RomAnalysisSession(rom, RomHeaderReader.read(rom))
        val resolved = learnsetTables.mapNotNull { selected ->
            val format = when (selected.table.format) {
                TableRecordFormat.GEN3_LEVEL_U8_MOVE_U16 -> LearnsetFormat.LevelU8MoveU16
                TableRecordFormat.GEN3_MOVE_U16_LEVEL_U8 -> LearnsetFormat.MoveU16LevelU8
                TableRecordFormat.GEN3_MOVE_U16_LEVEL_U16 -> LearnsetFormat.MoveU16LevelU16
                else -> LearnsetFormat.PackedU16(
                    com.enrpau.dualscreendex.parser.model.Gen3LearnsetEncoding.packedMoveBits(moveDomain),
                )
            }
            val table = LearnsetTableLayout(
                selected.table.offset.toLong(),
                selected.table.count,
                format,
            )
            val decoded = LearnsetCodec().decodeGen3(session, table, moveDomain)
                as? LearnsetTableOutcome.Decoded ?: return@mapNotNull null
            ResolvedSelectedLearnsetTable(
                ResolvedLearnsetLayout(table, decoded.rows),
                selected.confidence,
                selected.referenceCount,
            )
        }
        val primaryOffset = tables.learnsets?.offset?.toLong()?.takeIf { offset ->
            resolved.any { it.layout.table.offset == offset }
        }
        return copy(
            resolvedDatasets = if (resolved.isEmpty()) {
                ResolvedDatasetLayouts()
            } else {
                ResolvedDatasetLayouts(
                    learnsets = ResolvedLearnsetSet(resolved, primaryOffset, selector = null),
                )
            },
        )
    }

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

    private fun putThumbLiteralReferences(
        target: ByteArray,
        instructionOffset: Int,
        literalOffset: Int,
        tableOffset: Int,
    ) {
        val pc = (instructionOffset + 4) and -4
        val immediate = (literalOffset - pc) / 4
        putU16(target, instructionOffset, 0x4800 or immediate)
        putU16(target, instructionOffset + 2, 0x4800 or immediate)
        putGbaPointer(target, literalOffset, tableOffset)
    }
}
