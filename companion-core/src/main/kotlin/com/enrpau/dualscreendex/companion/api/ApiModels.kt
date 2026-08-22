package com.enrpau.dualscreendex.companion.api

import com.enrpau.dualscreendex.companion.battle.RarityEvaluator
import com.enrpau.dualscreendex.companion.knowledge.KnowledgePolicy
import com.enrpau.dualscreendex.companion.model.AppSnapshot
import com.enrpau.dualscreendex.companion.model.Effectiveness
import com.enrpau.dualscreendex.companion.model.MoveObservation
import com.enrpau.dualscreendex.companion.model.KnowledgeMode
import com.enrpau.dualscreendex.companion.owned.PreferredIndividualSelector
import com.enrpau.dualscreendex.parser.catalog.EvolutionEdge
import com.enrpau.dualscreendex.parser.catalog.LearnsetNormalizer
import com.enrpau.dualscreendex.parser.catalog.LocalMapCatalog
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.enrpau.dualscreendex.parser.dataset.natures.NatureStat
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class BootstrapView(val catalog: CatalogView?, val state: StateView)

data class CatalogView(
    val hash: String,
    val crc32: String,
    val family: String,
    val platform: String,
    val rulesets: List<RulesetView>,
    val species: List<SpeciesView>,
    val moves: List<MoveView>,
    val types: List<TypeView>,
    val areas: List<AreaView>,
    val balls: List<BallView>,
    val natures: List<NatureView>,
    val worldMaps: List<WorldMapRegionView>,
    val localMaps: List<LocalMapView>,
    val mapScenes: List<LocalMapSceneView>,
    val theme: CatalogThemeView,
    val capabilities: Map<String, String>,
)

data class CatalogThemeView(
    val method: String,
    val assetClasses: List<String>,
    val contrastCorrected: Boolean,
    val tokens: CatalogThemeTokensView,
)

data class CatalogThemeTokensView(
    val field: String,
    val fieldPattern: String,
    val header: String,
    val headerShadow: String,
    val menu: String,
    val menuShadow: String,
    val panel: String,
    val border: String,
    val text: String,
    val textShadow: String,
    val accent: String,
    val accentText: String,
)

data class LocalMapView(
    val key: String,
    val displayName: String?,
    val baseAreaId: Int,
    val pixelWidth: Int,
    val pixelHeight: Int,
    val gridWidth: Int,
    val gridHeight: Int,
    val imageUrl: String,
    val dynamicLighting: Boolean,
)

data class LocalMapSceneView(
    val key: String,
    val pixelWidth: Int,
    val pixelHeight: Int,
    val gridWidth: Int,
    val gridHeight: Int,
    val placements: List<LocalMapScenePlacementView>,
)

data class LocalMapScenePlacementView(
    val localMapKey: String,
    val baseAreaId: Int,
    val gridX: Int,
    val gridY: Int,
    val pixelX: Int,
    val pixelY: Int,
    val pixelWidth: Int,
    val pixelHeight: Int,
    val gridWidth: Int,
    val gridHeight: Int,
    val imageUrl: String,
    val dynamicLighting: Boolean,
)

data class LocalMapPoiView(
    val key: String,
    val localMapKey: String,
    val baseAreaId: Int,
    val tileX: Int,
    val tileY: Int,
    val category: String,
    val state: String,
    val displayName: String?,
    val service: String?,
    val itemId: Int?,
    val itemName: String?,
    val destinationBaseAreaId: Int?,
)

data class WorldMapRegionView(
    val key: String,
    val displayName: String?,
    val pixelWidth: Int,
    val pixelHeight: Int,
    val gridWidth: Int,
    val gridHeight: Int,
    val imageUrl: String,
    val locations: List<WorldMapLocationView>,
)

data class WorldMapLocationView(
    val key: String,
    val displayName: String,
    val baseAreaIds: List<Int>,
    val geometry: List<WorldMapCellView>,
)

data class WorldMapCellView(val x: Int, val y: Int, val width: Int, val height: Int)

data class SpeciesView(
    val id: Int,
    val dex: Int,
    val name: String,
    val typeIds: List<Int>,
    val stats: Map<String, Int>?,
    val description: String?,
    val height: Int?,
    val weight: Int?,
    val learnset: List<LearnsetView>,
    val learnsets: Map<String, List<LearnsetView>>,
    val normalizedLearnsets: Map<String, List<NormalizedMoveView>>,
    val moveAcquisitions: List<MoveAcquisitionView>,
    val abilities: List<AbilityView>,
    val evolutions: List<EvolutionView>,
    val hasSprite: Boolean,
)

