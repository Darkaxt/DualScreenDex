package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.io.RomImage

internal object Gen3InvariantSignHeadlineResolver {
    fun resolve(
        rom: RomImage,
        script: Int,
        cancellation: ParserCancellationToken = ParserCancellationToken.NONE,
        readHeadline: (Int) -> String?,
    ): Headline? {
        val pending = ArrayDeque<State>()
        pending.addLast(State(script))
        var headline: String? = null
        var contextual = false
        var processed = 0
        while (pending.isNotEmpty()) {
            cancellation.throwIfCancellationRequested()
            if (++processed > MAX_STATES) return null
            val state = pending.removeLast()
            val cursor = state.cursor
            if (!rom.contains(cursor, 1) || cursor in state.path) return null
            val path = state.path + cursor
            fun follow(target: Int, text: Int? = state.text) {
                pending.addLast(State(target, text, path))
            }
            when (rom.u8(cursor)) {
                LOCK_ALL -> follow(cursor + 1)
                CHECK_FLAG -> {
                    if (!rom.contains(cursor, 3)) return null
                    follow(cursor + 3)
                }
                GOTO -> {
                    if (!rom.contains(cursor, 5)) return null
                    follow(rom.gbaPointer(cursor + 1) ?: return null)
                }
                GOTO_IF -> {
                    if (!rom.contains(cursor, 6) || rom.u8(cursor + 1) !in 0..5) return null
                    val target = rom.gbaPointer(cursor + 2) ?: return null
                    // Do not select a live flag state: every outcome must have a valid headline.
                    follow(cursor + 6)
                    follow(target)
                }
                LOAD_WORD -> {
                    if (!rom.contains(cursor, 6) || rom.u8(cursor + 1) != 0) return null
                    follow(cursor + 6, rom.gbaPointer(cursor + 2) ?: return null)
                }
                CALL_STD -> {
                    if (!rom.contains(cursor, 2) || rom.u8(cursor + 1) !in MESSAGE_STANDARD_IDS) return null
                    val text = state.text ?: return null
                    val candidate = readHeadline(text) ?: return null
                    if (headline != null && headline != candidate) contextual = true
                    headline = candidate
                }
                else -> return null
            }
        }
        cancellation.throwIfCancellationRequested()
        return headline?.let { Headline(displayName = it.takeUnless { contextual }, contextual = contextual) }
    }

    data class Headline(val displayName: String?, val contextual: Boolean)

    private fun RomImage.contains(offset: Int, length: Int): Boolean =
        offset >= 0 && offset.toLong() + length <= size.toLong()

    private data class State(
        val cursor: Int,
        val text: Int? = null,
        val path: Set<Int> = emptySet(),
    )

    private const val LOCK_ALL = 0x69
    private const val CHECK_FLAG = 0x2B
    private const val GOTO = 0x05
    private const val GOTO_IF = 0x06
    private const val LOAD_WORD = 0x0F
    private const val CALL_STD = 0x09
    private val MESSAGE_STANDARD_IDS = 3..5
    private const val MAX_STATES = 128
}
