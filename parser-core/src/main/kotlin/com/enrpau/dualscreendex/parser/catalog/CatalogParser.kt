package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.CapabilityEvidence
import com.enrpau.dualscreendex.parser.model.CapabilityReviewStatus
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.ParseResult
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import com.enrpau.dualscreendex.parser.model.RomCapability
import com.enrpau.dualscreendex.parser.model.SelectionStatus
import com.enrpau.dualscreendex.parser.parse.ParserOrchestrator
import com.enrpau.dualscreendex.parser.parse.LocalMapResolution
import com.enrpau.dualscreendex.parser.parse.WorldMapResolution
import com.enrpau.dualscreendex.parser.parse.Gen3SaveBlock1PointerResolver
import com.enrpau.dualscreendex.parser.parse.Gen3RuntimeMemoryLayoutResolver
import com.enrpau.dualscreendex.parser.parse.Gen3TrainerAssetResolver
import com.enrpau.dualscreendex.parser.parse.GbTrainerAssetResolver
import com.enrpau.dualscreendex.parser.dataset.natures.NatureResolution
import com.enrpau.dualscreendex.parser.sprite.BallSpriteMaterializer
import com.enrpau.dualscreendex.parser.sprite.SpriteMaterializer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.concurrent.CancellationException
import java.util.zip.InflaterInputStream

data class CatalogParseResult(
    val analysis: ParseResult,
    val layout: ResolvedRomLayout?,
    val catalog: ParsedCatalog?,
)

data class CatalogParseAttempt(
    val analysis: ParseResult,
    val layout: ResolvedRomLayout?,
    val catalog: Result<ParsedCatalog>?,
)

enum class CatalogMaterializationPhase { ESSENTIAL, SPECIES_MEDIA, RELATIONSHIPS, EXTENDED, COMPLETE }

enum class CatalogWorkModule {
    ROM_IDENTITY,
    FAMILY_AND_TABLES,
    CORE_RECORDS,
    SPECIES_MEDIA,
    EVOLUTIONS_AND_LEARNSETS,
    ENCOUNTERS,
    MOVE_DATA,
    ABILITY_DATA,
    MAPS,
    TRAINER_AND_THEME,
    CATALOG_STORAGE,
}

data class CatalogWorkProgress(
    val module: CatalogWorkModule,
    val completedUnits: Int = module.ordinal,
    val totalUnits: Int = CatalogWorkModule.entries.size,
)

internal fun reportCatalogWork(
    onWork: ((CatalogWorkProgress) -> Unit)?,
    module: CatalogWorkModule,
) {
    if (onWork == null) return
    try {
        onWork(CatalogWorkProgress(module))
    } catch (failure: CancellationException) {
        throw failure
    } catch (_: Exception) {
        // Progress observers are optional and must not fail parsing.
    }
}

internal fun natureCapabilityEvidence(
    resolution: NatureResolution?,
    generation: Int,
): CapabilityEvidence = when (resolution) {
    is NatureResolution.Resolved -> {
        val catalog = resolution.catalog
        val namedRecords = catalog.records.count { it.name != null }
        val flavorRecords = catalog.records.count { it.flavorModifiers != null }
        val completeRecords = catalog.records.count { it.name != null && it.flavorModifiers != null }
        val totalRecords = catalog.records.size
        val complete = completeRecords == totalRecords
        CapabilityEvidence(
            capability = RomCapability.NATURES,
            compatible = true,
            confidence = (totalRecords + namedRecords + flavorRecords).toDouble() / (totalRecords * 3),
            offset = catalog.statTableOffset,
            count = totalRecords,
            reasons = buildList {
                add("decoded ROM-native Nature stat effects")
                if (namedRecords == totalRecords) {
                    add("decoded ROM-native Nature names")
                } else {
                    add("ROM-native Nature names are unavailable for ${totalRecords - namedRecords}/$totalRecords records")
                }
                if (flavorRecords == totalRecords) {
                    add("decoded ROM-native Nature flavor affinities")
                } else {
                    add(
                        "ROM-native Nature flavor affinities are unavailable for " +
                            "${totalRecords - flavorRecords}/$totalRecords records",
                    )
                }
            },
            status = if (complete) CapabilityStatus.AVAILABLE else CapabilityStatus.PARTIAL,
            validRecords = totalRecords,
            totalRecords = totalRecords,
            reviewStatus = if (complete) CapabilityReviewStatus.NONE else CapabilityReviewStatus.MANUAL_REVIEW,
            coveredRecords = completeRecords,
            expectedRecords = totalRecords,
            incompleteRecords = totalRecords - completeRecords,
        )
    }
    is NatureResolution.Ambiguous -> CapabilityEvidence(
        capability = RomCapability.NATURES,
        compatible = false,
        confidence = 0.0,
        reasons = listOf("multiple compiled Nature table contracts remained (${resolution.candidates})"),
        status = CapabilityStatus.AMBIGUOUS,
        reviewStatus = CapabilityReviewStatus.MANUAL_REVIEW,
    )
    is NatureResolution.BudgetExceeded -> CapabilityEvidence(
        capability = RomCapability.NATURES,
        compatible = false,
        confidence = 0.0,
        reasons = listOf(resolution.reason),
        status = CapabilityStatus.NOT_FOUND,
        reviewStatus = CapabilityReviewStatus.MANUAL_REVIEW,
    )
    is NatureResolution.Unavailable -> CapabilityEvidence(
        capability = RomCapability.NATURES,
        compatible = false,
        confidence = 0.0,
        reasons = listOf(resolution.reason),
        status = if (generation < 3) CapabilityStatus.NOT_APPLICABLE else CapabilityStatus.NOT_FOUND,
    )
    null -> CapabilityEvidence(
        capability = RomCapability.NATURES,
        compatible = false,
        confidence = 0.0,
        reasons = listOf("Natures are not part of this engine"),
        status = if (generation < 3) CapabilityStatus.NOT_APPLICABLE else CapabilityStatus.NOT_FOUND,
    )
}

data class CatalogMaterializationProgress(
    val phase: CatalogMaterializationPhase,
    val completedUnits: Int,
    val totalUnits: Int,
    val catalog: ParsedCatalog,
)

object CatalogParser {
    fun parse(
        rom: RomImage,
        onProgress: ((CatalogMaterializationProgress) -> Unit)? = null,
    ): CatalogParseResult = parseWithWork(rom, onProgress, null)

    fun parse(
        rom: RomImage,
        cancellation: ParserCancellationToken,
        onProgress: ((CatalogMaterializationProgress) -> Unit)? = null,
    ): CatalogParseResult = parseWithWork(rom, cancellation, onProgress, null)