data class LearnsetView(val level: Int, val moveId: Int)
data class NormalizedMoveView(val moveId: Int, val initial: Boolean, val levels: List<Int>, val label: String)
data class RulesetView(
    val id: String,
    val label: String,
    val sourceOffset: Int,
    val confidence: Double,
    val primary: Boolean,
)
data class MoveAcquisitionView(val moveId: Int, val method: String, val sourceId: Int?)
data class AbilityMechanicView(
    val kind: String,
    val label: String,
    val value: String,
    val numerator: Int,
    val denominator: Int,
    val conditions: List<AbilityMechanicConditionView> = emptyList(),
)
data class AbilityMechanicConditionView(val kind: String, val value: Long, val label: String)
data class AbilityView(
    val id: Int,
    val name: String,
    val description: String?,
    val mechanics: List<AbilityMechanicView>,
)
data class EvolutionView(
    val targetSpeciesId: Int,
    val targetName: String,
    val methodId: Int,
    val parameter: Int,
    val condition: String,
)
data class MoveView(
    val id: Int,
    val name: String,
    val typeId: Int?,
    val category: String?,
    val power: Int?,
    val accuracy: Int?,
    val pp: Int?,
    val priority: Int?,
    val effectId: Int?,
    val description: String?,
)
data class TypeView(val id: Int, val name: String, val foreground: String?, val background: String?, val border: String?)
data class EncounterSlotView(
    val speciesId: Int,
    val minimumLevel: Int,
    val maximumLevel: Int,
    val weight: Int?,
)
data class AreaView(
    val id: Int,
    val baseAreaId: Int,
    val name: String,
    val methodId: Int,
    val speciesIds: List<Int>,
    val slots: List<EncounterSlotView>,
    val windows: List<String>,
)
data class BallView(val id: Int, val name: String, val generic: Boolean, val hasSprite: Boolean)
data class NatureView(
    val id: Int,
    val name: String,
    val statMultipliers: Map<String, Int>,
    val raisedStat: String?,
    val loweredStat: String?,
    val positivePercent: Int,
    val negativePercent: Int,
    val likedFlavor: String?,
    val dislikedFlavor: String?,
)

data class DiagnosticCapabilityView(
    val capability: String,
    val status: String,
    val confidence: Double,
    val offset: Int?,
    val count: Int?,
    val recordSize: Int?,
    val reasons: List<String>,
    val validRecords: Int? = null,
    val totalRecords: Int? = null,
    val elementSize: Int? = null,
    val reviewStatus: String = "NONE",
)

data class DiagnosticView(
    val romName: String?,
    val sha256: String,
    val crc32: String,
    val family: String,
    val platform: String,
    val activeRulesetId: String?,
    val rulesetAssumed: Boolean,
    val rulesets: List<RulesetView>,
    val capabilities: List<DiagnosticCapabilityView>,
    val parserDiagnostics: List<String>,
    val species: SpeciesView?,
    val move: MoveView?,
)

data class StateView(
    val version: Long,
    val screen: String,
    val priorScreen: String,
    val settingsReturnScreen: String,
    val selectedSpeciesId: Int?,
    val selectedPartySlot: Int?,
    val filter: String,
    val selectedAreaId: Int?,
    val selectedAreaIds: List<Int>,
    val currentAreaIds: List<Int>,
    val currentAreaBaseId: Int?,
    val currentAreaName: String?,
    val currentMapPosition: MapPositionView?,
    val localMapPois: List<LocalMapPoiView>,
    val localMapPoiPreferences: com.enrpau.dualscreendex.companion.model.LocalMapPoiPreferences,
    val currentAreaSpeciesIds: List<Int>,
    val revealedAreaBaseIds: List<Int>,
    val observedAreaBaseIdsBySpecies: Map<Int, List<Int>>,
    val battleTab: String,
    val settings: Any,
    val speciesState: Map<Int, SpeciesStateView>,
    val observedMoves: Map<Int, List<ObservedMoveView>>,
    val trainer: TrainerView?,
    val trainerAvatarUrl: String?,
    val party: List<PartyMemberView>,
    val battle: BattleView?,
    val catalogReady: Boolean,
    val catalogName: String?,
    val error: String?,
    val activeRulesetId: String?,
    val rulesetAssumed: Boolean,
    val loading: CatalogLoadingView,
    val retroArch: RetroArchView = RetroArchView(),
    val saveRam: SaveRamView = SaveRamView(),
    val gameTime: GameClockView? = null,
)
data class GameClockView(
    val hours: Int?,
    val minutes: Int?,
    val phase: String? = null,
    val phaseProgress: Double? = null,
)
data class TrainerView(
    val name: String,
    val gender: String,
    val publicTrainerId: Int,
    val money: Long,
    val playTimeHours: Int,
    val playTimeMinutes: Int,
    val dexSeen: Int,
    val dexCaught: Int,
    val stars: Int?,
    val avatarUrl: String?,
    val badges: List<TrainerBadgeView>,
)
data class TrainerBadgeView(val index: Int, val earned: Boolean, val imageUrl: String?)
data class PartyMemberView(
    val slot: Int,
    val occupied: Boolean,
    val speciesId: Int? = null,
    val speciesName: String? = null,
    val spriteUrl: String? = null,
    val typeIds: List<Int> = emptyList(),
    val nickname: String? = null,
    val level: Int? = null,
    val isEgg: Boolean = false,
    val gender: String? = null,
    val natureId: Int? = null,
    val nature: String? = null,
    val abilityId: Int? = null,
    val abilityName: String? = null,
    val heldItemId: Int? = null,
    val heldItemName: String? = null,
    val hasHeldItem: Boolean? = null,
    val currentHp: Int? = null,
    val maximumHp: Int? = null,
    val status: String? = null,
    val experienceProgress: Double? = null,
    val rarity: RarityView? = null,
    val stats: Map<String, Int> = emptyMap(),
    val moves: List<PartyMoveView> = emptyList(),
)
data class PartyMoveView(
    val slot: Int,
    val moveId: Int?,
    val name: String?,
    val currentPp: Int?,
    val maximumPp: Int?,
)
data class MapPositionView(val x: Int, val y: Int)
data class RetroArchView(
    val storageGrant: String = "MISSING",
    val configGrant: String = "MISSING",
    val romGrant: String = "MISSING",
    val configState: String = "NOT_CONFIGURED",
    val restartRequired: Boolean = false,
    val connection: String = "DISCONNECTED",
    val systemId: String? = null,
    val gameBasename: String? = null,
    val contentCrc32: String? = null,
    val resolution: String = "NO_CONTENT",
    val activeSource: String? = null,
    val savefileDirectory: String? = null,
    val indexedRoms: Int = 0,
    val message: String? = null,
)
data class SaveRamView(
    val status: String = "UNAVAILABLE",
    val sourceName: String? = null,
    val sourceLastModifiedEpochMs: Long? = null,
    val refreshedAtEpochMs: Long? = null,
    val autosaveStatus: String = "UNVERIFIED",
    val capabilities: Map<String, String> = emptyMap(),
    val candidates: List<SaveCandidateView> = emptyList(),
    val message: String? = null,
)
data class SaveCandidateView(
    val id: String,
    val path: String,
    val lastModifiedEpochMs: Long,
)
data class CatalogLoadingView(
    val active: Boolean,
    val phase: String,
    val completedUnits: Int,
    val totalUnits: Int,
)

