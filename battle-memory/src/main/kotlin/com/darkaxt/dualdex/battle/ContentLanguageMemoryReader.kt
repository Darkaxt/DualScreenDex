package com.darkaxt.dualdex.battle

import com.enrpau.dualscreendex.parser.language.LanguageTag
import com.enrpau.dualscreendex.parser.language.RuntimeLanguageSelectionLayout

sealed interface ContentLanguageMemoryInput {
    data object Pending : ContentLanguageMemoryInput
    data object Unavailable : ContentLanguageMemoryInput
    class Bytes(val value: ByteArray) : ContentLanguageMemoryInput
}

sealed interface ContentLanguageReadOutcome {
    data class Live(val language: LanguageTag) : ContentLanguageReadOutcome
    data object Default : ContentLanguageReadOutcome
    data object Deferred : ContentLanguageReadOutcome
    data object TerminalUnsupported : ContentLanguageReadOutcome
}

object ContentLanguageMemoryReader {
    fun read(
        layout: RuntimeLanguageSelectionLayout?,
        input: ContentLanguageMemoryInput,
    ): ContentLanguageReadOutcome {
        layout ?: return ContentLanguageReadOutcome.TerminalUnsupported
        return when (input) {
            ContentLanguageMemoryInput.Pending -> ContentLanguageReadOutcome.Deferred
            ContentLanguageMemoryInput.Unavailable -> ContentLanguageReadOutcome.Default
            is ContentLanguageMemoryInput.Bytes -> decode(layout, input.value)
        }
    }

    private fun decode(
        layout: RuntimeLanguageSelectionLayout,
        bytes: ByteArray,
    ): ContentLanguageReadOutcome {
        if (bytes.size != layout.readWidthBytes) return ContentLanguageReadOutcome.Default
        var rawValue = 0
        bytes.forEachIndexed { index, byte ->
            rawValue = rawValue or ((byte.toInt() and 0xff) shl (index * 8))
        }
        return layout.languageFor(rawValue)?.let(ContentLanguageReadOutcome::Live)
            ?: ContentLanguageReadOutcome.Default
    }
}
