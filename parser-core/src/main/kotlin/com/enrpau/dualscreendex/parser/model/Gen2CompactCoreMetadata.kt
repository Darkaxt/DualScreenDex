package com.enrpau.dualscreendex.parser.model

import java.util.Collections

/** A canonical ROM-native species joined through its compiled name and base-index consumers. */
data class Gen2CompactSpeciesSlot(val id: Int, val nameIndex: Int, val baseIndex: Int)

class Gen2CompactCoreMetadata(slots: Collection<Gen2CompactSpeciesSlot>) {
    val slots: List<Gen2CompactSpeciesSlot> = Collections.unmodifiableList(slots.toList())

    init {
        require(this.slots.isNotEmpty())
        require(this.slots.all { it.id > 0 && it.nameIndex >= 0 && it.baseIndex >= 0 })
        require(this.slots.map { it.id }.distinct().size == this.slots.size)
        require(this.slots.map { it.nameIndex }.distinct().size == this.slots.size)
    }

    override fun equals(other: Any?): Boolean = other is Gen2CompactCoreMetadata && slots == other.slots
    override fun hashCode(): Int = slots.hashCode()
}
