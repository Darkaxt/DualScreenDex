package com.enrpau.dualscreendex.parser.analysis

import com.enrpau.dualscreendex.parser.model.GbInlineDescriptionLayout
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.ProfileTables
import com.enrpau.dualscreendex.parser.model.RomProfile
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.model.TableRecordFormat
import java.util.Collections

class ExactTableLayoutSnapshot private constructor(
    val offset: Int,
    val count: Int,
    val recordSize: Int,
    val variableLength: Boolean,
    val bank: Int?,
    banks: List<Int>,
    pointerOffsets: List<Int>,
    val elementSize: Int?,
    val bankAdjustment: Int,
    bankRemap: Map<Int, Int>,
    val stride: Int?,
    val valuesArePointers: Boolean,
    val format: TableRecordFormat,
    val gbDescriptions: GbInlineDescriptionLayout?,
) {
    val banks: List<Int> = Collections.unmodifiableList(banks.toList())
    val pointerOffsets: List<Int> = Collections.unmodifiableList(pointerOffsets.toList())
    val bankRemap: Map<Int, Int> = Collections.unmodifiableMap(linkedMapOf<Int, Int>().apply {
        bankRemap.toSortedMap().forEach { (source, target) -> put(source, target) }
    })

    override fun equals(other: Any?): Boolean = other is ExactTableLayoutSnapshot &&
        offset == other.offset && count == other.count && recordSize == other.recordSize &&
        variableLength == other.variableLength && bank == other.bank && banks == other.banks &&
        pointerOffsets == other.pointerOffsets && elementSize == other.elementSize &&
        bankAdjustment == other.bankAdjustment && bankRemap == other.bankRemap &&
        stride == other.stride && valuesArePointers == other.valuesArePointers && format == other.format &&
        gbDescriptions == other.gbDescriptions

    override fun hashCode(): Int {
        var result = offset
        result = 31 * result + count
        result = 31 * result + recordSize
        result = 31 * result + variableLength.hashCode()
        result = 31 * result + (bank ?: 0)
        result = 31 * result + banks.hashCode()
        result = 31 * result + pointerOffsets.hashCode()
        result = 31 * result + (elementSize ?: 0)
        result = 31 * result + bankAdjustment
        result = 31 * result + bankRemap.hashCode()
        result = 31 * result + (stride ?: 0)
        result = 31 * result + valuesArePointers.hashCode()
        result = 31 * result + format.hashCode()
        result = 31 * result + (gbDescriptions?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String = "ExactTableLayoutSnapshot(" +
        "offset=$offset, count=$count, recordSize=$recordSize, variableLength=$variableLength, " +
        "bank=$bank, banks=$banks, pointerOffsets=$pointerOffsets, elementSize=$elementSize, " +
        "bankAdjustment=$bankAdjustment, bankRemap=$bankRemap, stride=$stride, " +
        "valuesArePointers=$valuesArePointers, format=$format, gbDescriptions=$gbDescriptions)"

    companion object {
        internal fun from(layout: TableLayout): ExactTableLayoutSnapshot = ExactTableLayoutSnapshot(
            offset = layout.offset,
            count = layout.count,
            recordSize = layout.recordSize,
            variableLength = layout.variableLength,
            bank = layout.bank,
            banks = layout.banks,
            pointerOffsets = layout.pointerOffsets,
            elementSize = layout.elementSize,
            bankAdjustment = layout.bankAdjustment,
            bankRemap = layout.bankRemap,
            stride = layout.stride,
            valuesArePointers = layout.valuesArePointers,
            format = layout.format,
            gbDescriptions = layout.gbDescriptions,
        )
    }
}

class ExactProfileTablesSnapshot private constructor(
    val speciesNames: ExactTableLayoutSnapshot?,
    val baseStats: ExactTableLayoutSnapshot?,
    val moveNames: ExactTableLayoutSnapshot?,
    val moveData: ExactTableLayoutSnapshot?,
    val typeChart: ExactTableLayoutSnapshot?,
    val evolutions: ExactTableLayoutSnapshot?,
    val learnsets: ExactTableLayoutSnapshot?,
    val sprites: ExactTableLayoutSnapshot?,
    val descriptions: ExactTableLayoutSnapshot?,
    val abilities: ExactTableLayoutSnapshot?,
) {
    companion object {
        internal fun from(tables: ProfileTables): ExactProfileTablesSnapshot = ExactProfileTablesSnapshot(
            speciesNames = tables.speciesNames?.let(ExactTableLayoutSnapshot::from),
            baseStats = tables.baseStats?.let(ExactTableLayoutSnapshot::from),
            moveNames = tables.moveNames?.let(ExactTableLayoutSnapshot::from),
            moveData = tables.moveData?.let(ExactTableLayoutSnapshot::from),
            typeChart = tables.typeChart?.let(ExactTableLayoutSnapshot::from),
            evolutions = tables.evolutions?.let(ExactTableLayoutSnapshot::from),
            learnsets = tables.learnsets?.let(ExactTableLayoutSnapshot::from),
            sprites = tables.sprites?.let(ExactTableLayoutSnapshot::from),
            descriptions = tables.descriptions?.let(ExactTableLayoutSnapshot::from),
            abilities = tables.abilities?.let(ExactTableLayoutSnapshot::from),
        )
    }
}

class ExactProfileSnapshot private constructor(
    val identity: ExactProfileIdentity,
    val family: EngineFamily,
    val platform: Platform,
    val dexSpeciesCount: Int,
    val internalSpeciesCount: Int,
    val moveCount: Int,
    val tables: ExactProfileTablesSnapshot,
) {
    companion object {
        internal fun from(profile: RomProfile, identity: ExactProfileIdentity): ExactProfileSnapshot =
            ExactProfileSnapshot(
                identity = identity,
                family = profile.family,
                platform = profile.platform,
                dexSpeciesCount = profile.dexSpeciesCount,
                internalSpeciesCount = profile.internalSpeciesCount,
                moveCount = profile.moveCount,
                tables = ExactProfileTablesSnapshot.from(profile.tables),
            )
    }
}
