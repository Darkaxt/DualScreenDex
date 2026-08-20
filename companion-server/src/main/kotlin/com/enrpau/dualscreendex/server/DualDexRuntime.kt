package com.enrpau.dualscreendex.server

import com.enrpau.dualscreendex.companion.CompanionGateway
import com.enrpau.dualscreendex.companion.api.ApiViewBuilder
import com.enrpau.dualscreendex.companion.api.BootstrapView
import com.enrpau.dualscreendex.companion.api.DiagnosticView
import com.enrpau.dualscreendex.companion.api.StateView
import com.enrpau.dualscreendex.companion.model.AppScreen
import com.enrpau.dualscreendex.companion.model.BattleTab
import com.enrpau.dualscreendex.companion.model.CatalogLoadingState
import com.enrpau.dualscreendex.companion.model.CompanionAction
import com.enrpau.dualscreendex.companion.model.CompanionSettings
import com.enrpau.dualscreendex.companion.model.Density
import com.enrpau.dualscreendex.companion.model.Effectiveness
import com.enrpau.dualscreendex.companion.model.KnowledgeMode
import com.enrpau.dualscreendex.companion.model.MatchupKey
import com.enrpau.dualscreendex.companion.model.PokedexFilter
import com.enrpau.dualscreendex.parser.catalog.CatalogParser
import com.enrpau.dualscreendex.parser.catalog.CatalogMaterializationProgress
import com.enrpau.dualscreendex.parser.catalog.LocalMapAssetRenderer
import com.enrpau.dualscreendex.parser.catalog.MapLighting
import com.enrpau.dualscreendex.parser.catalog.MapTimeOfDay
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.enrpau.dualscreendex.parser.catalog.RenderedMapAsset
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.io.LoadedRom
import com.enrpau.dualscreendex.parser.io.RomSourceLoader
import com.enrpau.dualscreendex.parser.sprite.PngEncoder
import com.enrpau.dualscreendex.simulator.EncounterSimulator
import com.enrpau.dualscreendex.simulator.SimulationRequest
import java.nio.file.Path
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