    fun parseWithWork(
        rom: RomImage,
        onProgress: ((CatalogMaterializationProgress) -> Unit)? = null,
        onWork: ((CatalogWorkProgress) -> Unit)? = null,
    ): CatalogParseResult = parseWithWork(
        rom,
        ParserCancellationToken.NONE,
        onProgress,
        onWork,
    )

    fun parseWithWork(
        rom: RomImage,
        cancellation: ParserCancellationToken,
        onProgress: ((CatalogMaterializationProgress) -> Unit)? = null,
        onWork: ((CatalogWorkProgress) -> Unit)? = null,
    ): CatalogParseResult {
        val attempt = parseCatchingWithWork(rom, cancellation, onProgress, onWork)
        return CatalogParseResult(
            attempt.analysis,
            attempt.layout,
            attempt.catalog?.getOrThrow(),
        )
    }

    fun parseCatching(
        rom: RomImage,
        onProgress: ((CatalogMaterializationProgress) -> Unit)? = null,
    ): CatalogParseAttempt = parseCatchingWithWork(rom, onProgress, null)

    fun parseCatching(
        rom: RomImage,
        cancellation: ParserCancellationToken,
        onProgress: ((CatalogMaterializationProgress) -> Unit)? = null,
    ): CatalogParseAttempt = parseCatchingWithWork(rom, cancellation, onProgress, null)

    fun parseCatchingWithWork(
        rom: RomImage,
        onProgress: ((CatalogMaterializationProgress) -> Unit)? = null,
        onWork: ((CatalogWorkProgress) -> Unit)? = null,
    ): CatalogParseAttempt = parseCatchingWithWork(
        rom,
        ParserCancellationToken.NONE,
        onProgress,
        onWork,
    )

    fun parseCatchingWithWork(
        rom: RomImage,
        cancellation: ParserCancellationToken,
        onProgress: ((CatalogMaterializationProgress) -> Unit)? = null,
        onWork: ((CatalogWorkProgress) -> Unit)? = null,
    ): CatalogParseAttempt {
        cancellation.throwIfCancellationRequested()
        val context = ParserOrchestrator.analyzeForCatalog(rom, onWork, cancellation)
        val analysis = context.analysis
        if (analysis.status != SelectionStatus.SELECTED || analysis.selectedFamily == null) {
            cancellation.throwIfCancellationRequested()
            return CatalogParseAttempt(analysis, null, null)
        }
        val layout = analysis.probes.singleOrNull { it.family == analysis.selectedFamily }?.resolvedLayout
            ?: return CatalogParseAttempt(analysis, null, null)
        return CatalogParseAttempt(
            analysis,
            layout,
            runCatching {
                CatalogMaterializer.materialize(
                    rom = rom,
                    analysis = analysis,
                    layout = layout,
                    onProgress = onProgress,
                    onWork = onWork,
                    resolveGen3AreaNames = context.resolveGen3AreaNames,
                    resolveWorldMap = context.resolveWorldMap,
                    resolveLocalMaps = context.resolveLocalMaps,
                    resolveMoveDescriptions = context.resolveMoveDescriptions,
                    resolveAbilityMechanics = context.resolveAbilityMechanics,
                    resolveNatures = context.resolveNatures,
                    cancellation = cancellation,
                )
            },
        )
    }
}

