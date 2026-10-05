package com.enrpau.dualscreendex.parser.model

import com.enrpau.dualscreendex.parser.dataset.core.basestats.BaseStatsAbi
import com.enrpau.dualscreendex.parser.dataset.core.basestats.BaseStatsRowOutcome
import com.enrpau.dualscreendex.parser.dataset.core.basestats.BaseStatsTableLayout
import com.enrpau.dualscreendex.parser.dataset.core.basestats.ResolvedBaseStatsLayout
import com.enrpau.dualscreendex.parser.dataset.learnsets.LearnsetFormat
import com.enrpau.dualscreendex.parser.dataset.learnsets.LearnsetRowOutcome
import com.enrpau.dualscreendex.parser.dataset.learnsets.LearnsetTermination
import com.enrpau.dualscreendex.parser.dataset.learnsets.ResolvedLearnsetSet
import com.enrpau.dualscreendex.parser.dataset.moves.MoveDetailsRowOutcome
import java.util.Collections

internal val ResolvedRomLayout.requiresCompiledCanonicalSpecies: Boolean get() =
    compiledCanonicalSpecies != null || tables.baseStats?.format == TableRecordFormat.WIDE_STATS_64 ||
        resolvedDatasets.baseStats?.table?.abi == BaseStatsAbi.WIDE_STATS_64

/** Canonical inverse-first-match authority; native IDs are not assumed to equal Dex numbers. */
class Gen3CompiledCanonicalSpeciesMetadata(
    speciesNames: TableLayout,
    val baseStatsTable: BaseStatsTableLayout,
    nativeToDex: Map<Int, Int>,
) {
    val speciesNames: TableLayout = speciesNames.copy(
        banks = Collections.unmodifiableList(speciesNames.banks.toList()),
        pointerOffsets = Collections.unmodifiableList(speciesNames.pointerOffsets.toList()),
        bankRemap = Collections.unmodifiableMap(LinkedHashMap(speciesNames.bankRemap)),
    )
    val nativeToDex: Map<Int, Int> = Collections.unmodifiableMap(LinkedHashMap(nativeToDex.toSortedMap()))

    init {
        require(baseStatsTable.abi == BaseStatsAbi.WIDE_STATS_64 && baseStatsTable.offset >= 0 &&
            baseStatsTable.count in 2..65535L) { "compiled canonical stats require the bounded widened ABI" }
        require(speciesNames.offset >= 0 && speciesNames.count.toLong() == baseStatsTable.count + 1 &&
            speciesNames.recordSize in 2..64 && !speciesNames.variableLength && !speciesNames.valuesArePointers &&
            (speciesNames.stride == null || speciesNames.stride == speciesNames.recordSize)) {
            "compiled canonical names require the independently bounded inline copier"
        }
        require(this.nativeToDex.isNotEmpty() && this.nativeToDex.all { (native, dex) ->
            native > 0 && native.toLong() < baseStatsTable.count && dex in 1..65535
        } && this.nativeToDex.values.distinct().size == this.nativeToDex.size) {
            "compiled canonical indices must be bounded, positive and inverse-first-match unique"
        }
    }

    internal fun coherentBaseStats(layout: ResolvedRomLayout): ResolvedBaseStatsLayout? {
        if (layout.platform != Platform.GBA || layout.generation != 3 ||
            layout.pokeemeraldExpansion != null || layout.headerlessUnifiedSpecies != null ||
            layout.tables.speciesNames != speciesNames
        ) return null
        val table = layout.tables.baseStats ?: return null
        if (table.format != TableRecordFormat.WIDE_STATS_64 || table.offset.toLong() != baseStatsTable.offset ||
            table.count.toLong() != baseStatsTable.count || table.recordSize != baseStatsTable.abi.recordSize ||
            table.variableLength || table.valuesArePointers ||
            (table.stride != null && table.stride != table.recordSize)
        ) return null
        val typed = layout.resolvedDatasets.baseStats ?: return null
        if (typed.table != baseStatsTable || typed.rows.any { it is BaseStatsRowOutcome.Malformed } ||
            nativeToDex.keys.any { typed.rows[it] !is BaseStatsRowOutcome.Decoded }
        ) return null
        return typed
    }

    internal fun coherentLearnsets(layout: ResolvedRomLayout): ResolvedLearnsetSet? {
        coherentBaseStats(layout) ?: return null
        val resolved = layout.resolvedDatasets.learnsets ?: return null
        val primary = resolved.primary ?: return null
        if (resolved.tables.size != 1 || resolved.selector != null || layout.learnsetSelector != null) return null
        val table = primary.layout.table
        val raw = layout.tables.learnsets ?: return null
        if (table.format != LearnsetFormat.MoveU16LevelU16 || table.speciesCount.toLong() != baseStatsTable.count ||
            table.pointerStride != 4 || raw.offset.toLong() != table.offset || raw.count != table.speciesCount ||
            raw.recordSize != 4 || !raw.variableLength || raw.elementSize != 4 ||
            raw.format != TableRecordFormat.GEN3_MOVE_U16_LEVEL_U16 ||
            (raw.stride != null && raw.stride != 4)
        ) return null
        val details = layout.resolvedDatasets.moveDetails ?: return null
        val moves = layout.tables.moveData ?: return null
        val names = layout.tables.moveNames ?: return null
        if (!details.table.abi.isAlignedByteTarget || layout.moveCount?.toLong() != details.table.count ||
            moves.offset.toLong() != details.table.offset || moves.count.toLong() != details.table.count ||
            moves.recordSize != details.table.abi.recordSize || moves.format != details.table.abi.tableRecordFormat ||
            moves.variableLength || moves.valuesArePointers ||
            (moves.stride != null && moves.stride != moves.recordSize) ||
            names.count != moves.count || names.recordSize != 17 || names.variableLength || names.valuesArePointers ||
            (names.stride != null && names.stride != 17)
        ) return null
        for (id in nativeToDex.keys) {
            val row = primary.layout.rows[id] as? LearnsetRowOutcome.Decoded ?: return null
            if (row.termination != LearnsetTermination.Explicit || row.entries.any {
                details.rows.getOrNull(it.moveId) !is MoveDetailsRowOutcome.Decoded
            }) return null
        }
        return resolved
    }

    override fun equals(other: Any?): Boolean = other is Gen3CompiledCanonicalSpeciesMetadata &&
        speciesNames == other.speciesNames && baseStatsTable == other.baseStatsTable && nativeToDex == other.nativeToDex

    override fun hashCode(): Int = 31 * (31 * speciesNames.hashCode() + baseStatsTable.hashCode()) + nativeToDex.hashCode()
}
