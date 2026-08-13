package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.catalog.Gen3MapLocationResolver
import com.enrpau.dualscreendex.parser.catalog.RelationshipMaterializers
import com.enrpau.dualscreendex.parser.catalog.RecordMaterializers
import com.enrpau.dualscreendex.parser.detect.RomHeaderReader
import com.enrpau.dualscreendex.parser.family.FamilyProbeCoordinator
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.ParseResult
import com.enrpau.dualscreendex.parser.model.CapabilityEvidence
import com.enrpau.dualscreendex.parser.model.CapabilityReviewStatus
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.ParserProbe
import com.enrpau.dualscreendex.parser.model.RomCapability
import com.enrpau.dualscreendex.parser.model.RomHeader
import com.enrpau.dualscreendex.parser.model.RomProfile
import com.enrpau.dualscreendex.parser.model.SelectionStatus
import com.enrpau.dualscreendex.parser.profile.KnownProfiles
import com.enrpau.dualscreendex.parser.sprite.SpriteMaterializer

internal data class CatalogAnalysisContext(
    val analysis: ParseResult,
    val resolveGen3AreaNames: (Set<Int>) -> Map<Int, String>,
    val resolveWorldMap: (Int, Set<Int>) -> WorldMapResolution,
)

object ParserOrchestrator {
    const val minimumScore = 75
    const val minimumMargin = 10
    private val familyProbeCoordinator = FamilyProbeCoordinator()

    fun analyze(rom: RomImage): ParseResult = analyze(rom, ::newSession)

    internal fun analyzeForCatalog(rom: RomImage): CatalogAnalysisContext {
        lateinit var sharedSession: RomAnalysisSession
        val analysis = analyze(rom) { analyzedRom, header, exactProfile ->
            newSession(analyzedRom, header, exactProfile).also { sharedSession = it }
        }
        return CatalogAnalysisContext(
            analysis = analysis,
            resolveGen3AreaNames = { baseAreaIds ->
                val references = sharedSession.gbaReferenceIndex
                if (references == null || references.overflowed) {
                    emptyMap()
                } else {
                    Gen3MapLocationResolver.resolve(sharedSession.rom, baseAreaIds, references)
                }
            },
            resolveWorldMap = { generation, baseAreaIds ->
                when (generation) {
                    1 -> Gen1WorldMapResolver.resolve(sharedSession, baseAreaIds)
                    2 -> Gen2WorldMapResolver.resolve(sharedSession, baseAreaIds)
                    3 -> Gen3WorldMapResolver.resolve(sharedSession, baseAreaIds)
                    else -> WorldMapResolution.Unavailable(
                        "generation",
                        "world maps are not part of this engine's normalized parser path",
                    )
                }
            },
        )
    }

    internal fun analyze(
        rom: RomImage,
        sessionFactory: (RomImage, RomHeader, RomProfile?) -> RomAnalysisSession,
    ): ParseResult {
        val header = RomHeaderReader.read(rom)
        val exact = KnownProfiles.bySha256(rom.sha256)
        val session = sessionFactory(rom, header, exact)
        val probes = familyProbeCoordinator.probeAll(session)
        val selection = if (exact != null) {
            val probe = probes.first { it.family == exact.family }
            Selection(SelectionStatus.SELECTED, probe, null)
        } else {
            select(probes)
        }
        val capabilities = applySpeciesSemanticDomain(
            rom,
            selection.winner?.resolvedLayout,
            resolveCapabilities(selection, probes),
        )
        return ParseResult(
            header = header,
            sha256 = rom.sha256,
            crc32 = rom.crc32,
            size = rom.size,
            status = selection.status,
            selectedFamily = selection.winner?.family,
            selectedProfile = selection.winner?.profileName,
            runnerUpMargin = selection.margin,
            probes = probes,
            capabilities = capabilities,
            diagnostics = when (selection.status) {
                SelectionStatus.AMBIGUOUS -> listOf("top parser did not lead by $minimumMargin points")
                SelectionStatus.NO_FAMILY_MATCH -> listOf("no mainline-family parser passed score and anchor requirements")
                else -> emptyList()
            },
        )
    }

    private fun newSession(
        rom: RomImage,
        header: RomHeader,
        exactProfile: RomProfile?,
    ): RomAnalysisSession = RomAnalysisSession(
        rom = rom,
        header = header,
        exactProfile = exactProfile,
    )