object CatalogMaterializer {
    fun materialize(
        rom: RomImage,
        analysis: ParseResult,
        layout: ResolvedRomLayout,
        onProgress: ((CatalogMaterializationProgress) -> Unit)? = null,
        onWork: ((CatalogWorkProgress) -> Unit)? = null,
        resolveGen3AreaNames: ((ResolvedRomLayout, Set<Int>) -> Map<Int, String>)? = null,
        resolveWorldMap: ((ResolvedRomLayout, Set<Int>) -> WorldMapResolution)? = null,
        resolveLocalMaps: ((ResolvedRomLayout, Set<Int>) -> LocalMapResolution)? = null,
        resolveMoveDescriptions: ((ResolvedRomLayout) -> MoveDescriptionResult?)? = null,
        resolveAbilityMechanics: ((ResolvedRomLayout, Map<Int, AbilityRecord>, Map<Int, TypeRecord>, AbilityDescriptionResult?) -> AbilityMechanicsResult?)? = null,
        resolveNatures: ((ResolvedRomLayout) -> NatureResolution)? = null,
        cancellation: ParserCancellationToken = ParserCancellationToken.NONE,
        materializeTheme: ((Map<CatalogThemeAssetClass, List<RgbaSprite>>, List<DirectCatalogThemePalette>) -> CatalogTheme) =
            RomThemeMaterializer::materialize,
    ): ParsedCatalog {
        fun beginWork(module: CatalogWorkModule) {
            cancellation.throwIfCancellationRequested()
            reportCatalogWork(onWork, module)
            cancellation.throwIfCancellationRequested()
        }
        fun publishProgress(progress: CatalogMaterializationProgress) {
            cancellation.throwIfCancellationRequested()
            onProgress?.invoke(progress)
            cancellation.throwIfCancellationRequested()
        }

        beginWork(CatalogWorkModule.CORE_RECORDS)
        val speciesMaterialization = RecordMaterializers.speciesWithIndexResolution(rom, layout, cancellation)
        val rawSpecies = speciesMaterialization.records
        val baseSpecies = if (layout.generation == 3 && layout.pokeemeraldExpansion == null) {
            layout.resolvedDatasets.abilityNames?.catalogDirectAbilityIds()?.let { catalogIds ->
                rawSpecies.mapValues { (_, species) ->
                    val abilityIds = species.abilityIds.value ?: return@mapValues species
                    species.copy(abilityIds = CatalogField.available(abilityIds.filter(catalogIds::contains)))
                }
            } ?: rawSpecies
        } else {
            rawSpecies
        }
        val rawMoves = RecordMaterializers.moves(rom, layout, cancellation)
        val chart = if (layout.generation == 3) {
            layout.resolvedDatasets.typeChart?.catalogMatchups().orEmpty()
        } else {
            RecordMaterializers.typeChart(rom, layout)
        }
        val rawTypes = RecordMaterializers.types(rom, layout, baseSpecies, chart, rawMoves, cancellation)
        val baseTypes = TypePresentationMaterializer.apply(rawTypes) { typeId ->
            TypeMappings.presentationRole(layout.generation, typeId)
        }
        val abilities = RecordMaterializers.abilities(rom, layout, cancellation)
        val natureResolution = if (layout.generation == 3) resolveNatures?.invoke(layout) else null
        val natures = (natureResolution as? NatureResolution.Resolved)?.catalog?.records
            ?.associateBy { it.id }
            .orEmpty()
        val initialCapabilities = analysis.capabilities.associateBy { it.capability }.toMutableMap().also { capabilities ->
            capabilities[RomCapability.TYPE_PRESENTATION] = collectionCapability(
                RomCapability.TYPE_PRESENTATION,
                baseTypes.values.count { it.presentation.status == CapabilityStatus.AVAILABLE },
                "family type colors with explicit accessible fallback for custom IDs",
                "no materialized types were available for presentation",
            )
            capabilities[RomCapability.NATURES] = natureCapabilityEvidence(
                natureResolution,
                layout.generation,
            )
        }
        val essentialText = CatalogLocalizedTextExtractor.extract(
            manifest = layout.languageManifest,
            speciesById = baseSpecies,
            movesById = rawMoves,
            typesById = baseTypes,
            abilitiesById = abilities,
            naturesById = natures,
            capabilities = initialCapabilities,
        )
        val essentialCatalog = ParsedCatalog(
            romSha256 = analysis.sha256,
            family = layout.family,
            platform = layout.platform,
            romCrc32 = analysis.crc32,
            speciesById = essentialText.speciesById,
            movesById = essentialText.movesById,
            typesById = essentialText.typesById,
            abilitiesById = essentialText.abilitiesById,
            naturesById = essentialText.naturesById,
            typeChart = chart,
            capabilities = essentialText.capabilities,
            localization = essentialText.localization,
        )
        publishProgress(CatalogMaterializationProgress(CatalogMaterializationPhase.ESSENTIAL, 1, 5, essentialCatalog))

        beginWork(CatalogWorkModule.SPECIES_MEDIA)
        val descriptionMaterialization = runCatching {
            RelationshipMaterializers.descriptionsWithEvidence(
                rom,
                layout,
            )
        }.getOrElse {
            RecordMaterialization(emptyMap(), emptyMap())
        }
        cancellation.throwIfCancellationRequested()
        val descriptions = descriptionMaterialization.records
        val sprites = SpriteMaterializer.pokemon(rom, layout, cancellation = cancellation)
        val resolvedSprites = resolveSpriteAliases(baseSpecies, sprites, layout.generation)
        val mediaSpecies = baseSpecies.mapValues { (id, record) ->
            val descriptionKey = when {
                layout.pokeemeraldExpansion != null ||
                    layout.headerlessUnifiedSpecies?.descriptionPointerOffset != null -> id
                layout.generation == 3 -> speciesMaterialization.indexResolution.descriptionRows[id]
                else -> id
            }
            val description = descriptions[descriptionKey]
            val sprite = resolvedSprites[id]
            val excludedDescriptionIds = (speciesMaterialization.indexResolution as? SpeciesIndexResolution.Resolved)
                ?.descriptionIndex?.excludedSpeciesIds.orEmpty()
            val pokedexApplicable = record.dexNumber.status != CapabilityStatus.NOT_APPLICABLE && id !in excludedDescriptionIds
            record.copy(
                sprite = sprite?.let { CatalogField(CapabilityStatus.AVAILABLE, it.sprite, it.reasons) }
                    ?: CatalogField.notFound("sprite could not be decoded for species $id"),
                description = when {
                    !pokedexApplicable -> CatalogField.notApplicable("species is outside the ROM's compiled description-table domain")
                    description?.text != null -> CatalogField.available(description.text)
                    else -> CatalogField.notFound("description could not be decoded for species $id")
                },
                height = when {
                    !pokedexApplicable -> CatalogField.notApplicable("species is outside the ROM's compiled description-table domain")
                    description?.height != null -> CatalogField.available(description.height)
                    else -> CatalogField.notFound("height could not be decoded for species $id")
                },
                weight = when {
                    !pokedexApplicable -> CatalogField.notApplicable("species is outside the ROM's compiled description-table domain")
                    description?.weight != null -> CatalogField.available(description.weight)
                    else -> CatalogField.notFound("weight could not be decoded for species $id")
                },
            )
        }
        val mediaCapabilities = initialCapabilities.toMutableMap()
        val expectedDescriptions = mediaSpecies.count { (id, record) ->
            id > 0 && record.dexNumber.status != CapabilityStatus.NOT_APPLICABLE &&
                record.description.status != CapabilityStatus.NOT_APPLICABLE
        }
        val coveredDescriptions = mediaSpecies.count { (id, record) ->
            id > 0 && record.description.status == CapabilityStatus.AVAILABLE
        }
        mediaCapabilities[RomCapability.POKEDEX_DESCRIPTIONS] = materializationCapability(
            capability = RomCapability.POKEDEX_DESCRIPTIONS,
            covered = coveredDescriptions,
            expected = expectedDescriptions,
            availableReason = "decoded bounded Pokédex descriptions",
            partialReason = "some Pokédex descriptions were malformed or unavailable",
            unavailableReason = "Pokédex descriptions were not materialized",
        )
        val mediaText = CatalogLocalizedTextExtractor.extract(
            manifest = layout.languageManifest,
            speciesById = mediaSpecies,
            movesById = rawMoves,
            typesById = baseTypes,
            abilitiesById = abilities,
            naturesById = natures,
            capabilities = mediaCapabilities,
        )
        val mediaCatalog = essentialCatalog.copy(
            speciesById = mediaText.speciesById,
            movesById = mediaText.movesById,
            typesById = mediaText.typesById,
            abilitiesById = mediaText.abilitiesById,
            naturesById = mediaText.naturesById,
            capabilities = mediaText.capabilities,
            localization = mediaText.localization,
        )
        publishProgress(CatalogMaterializationProgress(CatalogMaterializationPhase.SPECIES_MEDIA, 2, 5, mediaCatalog))

        beginWork(CatalogWorkModule.EVOLUTIONS_AND_LEARNSETS)
        beginWork(CatalogWorkModule.ENCOUNTERS)
        val encounterMaterialization = EncounterMaterializer.materializeWithEvidence(rom, layout)
        val rawEncounters = encounterMaterialization.areas
        val runtimeMetadata = if (layout.generation == 3) {
            val runtimeLayout = Gen3RuntimeMemoryLayoutResolver.resolve(rom, layout.family)
            CatalogRuntimeMetadata(
                gen3SaveBlock1PointerAddress = runtimeLayout?.saveBlock1PointerAddress
                    ?: Gen3SaveBlock1PointerResolver.resolve(rom),
                gen3RuntimeMemoryLayout = runtimeLayout,
                areaNamesByBaseId = if (layout.pokeemeraldExpansion == null && resolveGen3AreaNames != null) {
                    resolveGen3AreaNames(layout, rawEncounters.mapTo(linkedSetOf()) { it.id / 10 })
                } else {
                    emptyMap()
                },
            )
        } else {
            CatalogRuntimeMetadata()
        }
        val encounters = applyResolvedAreaNames(rawEncounters, runtimeMetadata.areaNamesByBaseId)
        val closedMediaSpecies = EncounterReferencedSpeciesClosure.close(
            rom = rom,
            layout = layout,
            namesStatus = initialCapabilities[RomCapability.SPECIES_NAMES]?.status,
            species = mediaSpecies,
            encounters = encounters,
            cancellation = cancellation,
        )
        val relationshipMaterialization = runCatching {
            RelationshipMaterializers.relationshipsWithEvidence(
                rom,
                layout,
                closedMediaSpecies.keys,
            )
        }.getOrElse {
            RelationshipMaterialization(
                evolutions = RecordMaterialization(emptyMap(), emptyMap()),
                learnsets = RecordMaterialization(emptyMap(), emptyMap()),
            )
        }
        cancellation.throwIfCancellationRequested()
        val evolutions = relationshipMaterialization.evolutions.records
        val learnsets = relationshipMaterialization.learnsets.records
        val relationshipSpecies = closedMediaSpecies.mapValues { (id, record) ->
            record.copy(
                evolutionEdges = if (layout.tables.evolutions != null) {
                    materializedField(
                        relationshipMaterialization.evolutions,
                        id,
                        "evolution row",
                    )
                } else {
                    CatalogField.notFound("evolution table was not resolved")
                },
                learnset = if (layout.tables.learnsets != null) {
                    materializedField(
                        relationshipMaterialization.learnsets,
                        id,
                        "learnset row",
                    )
                } else {
                    CatalogField.notFound("learnset table was not resolved")
                },
            )
        }
        val relationshipCapabilities = mediaCapabilities.toMutableMap()
        relationshipCapabilities[RomCapability.EVOLUTIONS] = materializationCapability(
            capability = RomCapability.EVOLUTIONS,
            covered = relationshipSpecies.values.count {
                it.evolutionEdges.status == CapabilityStatus.AVAILABLE
            },
            expected = relationshipSpecies.size,
            availableReason = "decoded bounded evolution relationships",
            partialReason = "some evolution rows were malformed or unavailable",
            unavailableReason = "evolution relationships were not materialized",
        )
        relationshipCapabilities[RomCapability.LEARNSETS] = materializationCapability(
            capability = RomCapability.LEARNSETS,
            covered = relationshipSpecies.values.count {
                it.learnset.status == CapabilityStatus.AVAILABLE
            },
            expected = relationshipSpecies.size,
            availableReason = "decoded explicitly terminated learnsets",
            partialReason = "some learnset rows were malformed or unavailable",
            unavailableReason = "learnsets were not materialized",
        )
        val relationshipText = CatalogLocalizedTextExtractor.extract(
            manifest = layout.languageManifest,
            speciesById = relationshipSpecies,
            movesById = rawMoves,
            typesById = baseTypes,
            abilitiesById = abilities,
            naturesById = natures,
            encounterAreas = encounters,
            runtimeMetadata = runtimeMetadata,
            capabilities = relationshipCapabilities,
        )
        val relationshipCatalog = mediaCatalog.copy(
            speciesById = relationshipText.speciesById,
            movesById = relationshipText.movesById,
            typesById = relationshipText.typesById,
            abilitiesById = relationshipText.abilitiesById,
            naturesById = relationshipText.naturesById,
            encounterAreas = relationshipText.encounterAreas,
            runtimeMetadata = relationshipText.runtimeMetadata,
            capabilities = relationshipText.capabilities,
            localization = relationshipText.localization,
        )
        publishProgress(CatalogMaterializationProgress(CatalogMaterializationPhase.RELATIONSHIPS, 3, 5, relationshipCatalog))

        beginWork(CatalogWorkModule.MOVE_DATA)
        val learnsetRulesets = LearnsetRulesetMaterializer.materialize(rom, layout, learnsets)
        // A session resolver owns its terminal absence/conflict/budget result, including null.
        val moveDescriptions = if (resolveMoveDescriptions != null) {
            resolveMoveDescriptions(layout)
        } else {
            MoveDescriptionMaterializer.materialize(rom, layout, cancellation = cancellation)
        }
        val moveAcquisitions = runCatching {
            MoveAcquisitionMaterializer.materialize(rom, layout)
        }.getOrElse {
            MoveAcquisitionMaterialization(emptyMap(), emptyMap())
        }
        cancellation.throwIfCancellationRequested()
        beginWork(CatalogWorkModule.ABILITY_DATA)
        val abilityDescriptions = AbilityDescriptionMaterializer.materialize(
            rom,
            layout,
            cancellation = cancellation,
        )
        val abilityMechanics = resolveAbilityMechanics?.invoke(layout, abilities, baseTypes, abilityDescriptions)
            ?: AbilityMechanicsMaterializer.materialize(rom, layout, abilities, baseTypes, abilityDescriptions)
        val species = relationshipSpecies.mapValues { (id, record) ->
            record.copy(
                moveAcquisitions = moveAcquisitionField(
                    moveAcquisitions,
                    id,
                ),
            )
        }
        val validTypeIds = layout.resolvedDatasets.typeChart?.table?.typeCount
            ?.let { typeCount -> (0 until typeCount).toSet() }
            ?: baseTypes.keys
        val closedMoves = ReferencedMoveCatalogClosure.close(
            moves = rawMoves,
            species = species,
            rulesets = learnsetRulesets,
            typedDetails = layout.resolvedDatasets.moveDetails?.catalogDetails().orEmpty(),
            validTypeIds = validTypeIds,
        )
        val moves = closedMoves.mapValues { (id, move) ->
            val effectText = moveDescriptions?.descriptions?.get(id)
            move.copy(
                effectText = when {
                    effectText != null -> CatalogField.available(effectText)
                    layout.generation == 1 -> CatalogField.notApplicable(
                        "this engine does not expose a compatible move-description pointer table",
                    )
                    else -> CatalogField.notFound("move description was not resolved from the ROM")
                },
            )
        }
        val types = ReferencedTypeCatalogClosure.close(baseTypes, moves, validTypeIds)
        val enrichedAbilities = abilities.mapValues { (id, ability) ->
            val description = abilityDescriptions?.descriptions?.get(id)
            ability.copy(
                description = when {
                    description != null -> CatalogField.available(description)
                    layout.generation < 3 -> CatalogField.notApplicable("abilities are not part of this engine")
                    else -> CatalogField.notFound("ability description was not resolved from the ROM")
                },
                mechanics = abilityMechanics?.mechanicsByAbility?.get(id)?.let(CatalogField.Companion::available)
                    ?: if (layout.generation < 3) {
                        CatalogField.notApplicable("abilities are not part of this engine")
                    } else {
                        CatalogField.notFound("ability mechanics were not resolved from ROM code")
                    },
            )
        }
        val balls = if (layout.generation == 3) {
            BallSpriteMaterializer.captureBalls(rom, layout.expandedSplitCaptureBalls)
        } else {
            emptyMap()
        }
        val capabilities = relationshipCapabilities.toMutableMap()
        capabilities[RomCapability.MOVE_DESCRIPTIONS] = if (moveDescriptions != null) {
            val expected = moves.keys.count { it > 0 }
            val covered = moveDescriptions.descriptions.keys.count { it > 0 && it in moves }
            val complete = covered >= expected
            CapabilityEvidence(
                capability = RomCapability.MOVE_DESCRIPTIONS,
                compatible = true,
                confidence = moveDescriptions.confidence,
                offset = moveDescriptions.sourceOffset,
                count = covered,
                reasons = listOf(
                    if (complete) "decoded a validated move-description pointer table"
                    else "decoded a partial move-description pointer table ($covered/$expected)",
                ),
                status = if (complete) CapabilityStatus.AVAILABLE else CapabilityStatus.PARTIAL,
                validRecords = covered,
                totalRecords = expected,
                coveredRecords = covered,
                expectedRecords = expected,
            )
        } else {
            CapabilityEvidence(
                capability = RomCapability.MOVE_DESCRIPTIONS,
                compatible = false,
                confidence = 0.0,
                reasons = listOf(
                    if (layout.generation == 1) "this engine has no compatible move-description pointer table"
                    else "move-description pointer table was not resolved",
                ),
                status = if (layout.generation == 1) CapabilityStatus.NOT_APPLICABLE else CapabilityStatus.NOT_FOUND,
            )
        }
        capabilities[RomCapability.ABILITY_DESCRIPTIONS] = if (abilityDescriptions != null) {
            val expected = abilities.keys.count { it > 0 }
            val covered = abilityDescriptions.descriptions.keys.count { it > 0 && it in abilities }
            val complete = covered >= expected
            CapabilityEvidence(
                capability = RomCapability.ABILITY_DESCRIPTIONS,
                compatible = true,
                confidence = abilityDescriptions.confidence,
                offset = abilityDescriptions.sourceOffset,
                count = covered,
                reasons = listOf(
                    if (complete) "decoded a structurally referenced ability-description pointer table"
                    else "decoded a structurally referenced partial ability-description table ($covered/$expected)",
                ),
                status = if (complete) CapabilityStatus.AVAILABLE else CapabilityStatus.PARTIAL,
                reviewStatus = if (complete) CapabilityReviewStatus.NONE else CapabilityReviewStatus.MANUAL_REVIEW,
                coveredRecords = covered,
                expectedRecords = expected,
                incompleteRecords = (expected - covered).coerceAtLeast(0),
            )
        } else {
            CapabilityEvidence(
                capability = RomCapability.ABILITY_DESCRIPTIONS,
                compatible = false,
                confidence = 0.0,
                reasons = listOf(
                    if (layout.generation < 3) "abilities are not part of this engine"
                    else "ability-description pointer table was not resolved",
                ),
                status = if (layout.generation < 3) CapabilityStatus.NOT_APPLICABLE else CapabilityStatus.NOT_FOUND,
            )
        }
        capabilities[RomCapability.ABILITY_MECHANICS] = if (abilityMechanics != null) {
            val covered = abilityMechanics.mechanicsByAbility.size
            val expected = abilities.size
            val numericCovered = abilityMechanics.mechanicsByAbility.count { (_, mechanics) ->
                mechanics.any { it.kind != AbilityMechanicKind.BEHAVIOR }
            }
            CapabilityEvidence(
                capability = RomCapability.ABILITY_MECHANICS,
                compatible = true,
                confidence = abilityMechanics.confidence,
                offset = abilityMechanics.sourceOffset,
                count = covered,
                coveredRecords = covered,
                expectedRecords = expected,
                incompleteRecords = (expected - covered).coerceAtLeast(0),
                reasons = listOf(
                    "mapped source-backed behavior for $covered/$expected abilities",
                    "decoded numeric mechanics for $numericCovered/$expected abilities",
                ),
                status = if (covered == expected) CapabilityStatus.AVAILABLE else CapabilityStatus.PARTIAL,
            )
        } else {
            initialCapabilities[RomCapability.ABILITY_MECHANICS] ?: CapabilityEvidence(
                capability = RomCapability.ABILITY_MECHANICS,
                compatible = false,
                confidence = 0.0,
                reasons = listOf(
                    if (layout.generation < 3) "abilities are not part of this engine"
                    else "structured ability values were not resolved from compiled battle code",
                ),
                status = if (layout.generation < 3) CapabilityStatus.NOT_APPLICABLE else CapabilityStatus.NOT_FOUND,
            )
        }
        moveAcquisitions.evidence.values.forEach { evidence -> capabilities[evidence.capability] = evidence }
        capabilities[RomCapability.AREA_ENCOUNTERS] = CapabilityEvidence(
            capability = RomCapability.AREA_ENCOUNTERS,
            compatible = encounters.isNotEmpty(),
            confidence = if (encounters.isNotEmpty()) 1.0 else 0.0,
            offset = encounterMaterialization.selectedRootOffset,
            count = encounters.size.takeIf { it > 0 },
            recordSize = encounterMaterialization.headerSize,
            reasons = encounterMaterialization.reasons,
            status = encounterMaterialization.status,
            reviewStatus = encounterMaterialization.reviewStatus,
        )
        capabilities[RomCapability.BALL_CATALOG] = if (balls.values.any { it.sprite.status == CapabilityStatus.AVAILABLE }) {
            CapabilityEvidence(
                capability = RomCapability.BALL_CATALOG,
                compatible = true,
                confidence = balls.values.count { it.sprite.status == CapabilityStatus.AVAILABLE }.toDouble() / balls.size,
                count = balls.size,
                reasons = listOf("located compressed ball graphics and palette tables"),
                status = CapabilityStatus.AVAILABLE,
            )
        } else {
            CapabilityEvidence(
                capability = RomCapability.BALL_CATALOG,
                compatible = false,
                confidence = 0.0,
                reasons = listOf(
                    if (layout.generation == 3) "compressed ball graphics tables were not located"
                    else "per-ball capture artwork is not part of this engine's individual record",
                ),
                status = if (layout.generation == 3) CapabilityStatus.NOT_FOUND else CapabilityStatus.NOT_APPLICABLE,
            )
        }
        beginWork(CatalogWorkModule.MAPS)
        val encounterAreaIdStride = if (layout.pokeemeraldExpansion == null) 10 else 100
        val worldMapResolution = if (layout.generation in 1..3 && resolveWorldMap != null) {
            try {
                resolveWorldMap(
                    layout,
                    encounters.mapTo(linkedSetOf()) { it.id / encounterAreaIdStride },
                ).also { resolution ->
                    if (resolution is WorldMapResolution.Resolved) resolution.catalog.validate()
                }
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                WorldMapResolution.Unavailable(
                    stage = "resolver-exception",
                    reason = "optional world-map resolution failed closed (${failure.javaClass.simpleName})",
                )
            }
        } else {
            null
        }
        val worldMaps = (worldMapResolution as? WorldMapResolution.Resolved)?.catalog ?: WorldMapCatalog()
        capabilities[RomCapability.WORLD_MAP] = when (worldMapResolution) {
            is WorldMapResolution.Resolved -> CapabilityEvidence(
                capability = RomCapability.WORLD_MAP,
                compatible = true,
                confidence = 1.0,
                count = worldMapResolution.catalog.regions.size,
                reasons = worldMapResolution.reasons,
                status = CapabilityStatus.AVAILABLE,
            )
            is WorldMapResolution.Ambiguous -> CapabilityEvidence(
                capability = RomCapability.WORLD_MAP,
                compatible = false,
                confidence = 0.0,
                reasons = listOf(
                    "world-map stage: ${worldMapResolution.stage}",
                    worldMapResolution.reason,
                ),
                status = CapabilityStatus.AMBIGUOUS,
                reviewStatus = CapabilityReviewStatus.MANUAL_REVIEW,
            )
            is WorldMapResolution.BudgetExceeded -> CapabilityEvidence(
                capability = RomCapability.WORLD_MAP,
                compatible = false,
                confidence = 0.0,
                reasons = listOf(
                    "world-map stage: ${worldMapResolution.stage}",
                    worldMapResolution.reason,
                ),
                status = CapabilityStatus.NOT_FOUND,
            )
            is WorldMapResolution.Unavailable -> CapabilityEvidence(
                capability = RomCapability.WORLD_MAP,
                compatible = false,
                confidence = 0.0,
                reasons = listOf(
                    "world-map stage: ${worldMapResolution.stage}",
                    worldMapResolution.reason,
                ),
                status = CapabilityStatus.NOT_FOUND,
            )
            null -> CapabilityEvidence(
                capability = RomCapability.WORLD_MAP,
                compatible = false,
                confidence = 0.0,
                reasons = listOf("world maps are not part of this engine's normalized parser path"),
                status = CapabilityStatus.NOT_APPLICABLE,
            )
        }
        val encounterBaseIds = encounters.mapTo(linkedSetOf()) { it.id / encounterAreaIdStride }
        val localMapResolution = if (
            layout.generation in 1..3 && resolveLocalMaps != null
        ) {
            try {
                resolveLocalMaps(layout, encounterBaseIds).also { resolution ->
                    if (resolution is LocalMapResolution.Resolved) resolution.catalog.validate()
                }
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                LocalMapResolution.Unavailable(
                    stage = "resolver-exception",
                    reason = "optional local-map resolution failed closed (${failure.javaClass.simpleName})",
                )
            }
        } else {
            null
        }
        val localMaps = (localMapResolution as? LocalMapResolution.Resolved)?.catalog ?: LocalMapCatalog()
        val finalRuntimeMetadata = runtimeMetadata.copy(
            gen2TimeOfDayWramOffset =
                (localMapResolution as? LocalMapResolution.Resolved)?.gen2TimeOfDayWramOffset,
        ).validate()
        capabilities[RomCapability.LOCAL_MAP] = when (localMapResolution) {
            is LocalMapResolution.Resolved -> {
                val total = localMapResolution.catalog.maps.size + localMapResolution.skippedMaps
                CapabilityEvidence(
                    capability = RomCapability.LOCAL_MAP,
                    compatible = true,
                    confidence = localMapResolution.catalog.maps.size.toDouble() / total.coerceAtLeast(1),
                    count = localMapResolution.catalog.maps.size,
                    coveredRecords = localMapResolution.catalog.maps.size,
                    expectedRecords = total,
                    incompleteRecords = localMapResolution.skippedMaps,
                    reasons = localMapResolution.reasons + if (localMapResolution.skippedMaps > 0) {
                        listOf("skipped ${localMapResolution.skippedMaps} maps that failed bounded rendering")
                    } else {
                        emptyList()
                    },
                    status = if (
                        localMapResolution.skippedMaps == 0 && localMapResolution.partialSubsystemFailures == 0
                    ) {
                        CapabilityStatus.AVAILABLE
                    } else {
                        CapabilityStatus.PARTIAL
                    },
                )
            }
            is LocalMapResolution.BudgetExceeded -> CapabilityEvidence(
                capability = RomCapability.LOCAL_MAP,
                compatible = false,
                confidence = 0.0,
                reasons = listOf(
                    "local-map stage: ${localMapResolution.stage}",
                    localMapResolution.reason,
                ),
                status = CapabilityStatus.NOT_FOUND,
            )
            is LocalMapResolution.Unavailable -> CapabilityEvidence(
                capability = RomCapability.LOCAL_MAP,
                compatible = false,
                confidence = 0.0,
                reasons = listOf(
                    "local-map stage: ${localMapResolution.stage}",
                    localMapResolution.reason,
                ),
                status = CapabilityStatus.NOT_FOUND,
            )
            null -> CapabilityEvidence(
                capability = RomCapability.LOCAL_MAP,
                compatible = false,
                confidence = 0.0,
                reasons = listOf("local maps are not part of this engine's normalized parser path"),
                status = CapabilityStatus.NOT_APPLICABLE,
            )
        }
        beginWork(CatalogWorkModule.TRAINER_AND_THEME)
        val trainerAssets = runCatching {
            when (layout.generation) {
                1, 2 -> GbTrainerAssetResolver.resolve(rom, layout.family)
                3 -> Gen3TrainerAssetResolver.resolve(rom, layout.family)
                else -> null
            }
        }.getOrNull() ?: TrainerAssetCatalog()
        cancellation.throwIfCancellationRequested()
        val theme = runCatching {
            materializeTheme(
                catalogThemeAssets(species, trainerAssets, worldMaps, localMaps),
                emptyList(),
            ).validate()
        }.getOrElse { CatalogTheme.neutral() }
        cancellation.throwIfCancellationRequested()
        beginWork(CatalogWorkModule.CATALOG_STORAGE)
        val finalText = CatalogLocalizedTextExtractor.extract(
            manifest = layout.languageManifest,
            speciesById = species,
            movesById = moves,
            typesById = types,
            abilitiesById = enrichedAbilities,
            naturesById = natures,
            encounterAreas = encounters,
            captureBallsById = balls,
            runtimeMetadata = finalRuntimeMetadata,
            worldMaps = worldMaps,
            localMaps = localMaps,
            capabilities = capabilities,
        )
        val catalog = ParsedCatalog(
            romSha256 = analysis.sha256,
            family = layout.family,
            platform = layout.platform,
            romCrc32 = analysis.crc32,
            speciesById = finalText.speciesById,
            movesById = finalText.movesById,
            typesById = finalText.typesById,
            abilitiesById = finalText.abilitiesById,
            naturesById = finalText.naturesById,
            typeChart = chart,
            encounterAreas = finalText.encounterAreas,
            captureBallsById = finalText.captureBallsById,
            learnsetRulesets = learnsetRulesets,
            runtimeMetadata = finalText.runtimeMetadata,
            worldMaps = finalText.worldMaps,
            trainerAssets = trainerAssets,
            localMaps = finalText.localMaps,
            theme = theme,
            capabilities = finalText.capabilities,
            diagnostics = buildList {
                moveDescriptions?.let {
                    add(
                        "move descriptions: offset=0x${it.sourceOffset.toString(16)} " +
                            "confidence=${"%.3f".format(java.util.Locale.ROOT, it.confidence)}",
                    )
                }
                abilityDescriptions?.let {
                    add(
                        "ability descriptions: offset=0x${it.sourceOffset.toString(16)} " +
                            "confidence=${"%.3f".format(java.util.Locale.ROOT, it.confidence)}",
                    )
                }
                abilityMechanics?.let {
                    add(
                        "ability mechanics: offset=0x${it.sourceOffset.toString(16)} " +
                            "confidence=${"%.3f".format(java.util.Locale.ROOT, it.confidence)} " +
                            "abilities=${it.mechanicsByAbility.size}",
                    )
                }
                learnsetRulesets.forEach {
                    add(
                        "learnset ruleset ${it.id}: offset=0x${it.sourceOffset.toString(16)} " +
                            "confidence=${"%.3f".format(java.util.Locale.ROOT, it.confidence)} primary=${it.primary}",
                    )
                }
                encounterMaterialization.selectedRootOffset?.let { root ->
                    add(
                        "area encounters: root=0x${root.toString(16)} " +
                            "headerSize=${encounterMaterialization.headerSize} " +
                            "headers=${encounterMaterialization.headerCount} " +
                            "populatedMethods=${encounterMaterialization.populatedMethodCount} " +
                            "areas=${encounters.size} references=${encounterMaterialization.referenceCount} " +
                            "candidates=${encounterMaterialization.candidateCount}",
                    )
                }
            },
            localization = finalText.localization,
        )
        publishProgress(CatalogMaterializationProgress(CatalogMaterializationPhase.EXTENDED, 4, 5, catalog))
        publishProgress(CatalogMaterializationProgress(CatalogMaterializationPhase.COMPLETE, 5, 5, catalog))
        return catalog
    }

