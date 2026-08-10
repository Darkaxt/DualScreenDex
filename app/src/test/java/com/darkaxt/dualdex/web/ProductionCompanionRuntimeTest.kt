package com.darkaxt.dualdex.web

import com.darkaxt.dualdex.catalog.CatalogRepository
import com.darkaxt.dualdex.catalog.CatalogSourceMetadata
import com.darkaxt.dualdex.catalog.CatalogWriteProgress
import com.darkaxt.dualdex.catalog.StoredCatalog
import com.darkaxt.dualdex.knowledge.KnowledgeRepository
import com.enrpau.dualscreendex.companion.api.RetroArchView
import com.enrpau.dualscreendex.companion.api.SaveRamView
import com.enrpau.dualscreendex.companion.model.CompanionSettings
import com.enrpau.dualscreendex.companion.model.DisplayTarget
import com.enrpau.dualscreendex.companion.model.KnowledgeMode
import com.enrpau.dualscreendex.companion.model.Theme
import com.darkaxt.dualdex.save.OwnedIndividual
import com.darkaxt.dualdex.save.SaveSnapshot
import com.darkaxt.dualdex.save.SavedArea
import com.enrpau.dualscreendex.parser.catalog.CatalogField
import com.enrpau.dualscreendex.parser.catalog.LearnsetRuleset
import com.enrpau.dualscreendex.parser.catalog.SpeciesRecord
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.enrpau.dualscreendex.parser.catalog.MoveRecord
import com.enrpau.dualscreendex.parser.catalog.TypeRecord
import com.enrpau.dualscreendex.parser.catalog.TypeMatchup
import com.enrpau.dualscreendex.parser.io.LoadedRom
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.assertSame
import org.junit.Test
import java.util.Collections
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import com.darkaxt.dualdex.battle.BattleMemorySample
import com.darkaxt.dualdex.battle.BattleMatchupObservation
import com.darkaxt.dualdex.battle.BattleMonSnapshot
import com.darkaxt.dualdex.battle.BattleTarget
import com.darkaxt.dualdex.battle.BattleTrackingUpdate
import com.darkaxt.dualdex.battle.ResolvedBattleLayout
import com.darkaxt.dualdex.battle.TargetMode

class ProductionCompanionRuntimeTest {
    @Test
    fun organicEffectivenessUnlocksAfterThePlayerConsumesMovePpAgainstTheTarget() {
        val runtime = ProductionCompanionRuntime()
        runtime.loadCatalog("fixture.gba", ParsedCatalog(
            "sha", EngineFamily.EMERALD, Platform.GBA,
            speciesById = mapOf(13 to SpeciesRecord(
                id = 13, dexNumber = CatalogField.available(13), name = CatalogField.available("WEEDLE"),
                typeIds = CatalogField.available(listOf(6)), baseStats = CatalogField.notFound("fixture"),
                sprite = CatalogField.notFound("fixture"), abilityIds = CatalogField.available(emptyList()),
            )),
            movesById = mapOf(10 to MoveRecord(
                10, CatalogField.available("THUNDER WAVE"), CatalogField.available(13), CatalogField.notFound("fixture"),
                CatalogField.available(0), CatalogField.available(100), CatalogField.available(20),
            )),
            typesById = mapOf(
                6 to TypeRecord(6, CatalogField.available("BUG")),
                13 to TypeRecord(13, CatalogField.available("ELECTRIC")),
            ),
            typeChart = listOf(TypeMatchup(13, 6, 0)),
        ))
        val opponent = BattleMonSnapshot(
            battlerIndex = 1, position = 1, speciesId = 13, level = 3, hp = 15, maxHp = 15,
            ivs = List(6) { 15 }, moves = listOf(40, 81, 0, 0), pp = listOf(35, 40, 0, 0),
            typeIds = listOf(6, 6), abilityId = 19, personality = 200,
        )
        val sample = BattleMemorySample(
            layout = ResolvedBattleLayout(0x143C, 0x1420, 0x142C, 0x16EE, 0x1874, 0x1878, 2),
            battlers = listOf(opponent), opponents = listOf(opponent), selectedMoveId = 10,
            target = BattleTarget(0, TargetMode.AUTOMATIC), capabilities = emptyMap(),
        )

        runtime.applyBattleTracking(BattleTrackingUpdate(true, sample))
        assertFalse(runtime.stateView().battle!!.effectivenessKnown)

        runtime.applyBattleTracking(BattleTrackingUpdate(
            true,
            sample,
            discoveredMatchups = setOf(BattleMatchupObservation(13, 10, listOf(6, 6))),
        ))

        assertTrue(runtime.stateView().battle!!.effectivenessKnown)
        assertEquals("NO_EFFECT", runtime.stateView().battle!!.effectiveness)
        runtime.close()
    }

