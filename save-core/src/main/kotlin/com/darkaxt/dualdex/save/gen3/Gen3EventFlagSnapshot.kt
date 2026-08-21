package com.darkaxt.dualdex.save.gen3

object Gen3EventFlagSnapshot {
    fun decode(saveBlock1: ByteArray, abi: Gen3EventFlagAbi): Set<Int>? {
        if (abi.byteOffset.toLong() + abi.byteCount > saveBlock1.size.toLong()) return null
        return buildSet {
            repeat(abi.byteCount) { byteIndex ->
                val value = saveBlock1[abi.byteOffset + byteIndex].toInt() and 0xFF
                repeat(Byte.SIZE_BITS) { bitIndex ->
                    if (value and (1 shl bitIndex) != 0) add(byteIndex * Byte.SIZE_BITS + bitIndex)
                }
            }
        }
    }
}
