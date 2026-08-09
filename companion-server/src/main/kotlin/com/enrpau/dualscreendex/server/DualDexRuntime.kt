package com.enrpau.dualscreendex.server

import com.enrpau.dualscreendex.companion.CompanionGateway
import com.enrpau.dualscreendex.companion.model.AppScreen
import com.enrpau.dualscreendex.companion.model.BattleTab
import com.enrpau.dualscreendex.companion.model.CompanionAction
import com.enrpau.dualscreendex.companion.model.CompanionSettings
import com.enrpau.dualscreendex.companion.model.Density
import com.enrpau.dualscreendex.companion.model.Effectiveness
import com.enrpau.dualscreendex.companion.model.KnowledgeMode
import com.enrpau.dualscreendex.companion.model.MatchupKey
import com.enrpau.dualscreendex.companion.model.PokedexFilter
import com.enrpau.dualscreendex.parser.catalog.CatalogParser
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.simulator.EncounterSimulator
import com.enrpau.dualscreendex.simulator.SimulationRequest
import java.nio.file.Path

class DualDexRuntime {
    private var catalog: ParsedCatalog? = null
    private var simulator: EncounterSimulator? = null
    val gateway = CompanionGateway()

    @Synchronized
    fun load(path: Path) = load(RomSourceLoader.load(path))

    @Synchronized
    fun load(source: LoadedRom) {
        load(source.displayName, source.rom)
    }

    @Synchronized
    fun load(name: String, rom: RomImage) {
        val parsed = CatalogParser.parse(rom).catalog
            ?: error("ROM did not produce a selected mainline-family catalog")
        catalog = parsed
        simulator = EncounterSimulator(parsed)
        gateway.dispatch(CompanionAction.CatalogLoaded(name))
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
        return ApiViewBuilder.state(snapshot, catalog, truth)
    }

    @Synchronized
    fun action(type: String, values: Map<String, String?>): StateView {
        when (type.uppercase()) {
            "GENERATE" -> {
                val active = requireNotNull(simulator) { "load a ROM before generating an encounter" }
                val result = active.generate(
                    SimulationRequest(
                        seed = values["seed"]?.toLongOrNull() ?: 1,
                        opponentCount = values["count"]?.toIntOrNull() ?: 1,
                        minimumLevel = values["minimumLevel"]?.toIntOrNull() ?: 20,
                        maximumLevel = values["maximumLevel"]?.toIntOrNull() ?: 45,
                        captured = values["captured"].toBoolean(),
                        areaId = values["areaId"]?.toIntOrNull(),
                    ),
                    gateway.bootstrap().ledger,
                )
                gateway.dispatch(CompanionAction.ReplaceLedger(result.ledger))
                gateway.dispatch(CompanionAction.BattleStarted(result.battle))
            }
            "END_BATTLE" -> gateway.dispatch(CompanionAction.BattleEnded)
            "OPEN_SPECIES" -> gateway.dispatch(CompanionAction.OpenSpecies(requireInt(values, "speciesId")))
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
    fun catalogHash(): String? = catalog?.romSha256

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
        val settings = CompanionSettings(
            knowledgeMode = values["knowledgeMode"]?.let { KnowledgeMode.valueOf(it.uppercase()) } ?: current.knowledgeMode,
            attackEnabled = values["attackEnabled"]?.toBooleanStrictOrNull() ?: current.attackEnabled,
            rarityEnabled = values["rarityEnabled"]?.toBooleanStrictOrNull() ?: current.rarityEnabled,
            movesEnabled = values["movesEnabled"]?.toBooleanStrictOrNull() ?: current.movesEnabled,
            fontScale = values["fontScale"]?.toDoubleOrNull()?.coerceIn(0.85, 1.35) ?: current.fontScale,
            density = values["density"]?.let { Density.valueOf(it.uppercase()) } ?: current.density,
            highContrast = values["highContrast"]?.toBooleanStrictOrNull() ?: current.highContrast,
            autoOpenTarget = values["autoOpenTarget"]?.toBooleanStrictOrNull() ?: current.autoOpenTarget,
        )
        gateway.dispatch(CompanionAction.UpdateSettings(settings))
    }

    private fun requireInt(values: Map<String, String?>, key: String): Int =
        requireNotNull(values[key]?.toIntOrNull()) { "$key is required" }
}
