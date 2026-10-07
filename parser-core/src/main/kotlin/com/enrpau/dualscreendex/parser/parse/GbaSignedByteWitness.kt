package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession

/** Local forward paths from an already proven byte read to an exact, unchanged sign extension. */
internal object GbaSignedByteWitness {
    fun proves(
        session: RomAnalysisSession,
        readAt: Int,
        destination: Int,
        codeEnd: Int,
        consumeWork: () -> Unit,
    ): Boolean {
        val end = minOf(codeEnd.toLong(), readAt.toLong() + 512).toInt()
        val pending = ArrayDeque<State>()
        pending += State(readAt + 2, List(16) { if (it == destination) RAW else ABSENT })
        val seen = hashSetOf<State>()
        while (pending.isNotEmpty()) {
            session.cancellation.throwIfCancellationRequested()
            val state = pending.removeFirst()
            if (!seen.add(state)) continue
            consumeWork()
            if (seen.size > 512) throw WideMoveBudgetStop()
            if (state.at < readAt + 2 || state.at.toLong() + 2 > end || state.registers.all { it == ABSENT }) continue
            val registers = state.registers.toMutableList()
            val word = session.rom.u16le(state.at)
            val lowDestination = word and 7
            val source = (word ushr 3) and 7
            val next = mutableListOf(state.at + 2)
            when {
                word and 0xf800 == 0x0000 -> registers[lowDestination] =
                    if ((word ushr 6) and 31 == 24 && registers[source] == RAW) SHIFTED else ABSENT
                word and 0xf800 == 0x1000 -> {
                    if ((word ushr 6) and 31 == 24 && registers[source] == SHIFTED) return true
                    registers[lowDestination] = ABSENT
                }
                word and 0xff00 == 0x4600 -> {
                    val target = lowDestination or ((word ushr 4) and 8)
                    val from = (word ushr 3) and 15
                    if (target >= 13 || from >= 13) continue
                    registers[target] = registers[from]
                }
                word and 0xf800 == 0x0800 || word and 0xf800 == 0x1800 -> registers[lowDestination] = ABSENT
                word and 0xf800 in listOf(0x2000, 0x3000, 0x3800, 0x4800) ->
                    registers[(word ushr 8) and 7] = ABSENT
                word and 0xf800 == 0x2800 || word and 0xff00 == 0x4500 -> Unit
                word and 0xfc00 == 0x4000 -> {
                    if ((word ushr 6) and 15 !in listOf(8, 10, 11)) registers[lowDestination] = ABSENT
                }
                word and 0xff00 == 0x4400 -> {
                    val target = lowDestination or ((word ushr 4) and 8)
                    if (target >= 13 || (word ushr 3) and 15 >= 13) continue
                    registers[target] = ABSENT
                }
                word and 0xe000 == 0x6000 || word and 0xf000 == 0x8000 -> {
                    if (word and 0x0800 != 0) registers[lowDestination] = ABSENT
                }
                word and 0xf000 == 0x5000 -> {
                    if ((word ushr 9) and 7 >= 3) registers[lowDestination] = ABSENT
                }
                word and 0xf000 == 0x9000 -> {
                    if (word and 0x0800 != 0) registers[(word ushr 8) and 7] = ABSENT
                }
                word and 0xf000 == 0xd000 && (word ushr 8) and 15 <= 13 -> {
                    val target = state.at + 4 + (word and 255).toByte().toInt() * 2
                    if (target <= state.at || target.toLong() + 2 > end) continue
                    next += target
                }
                word and 0xf800 == 0xe000 -> {
                    val raw = word and 0x7ff
                    val displacement = if (raw >= 0x400) raw - 0x800 else raw
                    val target = state.at + 4 + displacement * 2
                    if (target <= state.at || target.toLong() + 2 > end) continue
                    next.clear()
                    next += target
                }
                else -> continue
            }
            next.forEach { pending += State(it, registers.toList()) }
        }
        return false
    }

    private data class State(val at: Int, val registers: List<Int>)
    private const val ABSENT = 0
    private const val RAW = 1
    private const val SHIFTED = 2
}