    @Test
    fun publishesLiveBattleObservationsAndAcceptsProductionBattleActions() {
        val runtime = ProductionCompanionRuntime()
        runtime.loadCatalog("fixture.gba", ParsedCatalog(
            "sha", EngineFamily.EMERALD, Platform.GBA,
            speciesById = mapOf(1 to SpeciesRecord(
                id = 1, dexNumber = CatalogField.available(1), name = CatalogField.available("A"),
                typeIds = CatalogField.available(listOf(0)), baseStats = CatalogField.notFound("fixture"),
                sprite = CatalogField.notFound("fixture"), abilityIds = CatalogField.available(emptyList()),
            )),
            movesById = mapOf(1 to MoveRecord(
                1, CatalogField.available("MOVE"), CatalogField.available(0), CatalogField.notFound("fixture"),
                CatalogField.available(40), CatalogField.available(100), CatalogField.available(35),
            )),
            typesById = mapOf(0 to TypeRecord(0, CatalogField.available("NORMAL"))),
        ))
        val opponent = BattleMonSnapshot(
            battlerIndex = 1, position = 1, speciesId = 13, level = 3, hp = 15, maxHp = 15,
            ivs = listOf(10, 11, 12, 13, 14, 15), moves = listOf(40, 81, 0, 0), pp = listOf(34, 40, 0, 0),
            typeIds = listOf(6, 3), abilityId = 19, personality = 200,
        )
        val sample = BattleMemorySample(
            layout = ResolvedBattleLayout(0x143C, 0x1420, 0x142C, 0x16EE, 0x1874, 0x1878, 2),
            battlers = listOf(opponent), opponents = listOf(opponent), selectedMoveId = 10,
            target = BattleTarget(0, TargetMode.MANUAL_TARGET_FALLBACK), capabilities = emptyMap(),
        )

        runtime.applyBattleTracking(
            BattleTrackingUpdate(true, sample, observations = mapOf(13 to mapOf(40 to 2))),
        )

        var snapshot = runtime.gateway.bootstrap()
        assertEquals(13, snapshot.battle?.opponents?.single()?.speciesId)
        assertEquals(2, snapshot.ledger.observedMoves.getValue(13).single().frequency)
        assertEquals("MANUAL_TARGET_FALLBACK", snapshot.battle?.targetMode?.name)
        runtime.action("BATTLE_TAB", mapOf("tab" to "MOVES"))
        runtime.action("SELECT_TARGET", mapOf("index" to "0"))
        snapshot = runtime.gateway.bootstrap()
        assertEquals("MOVES", snapshot.battleTab.name)

        runtime.applyBattleTracking(BattleTrackingUpdate(false, null, ended = true))
        assertNull(runtime.gateway.bootstrap().battle)
        runtime.close()
    }

    @Test
    fun exposesGen1GbAndGen3GbaCatalogsAsProductionBattleContexts() {
        val runtime = ProductionCompanionRuntime()
        runtime.loadCatalog("fixture.gba", ParsedCatalog(
            "sha", EngineFamily.EMERALD, Platform.GBA,
            speciesById = mapOf(1 to SpeciesRecord(
                id = 1, dexNumber = CatalogField.available(1), name = CatalogField.available("A"),
                typeIds = CatalogField.available(listOf(0)), baseStats = CatalogField.notFound("fixture"),
                sprite = CatalogField.notFound("fixture"), abilityIds = CatalogField.available(emptyList()),
            )),
            movesById = mapOf(1 to MoveRecord(
                1, CatalogField.available("MOVE"), CatalogField.available(0), CatalogField.notFound("fixture"),
                CatalogField.available(40), CatalogField.available(100), CatalogField.available(35),
            )),
            typesById = mapOf(0 to TypeRecord(0, CatalogField.available("NORMAL"))),
        ))
        assertEquals("sha", runtime.battleCatalogContext()?.romIdentity)
        assertEquals(3, runtime.battleCatalogContext()?.generation)

        runtime.loadCatalog("fixture.gb", ParsedCatalog(
            "yellow", EngineFamily.YELLOW, Platform.GB,
            speciesById = mapOf(0x54 to SpeciesRecord(
                id = 0x54, dexNumber = CatalogField.available(25), name = CatalogField.available("PIKACHU"),
                typeIds = CatalogField.available(listOf(0x17)), baseStats = CatalogField.notFound("fixture"),
                sprite = CatalogField.notFound("fixture"), abilityIds = CatalogField.notApplicable("Gen 1"),
            )),
            movesById = mapOf(0x54 to MoveRecord(
                0x54, CatalogField.available("THUNDERSHOCK"), CatalogField.available(0x17), CatalogField.notFound("fixture"),
                CatalogField.available(40), CatalogField.available(100), CatalogField.available(30),
            )),
            typesById = mapOf(0x17 to TypeRecord(0x17, CatalogField.available("ELECTRIC"))),
        ))
        assertEquals("yellow", runtime.battleCatalogContext()?.romIdentity)
        assertEquals(1, runtime.battleCatalogContext()?.generation)

        runtime.loadCatalog("fixture.gbc", ParsedCatalog("gbc", EngineFamily.CRYSTAL, Platform.GBC))
        assertNull(runtime.battleCatalogContext())
        runtime.close()
    }