    internal fun catalogThemeAssets(
        species: Map<Int, SpeciesRecord>,
        trainerAssets: TrainerAssetCatalog,
        worldMaps: WorldMapCatalog,
        localMaps: LocalMapCatalog,
    ): Map<CatalogThemeAssetClass, List<RgbaSprite>> = buildMap {
        species.entries.asSequence()
            .sortedBy { it.key }
            .mapNotNull { (_, record) -> record.sprite.value }
            .take(MAX_THEME_SPECIES_ASSETS)
            .toList()
            .takeIf { it.isNotEmpty() }
            ?.let { put(CatalogThemeAssetClass.SPECIES, it) }
        trainerAssets.assets.toSortedMap().values.toList()
            .takeIf { it.isNotEmpty() }
            ?.let { put(CatalogThemeAssetClass.TRAINER, it) }
        worldMaps.assets.toSortedMap().values.toList()
            .takeIf { it.isNotEmpty() }
            ?.let { put(CatalogThemeAssetClass.WORLD_MAP, it) }
        localMaps.assets.toSortedMap().values.mapNotNull { asset ->
            runCatching { decodeNormalizedPng(asset.bytes) }.getOrNull()
        }.takeIf { it.isNotEmpty() }
            ?.let { put(CatalogThemeAssetClass.LOCAL_MAP, it) }
    }

