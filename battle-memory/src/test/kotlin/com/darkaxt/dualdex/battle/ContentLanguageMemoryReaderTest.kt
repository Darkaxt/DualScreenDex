package com.darkaxt.dualdex.battle

import com.enrpau.dualscreendex.parser.language.LanguageEvidence
import com.enrpau.dualscreendex.parser.language.LanguageEvidenceKind
import com.enrpau.dualscreendex.parser.language.LanguageResolutionStatus
import com.enrpau.dualscreendex.parser.language.LanguageTag
import com.enrpau.dualscreendex.parser.language.LocalizedTableLayout
import com.enrpau.dualscreendex.parser.language.RomLanguageManifest
import com.enrpau.dualscreendex.parser.language.RomLanguageProjection
import com.enrpau.dualscreendex.parser.language.RuntimeLanguageMemorySpace
import com.enrpau.dualscreendex.parser.language.RuntimeLanguageSelectionCandidate
import com.enrpau.dualscreendex.parser.language.RuntimeLanguageSelectionLayout
import com.enrpau.dualscreendex.parser.language.RuntimeLanguageSelectionResolver
import com.enrpau.dualscreendex.parser.language.RuntimeLanguageValueMapping
import org.junit.Assert.assertEquals
import org.junit.Test

class ContentLanguageMemoryReaderTest {
    @Test
    fun resolvesMappedLiveLanguageFromBoundedLittleEndianBytes() {
        val result = ContentLanguageMemoryReader.read(
            layout = layout(readWidthBytes = 2, mask = 0x0300, shift = 8),
            input = ContentLanguageMemoryInput.Bytes(byteArrayOf(0x55, 0x01)),
        )

        assertEquals(ContentLanguageReadOutcome.Live(LanguageTag.GERMAN), result)
    }

    @Test
    fun invalidOrUnavailableRamFallsBackToParserDefault() {
        val selected = layout()

        assertEquals(
            ContentLanguageReadOutcome.Default,
            ContentLanguageMemoryReader.read(selected, ContentLanguageMemoryInput.Unavailable),
        )
        assertEquals(
            ContentLanguageReadOutcome.Default,
            ContentLanguageMemoryReader.read(selected, ContentLanguageMemoryInput.Bytes(byteArrayOf(3))),
        )
        assertEquals(
            ContentLanguageReadOutcome.Default,
            ContentLanguageMemoryReader.read(selected, ContentLanguageMemoryInput.Bytes(byteArrayOf())),
        )
    }

    @Test
    fun pendingReadIsDeferredAndMissingLayoutIsTerminalUnsupported() {
        assertEquals(
            ContentLanguageReadOutcome.Deferred,
            ContentLanguageMemoryReader.read(layout(), ContentLanguageMemoryInput.Pending),
        )
        assertEquals(
            ContentLanguageReadOutcome.TerminalUnsupported,
            ContentLanguageMemoryReader.read(null, ContentLanguageMemoryInput.Pending),
        )
    }

    private fun layout(
        readWidthBytes: Int = 1,
        mask: Int = 0x03,
        shift: Int = 0,
    ): RuntimeLanguageSelectionLayout {
        val manifest = RomLanguageManifest(
            defaultLanguage = LanguageTag.ENGLISH,
            projections = listOf(
                projection(LanguageTag.ENGLISH, "gb-english"),
                projection(LanguageTag.GERMAN, "gb-german"),
            ),
            status = LanguageResolutionStatus.RESOLVED,
        )
        return requireNotNull(RuntimeLanguageSelectionResolver.resolve(
            manifest = manifest,
            candidates = listOf(RuntimeLanguageSelectionCandidate(
                memorySpace = RuntimeLanguageMemorySpace.GB_WRAM,
                offset = 0x123,
                readWidthBytes = readWidthBytes,
                mask = mask,
                shift = shift,
                defaultValue = 0,
                mappings = listOf(
                    RuntimeLanguageValueMapping(0, LanguageTag.ENGLISH),
                    RuntimeLanguageValueMapping(1, LanguageTag.GERMAN),
                ),
                evidence = listOf(
                    LanguageEvidence(LanguageEvidenceKind.COMPILED_CONSUMER, "compiled selector read", 100),
                    LanguageEvidence(LanguageEvidenceKind.TABLE_RELATIONSHIP, "localized table branch", 100),
                ),
            )),
        ))
    }

    private fun projection(language: LanguageTag, codecId: String) = RomLanguageProjection(
        language = language,
        codecId = codecId,
        codecVersion = 1,
        localizedTables = LocalizedTableLayout(),
        evidence = emptyList(),
        status = LanguageResolutionStatus.RESOLVED,
    )
}
