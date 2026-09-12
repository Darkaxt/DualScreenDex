package com.enrpau.dualscreendex.parser.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeLanguageSelectionResolverTest {
    @Test
    fun assemblesEveryResolvedProjectionOnlyWithSelectorAuthority() {
        val english = RomLanguageManifest(
            defaultLanguage = LanguageTag.ENGLISH,
            projections = listOf(projection(LanguageTag.ENGLISH)),
            status = LanguageResolutionStatus.RESOLVED,
        )
        val german = RomLanguageManifest(
            defaultLanguage = LanguageTag.GERMAN,
            projections = listOf(projection(LanguageTag.GERMAN, "gb-german")),
            status = LanguageResolutionStatus.RESOLVED,
        )

        val manifest = RuntimeLanguageSelectionResolver.assembleManifest(
            manifests = listOf(english, german),
            defaultLanguage = LanguageTag.ENGLISH,
            candidates = listOf(candidate()),
        )

        requireNotNull(manifest)
        assertEquals(listOf(LanguageTag.ENGLISH, LanguageTag.GERMAN), manifest.projections.map { it.language })
        assertEquals(LanguageTag.ENGLISH, manifest.defaultLanguage)
        assertEquals(LanguageTag.GERMAN, manifest.runtimeSelection?.languageFor(1))
        assertNull(RuntimeLanguageSelectionResolver.assembleManifest(
            manifests = listOf(english, german),
            defaultLanguage = LanguageTag.ENGLISH,
            candidates = emptyList(),
        ))
    }

    @Test
    fun resolvesCompiledBoundedOneByteSelector() {
        val result = RuntimeLanguageSelectionResolver.resolve(
            manifest = multilingualManifest(),
            candidates = listOf(candidate()),
        )

        requireNotNull(result)
        assertEquals(RuntimeLanguageMemorySpace.GB_WRAM, result.memorySpace)
        assertEquals(0x123, result.offset)
        assertEquals(1, result.readWidthBytes)
        assertEquals(0x03, result.mask)
        assertEquals(0, result.shift)
        assertEquals(LanguageTag.ENGLISH, result.defaultLanguage)
        assertEquals(LanguageTag.ENGLISH, result.languageFor(0))
        assertEquals(LanguageTag.GERMAN, result.languageFor(1))
        assertNull(result.languageFor(2))
    }

    @Test
    fun rejectsMissingCompiledConsumerProof() {
        assertNull(resolve(candidate(evidence = proof().filterNot {
            it.kind == LanguageEvidenceKind.COMPILED_CONSUMER
        })))
    }

    @Test
    fun rejectsMissingLocalizedTableSelectionProof() {
        assertNull(resolve(candidate(evidence = proof().filterNot {
            it.kind == LanguageEvidenceKind.TABLE_RELATIONSHIP
        })))
    }

    @Test
    fun rejectsOutOfRangeReadWindow() {
        assertNull(resolve(candidate(offset = 0x1fff, readWidthBytes = 2)))
        assertNull(resolve(candidate(offset = -1)))
    }

    @Test
    fun rejectsUnsupportedReadWidth() {
        assertNull(resolve(candidate(readWidthBytes = 3)))
        assertNull(resolve(candidate(readWidthBytes = 0)))
    }

    @Test
    fun rejectsDuplicateDecodedValues() {
        assertNull(resolve(candidate(mappings = listOf(
            RuntimeLanguageValueMapping(0, LanguageTag.ENGLISH),
            RuntimeLanguageValueMapping(0, LanguageTag.GERMAN),
        ))))
    }

    @Test
    fun rejectsMappingToUnavailableProjection() {
        assertNull(resolve(candidate(mappings = listOf(
            RuntimeLanguageValueMapping(0, LanguageTag.ENGLISH),
            RuntimeLanguageValueMapping(1, LanguageTag.JAPANESE),
        ))))
    }

    @Test
    fun rejectsDefaultValueNotMappedToManifestDefault() {
        assertNull(resolve(candidate(
            defaultValue = 0,
            mappings = listOf(
                RuntimeLanguageValueMapping(0, LanguageTag.GERMAN),
                RuntimeLanguageValueMapping(1, LanguageTag.ENGLISH),
            ),
        )))
    }

    @Test
    fun rejectsValuesOutsideMaskedDecodeRange() {
        assertNull(resolve(candidate(
            mask = 0x01,
            mappings = listOf(
                RuntimeLanguageValueMapping(0, LanguageTag.ENGLISH),
                RuntimeLanguageValueMapping(2, LanguageTag.GERMAN),
            ),
        )))
    }

    @Test
    fun rejectsCompetingCompleteCandidates() {
        assertNull(RuntimeLanguageSelectionResolver.resolve(
            manifest = multilingualManifest(),
            candidates = listOf(candidate(), candidate(offset = 0x124)),
        ))
    }

    @Test
    fun rejectsSingleLanguageAndUnresolvedManifests() {
        val singleLanguage = RomLanguageManifest(
            defaultLanguage = LanguageTag.ENGLISH,
            projections = listOf(projection(LanguageTag.ENGLISH)),
            status = LanguageResolutionStatus.RESOLVED,
        )
        assertNull(RuntimeLanguageSelectionResolver.resolve(singleLanguage, listOf(candidate())))
        assertNull(RuntimeLanguageSelectionResolver.resolve(RomLanguageManifest.UNKNOWN, listOf(candidate())))
    }

    @Test
    fun snapshotsPublishedMappingsAndEvidence() {
        val mappings = mutableListOf(
            RuntimeLanguageValueMapping(0, LanguageTag.ENGLISH),
            RuntimeLanguageValueMapping(1, LanguageTag.GERMAN),
        )
        val evidence = proof().toMutableList()
        val result = requireNotNull(resolve(candidate(mappings = mappings, evidence = evidence)))

        mappings += RuntimeLanguageValueMapping(2, LanguageTag.FRENCH)
        evidence += LanguageEvidence(LanguageEvidenceKind.HEADER_REGION_HINT, "late mutation", 50)

        assertEquals(2, result.mappings.size)
        assertEquals(2, result.evidence.size)
        assertTrue(result.mappings.none { it.language == LanguageTag.FRENCH })
    }

    private fun resolve(candidate: RuntimeLanguageSelectionCandidate): RuntimeLanguageSelectionLayout? =
        RuntimeLanguageSelectionResolver.resolve(multilingualManifest(), listOf(candidate))

    private fun candidate(
        offset: Int = 0x123,
        readWidthBytes: Int = 1,
        mask: Int = 0x03,
        shift: Int = 0,
        defaultValue: Int = 0,
        mappings: List<RuntimeLanguageValueMapping> = listOf(
            RuntimeLanguageValueMapping(0, LanguageTag.ENGLISH),
            RuntimeLanguageValueMapping(1, LanguageTag.GERMAN),
        ),
        evidence: List<LanguageEvidence> = proof(),
    ) = RuntimeLanguageSelectionCandidate(
        memorySpace = RuntimeLanguageMemorySpace.GB_WRAM,
        offset = offset,
        readWidthBytes = readWidthBytes,
        mask = mask,
        shift = shift,
        defaultValue = defaultValue,
        mappings = mappings,
        evidence = evidence,
    )

    private fun proof(): List<LanguageEvidence> = listOf(
        LanguageEvidence(LanguageEvidenceKind.COMPILED_CONSUMER, "compiled selector load and mask", 100),
        LanguageEvidence(LanguageEvidenceKind.TABLE_RELATIONSHIP, "selector branches to localized table roots", 100),
    )

    private fun multilingualManifest() = RomLanguageManifest(
        defaultLanguage = LanguageTag.ENGLISH,
        projections = listOf(
            projection(LanguageTag.ENGLISH),
            projection(LanguageTag.GERMAN, "gb-german"),
        ),
        status = LanguageResolutionStatus.RESOLVED,
    )

    private fun projection(language: LanguageTag, codecId: String = "gb-english") = RomLanguageProjection(
        language = language,
        codecId = codecId,
        codecVersion = 1,
        localizedTables = LocalizedTableLayout(),
        evidence = emptyList(),
        status = LanguageResolutionStatus.RESOLVED,
    )
}
