package com.enrpau.dualscreendex.parser.model

import java.util.Collections

/** A canonical ROM-native species joined through its compiled name and base-index consumers. */
data class Gen2CompactSpeciesSlot(val id: Int, val nameIndex: Int, val baseIndex: Int)

/**
 * Compiled compact relationship stream: one word pointer per base-stat row (same index transform),
 * evolution rows then level/move pairs, each section ended by [terminator].
 */
data class Gen2CompactRelationshipContract(
    val tableOffset: Int,
    val bank: Int,
    val rows: Int,
    val terminator: Int,
    /** Targets are `species, form` words (form bit 0x20 selects the extended species) instead of a species byte. */
    val formTargets: Boolean,
    /** Methods whose rows carry one extra parameter byte. */
    val wideMethods: Set<Int>,
    /** False when the compiled dispatcher rejects every linked evolution, so no method means trade. */
    val tradeMethodPresent: Boolean,
) {
    init {
        require(tableOffset >= 0 && bank > 0 && rows > 0 && terminator in setOf(0x00, 0xFF))
    }
}

class Gen2CompactCoreMetadata(
    slots: Collection<Gen2CompactSpeciesSlot>,
    val relationships: Gen2CompactRelationshipContract? = null,
) {
    val slots: List<Gen2CompactSpeciesSlot> = Collections.unmodifiableList(slots.toList())

    init {
        require(this.slots.isNotEmpty())
        require(this.slots.all { it.id > 0 && it.nameIndex >= 0 && it.baseIndex >= 0 })
        require(this.slots.map { it.id }.distinct().size == this.slots.size)
        require(this.slots.map { it.nameIndex }.distinct().size == this.slots.size)
    }

    fun withRelationships(contract: Gen2CompactRelationshipContract?) = Gen2CompactCoreMetadata(slots, contract)

    override fun equals(other: Any?): Boolean =
        other is Gen2CompactCoreMetadata && slots == other.slots && relationships == other.relationships
    override fun hashCode(): Int = 31 * slots.hashCode() + (relationships?.hashCode() ?: 0)
}
