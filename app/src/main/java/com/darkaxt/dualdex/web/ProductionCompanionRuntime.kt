package com.darkaxt.dualdex.web

import com.enrpau.dualscreendex.companion.CompanionGateway
import com.enrpau.dualscreendex.companion.api.ApiViewBuilder
import com.enrpau.dualscreendex.companion.api.BootstrapView
import com.enrpau.dualscreendex.companion.api.DiagnosticView
import com.enrpau.dualscreendex.companion.api.StateView
import com.enrpau.dualscreendex.companion.model.AppScreen
import com.enrpau.dualscreendex.companion.model.AppSnapshot
import com.enrpau.dualscreendex.companion.model.CatalogLoadingState
import com.enrpau.dualscreendex.companion.model.CompanionAction
import com.enrpau.dualscreendex.companion.model.CompanionSettings
import com.enrpau.dualscreendex.companion.model.Density
import com.enrpau.dualscreendex.companion.model.KnowledgeMode
import com.enrpau.dualscreendex.companion.model.PokedexFilter
import com.enrpau.dualscreendex.parser.catalog.CatalogMaterializationProgress
import com.enrpau.dualscreendex.parser.catalog.CatalogParser
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
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
) : AutoCloseable {
    private var catalog: ParsedCatalog? = null
    private val loadGeneration = AtomicLong()
    val gateway = CompanionGateway(
        AppSnapshot(
            // Until SaveRAM arrives in Stages 4 and 5, manual browsing opens the complete parsed index.
            settings = CompanionSettings(knowledgeMode = KnowledgeMode.DISCOVERED),
        ),
    )

    fun load(name: String, input: InputStream): BootstrapView = load(RomSourceLoader.load(name, input))

    fun load(source: LoadedRom): BootstrapView {
        load(source.displayName, source.rom)
        return bootstrap()
    }

    fun load(name: String, rom: RomImage) {
        val generation = loadGeneration.incrementAndGet()
        synchronized(this) { catalog = null }
        gateway.dispatch(CompanionAction.SetScreen(AppScreen.POKEDEX))
        gateway.dispatch(
            CompanionAction.CatalogLoadingChanged(
                CatalogLoadingState(active = true, phase = "IDENTIFYING", completedUnits = 0, totalUnits = 5),
                name,
            ),
        )
        parserWorker.execute {
            try {
                val parsed = CatalogParser.parse(rom) { progress -> publishProgress(generation, progress) }.catalog
                    ?: error("ROM did not produce a supported mainline-family catalog")
                if (generation != loadGeneration.get()) return@execute
                synchronized(this) { catalog = parsed }
                gateway.dispatch(CompanionAction.CatalogLoaded(name))
            } catch (failure: Exception) {
                if (generation != loadGeneration.get()) return@execute
                gateway.dispatch(CompanionAction.Failure(failure.message ?: failure.javaClass.simpleName))
                gateway.dispatch(
                    CompanionAction.CatalogLoadingChanged(
                        CatalogLoadingState(active = false, phase = "FAILED", completedUnits = 0, totalUnits = 5),
                    ),
                )
            }
        }
    }

    /** Test and cache-reopen seam; Stage 2 will use this for persisted catalogs. */
    @Synchronized
    fun loadCatalog(name: String, parsed: ParsedCatalog) {
        loadGeneration.incrementAndGet()
        catalog = parsed
        gateway.dispatch(CompanionAction.SetScreen(AppScreen.POKEDEX))
        gateway.dispatch(CompanionAction.CatalogLoaded(name))
    }

    @Synchronized
    fun bootstrap(): BootstrapView {
        val snapshot = gateway.bootstrap()
        return BootstrapView(catalog?.let(ApiViewBuilder::catalog), stateView(snapshot))
    }

    @Synchronized
    fun stateView(snapshot: AppSnapshot = gateway.bootstrap()): StateView {
        val active = resolveRuleset(snapshot.settings.ruleset)
        return ApiViewBuilder.state(
            snapshot,
            catalog,
            activeRulesetId = active?.id,
            rulesetAssumed = snapshot.settings.ruleset == "AUTO",
        )
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
        gateway.dispatch(
            CompanionAction.UpdateSettings(
                CompanionSettings(
                    knowledgeMode = values["knowledgeMode"]?.let { KnowledgeMode.valueOf(it.uppercase()) } ?: current.knowledgeMode,
                    attackEnabled = values["attackEnabled"]?.toBooleanStrictOrNull() ?: current.attackEnabled,
                    rarityEnabled = values["rarityEnabled"]?.toBooleanStrictOrNull() ?: current.rarityEnabled,
                    movesEnabled = values["movesEnabled"]?.toBooleanStrictOrNull() ?: current.movesEnabled,
                    fontScale = values["fontScale"]?.toDoubleOrNull()?.coerceIn(0.85, 1.35) ?: current.fontScale,
                    density = values["density"]?.let { Density.valueOf(it.uppercase()) } ?: current.density,
                    highContrast = values["highContrast"]?.toBooleanStrictOrNull() ?: current.highContrast,
                    autoOpenTarget = values["autoOpenTarget"]?.toBooleanStrictOrNull() ?: current.autoOpenTarget,
                    ruleset = ruleset,
                ),
            ),
        )
    }

    private fun resolveRuleset(selection: String) = catalog?.learnsetRulesets?.let { rulesets ->
        if (selection == "AUTO") rulesets.firstOrNull { it.primary } ?: rulesets.firstOrNull()
        else rulesets.firstOrNull { it.id == selection }
    }

    private fun publishProgress(generation: Long, progress: CatalogMaterializationProgress) {
        if (generation != loadGeneration.get()) return
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

    private fun requireInt(values: Map<String, String?>, key: String): Int =
        requireNotNull(values[key]?.toIntOrNull()) { "$key is required" }
}
