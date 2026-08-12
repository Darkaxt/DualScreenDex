package com.enrpau.dualscreendex.parser.analysis.arm7

sealed interface Arm7DecodeResult {
    data class Decoded(val instruction: Arm7Instruction) : Arm7DecodeResult
    data class Undefined(val offset: Int, val size: Int, val raw: Long, val reason: String) : Arm7DecodeResult
    data class UnsupportedArchitecture(val offset: Int, val size: Int, val raw: Long, val reason: String) : Arm7DecodeResult
    data class OutOfBounds(val offset: Int, val size: Int, val reason: String) : Arm7DecodeResult
    data class NeedsSecondHalf(val offset: Int, val raw: Long, val reason: String) : Arm7DecodeResult
}
