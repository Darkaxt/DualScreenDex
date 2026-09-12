package com.enrpau.dualscreendex.parser.language

import java.util.Collections

enum class RuntimeLanguageMemorySpace(val byteCount: Int) {
    GB_WRAM(0x2000),
    GBA_EWRAM(0x40000),
    GBA_IWRAM(0x8000),
}

data class RuntimeLanguageValueMapping(
    val value: Int,
    val language: LanguageTag,
)

data class RuntimeLanguageSelectionCandidate(
    val memorySpace: RuntimeLanguageMemorySpace,
    val offset: Int,
    val readWidthBytes: Int,
    val mask: Int,
    val shift: Int,
    val defaultValue: Int,
    val mappings: List<RuntimeLanguageValueMapping>,
    val evidence: List<LanguageEvidence>,
)

class RuntimeLanguageSelectionLayout internal constructor(
    val memorySpace: RuntimeLanguageMemorySpace,
    val offset: Int,
    val readWidthBytes: Int,
    val mask: Int,
    val shift: Int,
    val defaultValue: Int,
    mappings: List<RuntimeLanguageValueMapping>,
    evidence: List<LanguageEvidence>,
) {
    val mappings: List<RuntimeLanguageValueMapping> = Collections.unmodifiableList(mappings.toList())
    val evidence: List<LanguageEvidence> = Collections.unmodifiableList(evidence.toList())
    val defaultLanguage: LanguageTag = mappings.single { it.value == defaultValue }.language

    fun languageFor(rawValue: Int): LanguageTag? {
        val maximumRawValue = if (readWidthBytes == 1) 0xff else 0xffff
        if (rawValue !in 0..maximumRawValue) return null
        val decoded = (rawValue and mask) ushr shift
        return mappings.firstOrNull { it.value == decoded }?.language
    }

    override fun equals(other: Any?): Boolean = other is RuntimeLanguageSelectionLayout &&
        memorySpace == other.memorySpace &&
        offset == other.offset &&
        readWidthBytes == other.readWidthBytes &&
        mask == other.mask &&
        shift == other.shift &&
        defaultValue == other.defaultValue &&
        mappings == other.mappings &&
        evidence == other.evidence

    override fun hashCode(): Int {
        var result = memorySpace.hashCode()
        result = 31 * result + offset
        result = 31 * result + readWidthBytes
        result = 31 * result + mask
        result = 31 * result + shift
        result = 31 * result + defaultValue
        result = 31 * result + mappings.hashCode()
        result = 31 * result + evidence.hashCode()
        return result
    }
}

object RuntimeLanguageSelectionResolver {
    fun assembleManifest(
        manifests: List<RomLanguageManifest>,
        defaultLanguage: LanguageTag,
        candidates: List<RuntimeLanguageSelectionCandidate>,
    ): RomLanguageManifest? {
        if (manifests.isEmpty() || manifests.any { it.status != LanguageResolutionStatus.RESOLVED }) return null
        val projections = manifests.flatMap { it.projections }
        if (projections.size < 2 || projections.any { it.status != LanguageResolutionStatus.RESOLVED }) return null
        val byLanguage = projections.groupBy { it.language }
        if (byLanguage.any { (_, values) -> values.distinct().size != 1 }) return null
        val distinctProjections = byLanguage.values.map { it.single() }
        if (distinctProjections.none { it.language == defaultLanguage }) return null
        val manifest = RomLanguageManifest(
            defaultLanguage = defaultLanguage,
            projections = distinctProjections,
            status = LanguageResolutionStatus.RESOLVED,
            diagnostics = manifests.flatMap { it.diagnostics }.distinct(),
        )
        val runtimeSelection = resolve(manifest, candidates) ?: return null
        return manifest.withRuntimeSelection(runtimeSelection)
    }

    fun resolve(
        manifest: RomLanguageManifest,
        candidates: List<RuntimeLanguageSelectionCandidate>,
    ): RuntimeLanguageSelectionLayout? {
        if (manifest.status != LanguageResolutionStatus.RESOLVED || manifest.projections.size < 2) return null
        val defaultLanguage = manifest.defaultLanguage ?: return null
        val availableLanguages = manifest.projections
            .filter { it.status == LanguageResolutionStatus.RESOLVED }
            .mapTo(linkedSetOf()) { it.language }
        if (availableLanguages.size != manifest.projections.size) return null

        val layouts = candidates.mapNotNull { candidate ->
            candidate.toLayoutOrNull(availableLanguages, defaultLanguage)
        }.distinct()
        return layouts.singleOrNull()
    }

    private fun RuntimeLanguageSelectionCandidate.toLayoutOrNull(
        availableLanguages: Set<LanguageTag>,
        defaultLanguage: LanguageTag,
    ): RuntimeLanguageSelectionLayout? {
        if (readWidthBytes !in 1..2) return null
        if (offset < 0 || offset.toLong() + readWidthBytes > memorySpace.byteCount.toLong()) return null
        val maximumRawValue = if (readWidthBytes == 1) 0xff else 0xffff
        if (mask !in 1..maximumRawValue || shift !in 0..15 || (mask ushr shift) == 0) return null
        if (evidence.none { it.kind == LanguageEvidenceKind.COMPILED_CONSUMER }) return null
        if (evidence.none { it.kind == LanguageEvidenceKind.TABLE_RELATIONSHIP }) return null
        if (mappings.size < 2 || mappings.map { it.value }.distinct().size != mappings.size) return null
        if (mappings.mapTo(linkedSetOf()) { it.language } != availableLanguages) return null
        if (mappings.any { mapping ->
                mapping.value < 0 || (((mapping.value.toLong() shl shift) and mask.toLong()) ushr shift) != mapping.value.toLong()
            }) return null
        if (mappings.singleOrNull { it.value == defaultValue }?.language != defaultLanguage) return null

        return RuntimeLanguageSelectionLayout(
            memorySpace = memorySpace,
            offset = offset,
            readWidthBytes = readWidthBytes,
            mask = mask,
            shift = shift,
            defaultValue = defaultValue,
            mappings = mappings,
            evidence = evidence,
        )
    }
}
