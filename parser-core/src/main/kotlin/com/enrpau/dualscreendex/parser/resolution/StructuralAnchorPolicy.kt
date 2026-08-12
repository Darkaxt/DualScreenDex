package com.enrpau.dualscreendex.parser.resolution

import java.util.Collections

class StructuralAnchorPolicy private constructor(permittedKinds: Set<DatasetKind>) {
    private val permittedKinds: Set<DatasetKind> = Collections.unmodifiableSet(permittedKinds.toSet())

    fun permits(kind: DatasetKind): Boolean = kind in permittedKinds

    companion object {
        private val DENY_ALL = StructuralAnchorPolicy(emptySet())

        fun denyAll(): StructuralAnchorPolicy = DENY_ALL

        fun allow(vararg kinds: DatasetKind): StructuralAnchorPolicy = StructuralAnchorPolicy(kinds.toSet())
    }

    override fun equals(other: Any?): Boolean =
        other is StructuralAnchorPolicy && permittedKinds == other.permittedKinds

    override fun hashCode(): Int = permittedKinds.hashCode()

    override fun toString(): String = "StructuralAnchorPolicy(permittedKinds=${permittedKinds.sortedBy { it.name }})"
}