    @Test
    fun persistsBattleKnowledgeAndRestoresItWhenTheSameRomReopens() {
        val identity = "e".repeat(64)
        val repository = InMemoryKnowledgeRepository()
        val catalog = ParsedCatalog(identity, EngineFamily.YELLOW, Platform.GB)
        val first = ProductionCompanionRuntime(knowledgeRepository = repository)
        first.loadCatalog("yellow.gb", catalog)
        first.applyBattleTracking(
            BattleTrackingUpdate(
                active = false,
                sample = null,
                observations = mapOf(0x66 to mapOf(0x21 to 2)),
                ended = true,
            ),
        )
        first.close()

        val reopened = ProductionCompanionRuntime(knowledgeRepository = repository)
        reopened.loadCatalog("yellow.gb", catalog)

        assertEquals(2, reopened.gateway.bootstrap().ledger.observedMoves.getValue(0x66).single().frequency)
        reopened.close()
    }
    @Test
    fun reusesAnUnchangedPresentationSnapshotForPollingClients() {
        val runtime = ProductionCompanionRuntime(parserWorker = ImmediateExecutorService())
        runtime.loadCatalog("fixture.gba", ParsedCatalog("sha", EngineFamily.EMERALD, Platform.GBA))

        val first = runtime.stateView()
        val second = runtime.stateView()

        assertSame(first, second)
        runtime.close()
    }
    @Test
    fun exposesRealCatalogWithoutSimulatorActions() {
        val runtime = ProductionCompanionRuntime()
        runtime.loadCatalog(
            "fixture.gba",
            ParsedCatalog("sha", EngineFamily.EMERALD, Platform.GBA, romCrc32 = "1234ABCD"),
        )

        val bootstrap = runtime.bootstrap()

        assertEquals("1234ABCD", bootstrap.catalog?.crc32)
        assertEquals("fixture.gba", bootstrap.state.catalogName)
        assertNull(bootstrap.state.battle)
        assertThrows(IllegalArgumentException::class.java) {
            runtime.action("GENERATE", emptyMap())
        }
        runtime.close()
    }

    @Test
    fun restoresACompletedCatalogWithoutReadingTheRomAgain() {
        val catalog = ParsedCatalog(
            "a".repeat(64),
            EngineFamily.EMERALD,
            Platform.GBA,
            romCrc32 = "89ABCDEF",
        )
        val source = CatalogSourceMetadata.direct("Modern Emerald.gba", 16_777_216, "POKEMON EMER")
        val repository = FakeCatalogRepository(
            StoredCatalog(
                catalog,
                source,
                CatalogWriteProgress.complete(),
                committedSections = emptySet(),
                writtenAtEpochMs = 1,
            ),
        )
        val runtime = ProductionCompanionRuntime(catalogRepository = repository)

        assertTrue(runtime.restoreCatalog(catalog.romSha256))
        assertFalse(runtime.restoreCatalog("b".repeat(64)))
        val restored = runtime.bootstrap()

        assertEquals("Modern Emerald.gba", restored.state.catalogName)
        assertEquals("CACHE_REOPEN", restored.state.loading.phase)
        assertEquals(catalog.romSha256, restored.catalog?.hash)
        runtime.close()
    }

