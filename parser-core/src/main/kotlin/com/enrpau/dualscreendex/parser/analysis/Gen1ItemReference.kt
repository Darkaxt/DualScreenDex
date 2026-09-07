package com.enrpau.dualscreendex.parser.analysis

/** Source-bound structural item reference, not a claim that every byte is a valid game item. */
@ConsistentCopyVisibility
data class Gen1ItemReference internal constructor(
    val poiKey: String,
    val itemId: Int,
    val operandOffset: Int,
    val kind: Kind,
    val baseAreaId: Int,
    val sourceBank: Int,
    val recordRoot: Int,
    val mapHeader: Int? = null,
    val objectPointerField: Int? = null,
    val mapBankTable: Int? = null,
    val mapPointerTable: Int? = null,
    val hiddenHandler: Int? = null,
    val coordinateRoot: Int? = null,
    val coordinateIndex: Int? = null,
) {
    enum class Kind { VISIBLE_OBJECT, HIDDEN_EVENT }
}