class DualDexRuntime(
    private val parserWorker: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "dualdex-parser").apply { isDaemon = true }
    },
) : AutoCloseable {
    private var catalog: ParsedCatalog? = null
    private var simulator: EncounterSimulator? = null
    private val loadGeneration = AtomicLong()
    val gateway = CompanionGateway()

    fun load(path: Path) = load(RomSourceLoader.load(path))

    fun load(source: LoadedRom) {
        load(source.displayName, source.rom)
    }

    fun load(name: String, rom: RomImage) {
        val generation = loadGeneration.incrementAndGet()
        synchronized(this) {
            catalog = null
            simulator = null
        }
        gateway.dispatch(
            CompanionAction.CatalogLoadingChanged(
                CatalogLoadingState(active = true, phase = "IDENTIFYING", completedUnits = 0, totalUnits = 5),
                name,
            ),
        )
        parserWorker.execute {
            try {
                val parsed = CatalogParser.parse(
                    rom = rom,
                    onProgress = { progress -> publishProgress(generation, progress) },
                ).catalog
                    ?: error("ROM did not produce a selected mainline-family catalog")
                if (generation != loadGeneration.get()) return@execute
                synchronized(this) {
                    catalog = parsed
                    simulator = EncounterSimulator(parsed)
                }
                gateway.dispatch(CompanionAction.CatalogLoaded(name))
                gateway.dispatch(
                    CompanionAction.CatalogLoadingChanged(
                        CatalogLoadingState(false, "COMPLETE", 5, 5),
                        name,
                    ),
                )
            } catch (failure: Exception) {
                if (generation != loadGeneration.get()) return@execute
                gateway.dispatch(CompanionAction.Failure(failure.message ?: failure.javaClass.simpleName))
                gateway.dispatch(
                    CompanionAction.CatalogLoadingChanged(
                        CatalogLoadingState(false, "FAILED", 0, 5),
                        name,
                    ),
                )
            }
        }
    }

    @Synchronized
    fun loadCatalog(name: String, parsed: ParsedCatalog) {
        loadGeneration.incrementAndGet()
        catalog = parsed
        simulator = EncounterSimulator(parsed)
        gateway.dispatch(CompanionAction.CatalogLoaded(name))
        gateway.dispatch(
            CompanionAction.CatalogLoadingChanged(CatalogLoadingState(false, "CACHE", 5, 5), name),
        )
    }

    @Synchronized
    fun bootstrap(): BootstrapView {
        val current = catalog
        return BootstrapView(current?.let(ApiViewBuilder::catalog), stateView())
    }

    @Synchronized
    fun stateView(): StateView {
        val snapshot = gateway.bootstrap()
        val battle = snapshot.battle
        val truth = if (battle != null) {
            val target = battle.opponents.getOrNull(battle.targetIndex)
            val move = battle.selectedMoveId
            if (target != null && move != null) simulator?.effectiveness(move, target.speciesId) else null
        } else null
        val resolved = resolveRuleset(snapshot.settings.ruleset)
        return ApiViewBuilder.state(snapshot, catalog, truth, resolved?.id, snapshot.settings.ruleset == "AUTO")
    }

    @Synchronized
    fun action(type: String, values: Map<String, String?>): StateView {
        when (type.uppercase()) {
            "GENERATE" -> {
                val active = requireNotNull(simulator) { "load a ROM before generating an encounter" }
                val snapshot = gateway.bootstrap()
                val result = active.generate(
                    SimulationRequest(
                        seed = values["seed"]?.toLongOrNull() ?: 1,
                        opponentCount = values["count"]?.toIntOrNull() ?: 1,
                        minimumLevel = values["minimumLevel"]?.toIntOrNull() ?: 20,
                        maximumLevel = values["maximumLevel"]?.toIntOrNull() ?: 45,
                        captured = values["captured"].toBoolean(),
                        areaId = values["areaId"]?.toIntOrNull(),
                    ),
                    snapshot.ledger,
                    activeRulesetId = generationRulesetId(snapshot.settings.ruleset),
                )
                gateway.dispatch(CompanionAction.ReplaceLedger(result.ledger))
                gateway.dispatch(CompanionAction.BattleStarted(result.battle))
            }
            "END_BATTLE" -> gateway.dispatch(CompanionAction.BattleEnded)
            "OPEN_SPECIES" -> gateway.dispatch(CompanionAction.OpenSpecies(requireInt(values, "speciesId")))
            "OPEN_TRAINER" -> gateway.dispatch(CompanionAction.OpenTrainer)
            "OPEN_PARTY" -> gateway.dispatch(CompanionAction.OpenParty)
            "OPEN_PARTY_MEMBER" -> gateway.dispatch(CompanionAction.OpenPartyMember(requireInt(values, "slot")))
            "BACK" -> gateway.dispatch(CompanionAction.BackToPokedex)
            "SCREEN" -> gateway.dispatch(CompanionAction.SetScreen(AppScreen.valueOf(values["screen"]!!.uppercase())))
            "FILTER" -> gateway.dispatch(
                CompanionAction.SetFilter(PokedexFilter.valueOf(values["filter"]!!.uppercase()), values["areaId"]?.toIntOrNull()),
            )
            "TAB" -> gateway.dispatch(CompanionAction.SetBattleTab(BattleTab.valueOf(values["tab"]!!.uppercase())))
            "TARGET" -> gateway.dispatch(CompanionAction.SelectTarget(requireInt(values, "index")))
            "MOVE" -> gateway.dispatch(CompanionAction.SelectMove(requireInt(values, "moveId")))
            "RESOLVE_ATTACK" -> discoverCurrentMatchup()
            "SETTINGS" -> updateSettings(values)
            else -> error("unknown action: $type")
        }
        return stateView()
    }

    @Synchronized
    fun speciesSprite(id: Int) = catalog?.speciesById?.get(id)?.sprite?.value

    @Synchronized
    fun ballSprite(id: Int) = catalog?.captureBallsById?.get(id)?.sprite?.value

    @Synchronized
    fun mapAsset(
        key: String,
        requestedLighting: MapLighting,
        time: MapTimeOfDay? = null,
    ): RenderedMapAsset? = catalog?.let { current ->
        LocalMapAssetRenderer.render(current.localMaps, key, requestedLighting, time)
            ?: current.worldMaps.assets[key]?.let { RenderedMapAsset(PngEncoder.encode(it), null) }
    }

    @Synchronized
    fun catalogHash(): String? = catalog?.romSha256

    override fun close() {
        parserWorker.shutdown()
    }

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

    private fun discoverCurrentMatchup() {
        val snapshot = gateway.bootstrap()
        val battle = snapshot.battle ?: return
        val target = battle.opponents.getOrNull(battle.targetIndex) ?: return
        val moveId = battle.selectedMoveId ?: return
        val truth = simulator?.effectiveness(moveId, target.speciesId) ?: return
        gateway.dispatch(
            CompanionAction.ReplaceLedger(
                snapshot.ledger.copy(
                    discoveredMatchups = snapshot.ledger.discoveredMatchups + (MatchupKey(target.speciesId, moveId) to truth),
                    knownMoves = snapshot.ledger.knownMoves + moveId,
                ),
            ),
        )
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
        val settings = CompanionSettings(
            knowledgeMode = values["knowledgeMode"]?.let { KnowledgeMode.valueOf(it.uppercase()) } ?: current.knowledgeMode,
            attackEnabled = values["attackEnabled"]?.toBooleanStrictOrNull() ?: current.attackEnabled,
            rarityEnabled = values["rarityEnabled"]?.toBooleanStrictOrNull() ?: current.rarityEnabled,
            movesEnabled = values["movesEnabled"]?.toBooleanStrictOrNull() ?: current.movesEnabled,
            fontScale = values["fontScale"]?.toDoubleOrNull()?.coerceIn(0.85, 1.35) ?: current.fontScale,
            density = values["density"]?.let { Density.valueOf(it.uppercase()) } ?: current.density,
            highContrast = values["highContrast"]?.toBooleanStrictOrNull() ?: current.highContrast,
            autoOpenTarget = values["autoOpenTarget"]?.toBooleanStrictOrNull() ?: current.autoOpenTarget,
            ruleset = ruleset,
        )
        gateway.dispatch(CompanionAction.UpdateSettings(settings))
    }

    private fun resolveRuleset(selection: String) = catalog?.learnsetRulesets?.let { rulesets ->
        if (selection == "AUTO") {
            rulesets.singleOrNull()
        } else {
            rulesets.firstOrNull { it.id == selection }
        }
    }

    private fun generationRulesetId(selection: String): String? {
        val rulesets = catalog?.learnsetRulesets.orEmpty()
        if (rulesets.isEmpty()) return null
        val resolved = resolveRuleset(selection)
        if (selection == "AUTO") {
            require(rulesets.size == 1 && resolved != null) {
                "level-up ruleset is unresolved for this multi-table catalog"
            }
            return resolved.id
        }
        return requireNotNull(resolved) { "unknown catalog ruleset: $selection" }.id
    }

    private fun publishProgress(generation: Long, progress: CatalogMaterializationProgress) {
        if (generation != loadGeneration.get()) return
        synchronized(this) {
            catalog = progress.catalog
            simulator = EncounterSimulator(progress.catalog)
        }
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

    private fun requireInt(values: Map<String, String?>, key: String): Int =
        requireNotNull(values[key]?.toIntOrNull()) { "$key is required" }
}