    @Test
    fun doesNotExposeSaveParsingContextWhileAStoredCatalogIsStillBeingPublished() {
        val hash = "a".repeat(64)
        val catalog = ParsedCatalog(hash, EngineFamily.EMERALD, Platform.GBA)
        val runtime = ProductionCompanionRuntime(
            catalogRepository = FakeCatalogRepository(
                StoredCatalog(
                    catalog,
                    CatalogSourceMetadata.direct("fixture.gba", 1, "FIXTURE"),
                    CatalogWriteProgress.complete(),
                    committedSections = emptySet(),
                    writtenAtEpochMs = 1,
                ),
            ),
        )
        var observedPublication = false
        var contextDuringPublication: com.darkaxt.dualdex.save.SaveParseContext? = null
        val subscription = runtime.gateway.subscribe {
            if (!observedPublication) {
                observedPublication = true
                contextDuringPublication = runtime.saveParseContext()
            }
        }

        assertTrue(runtime.restoreCatalog(hash))

        assertTrue(observedPublication)
        assertNull(contextDuringPublication)
        assertEquals(hash, runtime.saveParseContext()?.romIdentity)
        subscription.close()
        runtime.close()
    }

    @Test
    fun exposesRetroArchSetupAndSessionStateWithoutRequiringACatalog() {
        val runtime = ProductionCompanionRuntime()
        runtime.updateRetroArch(
            RetroArchView(
                storageGrant = "GRANTED",
                configGrant = "GRANTED",
                romGrant = "GRANTED",
                configState = "RESTART_REQUIRED",
                restartRequired = true,
                connection = "DISCONNECTED",
                indexedRoms = 14,
                message = "Restart RetroArch to verify Network Commands.",
            ),
        )

        val state = runtime.stateView()

        assertEquals("GRANTED", state.retroArch.storageGrant)
        assertEquals("GRANTED", state.retroArch.configGrant)
        assertEquals(14, state.retroArch.indexedRoms)
        assertTrue(state.retroArch.restartRequired)
        runtime.close()
    }

    @Test
    fun updatesTheOptionalDisplayModeThroughTheNormalSettingsContract() {
        val runtime = ProductionCompanionRuntime()

        val state = runtime.action("SETTINGS", mapOf("displayMode" to "OVERLAY"))

        assertEquals("OVERLAY", state.settings.let { it as com.enrpau.dualscreendex.companion.model.CompanionSettings }.displayMode.name)
        runtime.close()
    }

    @Test
    fun startsFromAndPersistsTheCompleteSettingsDocument() {
        var persisted: CompanionSettings? = null
        val runtime = ProductionCompanionRuntime(
            initialSettings = CompanionSettings(knowledgeMode = KnowledgeMode.DISCOVERED, theme = Theme.DARK),
            onSettingsChanged = { persisted = it },
        )

        val state = runtime.action(
            "SETTINGS",
            mapOf("displayTarget" to "EXTERNAL", "theme" to "LIGHT", "fontScale" to "1.2"),
        )

        val settings = state.settings as CompanionSettings
        assertEquals(KnowledgeMode.DISCOVERED, settings.knowledgeMode)
        assertEquals(DisplayTarget.EXTERNAL, settings.displayTarget)
        assertEquals(Theme.LIGHT, settings.theme)
        assertEquals(1.2, settings.fontScale, 0.0)
        assertEquals(settings, persisted)
        runtime.close()
    }

    @Test
    fun switchingRulesetsReusesTheOpenCatalogAndSaveSnapshotWithoutDatabaseWrites() {
        val repository = RecordingCatalogRepository()
        val runtime = ProductionCompanionRuntime(catalogRepository = repository)
        val hash = "a".repeat(64)
        runtime.loadCatalog(
            "fixture.gba",
            ParsedCatalog(
                hash,
                EngineFamily.EMERALD,
                Platform.GBA,
                learnsetRulesets = listOf(
                    LearnsetRuleset("base", "Base", 1, 1.0, emptyMap(), primary = true),
                    LearnsetRuleset("alternate", "Alternate", 2, 0.9, emptyMap()),
                ),
            ),
        )
        runtime.updateSaveRam(SaveRamView(status = "MATCHED", sourceName = "fixture.srm"))

        val state = runtime.action("SETTINGS", mapOf("ruleset" to "alternate"))

        assertEquals(hash, runtime.catalogHash())
        assertEquals("alternate", state.activeRulesetId)
        assertEquals("MATCHED", state.saveRam.status)
        assertEquals("fixture.srm", state.saveRam.sourceName)
        assertEquals(0, repository.writeCalls)
        runtime.close()
    }

