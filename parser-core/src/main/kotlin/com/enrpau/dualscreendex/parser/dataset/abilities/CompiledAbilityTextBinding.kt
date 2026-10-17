package com.enrpau.dualscreendex.parser.dataset.abilities

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.model.TableRecordFormat

/** Scalar geometry derived from a compiled getter over the independently selected species core. */
class CompiledSpeciesAbilitySlots internal constructor(
    val coreOffset: Int,
    val speciesCount: Int,
    val recordStride: Int,
    val fieldOffset: Int,
    val elementSize: Int,
    val slotCount: Int,
    val recordSize: Int = recordStride,
    offsets: List<Int> = List(slotCount) { fieldOffset + it * elementSize },
) {
    val offsets: List<Int> = java.util.Collections.unmodifiableList(offsets.toList())
    val layoutIdentity: String = "${coreOffset.toString(16)}:$speciesCount:$recordStride:$recordSize:$fieldOffset:$elementSize:$slotCount:${this.offsets.joinToString(",")}"

    init {
        require(coreOffset >= 0 && speciesCount > 0 && recordSize in 1..recordStride)
        require(elementSize in 1..2 && slotCount in 1..8 && fieldOffset >= 0)
        require(this.offsets.size == slotCount && this.offsets.distinct().size == slotCount && this.offsets.first() == fieldOffset)
        require(this.offsets.all { it >= 0 && it.toLong() + elementSize <= recordSize && (coreOffset.toLong() + it) % elementSize == 0L })
        require(recordStride % elementSize == 0)
    }

    fun matches(core: TableLayout): Boolean = core.offset == coreOffset && core.count == speciesCount &&
        (core.stride ?: core.recordSize) == recordStride && core.recordSize == recordSize &&
        !core.variableLength && !core.valuesArePointers && core.bank == null && core.banks.isEmpty() &&
        core.pointerOffsets.isEmpty() && core.bankAdjustment == 0 && core.bankRemap.isEmpty() &&
        core.format == TableRecordFormat.STANDARD

    fun read(rom: RomImage, speciesId: Int): List<Int> {
        if (speciesId !in 0 until speciesCount ||
            coreOffset.toLong() + (speciesCount - 1L) * recordStride + recordSize > rom.size.toLong()
        ) return emptyList()
        val record = coreOffset.toLong() + speciesId.toLong() * recordStride
        val end = record + recordSize
        if (record < 0 || end > rom.size.toLong()) return emptyList()
        return offsets.map { field ->
            val offset = (record + field).toInt()
            if (elementSize == 1) rom.u8(offset) else rom.u16le(offset)
        }.filter { it != 0 }.distinct()
    }

    override fun equals(other: Any?): Boolean = other is CompiledSpeciesAbilitySlots &&
        coreOffset == other.coreOffset && speciesCount == other.speciesCount && recordStride == other.recordStride &&
        fieldOffset == other.fieldOffset && elementSize == other.elementSize && slotCount == other.slotCount &&
        recordSize == other.recordSize && offsets == other.offsets

    override fun hashCode(): Int = listOf(coreOffset, speciesCount, recordStride, recordSize, fieldOffset, elementSize, slotCount, offsets).hashCode()
}

/** Paired text objects indexed by the same proved native species-ability getter. */
@ConsistentCopyVisibility
data class CompiledAbilityTextBinding internal constructor(
    val names: AbilityNameTableLayout,
    val descriptions: AbilityDescriptionTableLayout,
    val speciesSlots: CompiledSpeciesAbilitySlots,
    val getterEntry: Int,
    val nameAccessorEntry: Int,
    val descriptionAccessorEntry: Int,
    val staticDefaultOnly: Boolean = false,
)