    private fun decodeNormalizedPng(bytes: ByteArray): RgbaSprite {
        require(bytes.size >= 33 && bytes.copyOfRange(0, PNG_SIGNATURE.size).contentEquals(PNG_SIGNATURE))
        var cursor = PNG_SIGNATURE.size
        var width = 0
        var height = 0
        val compressed = ByteArrayOutputStream()
        while (cursor + 12 <= bytes.size) {
            val length = readBigEndianInt(bytes, cursor)
            require(length >= 0 && cursor.toLong() + 12L + length <= bytes.size.toLong())
            val type = bytes.copyOfRange(cursor + 4, cursor + 8).toString(Charsets.US_ASCII)
            val dataOffset = cursor + 8
            when (type) {
                "IHDR" -> {
                    require(length == 13)
                    width = readBigEndianInt(bytes, dataOffset)
                    height = readBigEndianInt(bytes, dataOffset + 4)
                    require(width > 0 && height > 0)
                    require(bytes[dataOffset + 8].toInt() == 8 && bytes[dataOffset + 9].toInt() == 6)
                    require(bytes[dataOffset + 10].toInt() == 0 && bytes[dataOffset + 11].toInt() == 0)
                    require(bytes[dataOffset + 12].toInt() == 0)
                }
                "IDAT" -> compressed.write(bytes, dataOffset, length)
                "IEND" -> break
            }
            cursor += length + 12
        }
        require(width > 0 && height > 0 && compressed.size() > 0)
        val stride = Math.multiplyExact(width, 4)
        val expectedSize = Math.multiplyExact(height, stride + 1)
        val filtered = InflaterInputStream(ByteArrayInputStream(compressed.toByteArray())).use { it.readBytes() }
        require(filtered.size == expectedSize)
        val decoded = ByteArray(Math.multiplyExact(height, stride))
        repeat(height) { y ->
            val filter = filtered[y * (stride + 1)].toInt() and 0xff
            require(filter in 0..4)
            repeat(stride) { x ->
                val raw = filtered[y * (stride + 1) + 1 + x].toInt() and 0xff
                val left = if (x >= 4) decoded[y * stride + x - 4].toInt() and 0xff else 0
                val up = if (y > 0) decoded[(y - 1) * stride + x].toInt() and 0xff else 0
                val upperLeft = if (y > 0 && x >= 4) decoded[(y - 1) * stride + x - 4].toInt() and 0xff else 0
                val value = when (filter) {
                    0 -> raw
                    1 -> raw + left
                    2 -> raw + up
                    3 -> raw + ((left + up) ushr 1)
                    else -> raw + paeth(left, up, upperLeft)
                }
                decoded[y * stride + x] = value.toByte()
            }
        }
        return RgbaSprite(width, height, IntArray(width * height) { index ->
            val offset = index * 4
            ((decoded[offset + 3].toInt() and 0xff) shl 24) or
                ((decoded[offset].toInt() and 0xff) shl 16) or
                ((decoded[offset + 1].toInt() and 0xff) shl 8) or
                (decoded[offset + 2].toInt() and 0xff)
        })
    }