    @Test
    fun reportsAutomaticCatalogActivationOnlyAfterTheVerifiedCatalogIsOpen() {
        val bytes = ByteArray(0xC0)
        "POKEMON EMER".toByteArray().copyInto(bytes, 0xA0)
        "BPEE".toByteArray().copyInto(bytes, 0xAC)
        val rom = RomImage(bytes)
        val catalog = ParsedCatalog(
            rom.sha256,
            EngineFamily.EMERALD,
            Platform.GBA,
            romCrc32 = rom.crc32,
        )
        val repository = FakeCatalogRepository(
            StoredCatalog(
                catalog,
                CatalogSourceMetadata.direct("Modern Emerald.gba", rom.size, "POKEMON EMER"),
                CatalogWriteProgress.complete(),
                committedSections = emptySet(),
                writtenAtEpochMs = 1,
            ),
        )
        val runtime = ProductionCompanionRuntime(
            parserWorker = ImmediateExecutorService(),
            catalogRepository = repository,
        )
        var completion: Result<Unit>? = null

        runtime.load(LoadedRom("Modern Emerald.gba", rom)) { completion = it }

        assertTrue(requireNotNull(completion).isSuccess)
        assertEquals(rom.sha256, runtime.catalogHash())
        assertEquals("Modern Emerald.gba", runtime.bootstrap().state.catalogName)
        runtime.close()
    }

    @Test
    fun exposesCatalogCoupledSaveContextAndPublishesOneValidatedSnapshot() {
        val hash = "a".repeat(64)
        val runtime = ProductionCompanionRuntime()
        runtime.loadCatalog(
            "fixture.gba",
            ParsedCatalog(
                hash,
                EngineFamily.EMERALD,
                Platform.GBA,
                speciesById = mapOf(
                    25 to SpeciesRecord(
                        id = 25,
                        dexNumber = CatalogField.available(25),
                        name = CatalogField.available("PIKACHU"),
                        typeIds = CatalogField.available(emptyList()),
                        baseStats = CatalogField.notFound("fixture"),
                        sprite = CatalogField.notFound("fixture"),
                        growthRate = CatalogField.available(0),
                    ),
                ),
            ),
        )
        val context = requireNotNull(runtime.saveParseContext())
        assertEquals(25, context.speciesById.getValue(25).dexNumber)
        val snapshot = SaveSnapshot(
            romIdentity = hash,
            saveIdentity = "save",
            saveGeneration = 3,
            saveCounter = 2,
            currentArea = SavedArea(2, 3),
            seenDexNumbers = setOf(25),
            caughtDexNumbers = setOf(25),
            party = listOf(OwnedIndividual("party-0", 25, level = 12, ivs = List(6) { 31 }, captureBallId = 4)),
            storedIndividuals = emptyList(),
            capabilities = emptyMap(),
        )

        assertTrue(runtime.applySaveSnapshot(snapshot, SaveRamView(status = "MATCHED", sourceName = "fixture.srm")))

        val state = runtime.stateView()
        assertTrue(state.speciesState.getValue(25).caught)
        assertEquals("MATCHED", state.saveRam.status)
        assertEquals("fixture.srm", state.saveRam.sourceName)
        runtime.close()
    }

    private class FakeCatalogRepository(private val stored: StoredCatalog) : CatalogRepository {
        override fun write(
            catalog: ParsedCatalog,
            source: CatalogSourceMetadata,
            progress: CatalogWriteProgress,
        ) = Unit

        override fun readComplete(sha256: String): StoredCatalog? = stored.takeIf { it.catalog.romSha256 == sha256 }

        override fun findCompleted(crc32: String, romSize: Int, romTitle: String?): List<StoredCatalog> = emptyList()
    }

    private class RecordingCatalogRepository : CatalogRepository {
        var writeCalls = 0

        override fun write(
            catalog: ParsedCatalog,
            source: CatalogSourceMetadata,
            progress: CatalogWriteProgress,
        ) {
            writeCalls++
        }

        override fun readComplete(sha256: String): StoredCatalog? = null

        override fun findCompleted(crc32: String, romSize: Int, romTitle: String?): List<StoredCatalog> = emptyList()
    }

    private class InMemoryKnowledgeRepository : KnowledgeRepository {
        private val documents = mutableMapOf<String, com.enrpau.dualscreendex.companion.model.KnowledgeLedger>()

        override fun read(romIdentity: String) = documents[romIdentity]

        override fun write(
            romIdentity: String,
            ledger: com.enrpau.dualscreendex.companion.model.KnowledgeLedger,
        ) {
            documents[romIdentity] = ledger
        }
    }

    private class ImmediateExecutorService : AbstractExecutorService() {
        private var closed = false

        override fun execute(command: Runnable) = command.run()

        override fun shutdown() {
            closed = true
        }

        override fun shutdownNow(): MutableList<Runnable> {
            closed = true
            return Collections.emptyList()
        }

        override fun isShutdown(): Boolean = closed

        override fun isTerminated(): Boolean = closed

        override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = closed
    }
}