    internal fun applySpeciesSemanticDomain(
        rom: RomImage,
        layout: com.enrpau.dualscreendex.parser.model.ResolvedRomLayout?,
        capabilities: List<CapabilityEvidence>,
    ): List<CapabilityEvidence> {
        if (layout?.generation != 3 || layout.tables.speciesNames == null || layout.tables.baseStats == null) {
            return capabilities
        }
        val domain = when (val resolution = SpeciesSemanticDomainResolver.resolveWithEvidence(rom, layout)) {
            is SpeciesSemanticDomainResolution.Resolved -> resolution.domain
            is SpeciesSemanticDomainResolution.Unavailable -> {
                return speciesIndexUnavailableCapabilities(
                    capabilities,
                    resolution.reason,
                    resolution.ambiguous,
                )
            }
            is SpeciesSemanticDomainResolution.BudgetExceeded -> {
                return speciesIndexUnavailableCapabilities(
                    capabilities,
                    "budget kind: ${resolution.budgetKind.name}; " +
                        "budget observation: at least ${resolution.observed} units (limit ${resolution.limit}); " +
                        resolution.reason,
                    ambiguous = false,
                )
            }
        }
        val byCapability = capabilities.associateBy { it.capability }
        val expansion = layout.pokeemeraldExpansion != null
        val names = byCapability[RomCapability.SPECIES_NAMES]?.toValidationEvidence()?.let { evidence ->
            domain.applyToNames(evidence, authoritativeFallback = expansion)
        }
            ?: return capabilities
        val stats = byCapability[RomCapability.BASE_STATS]?.toValidationEvidence()?.let { evidence ->
            domain.applyToStats(evidence, authoritativeFallback = expansion)
        }
            ?: return capabilities
        val learnsets = byCapability[RomCapability.LEARNSETS]?.toValidationEvidence()?.let { evidence ->
            domain.applyToLearnsets(
                evidence,
                if (expansion) {
                    RelationshipMaterializers.learnsets(rom, layout).keys
                } else {
                    layout.resolvedDatasets.learnsets?.catalogPrimaryEntries()?.keys.orEmpty()
                },
                authoritativeFallback = expansion,
            )
        }
        val descriptions = when {
            expansion -> byCapability[RomCapability.POKEDEX_DESCRIPTIONS]?.toValidationEvidence()?.let { evidence ->
                domain.applyToDescriptions(
                    evidence,
                    RelationshipMaterializers.descriptions(rom, layout).keys,
                    authoritativeFallback = true,
                )
            }
            domain.source == SpeciesSemanticDomainSource.STRONGLY_REFERENCED_REGIONAL_ORDER ||
                domain.source == SpeciesSemanticDomainSource.COMPILED_SPECIES_TO_DEX_MAP -> {
                byCapability[RomCapability.POKEDEX_DESCRIPTIONS]?.toValidationEvidence()?.let { evidence ->
                    val byDex = layout.resolvedDatasets.descriptions?.catalogDescriptions().orEmpty()
                    val coveredSpeciesIds = RecordMaterializers.species(rom, layout).values
                        .filter { species -> species.dexNumber.value in byDex }
                        .mapTo(linkedSetOf()) { it.id }
                    domain.applyToDescriptions(evidence, coveredSpeciesIds)
                }
            }
            else -> null
        }
        val sprites = if (expansion) {
            byCapability[RomCapability.SPRITES]?.toValidationEvidence()?.let { evidence ->
                domain.applyToSprites(
                    evidence,
                    SpriteMaterializer.pokemon(rom, layout).keys,
                    authoritativeFallback = true,
                )
            }
        } else {
            null
        }
        val replacements = mapOf(
            RomCapability.SPECIES_NAMES to capabilityEvidence(RomCapability.SPECIES_NAMES, names),
            RomCapability.SPECIES_TYPES to capabilityEvidence(RomCapability.SPECIES_TYPES, stats),
            RomCapability.BASE_STATS to capabilityEvidence(RomCapability.BASE_STATS, stats),
            RomCapability.SPECIES_CATALOG to capabilityEvidence(
                RomCapability.SPECIES_CATALOG,
                speciesCatalogEvidence(names, stats),
            ),
        ) + buildMap {
            learnsets?.let { put(RomCapability.LEARNSETS, capabilityEvidence(RomCapability.LEARNSETS, it)) }
            descriptions?.let {
                put(
                    RomCapability.POKEDEX_DESCRIPTIONS,
                    capabilityEvidence(RomCapability.POKEDEX_DESCRIPTIONS, it),
                )
            }
            sprites?.let { put(RomCapability.SPRITES, capabilityEvidence(RomCapability.SPRITES, it)) }
        }
        return capabilities.map { replacements[it.capability] ?: it }
    }

    private fun speciesIndexUnavailableCapabilities(
        capabilities: List<CapabilityEvidence>,
        reason: String,
        ambiguous: Boolean,
    ): List<CapabilityEvidence> = capabilities.map { evidence ->
        if (evidence.capability != RomCapability.SPECIES_CATALOG) return@map evidence
        evidence.copy(
            compatible = false,
            confidence = 0.0,
            reasons = (evidence.reasons + listOf(
                "species-to-Dex resolution unavailable; semantic species domain was not applied",
                reason,
            )).distinct(),
            status = if (ambiguous) CapabilityStatus.AMBIGUOUS else CapabilityStatus.NOT_FOUND,
            reviewStatus = CapabilityReviewStatus.MANUAL_REVIEW,
            coveredRecords = null,
            expectedRecords = null,
            incompleteRecords = null,
            validatorReviewRecommended = true,
        )
    }

