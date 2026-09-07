package com.enrpau.dualscreendex.parser.analysis

/** Operand evidence retained only by the current accepted Gen II map-event traversal. */
@ConsistentCopyVisibility
data class Gen2ItemReference internal constructor(
    val poiKey: String,
    val itemId: Int,
    val operandOffset: Int,
    val kind: Kind,
    val baseAreaId: Int,
    val mapGroupTable: Int?,
    val mapGroupBank: Int?,
    val mapHeader: Int?,
    val attributesBank: Int,
    val attributes: Int,
    val scriptsBank: Int,
    val eventsRoot: Int,
    val eventRow: Int,
    val pointerField: Int,
    val tileX: Int,
    val tileY: Int,
    val quantity: Int?,
    val collectionFlag: Int?,
) {
    enum class Kind { VISIBLE_OBJECT, HIDDEN_EVENT }
}