data class SpeciesStateView(
    val seen: Boolean,
    val caught: Boolean,
    val team: Boolean,
    val ballId: Int?,
    val preferredLevel: Int? = null,
    val innateTier: String? = null,
)
data class BattleView(
    val opponents: List<OpponentView>,
    val targetIndex: Int,
    val targetMode: String,
    val capabilities: Map<String, String>,
    val selectedMoveId: Int?,
    val effectiveness: String?,
    val effectivenessKnown: Boolean,
)
data class OpponentView(
    val speciesId: Int,
    val level: Int,
    val typeIds: List<Int>,
    val rarity: RarityView,
    val moves: List<ObservedMoveView>,
)
data class RarityView(
    val relativeTier: String?,
    val innateTier: String?,
    val baseStars: Int?,
    val areaAdjustment: Double?,
    val stars: Double?,
    val areaOutcome: String,
    val currentAreaBaseId: Int?,
    val currentAreaName: String?,
    val matchingAreaCount: Int,
    val candidateAreaCount: Int,
)
data class ObservedMoveView(val moveId: Int, val frequency: Int)

object ApiViewBuilder {
    fun catalog(catalog: ParsedCatalog): CatalogView = CatalogView(
        hash = catalog.romSha256,
        crc32 = catalog.romCrc32,
        family = catalog.family.name,
        platform = catalog.platform.name,
        rulesets = catalog.learnsetRulesets.map {
            RulesetView(it.id, it.label, it.sourceOffset, it.confidence, it.primary)
        },
        species = catalog.navigableSpecies().sortedWith(compareBy({ it.dexNumber.value }, { it.id })).map { species ->
            val stats = species.baseStats.value
            val rulesetLearnsets = catalog.learnsetRulesets.associate { ruleset ->
                ruleset.id to ruleset.entriesBySpecies[species.id].orEmpty()
            }.ifEmpty {
                mapOf("default" to species.learnset.value.orEmpty())
            }
            SpeciesView(
                id = species.id,
                dex = species.dexNumber.value ?: species.id,
                name = species.name.value ?: "#${species.id}",
                typeIds = species.typeIds.value.orEmpty(),
                stats = stats?.let {
                    linkedMapOf(
                        "HP" to it.hp,
                        "ATTACK" to it.attack,
                        "DEFENSE" to it.defense,
                        "SPEED" to it.speed,
                        "SP. ATK" to it.specialAttack,
                        "SP. DEF" to it.specialDefense,
                    )
                },
                description = species.description.value,
                height = species.height.value,
                weight = species.weight.value,
                learnset = species.learnset.value.orEmpty().map { LearnsetView(it.level, it.moveId) },
                learnsets = rulesetLearnsets.mapValues { (_, entries) ->
                    entries.map { LearnsetView(it.level, it.moveId) }
                },
                normalizedLearnsets = rulesetLearnsets.mapValues { (_, entries) ->
                    LearnsetNormalizer.normalize(entries).map { normalized ->
                        val parts = buildList {
                            if (normalized.initial) add("Initial")
                            normalized.levels.forEach { add("Lv $it") }
                        }
                        NormalizedMoveView(
                            normalized.moveId,
                            normalized.initial,
                            normalized.levels,
                            parts.joinToString(" · "),
                        )
                    }
                },
                moveAcquisitions = species.moveAcquisitions.value.orEmpty().map {
                    MoveAcquisitionView(it.moveId, it.method.name, it.sourceId)
                },
                abilities = species.abilityIds.value.orEmpty().mapNotNull { abilityId ->
                    val ability = catalog.abilitiesById[abilityId]
                    val name = ability?.name?.value
                    if (abilityId == 0 || name.isNullOrBlank()) null
                    else AbilityView(
                        abilityId,
                        name,
                        ability.description.value,
                        ability.mechanics.value.orEmpty().map { mechanic ->
                            AbilityMechanicView(
                                mechanic.kind.name,
                                mechanic.label,
                                mechanic.value,
                                mechanic.numerator,
                                mechanic.denominator,
                                mechanic.conditions.map { condition ->
                                    AbilityMechanicConditionView(
                                        condition.kind.name,
                                        condition.value,
                                        condition.label,
                                    )
                                },
                            )
                        },
                    )
                },
                evolutions = species.evolutionEdges.value.orEmpty().map { edge ->
                    EvolutionView(
                        edge.targetSpeciesId,
                        catalog.speciesById[edge.targetSpeciesId]?.name?.value ?: "Species ${edge.targetSpeciesId}",
                        edge.methodId,
                        edge.parameter,
                        evolutionCondition(catalog, edge),
                    )
                },
                hasSprite = species.sprite.value != null,
            )
        },
        moves = catalog.movesById.values.sortedBy { it.id }.map {
            MoveView(
                it.id,
                it.name.value ?: "#${it.id}",
                it.typeId.value,
                it.category.value?.name,
                it.power.value,
                it.accuracy.value,
                it.pp.value,
                it.priority.value,
                it.effectId.value,
                it.effectText.value,
            )
        },
        types = catalog.typesById.values.sortedBy { it.id }.map {
            val presentation = it.presentation.value
            TypeView(
                it.id,
                it.name.value ?: "TYPE ${it.id}",
                presentation?.foregroundArgb?.toCss(),
                presentation?.backgroundArgb?.toCss(),
                presentation?.borderArgb?.toCss(),
            )
        },
        areas = catalog.encounterAreas.sortedBy { it.id }.map {
            AreaView(
                it.id,
                it.id / 10,
                it.name.value ?: "Area ${it.id}",
                it.methodId,
                it.slots.map { slot -> slot.speciesId }.filter { id -> id > 0 }.distinct(),
                it.slots.map { slot ->
                    EncounterSlotView(slot.speciesId, slot.minimumLevel, slot.maximumLevel, slot.weight)
                },
                it.windows.map { window -> window.name }.sorted(),
            )
        },
        balls = catalog.captureBallsById.values.sortedBy { it.id }.map {
            BallView(it.id, it.name.value ?: "Ball ${it.id}", it.generic, it.sprite.value != null)
        },
        natures = catalog.naturesById.values.sortedBy { it.id }.map { nature ->
            NatureView(
                id = nature.id,
                name = nature.name,
                statMultipliers = NatureStat.entries.associate { stat -> stat.name to nature.multiplierPercent(stat) },
                raisedStat = nature.raisedStat?.name,
                loweredStat = nature.loweredStat?.name,
                positivePercent = nature.positivePercent,
                negativePercent = nature.negativePercent,
                likedFlavor = nature.likedFlavor?.name,
                dislikedFlavor = nature.dislikedFlavor?.name,
            )
        },
        worldMaps = catalog.worldMaps.regions.map { region ->
            WorldMapRegionView(
                key = region.key,
                displayName = region.displayName,
                pixelWidth = region.pixelWidth,
                pixelHeight = region.pixelHeight,
                gridWidth = region.gridWidth,
                gridHeight = region.gridHeight,
                imageUrl = "/api/maps/${URLEncoder.encode(region.imageAssetKey, StandardCharsets.UTF_8)}.png",
                locations = region.locations.map { location ->
                    WorldMapLocationView(
                        key = location.key,
                        displayName = location.displayName,
                        baseAreaIds = location.baseAreaIds.sorted(),
                        geometry = location.geometry.map { cell ->
                            WorldMapCellView(cell.x, cell.y, cell.width, cell.height)
                        },
                    )
                },
            )
        },
        localMaps = catalog.localMaps.maps.map { map ->
            LocalMapView(
                key = map.key,
                displayName = map.displayName,
                baseAreaId = map.baseAreaId,
                pixelWidth = map.pixelWidth,
                pixelHeight = map.pixelHeight,
                gridWidth = map.gridWidth,
                gridHeight = map.gridHeight,
                imageUrl = localMapAssetUrl(map.imageAssetKey),
                dynamicLighting = catalog.localMaps.isDynamic(map.imageAssetKey),
            )
        },
        mapScenes = catalog.localMaps.scenes.map { scene ->
            LocalMapSceneView(
                key = scene.key,
                pixelWidth = scene.pixelWidth,
                pixelHeight = scene.pixelHeight,
                gridWidth = scene.gridWidth,
                gridHeight = scene.gridHeight,
                placements = scene.placements.map { placement ->
                    val map = catalog.localMaps.maps.single { it.key == placement.localMapKey }
                    LocalMapScenePlacementView(
                        localMapKey = map.key,
                        baseAreaId = map.baseAreaId,
                        gridX = placement.gridX,
                        gridY = placement.gridY,
                        pixelX = placement.gridX * 16,
                        pixelY = placement.gridY * 16,
                        pixelWidth = map.pixelWidth,
                        pixelHeight = map.pixelHeight,
                        gridWidth = map.gridWidth,
                        gridHeight = map.gridHeight,
                        imageUrl = localMapAssetUrl(map.imageAssetKey),
                        dynamicLighting = catalog.localMaps.isDynamic(map.imageAssetKey),
                    )
                },
            )
        },
        theme = CatalogThemeView(
            method = catalog.theme.method.name,
            assetClasses = catalog.theme.assetClasses.sortedBy { it.ordinal }.map { it.name },
            contrastCorrected = catalog.theme.contrastCorrected,
            tokens = catalog.theme.tokens.let { tokens ->
                CatalogThemeTokensView(
                    field = tokens.field.toCssRgb(),
                    fieldPattern = tokens.fieldPattern.toCssRgb(),
                    header = tokens.header.toCssRgb(),
                    headerShadow = tokens.headerShadow.toCssRgb(),
                    menu = tokens.menu.toCssRgb(),
                    menuShadow = tokens.menuShadow.toCssRgb(),
                    panel = tokens.panel.toCssRgb(),
                    border = tokens.border.toCssRgb(),
                    text = tokens.text.toCssRgb(),
                    textShadow = tokens.textShadow.toCssRgb(),
                    accent = tokens.accent.toCssRgb(),
                    accentText = tokens.accentText.toCssRgb(),
                )
            },
        ),
        capabilities = catalog.capabilities.mapKeys { it.key.name }.mapValues { it.value.status.name },
    )