    private fun CapabilityEvidence.toValidationEvidence() =
        com.enrpau.dualscreendex.parser.model.ValidationEvidence(
            compatible = compatible,
            validRecords = validRecords ?: count ?: 0,
            totalRecords = totalRecords ?: count ?: 0,
            confidence = confidence,
            reasons = reasons,
            offset = offset,
            recordSize = recordSize,
            elementSize = elementSize,
            ambiguous = status == CapabilityStatus.AMBIGUOUS,
            reviewRecommended = validatorReviewRecommended,
            coveredRecords = coveredRecords,
            expectedRecords = expectedRecords,
            incompleteRecords = incompleteRecords,
        )

    fun select(probes: List<ParserProbe>): Selection {
        val eligible = probes
            .filter { it.hardGatePassed && it.anchors >= 2 }
            .sortedWith(compareByDescending<ParserProbe> { it.score }.thenBy { it.family.name })
        val top = eligible.firstOrNull() ?: return Selection(SelectionStatus.NO_FAMILY_MATCH, null, null)
        if (top.score < minimumScore) return Selection(SelectionStatus.NO_FAMILY_MATCH, null, null)
        val runnerUp = eligible.drop(1).firstOrNull()
        val margin = top.score - (runnerUp?.score ?: 0)
        return if (margin >= minimumMargin) {
            Selection(SelectionStatus.SELECTED, top, margin)
        } else {
            Selection(SelectionStatus.AMBIGUOUS, null, margin)
        }
    }

    fun resolveCapabilities(selection: Selection, probes: List<ParserProbe>): List<CapabilityEvidence> {
        selection.winner?.let { return completeCapabilitySet(it.capabilities) }
        val structurallyCredible = probes.filter { it.hardGatePassed && it.anchors >= 2 }
        return RomCapability.entries.map { capability ->
            val evidence = structurallyCredible.mapNotNull { probe ->
                probe.capabilities.firstOrNull { it.capability == capability }
            }
            val compatible = evidence.filter { it.compatible }
            if (compatible.isNotEmpty()) {
                val locations = compatible.map { Triple(it.offset, it.count, it.recordSize) }.distinct()
                if (locations.size == 1) {
                    val strongest = compatible.maxBy { it.confidence }
                    val ambiguous = compatible.any { it.status == CapabilityStatus.AMBIGUOUS }
                    val validatorReviewRecommended = compatible.any { it.validatorReviewRecommended }
                    strongest.copy(
                        reasons = (listOf(strongest) + compatible)
                            .distinct()
                            .flatMap { it.reasons }
                            .distinct(),
                        status = if (ambiguous) CapabilityStatus.AMBIGUOUS else strongest.status,
                        reviewStatus = if (
                            ambiguous || validatorReviewRecommended ||
                            compatible.any { it.reviewStatus == CapabilityReviewStatus.MANUAL_REVIEW }
                        ) {
                            CapabilityReviewStatus.MANUAL_REVIEW
                        } else {
                            strongest.reviewStatus
                        },
                        validatorReviewRecommended = validatorReviewRecommended,
                    )
                } else {
                    CapabilityEvidence(
                        capability = capability,
                        compatible = false,
                        confidence = compatible.maxOf { it.confidence },
                        reasons = buildList {
                            add("conflicting validated locators across candidate families")
                            compatible.flatMapTo(this) { it.reasons }
                        }.distinct(),
                        status = CapabilityStatus.AMBIGUOUS,
                        reviewStatus = CapabilityReviewStatus.MANUAL_REVIEW,
                        validatorReviewRecommended = compatible.any { it.validatorReviewRecommended },
                    )
                }
            } else {
                evidence.maxByOrNull { it.confidence }?.copy(
                    compatible = false,
                    reasons = (evidence.maxByOrNull { it.confidence }?.reasons.orEmpty() +
                        "no family-independent compatible evidence").distinct(),
                    status = CapabilityStatus.NOT_FOUND,
                ) ?: unavailable(capability)
            }
        }
    }

    private fun completeCapabilitySet(capabilities: List<CapabilityEvidence>): List<CapabilityEvidence> =
        RomCapability.entries.map { capability ->
            capabilities.firstOrNull { it.capability == capability } ?: unavailable(capability)
        }

    private fun unavailable(capability: RomCapability) = CapabilityEvidence(
        capability = capability,
        compatible = false,
        confidence = 0.0,
        reasons = listOf("no validated locator was found"),
        status = CapabilityStatus.NOT_FOUND,
    )

    data class Selection(
        val status: SelectionStatus,
        val winner: ParserProbe?,
        val margin: Int?,
    )
}
