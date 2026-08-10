package com.enrpau.dualscreendex.simulator

class SplitMix64(seed: Long) {
    private var state = seed

    fun nextLong(): Long {
        state += -7046029254386353131L
        var value = state
        value = (value xor (value ushr 30)) * -4658895280553007687L
        value = (value xor (value ushr 27)) * -7723592293110705685L
        return value xor (value ushr 31)
    }

    fun nextInt(bound: Int): Int {
        require(bound > 0) { "bound must be positive" }
        return java.lang.Long.remainderUnsigned(nextLong(), bound.toLong()).toInt()
    }

    fun nextBoolean(): Boolean = nextLong() and 1L == 0L
}