    fun state(
        snapshot: AppSnapshot,
        catalog: ParsedCatalog?,
        truth: Effectiveness? = null,
        activeRulesetId: String? = null,
        rulesetAssumed: Boolean = true,
        retroArch: RetroArchView = RetroArchView(),
        saveRam: SaveRamView = SaveRamView(),
    ): StateView {
        val liveAreaBaseId = snapshot.liveAreaBaseId
        val effectiveAreaBaseId = liveAreaBaseId
            ?: snapshot.ledger.currentAreaBaseId.takeIf {
                !retroArch.connection.equals("CONNECTED", ignoreCase = true) && saveRam.status == "MATCHED"
            }
        val encounterAreasById = catalog?.encounterAreas.orEmpty().associateBy { it.id }
        val selectedAreaIds = if (snapshot.filter == com.enrpau.dualscreendex.companion.model.PokedexFilter.AREA) {
            val requested = snapshot.selectedAreaIds.ifEmpty { setOfNotNull(snapshot.selectedAreaId) }
            requested.filterTo(sortedSetOf()) { it in encounterAreasById }
        } else {
            sortedSetOf()
        }
        val browsedAreaBaseIds = selectedAreaIds
            .mapTo(sortedSetOf()) { selectedId -> requireNotNull(encounterAreasById[selectedId]).id / 10 }
            .ifEmpty { effectiveAreaBaseId?.let(::setOf).orEmpty() }
        val currentAreaIds = if (selectedAreaIds.isNotEmpty()) {
            selectedAreaIds.toList()
        } else {
            browsedAreaBaseIds.flatMap { baseId ->
                catalog?.encounterAreas?.filter { it.id / 10 == baseId }?.map { it.id }.orEmpty()
            }.distinct().sorted()
        }
        val currentAreaName = effectiveAreaBaseId?.let { catalog?.runtimeMetadata?.areaNamesByBaseId?.get(it) }
        val currentAreaSpeciesIds = browsedAreaBaseIds.takeIf { it.isNotEmpty() }?.let { baseIds ->
            val navigableIds = catalog?.navigableSpecies()?.mapTo(mutableSetOf()) { it.id }.orEmpty()
            val captured = navigableIds.filterTo(mutableSetOf()) { KnowledgePolicy.isCaught(it, snapshot.ledger) }
            (baseIds.flatMap { baseId -> snapshot.ledger.seenSpeciesByArea[baseId].orEmpty() } + captured)
                .filter { it in navigableIds }
                .distinct()
                .sorted()
        }.orEmpty()
        val revealedAreaBaseIds = (
            snapshot.ledger.visitedAreaBaseIds +
                snapshot.ledger.seenSpeciesByArea.keys +
                listOfNotNull(effectiveAreaBaseId)
            ).sorted()
        val observedAreaBaseIdsBySpecies = snapshot.ledger.seenSpeciesByArea.entries
            .flatMap { (areaBaseId, speciesIds) -> speciesIds.map { speciesId -> speciesId to areaBaseId } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, areaBaseIds) -> areaBaseIds.distinct().sorted() }
        val localMapPois = catalog?.localMaps?.pois.orEmpty().mapNotNull { poi ->
            val collected = poi.key in snapshot.ledger.collectedPoiKeys
            val explicitlyIdentified = poi.key in snapshot.ledger.identifiedPoiKeys || poi.key in snapshot.ledger.enteredPoiKeys
            val proximityRevealed = poi.key in snapshot.ledger.proximityRevealedPoiKeys
            val identified = snapshot.settings.knowledgeMode == KnowledgeMode.DISCOVERED || collected || explicitlyIdentified
            val visibleWithoutDiscovery = poi.organicVisibility ==
                com.enrpau.dualscreendex.parser.catalog.LocalMapPoiOrganicVisibility.VISIBLE
            val included = when (snapshot.settings.knowledgeMode) {
                KnowledgeMode.HIDDEN -> false
                KnowledgeMode.DISCOVERED -> true
                KnowledgeMode.ORGANIC -> visibleWithoutDiscovery || proximityRevealed || identified
            }
            if (!included) return@mapNotNull null
            val state = when {
                collected -> "COLLECTED"
                identified -> "IDENTIFIED"
                else -> "SILHOUETTE"
            }
            val category = when {
                collected -> "COLLECTED_ITEM"
                poi.kind == com.enrpau.dualscreendex.parser.catalog.LocalMapPoiKind.VISIBLE_ITEM ||
                    poi.kind == com.enrpau.dualscreendex.parser.catalog.LocalMapPoiKind.HIDDEN_ITEM -> "AVAILABLE_ITEM"
                poi.kind == com.enrpau.dualscreendex.parser.catalog.LocalMapPoiKind.SERVICE -> "SERVICE"
                poi.kind == com.enrpau.dualscreendex.parser.catalog.LocalMapPoiKind.PLACE -> "PLACE"
                else -> "UNKNOWN"
            }
            LocalMapPoiView(
                key = poi.key,
                localMapKey = poi.localMapKey,
                baseAreaId = poi.baseAreaId,
                tileX = poi.tileX,
                tileY = poi.tileY,
                category = category,
                state = state,
                displayName = poiDisplayName(
                    poi,
                    snapshot.trainerIdentity?.gender ?: snapshot.trainer?.gender,
                    snapshot.trainerIdentity?.name ?: snapshot.trainer?.name,
                )
                    .takeIf { identified },
                service = poi.service?.name,
                itemId = poi.item?.itemId.takeIf { identified },
                itemName = poi.item?.displayName.takeIf { identified },
                destinationBaseAreaId = poi.destinationBaseAreaId.takeIf { identified },
            )
        }
        val speciesState = catalog?.navigableSpecies()?.associate { species ->
            val owned = snapshot.ledger.owned.filter { it.speciesId == species.id }
            val preferred = PreferredIndividualSelector.select(owned)
            species.id to SpeciesStateView(
                seen = species.id in snapshot.ledger.seenSpecies,
                caught = KnowledgePolicy.isCaught(species.id, snapshot.ledger),
                team = species.id in snapshot.ledger.teamSpecies,
                ballId = preferred?.captureBallId
                    ?.takeIf { it in catalog.captureBallsById },
                preferredLevel = preferred?.level,
                innateTier = preferred?.let(PreferredIndividualSelector::tier)?.takeUnless { it == "UNAVAILABLE" },
            )
        }.orEmpty()
        val activeBattle = snapshot.battle
        val target = activeBattle?.opponents?.getOrNull(activeBattle.targetIndex)
        val knownEffectiveness = target?.let { opponent ->
            activeBattle.selectedMoveId?.let { moveId ->
                KnowledgePolicy.matchup(snapshot.settings.knowledgeMode, opponent.speciesId, moveId, truth, snapshot.ledger)
            }
        }
        return StateView(
            snapshot.version,
            snapshot.screen.name,
            snapshot.priorScreen.name,
            snapshot.settingsReturnScreen.name,
            snapshot.selectedSpeciesId,
            snapshot.selectedPartySlot,
            snapshot.filter.name,
            snapshot.selectedAreaId,
            selectedAreaIds.toList(),
            currentAreaIds,
            effectiveAreaBaseId,
            currentAreaName,
            snapshot.liveMapPosition?.let { MapPositionView(it.x, it.y) },
            localMapPois,
            snapshot.ledger.localMapPoiPreferences,
            currentAreaSpeciesIds,
            revealedAreaBaseIds,
            observedAreaBaseIdsBySpecies,
            snapshot.battleTab.name,
            snapshot.settings,
            speciesState,
            snapshot.ledger.observedMoves.mapValues { (_, observations) ->
                observations.toObservedMoveViews()
            },
            trainerView(snapshot, catalog),
            trainerAvatarUrl(snapshot, catalog),
            partyView(snapshot, catalog),
            snapshot.battle?.let { battle ->
                BattleView(
                    opponents = battle.opponents.map { opponent ->
                        val generation = when (catalog?.platform?.name) {
                            "GBA" -> 3
                            "GBC" -> 2
                            else -> 1
                        }
                        val individual = com.enrpau.dualscreendex.companion.model.OwnedPokemon(
                            "battle",
                            opponent.speciesId,
                            generation,
                            opponent.level,
                            ivs = opponent.ivs,
                            dvs = opponent.dvs,
                        )
                        val rarity = RarityEvaluator.evaluate(
                            individual = individual,
                            currentAreaBaseId = effectiveAreaBaseId,
                            encounterAreas = catalog?.encounterAreas.orEmpty(),
                        )
                        OpponentView(
                            opponent.speciesId,
                            opponent.level,
                            opponent.typeIds,
                            RarityView(
                                relativeTier = rarity.relativeTier?.name,
                                innateTier = rarity.innateTier?.name,
                                baseStars = rarity.baseStars,
                                areaAdjustment = rarity.areaAdjustment,
                                stars = rarity.stars,
                                areaOutcome = rarity.areaOutcome.name,
                                currentAreaBaseId = rarity.currentAreaBaseId,
                                currentAreaName = currentAreaName,
                                matchingAreaCount = rarity.matchingAreaCount,
                                candidateAreaCount = rarity.candidateAreaCount,
                            ),
                            opponent.moveHistory.toObservedMoveViews(),
                        )
                    },
                    targetIndex = battle.targetIndex,
                    targetMode = battle.targetMode.name,
                    capabilities = battle.capabilities,
                    selectedMoveId = battle.selectedMoveId,
                    effectiveness = knownEffectiveness?.name,
                    effectivenessKnown = knownEffectiveness != null,
                )
            },
            snapshot.catalogReady,
            snapshot.catalogName,
            snapshot.error,
            activeRulesetId,
            rulesetAssumed,
            CatalogLoadingView(
                snapshot.catalogLoading.active,
                snapshot.catalogLoading.phase,
                snapshot.catalogLoading.completedUnits,
                snapshot.catalogLoading.totalUnits,
            ),
            retroArch,
            saveRam,
            snapshot.gameTime?.let { GameClockView(it.hours, it.minutes, it.phase?.name, it.phaseProgress) },
        )
    }

    fun diagnostics(
        catalog: ParsedCatalog,
        romName: String?,
        activeRulesetId: String?,
        rulesetAssumed: Boolean,
        speciesId: Int?,
        moveId: Int?,
    ): DiagnosticView {
        val view = catalog(catalog)
        return DiagnosticView(
            romName = romName,
            sha256 = catalog.romSha256,
            crc32 = catalog.romCrc32,
            family = catalog.family.name,
            platform = catalog.platform.name,
            activeRulesetId = activeRulesetId,
            rulesetAssumed = rulesetAssumed,
            rulesets = view.rulesets,
            capabilities = catalog.capabilities.values.sortedBy { it.capability.ordinal }.map {
                val validRecords = it.validRecords
                val totalRecords = it.totalRecords
                DiagnosticCapabilityView(
                    it.capability.name,
                    if (
                        it.status == com.enrpau.dualscreendex.parser.model.CapabilityStatus.AVAILABLE &&
                        validRecords != null && totalRecords != null && validRecords < totalRecords
                    ) "PARTIAL" else it.status.name,
                    it.confidence,
                    it.offset,
                    it.count,
                    it.recordSize,
                    it.reasons,
                    validRecords,
                    totalRecords,
                    it.elementSize,
                    it.reviewStatus.name,
                )
            },
            parserDiagnostics = catalog.diagnostics,
            species = speciesId?.let { id -> view.species.firstOrNull { it.id == id } },
            move = moveId?.let { id -> view.moves.firstOrNull { it.id == id } },
        )
    }

    private fun Int.toCss(): String = "#%02X%02X%02X%02X".format(
        this ushr 16 and 0xFF,
        this ushr 8 and 0xFF,
        this and 0xFF,
        this ushr 24 and 0xFF,
    )

    private fun Int.toCssRgb(): String = "#%06x".format(this and 0xFFFFFF)

    private fun List<MoveObservation>.toObservedMoveViews(): List<ObservedMoveView> =
        sortedWith(compareByDescending<MoveObservation> { it.frequency }.thenBy { it.moveId })
            .map { ObservedMoveView(it.moveId, it.frequency) }

    private fun trainerView(snapshot: AppSnapshot, catalog: ParsedCatalog?): TrainerView? =
        snapshot.trainer?.let { trainer ->
            val assets = catalog?.trainerAssets
            TrainerView(
                name = trainer.name,
                gender = if (trainer.gender == 0) "MALE" else "FEMALE",
                publicTrainerId = trainer.publicTrainerId,
                money = trainer.money,
                playTimeHours = trainer.playTimeHours,
                playTimeMinutes = trainer.playTimeMinutes,
                dexSeen = trainer.dexSeen,
                dexCaught = trainer.dexCaught,
                stars = trainer.stars,
                avatarUrl = assets?.avatarAssetKeys?.get(trainer.gender)?.let(::trainerAssetUrl),
                badges = (0 until 8).map { badgeIndex ->
                    TrainerBadgeView(
                        index = badgeIndex,
                        earned = trainer.badgeFlags and (1 shl badgeIndex) != 0,
                        imageUrl = assets?.badgeAssetKeys?.getOrNull(badgeIndex)?.let(::trainerAssetUrl),
                    )
                },
            )
        }

    private fun trainerAvatarUrl(snapshot: AppSnapshot, catalog: ParsedCatalog?): String? {
        val gender = snapshot.trainer?.gender ?: snapshot.trainerIdentity?.gender ?: return null
        return catalog?.trainerAssets?.avatarAssetKeys?.get(gender)?.let(::trainerAssetUrl)
    }

    private fun partyView(snapshot: AppSnapshot, catalog: ParsedCatalog?): List<PartyMemberView> =
        (0 until PARTY_SLOT_COUNT).map { slot ->
            val individual = snapshot.party.getOrNull(slot) ?: return@map PartyMemberView(slot, occupied = false)
            val species = catalog?.speciesById?.get(individual.speciesId)
            val speciesName = species?.name?.value?.takeIf(String::isNotBlank)
            val details = individual.details
            val resolvedAbility = details?.abilityId?.let { abilityId ->
                catalog?.abilitiesById?.get(abilityId)?.name?.value
                    ?.takeIf(String::isNotBlank)
                    ?.let { abilityId to it }
            }
            val resolvedNature = details?.natureId?.let { catalog?.naturesById?.get(it) }
            val quality = individual.level?.takeIf { it > 0 }?.let { level ->
                val generation = when (catalog?.platform?.name) {
                    "GBA" -> 3
                    "GBC" -> 2
                    else -> 1
                }
                val candidate = com.enrpau.dualscreendex.companion.model.OwnedPokemon(
                    stableKey = individual.stableLocation,
                    speciesId = individual.speciesId,
                    generation = generation,
                    level = level,
                    ivs = individual.ivs.orEmpty(),
                    dvs = individual.dvs.orEmpty(),
                    isEgg = individual.isEgg,
                    party = true,
                )
                RarityEvaluator.evaluate(candidate, currentAreaBaseId = null, encounterAreas = emptyList())
                    .takeIf { it.innateTier != null }
            }
            PartyMemberView(
                slot = slot,
                occupied = true,
                speciesId = individual.speciesId.takeIf { speciesName != null },
                speciesName = speciesName,
                spriteUrl = individual.speciesId.takeIf { species?.sprite?.value != null }
                    ?.let { "/api/sprites/species/$it.png" },
                typeIds = species?.typeIds?.value.orEmpty(),
                nickname = details?.nickname,
                level = individual.level,
                isEgg = individual.isEgg,
                gender = details?.gender?.let(::partyGender),
                natureId = resolvedNature?.id,
                nature = resolvedNature?.name,
                abilityId = resolvedAbility?.first,
                abilityName = resolvedAbility?.second,
                heldItemId = null,
                heldItemName = null,
                hasHeldItem = details?.let { it.heldItemId != null },
                currentHp = details?.currentHp,
                maximumHp = details?.maximumHp,
                status = details?.status?.let(::partyStatus),
                experienceProgress = details?.experienceProgress,
                rarity = quality?.let { rarity ->
                    RarityView(
                        relativeTier = null,
                        innateTier = rarity.innateTier?.name,
                        baseStars = rarity.baseStars,
                        areaAdjustment = null,
                        stars = rarity.stars,
                        areaOutcome = rarity.areaOutcome.name,
                        currentAreaBaseId = null,
                        currentAreaName = null,
                        matchingAreaCount = 0,
                        candidateAreaCount = 0,
                    )
                },
                stats = details?.stats.orEmpty().takeIf { it.size == STAT_NAMES.size }
                    ?.let { values -> STAT_NAMES.zip(values).toMap(linkedMapOf()) }
                    .orEmpty(),
                moves = (0 until MOVE_SLOT_COUNT).map { moveSlot ->
                    val moveId = details?.moveIds?.getOrNull(moveSlot)?.takeIf { it > 0 }
                    val move = moveId?.let { catalog?.movesById?.get(it) }
                    val name = move?.name?.value?.takeIf(String::isNotBlank)
                    PartyMoveView(
                        slot = moveSlot,
                        moveId = moveId.takeIf { name != null },
                        name = name,
                        currentPp = details?.movePp?.getOrNull(moveSlot).takeIf { name != null },
                        maximumPp = move?.pp?.value.takeIf { name != null },
                    )
                },
            )
        }

    private fun localMapAssetUrl(key: String): String =
        "/api/maps/${URLEncoder.encode(key, StandardCharsets.UTF_8)}.png"

    private fun poiDisplayName(
        poi: com.enrpau.dualscreendex.parser.catalog.LocalMapPoi,
        trainerGender: Int?,
        trainerName: String?,
    ): String? {
        val conditioned = trainerGender?.let(poi.displayNamesByTrainerGender::get)
        val unresolved = poi.displayNamesByTrainerGender.toSortedMap().values.firstOrNull()
        val template = conditioned ?: poi.displayName ?: unresolved ?: return null
        return resolvePlayerPlaceholder(template, trainerName)
    }

    private fun LocalMapCatalog.isDynamic(key: String): Boolean =
        key in indexedAssets || key in timedAssets

    private fun trainerAssetUrl(key: String): String =
        "/api/trainer-assets/${URLEncoder.encode(key, StandardCharsets.UTF_8)}.png"

    private fun partyGender(gender: Int): String? = when (gender) {
        0 -> "MALE"
        1 -> "FEMALE"
        2 -> "GENDERLESS"
        else -> null
    }

    private fun partyStatus(value: Long): String? {
        if (value == 0L) return null
        val statuses = buildList {
            if (value and 0x7L != 0L) add("SLP")
            if (value and 0x8L != 0L) add("PSN")
            if (value and 0x10L != 0L) add("BRN")
            if (value and 0x20L != 0L) add("FRZ")
            if (value and 0x40L != 0L) add("PAR")
            if (value and 0x80L != 0L) add("TOX")
        }
        return when (statuses.size) {
            0 -> null
            1 -> statuses.single()
            else -> "AILMENT"
        }
    }

    private val STAT_NAMES = listOf("HP", "ATTACK", "DEFENSE", "SPEED", "SP. ATK", "SP. DEF")
    private const val PARTY_SLOT_COUNT = 6
    private const val MOVE_SLOT_COUNT = 4

    private fun evolutionCondition(catalog: ParsedCatalog, edge: EvolutionEdge): String {
        val generation = when (catalog.platform) {
            com.enrpau.dualscreendex.parser.model.Platform.GBA -> 3
            com.enrpau.dualscreendex.parser.model.Platform.GBC -> 2
            else -> 1
        }
        return when {
            generation == 3 && edge.methodId == 4 -> "Level ${edge.parameter}"
            generation <= 2 && edge.methodId == 1 -> "Level ${edge.parameter}"
            generation == 3 && edge.methodId == 5 -> "Trade"
            generation <= 2 && edge.methodId == 3 -> "Trade"
            generation == 3 && edge.methodId == 6 -> "Trade with item ${edge.parameter}"
            (generation == 3 && edge.methodId == 7) || (generation <= 2 && edge.methodId == 2) -> "Use item ${edge.parameter}"
            generation == 3 && edge.methodId in 1..3 -> "High friendship"
            else -> "Method ${edge.methodId} · parameter ${edge.parameter}"
        }
    }
}

internal fun resolvePlayerPlaceholder(template: String, trainerName: String?): String {
    val resolvedName = trainerName?.takeIf(String::isNotBlank)
    return if (resolvedName != null) {
        template.replace("{PLAYER}", resolvedName, ignoreCase = true)
    } else {
        template
            .replace("{PLAYER}'s", "Your", ignoreCase = true)
            .replace("{PLAYER}’s", "Your", ignoreCase = true)
            .replace("{PLAYER}", "You", ignoreCase = true)
    }
}
