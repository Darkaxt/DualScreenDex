package com.enrpau.dualscreendex.parser.model

object Gen3LearnsetEncoding {
    const val STANDARD_MOVE_BITS = 9
    const val EXPANDED_MOVE_BITS = 10
    const val STANDARD_MOVE_CAPACITY = 1 shl STANDARD_MOVE_BITS

    fun packedMoveBits(moveCount: Int): Int =
        if (moveCount > STANDARD_MOVE_CAPACITY) EXPANDED_MOVE_BITS else STANDARD_MOVE_BITS
}
