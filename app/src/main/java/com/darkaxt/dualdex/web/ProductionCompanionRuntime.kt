package com.darkaxt.dualdex.web

import com.darkaxt.dualdex.catalog.CatalogRepository
import com.darkaxt.dualdex.catalog.CatalogSourceMetadata
import com.darkaxt.dualdex.catalog.CatalogWriteProgress
import com.darkaxt.dualdex.knowledge.KnowledgeRepository
import com.darkaxt.dualdex.battle.BattleCatalogContext
import com.darkaxt.dualdex.battle.BattleCatalogView
import com.darkaxt.dualdex.battle.BattleMove
import com.darkaxt.dualdex.battle.BattleSpecies
import com.darkaxt.dualdex.battle.BattleTrackingUpdate
import com.darkaxt.dualdex.battle.TargetMode
import com.enrpau.dualscreendex.companion.CompanionGateway
import com.enrpau.dualscreendex.companion.api.ApiViewBuilder
import com.enrpau.dualscreendex.companion.api.BootstrapView
import com.enrpau.dualscreendex.companion.api.DiagnosticView
import com.enrpau.dualscreendex.companion.api.RetroArchView
import com.enrpau.dualscreendex.companion.api.SaveRamView
import com.enrpau.dualscreendex.companion.api.StateView
import com.enrpau.dualscreendex.companion.model.AppScreen
import com.enrpau.dualscreendex.companion.model.AppSnapshot
import com.enrpau.dualscreendex.companion.model.CatalogLoadingState
import com.enrpau.dualscreendex.companion.model.BattleState
import com.enrpau.dualscreendex.companion.model.BattleTab
import com.enrpau.dualscreendex.companion.model.BattleTargetMode
import com.enrpau.dualscreendex.companion.model.CompanionAction
import com.enrpau.dualscreendex.companion.model.CompanionSettings
import com.enrpau.dualscreendex.companion.model.Density
import com.enrpau.dualscreendex.companion.model.DisplayMode
import com.enrpau.dualscreendex.companion.model.DisplayTarget
import com.enrpau.dualscreendex.companion.model.Effectiveness
import com.enrpau.dualscreendex.companion.model.KnowledgeMode
import com.enrpau.dualscreendex.companion.model.KnowledgeLedger
import com.enrpau.dualscreendex.companion.model.MatchupKey
import com.enrpau.dualscreendex.companion.model.MoveObservation
import com.enrpau.dualscreendex.companion.model.OpponentState
import com.enrpau.dualscreendex.companion.model.PokedexFilter
import com.enrpau.dualscreendex.companion.model.Theme
import com.enrpau.dualscreendex.companion.knowledge.SaveKnowledgeMapper
import com.darkaxt.dualdex.save.SaveParseContext
import com.darkaxt.dualdex.save.SaveSnapshot
import com.darkaxt.dualdex.save.SaveSpeciesContext
import com.enrpau.dualscreendex.parser.catalog.CatalogMaterializationProgress
import com.enrpau.dualscreendex.parser.catalog.CatalogParser
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.enrpau.dualscreendex.parser.detect.RomHeaderReader
import com.enrpau.dualscreendex.parser.io.LoadedRom
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.io.RomSourceLoader
import java.io.InputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/** Production ROM catalog runtime. It deliberately has no simulator dependency or battle generator. */
class ProductionCompanionRuntime(
    private val parserWorker: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "dualdex-parser").apply { isDaemon = true }
    },
    private val catalogRepository: CatalogRepository? = null,
    private val onCatalogCommitted: (sha256: String, displayName: String) -> Unit = { _, _ -> },
    initialSettings: CompanionSettings = CompanionSettings(),
    private val onSettingsChanged: (CompanionSettings) -> Unit = {},
    private val knowledgeRepository: KnowledgeRepository? = null,
) : AutoCloseable {
    private var catalog: ParsedCatalog? = null
    @Volatile private var retroArch = RetroArchView()
    @Volatile private var saveRam = SaveRamView()
    private var catalogPublicationInProgress = false
    private var cachedState: CachedState? = null
    private val loadGeneration = AtomicLong()
    val gateway = CompanionGateway(
        AppSnapshot(
            settings = initialSettings,
        ),
    )

    fun load(name: String, input: InputStream): BootstrapView = load(RomSourceLoader.load(name, input))

    fun load(source: LoadedRom): BootstrapView {
        load(source.displayName, source.rom)
        return bootstrap()
    }

    fun load(source: LoadedRom, onComplete: (Result<Unit>) -> Unit) {
        loadInternal(source.displayName, source.rom, onComplete)
    }

    fun load(name: String, rom: RomImage) {
        loadInternal(name, rom, null)
    }

    private fun loadInternal(name: String, rom: RomImage, onComplete: ((Result<Unit>) -> Unit)?) {
        val generation = loadGeneration.incrementAndGet()
        val header = RomHeaderReader.read(rom)
        val source = CatalogSourceMetadata.fromDisplayName(name, rom.size, header.title)
        synchronized(this) { catalog = null }
        clearLiveBattle()
        saveRam = SaveRamView()
        gateway.dispatch(CompanionAction.ReplaceLedger(com.enrpau.dualscreendex.companion.model.KnowledgeLedger()))
        gateway.dispatch(CompanionAction.SetScreen(AppScreen.POKEDEX))
        gateway.dispatch(
            CompanionAction.CatalogLoadingChanged(
                CatalogLoadingState(active = true, phase = "IDENTIFYING", completedUnits = 0, totalUnits = 5),
                name,
            ),
        )
        parserWorker.execute {
            try {
                val cached = catalogRepository?.readComplete(rom.sha256)
                if (cached != null) {
                    if (generation != loadGeneration.get()) {
                        notifyCompletion(onComplete, Result.failure(IllegalStateException("catalog load was superseded")))
                        return@execute
                    }
                    catalogRepository.write(cached.catalog, source, CatalogWriteProgress.complete())
                    publishReopened(generation, name, cached.catalog)
                    onCatalogCommitted(cached.catalog.romSha256, name)
                    notifyCompletion(onComplete, Result.success(Unit))
                    return@execute
                }
                val parsed = CatalogParser.parse(rom) { progress -> publishProgress(generation, progress, source) }.catalog
                    ?: error("ROM did not produce a supported mainline-family catalog")
                if (generation != loadGeneration.get()) {
                    notifyCompletion(onComplete, Result.failure(IllegalStateException("catalog load was superseded")))
                    return@execute
                }
                synchronized(this) { catalog = parsed }
                restoreKnowledge(parsed.romSha256)
                gateway.dispatch(CompanionAction.CatalogLoaded(name))
                onCatalogCommitted(parsed.romSha256, name)
                notifyCompletion(onComplete, Result.success(Unit))
            } catch (failure: Exception) {
                if (generation == loadGeneration.get()) {
                    gateway.dispatch(CompanionAction.Failure(failure.message ?: failure.javaClass.simpleName))
                    gateway.dispatch(
                        CompanionAction.CatalogLoadingChanged(
                            CatalogLoadingState(active = false, phase = "FAILED", completedUnits = 0, totalUnits = 5),
                        ),
                    )
                }
                notifyCompletion(onComplete, Result.failure(failure))
            }
        }
    }

    /** Test and cache-reopen seam; Stage 2 will use this for persisted catalogs. */
    @Synchronized
    fun loadCatalog(name: String, parsed: ParsedCatalog) {
        loadGeneration.incrementAndGet()
        clearLiveBattle()
        catalog = parsed
        saveRam = SaveRamView()
        gateway.dispatch(CompanionAction.ReplaceLedger(readKnowledge(parsed.romSha256)))
        gateway.dispatch(CompanionAction.SetScreen(AppScreen.POKEDEX))
        gateway.dispatch(CompanionAction.CatalogLoaded(name))
    }

    fun restoreCatalog(sha256: String): Boolean {
        val stored = catalogRepository?.readComplete(sha256) ?: return false
        val generation = loadGeneration.incrementAndGet()
        publishReopened(generation, stored.source.displayName, stored.catalog)
        onCatalogCommitted(stored.catalog.romSha256, stored.source.displayName)
        return true
    }

    fun restoreCatalogAsync(sha256: String) {
        gateway.dispatch(
            CompanionAction.CatalogLoadingChanged(
                CatalogLoadingState(active = true, phase = "CACHE_REOPEN", completedUnits = 0, totalUnits = 5),
            ),
        )
        parserWorker.execute {
            if (!restoreCatalog(sha256)) {
                gateway.dispatch(
                    CompanionAction.CatalogLoadingChanged(
                        CatalogLoadingState(active = false, phase = "IDLE", completedUnits = 0, totalUnits = 0),
                    ),
                )
            }
        }
    }

    @Synchronized
    fun bootstrap(): BootstrapView {
        val snapshot = gateway.bootstrap()
        return BootstrapView(catalog?.let(ApiViewBuilder::catalog), stateView(snapshot))
    }

    @Synchronized
    fun stateView(snapshot: AppSnapshot = gateway.bootstrap()): StateView {
        val currentCatalog = catalog
        cachedState?.let { cached ->
            if (
                cached.snapshotVersion == snapshot.version &&
                cached.catalog === currentCatalog &&
                cached.retroArch == retroArch &&
                cached.saveRam == saveRam
            ) return cached.view
        }
        val active = resolveRuleset(snapshot.settings.ruleset)
        return ApiViewBuilder.state(
            snapshot,
            currentCatalog,
            truth = battleTruth(snapshot, currentCatalog),
            activeRulesetId = active?.id,
            rulesetAssumed = snapshot.settings.ruleset == "AUTO",
            retroArch = retroArch,
            saveRam = saveRam,
        ).also { view -> cachedState = CachedState(snapshot.version, currentCatalog, retroArch, saveRam, view) }
    }

    fun updateRetroArch(state: RetroArchView) {
        retroArch = state
    }

    fun retroArchState(): RetroArchView = retroArch

    fun updateSaveRam(state: SaveRamView) {
        saveRam = state
    }

    @Synchronized
    fun saveParseContext(): SaveParseContext? {
        if (catalogPublicationInProgress) return null
        return catalog?.let { current ->
            SaveParseContext(
                romIdentity = current.romSha256,
                speciesById = current.speciesById.mapValues { (id, species) ->
                    SaveSpeciesContext(id, species.dexNumber.value, species.growthRate.value, species.formId)
                },
                captureBallIds = current.captureBallsById.keys.ifEmpty { (1..15).toSet() },
            )
        }
    }

    @Synchronized
    fun battleCatalogContext(): BattleCatalogContext? {
        if (catalogPublicationInProgress) return null
        val current = catalog ?: return null
        val generation = when (current.platform.name) {
            "GB" -> 1
            "GBA" -> 3
            else -> return null
        }
        val species = current.speciesById.mapNotNull { (id, record) ->
            val types = record.typeIds.value ?: return@mapNotNull null
            id to BattleSpecies(
                id = id,
                typeIds = types,
                abilityIds = record.abilityIds.value.orEmpty().filterTo(mutableSetOf()) { it > 0 },
            )
        }.toMap()
        val moves = current.movesById.mapNotNull { (id, record) ->
            val pp = record.pp.value?.takeIf { it > 0 } ?: return@mapNotNull null
            id to BattleMove(id, pp)
        }.toMap()
        if (species.isEmpty() || moves.isEmpty() || current.typesById.isEmpty()) return null
        return BattleCatalogContext(
            romIdentity = current.romSha256,
            generation = generation,
            catalog = BattleCatalogView(species, moves, current.typesById.keys),
        )
    }

    @Synchronized
    fun applyBattleTracking(update: BattleTrackingUpdate) {
        val before = gateway.bootstrap()
        val observed = before.ledger.observedMoves.toMutableMap()
        update.observations.forEach { (speciesId, increments) ->
            val frequencies = observed[speciesId].orEmpty().associate { it.moveId to it.frequency }.toMutableMap()
            increments.forEach { (moveId, count) -> frequencies.merge(moveId, count, Int::plus) }
            observed[speciesId] = frequencies.entries
                .sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })
                .map { MoveObservation(it.key, it.value) }
        }
        val seen = before.ledger.seenSpecies + update.sample?.opponents.orEmpty().map { it.speciesId }
        val discoveredMatchups = before.ledger.discoveredMatchups.toMutableMap()
        val currentCatalog = catalog
        update.discoveredMatchups.forEach { observation ->
            effectivenessFor(currentCatalog, observation.moveId, observation.defendingTypeIds)?.let { effectiveness ->
                discoveredMatchups[MatchupKey(observation.speciesId, observation.moveId)] = effectiveness
            }
        }
        val mergedLedger = before.ledger.copy(
            seenSpecies = seen,
            observedMoves = observed,
            discoveredMatchups = discoveredMatchups,
        )
        if (mergedLedger != before.ledger) {
            gateway.dispatch(CompanionAction.ReplaceLedger(mergedLedger))
            persistKnowledge(mergedLedger)
        }

        if (update.ended) {
            clearLiveBattle()
            return
        }
        val sample = update.sample ?: return
        if (!update.active) return

        val latestLedger = gateway.bootstrap().ledger
        val opponents = sample.opponents.map { opponent ->
            OpponentState(
                speciesId = opponent.speciesId,
                level = opponent.level,
                typeIds = opponent.typeIds,
                ivs = opponent.ivs,
                dvs = opponent.dvs,
                moveHistory = latestLedger.observedMoves[opponent.speciesId].orEmpty(),
            )
        }
        val battle = BattleState(
            opponents = opponents,
            targetIndex = sample.target.opponentIndex.coerceIn(0, (opponents.size - 1).coerceAtLeast(0)),
            selectedMoveId = sample.selectedMoveId,
            playerReferenceLevel = sample.battlers.filter { it.position and 1 == 0 }.maxOfOrNull { it.level },
            targetMode = when (sample.target.mode) {
                TargetMode.AUTOMATIC -> BattleTargetMode.AUTOMATIC
                TargetMode.MANUAL_TARGET_FALLBACK -> BattleTargetMode.MANUAL_TARGET_FALLBACK
            },
            capabilities = sample.capabilities.mapKeys { it.key.name }.mapValues { it.value.name },
        )
        gateway.dispatch(CompanionAction.BattleStarted(battle))
    }

    @Synchronized
    fun clearLiveBattle() {
        if (gateway.bootstrap().battle != null) gateway.dispatch(CompanionAction.BattleEnded)
    }

    private fun battleTruth(snapshot: AppSnapshot, currentCatalog: ParsedCatalog?): Effectiveness? {
        val battle = snapshot.battle ?: return null
        val target = battle.opponents.getOrNull(battle.targetIndex) ?: return null
        val moveId = battle.selectedMoveId ?: return null
        return effectivenessFor(currentCatalog, moveId, target.typeIds)
    }

    private fun effectivenessFor(
        currentCatalog: ParsedCatalog?,
        moveId: Int,
        defendingTypeIds: List<Int>,
    ): Effectiveness? {
        val current = currentCatalog ?: return null
        if (current.typeChart.isEmpty() || defendingTypeIds.isEmpty()) return null
        val attackingTypeId = current.movesById[moveId]?.typeId?.value ?: return null
        var multiplier = 100L
        defendingTypeIds.distinct().forEach { defendingTypeId ->
            val factor = current.typeChart.lastOrNull {
                it.attackingTypeId == attackingTypeId && it.defendingTypeId == defendingTypeId
            }?.multiplierPercent ?: 100
            multiplier = multiplier * factor / 100
        }
        return when {
            multiplier == 0L -> Effectiveness.NO_EFFECT
            multiplier < 100L -> Effectiveness.RESISTED
            multiplier == 100L -> Effectiveness.NEUTRAL
            else -> Effectiveness.SUPER_EFFECTIVE
        }
    }

    @Synchronized
    fun applySaveSnapshot(snapshot: SaveSnapshot, state: SaveRamView): Boolean {
        val current = catalog ?: return false
        if (!snapshot.romIdentity.equals(current.romSha256, ignoreCase = true)) return false
        val merged = SaveKnowledgeMapper.merge(gateway.bootstrap().ledger, current, snapshot)
        saveRam = state
        gateway.dispatch(CompanionAction.ReplaceLedger(merged))
        persistKnowledge(merged)
        return true
    }

    fun action(type: String, values: Map<String, String?>): StateView {
        when (type.uppercase()) {
            "OPEN_SPECIES" -> gateway.dispatch(CompanionAction.OpenSpecies(requireInt(values, "speciesId")))
            "BACK" -> gateway.dispatch(CompanionAction.BackToPokedex)
            "SCREEN" -> gateway.dispatch(CompanionAction.SetScreen(AppScreen.valueOf(requireNotNull(values["screen"]).uppercase())))
            "FILTER" -> gateway.dispatch(
                CompanionAction.SetFilter(
                    PokedexFilter.valueOf(requireNotNull(values["filter"]).uppercase()),
                    values["areaId"]?.toIntOrNull(),
                ),
            )
            "SETTINGS" -> updateSettings(values)
            "TAB", "BATTLE_TAB" -> gateway.dispatch(CompanionAction.SetBattleTab(BattleTab.valueOf(requireNotNull(values["tab"]).uppercase())))
            "TARGET", "SELECT_TARGET" -> gateway.dispatch(CompanionAction.SelectTarget(requireInt(values, "index")))
            else -> throw IllegalArgumentException("unknown production action: $type")
        }
        return stateView()
    }

    @Synchronized
    fun speciesSprite(id: Int) = catalog?.speciesById?.get(id)?.sprite?.value

    @Synchronized
    fun ballSprite(id: Int) = catalog?.captureBallsById?.get(id)?.sprite?.value

    @Synchronized
    fun catalogHash(): String? = catalog?.romSha256

    @Synchronized
    fun diagnostics(speciesId: Int?, moveId: Int?): DiagnosticView {
        val current = requireNotNull(catalog) { "load a ROM before requesting diagnostics" }
        val snapshot = gateway.bootstrap()
        val active = resolveRuleset(snapshot.settings.ruleset)
        return ApiViewBuilder.diagnostics(
            current,
            snapshot.catalogName,
            active?.id,
            snapshot.settings.ruleset == "AUTO",
            speciesId,
            moveId,
        )
    }

    override fun close() {
        loadGeneration.incrementAndGet()
        parserWorker.shutdown()
    }

    private fun updateSettings(values: Map<String, String?>) {
        val current = gateway.bootstrap().settings
        val ruleset = values["ruleset"]?.let { requested ->
            if (requested.equals("AUTO", ignoreCase = true)) {
                "AUTO"
            } else {
                requireNotNull(catalog?.learnsetRulesets?.firstOrNull { it.id == requested }) {
                    "unknown catalog ruleset: $requested"
                }.id
            }
        } ?: current.ruleset
        val updated = CompanionSettings(
                    knowledgeMode = values["knowledgeMode"]?.let { KnowledgeMode.valueOf(it.uppercase()) } ?: current.knowledgeMode,
                    attackEnabled = values["attackEnabled"]?.toBooleanStrictOrNull() ?: current.attackEnabled,
                    rarityEnabled = values["rarityEnabled"]?.toBooleanStrictOrNull() ?: current.rarityEnabled,
                    movesEnabled = values["movesEnabled"]?.toBooleanStrictOrNull() ?: current.movesEnabled,
                    fontScale = values["fontScale"]?.toDoubleOrNull()?.coerceIn(0.85, 1.35) ?: current.fontScale,
                    density = values["density"]?.let { Density.valueOf(it.uppercase()) } ?: current.density,
                    highContrast = values["highContrast"]?.toBooleanStrictOrNull() ?: current.highContrast,
                    autoOpenTarget = values["autoOpenTarget"]?.toBooleanStrictOrNull() ?: current.autoOpenTarget,
                    ruleset = ruleset,
                    displayMode = values["displayMode"]?.let { DisplayMode.valueOf(it.uppercase()) } ?: current.displayMode,
                    theme = values["theme"]?.let { Theme.valueOf(it.uppercase()) } ?: current.theme,
                    displayTarget = values["displayTarget"]?.let { DisplayTarget.valueOf(it.uppercase()) } ?: current.displayTarget,
                    overlayScale = current.overlayScale,
                )
        gateway.dispatch(
            CompanionAction.UpdateSettings(updated),
        )
        onSettingsChanged(updated)
    }

    private fun resolveRuleset(selection: String) = catalog?.learnsetRulesets?.let { rulesets ->
        if (selection == "AUTO") rulesets.firstOrNull { it.primary } ?: rulesets.firstOrNull()
        else rulesets.firstOrNull { it.id == selection }
    }

    private fun publishProgress(
        generation: Long,
        progress: CatalogMaterializationProgress,
        source: CatalogSourceMetadata,
    ) {
        if (generation != loadGeneration.get()) return
        catalogRepository?.write(
            progress.catalog,
            source,
            CatalogWriteProgress(
                phase = progress.phase.name,
                completedUnits = progress.completedUnits,
                totalUnits = progress.totalUnits,
                complete = progress.completedUnits == progress.totalUnits,
                changedSections = changedCatalogSections(progress.phase.name),
            ),
        )
        synchronized(this) { catalog = progress.catalog }
        gateway.dispatch(
            CompanionAction.CatalogLoadingChanged(
                CatalogLoadingState(
                    active = progress.completedUnits < progress.totalUnits,
                    phase = progress.phase.name,
                    completedUnits = progress.completedUnits,
                    totalUnits = progress.totalUnits,
                ),
            ),
        )
    }

    @Synchronized
    private fun publishReopened(generation: Long, name: String, reopened: ParsedCatalog) {
        if (generation != loadGeneration.get()) return
        catalogPublicationInProgress = true
        try {
            saveRam = SaveRamView()
            clearLiveBattle()
            gateway.dispatch(CompanionAction.ReplaceLedger(readKnowledge(reopened.romSha256)))
            gateway.dispatch(CompanionAction.SetScreen(AppScreen.POKEDEX))
            gateway.dispatch(
                CompanionAction.CatalogLoadingChanged(
                    CatalogLoadingState(active = false, phase = "CACHE_REOPEN", completedUnits = 5, totalUnits = 5),
                    name,
                ),
            )
            catalog = reopened
            gateway.dispatch(CompanionAction.CatalogLoaded(name))
        } finally {
            catalogPublicationInProgress = false
        }
    }

    private fun changedCatalogSections(phase: String): Set<String> = when (phase) {
        "ESSENTIAL" -> com.darkaxt.dualdex.catalog.CatalogSchema.requiredSections
        "SPECIES_MEDIA" -> setOf("species")
        "RELATIONSHIPS" -> setOf("species", "encounters")
        "EXTENDED" -> setOf(
            "species",
            "moves",
            "abilities",
            "capture_balls",
            "learnset_rulesets",
            "capabilities",
            "diagnostics",
        )
        "COMPLETE" -> emptySet()
        else -> com.darkaxt.dualdex.catalog.CatalogSchema.requiredSections
    }

    private fun requireInt(values: Map<String, String?>, key: String): Int =
        requireNotNull(values[key]?.toIntOrNull()) { "$key is required" }

    private fun restoreKnowledge(romIdentity: String) {
        gateway.dispatch(CompanionAction.ReplaceLedger(readKnowledge(romIdentity)))
    }

    private fun readKnowledge(romIdentity: String): KnowledgeLedger =
        runCatching { knowledgeRepository?.read(romIdentity) }.getOrNull() ?: KnowledgeLedger()

    private fun persistKnowledge(ledger: KnowledgeLedger) {
        val romIdentity = catalog?.romSha256 ?: return
        runCatching { knowledgeRepository?.write(romIdentity, ledger) }
    }

    private fun notifyCompletion(callback: ((Result<Unit>) -> Unit)?, result: Result<Unit>) {
        if (callback != null) runCatching { callback(result) }
    }

    private data class CachedState(
        val snapshotVersion: Long,
        val catalog: ParsedCatalog?,
        val retroArch: RetroArchView,
        val saveRam: SaveRamView,
        val view: StateView,
    )
}