    private fun readBigEndianInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 24) or
            ((bytes[offset + 1].toInt() and 0xff) shl 16) or
            ((bytes[offset + 2].toInt() and 0xff) shl 8) or
            (bytes[offset + 3].toInt() and 0xff)

    private fun paeth(left: Int, up: Int, upperLeft: Int): Int {
        val estimate = left + up - upperLeft
        val leftDistance = kotlin.math.abs(estimate - left)
        val upDistance = kotlin.math.abs(estimate - up)
        val upperLeftDistance = kotlin.math.abs(estimate - upperLeft)
        return when {
            leftDistance <= upDistance && leftDistance <= upperLeftDistance -> left
            upDistance <= upperLeftDistance -> up
            else -> upperLeft
        }
    }

    private const val MAX_THEME_SPECIES_ASSETS = 16
    private val PNG_SIGNATURE = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)

    private fun applyResolvedAreaNames(
        areas: List<EncounterArea>,
        namesByBaseId: Map<Int, String>,
    ): List<EncounterArea> = areas.map { area ->
        val resolvedName = namesByBaseId[area.id / 10] ?: return@map area
        val methodName = area.name.value
            ?.substringAfter(" - ", missingDelimiterValue = "")
            ?.trim()
            .orEmpty()
        area.copy(
            name = CatalogField.available(
                if (methodName.isEmpty()) resolvedName else "$resolvedName - $methodName",
            ),
        )
    }

    private fun resolveSpriteAliases(
        species: Map<Int, SpeciesRecord>,
        decoded: Map<Int, RgbaSprite>,
        generation: Int,
    ): Map<Int, ResolvedSprite> {
        fun spriteFor(id: Int, record: SpeciesRecord): RgbaSprite? =
            decoded[if (generation == 1) record.dexNumber.value ?: id else id]
        fun normalizedName(record: SpeciesRecord): String? = record.name.value
            ?.trim()
            ?.replace(Regex("\\s+"), " ")
            ?.uppercase(Locale.ROOT)
            ?.takeIf(String::isNotBlank)

        val resolved = species.mapNotNull { (id, record) ->
            spriteFor(id, record)?.let { id to ResolvedSprite(it) }
        }.toMap().toMutableMap()
        species.forEach { (id, record) ->
            if (id in resolved) return@forEach
            val dex = record.dexNumber.value?.takeIf { it > 0 } ?: return@forEach
            val name = normalizedName(record) ?: return@forEach
            val candidates = species.asSequence()
                .filter { (candidateId, candidate) ->
                    candidateId != id && candidate.dexNumber.value == dex && normalizedName(candidate) == name
                }
                .mapNotNull { (candidateId, candidate) ->
                    spriteFor(candidateId, candidate)?.let { candidateId to it }
                }
                .toList()
            val uniqueSprites = candidates.map { it.second }.distinct()
            if (uniqueSprites.size == 1) {
                val donorId = candidates.minOf { it.first }
                resolved[id] = ResolvedSprite(
                    uniqueSprites.single(),
                    listOf("inferred ROM sprite from species $donorId with the same normalized name and Pokédex index"),
                )
            }
        }
        return resolved
    }

    private data class ResolvedSprite(
        val sprite: RgbaSprite,
        val reasons: List<String> = emptyList(),
    )

    private fun moveAcquisitionField(
        materialization: MoveAcquisitionMaterialization,
        id: Int,
    ): CatalogField<List<MoveAcquisition>> {
        if (materialization.failedSpeciesByMethod.values.any { id in it }) {
            return CatalogField.notFound(
                "a non-level move acquisition row was malformed for species $id",
            )
        }
        val applicableMethods = materialization.evidence
            .filterValues(CapabilityEvidence::compatible)
            .keys
        if (applicableMethods.isEmpty()) {
            return CatalogField.notFound(
                "non-level move acquisition tables were not resolved",
            )
        }
        return CatalogField.available(
            materialization.acquisitionsBySpecies[id].orEmpty(),
        )
    }

    private fun <T> materializedField(
        materialization: RecordMaterialization<T>,
        id: Int,
        label: String,
    ): CatalogField<T> = if (id in materialization.records) {
        CatalogField.available(materialization.records.getValue(id))
    } else {
        CatalogField.notFound(
            materialization.failures[id]
                ?: "$label was not materialized for species $id",
        )
    }

    private fun materializationCapability(
        capability: RomCapability,
        covered: Int,
        expected: Int,
        availableReason: String,
        partialReason: String,
        unavailableReason: String,
    ): CapabilityEvidence {
        val incomplete = (expected - covered).coerceAtLeast(0)
        val status = when {
            covered <= 0 -> CapabilityStatus.NOT_FOUND
            incomplete > 0 -> CapabilityStatus.PARTIAL
            else -> CapabilityStatus.AVAILABLE
        }
        val reason = when (status) {
            CapabilityStatus.AVAILABLE -> availableReason
            CapabilityStatus.PARTIAL -> "$partialReason ($covered/$expected)"
            else -> unavailableReason
        }
        return CapabilityEvidence(
            capability = capability,
            compatible = covered > 0,
            confidence = if (expected > 0) covered.toDouble() / expected else 0.0,
            count = covered.takeIf { it > 0 },
            reasons = listOf(reason),
            status = status,
            validRecords = covered,
            totalRecords = expected,
            coveredRecords = covered,
            expectedRecords = expected,
            incompleteRecords = incomplete,
        )
    }

    private fun collectionCapability(
        capability: RomCapability,
        count: Int,
        availableReason: String,
        unavailableReason: String,
    ) = CapabilityEvidence(
        capability = capability,
        compatible = count > 0,
        confidence = if (count > 0) 1.0 else 0.0,
        count = count.takeIf { it > 0 },
        reasons = listOf(if (count > 0) availableReason else unavailableReason),
        status = if (count > 0) CapabilityStatus.AVAILABLE else CapabilityStatus.NOT_FOUND,
    )
}
