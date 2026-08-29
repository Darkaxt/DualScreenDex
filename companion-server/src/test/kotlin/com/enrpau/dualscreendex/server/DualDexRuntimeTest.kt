package com.enrpau.dualscreendex.server

import com.enrpau.dualscreendex.parser.catalog.LearnsetRuleset
import com.enrpau.dualscreendex.parser.catalog.BaseStats
import com.enrpau.dualscreendex.parser.catalog.CatalogMaterializationPhase
import com.enrpau.dualscreendex.parser.catalog.CatalogMaterializationProgress
import com.enrpau.dualscreendex.parser.catalog.CatalogField
import com.enrpau.dualscreendex.parser.catalog.LearnsetEntry
import com.enrpau.dualscreendex.parser.catalog.MoveCategory
import com.enrpau.dualscreendex.parser.catalog.MoveRecord
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.enrpau.dualscreendex.parser.catalog.RgbaSprite
import com.enrpau.dualscreendex.parser.catalog.SpeciesRecord
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.io.LoadedRom
import com.enrpau.dualscreendex.parser.io.RomImage
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DualDexRuntimeTest {
    @Test
    fun repeatedCapturedEncountersRemainUniqueAcrossCatalogReloads() {
        val runtime = DualDexRuntime()
        val catalog = simulationCatalog(multiTable = false)
        runtime.loadCatalog("fixture.gba", catalog)
        runtime.action("GENERATE", mapOf("seed" to "1", "captured" to "true"))

        runtime.loadCatalog("fixture.gba", catalog)
        runtime.action("GENERATE", mapOf("seed" to "1", "captured" to "true"))

        val keys = runtime.gateway.bootstrap().ledger.owned.map { it.stableKey }
        assertEquals(keys.size, keys.distinct().size)
        runtime.close()
    }

    @Test
    fun autoRemainsUnresolvedWithoutSaveDetectionAndManualSelectionOnlyChangesActiveId() {
        val runtime = DualDexRuntime()
        runtime.loadCatalog(
            "fixture.gba",
            ParsedCatalog(
                "sha",
                EngineFamily.EMERALD,
                Platform.GBA,
                learnsetRulesets = listOf(
                    LearnsetRuleset("base", "Base", 100, 1.0, emptyMap(), primary = true),
                    LearnsetRuleset("modern", "Expanded", 200, 1.0, emptyMap()),
                ),
            ),
        )

        assertEquals(null, runtime.stateView().activeRulesetId)
        assertTrue(runtime.stateView().rulesetAssumed)

        val selected = runtime.action("SETTINGS", mapOf("ruleset" to "modern"))
        assertEquals("modern", selected.activeRulesetId)
        assertEquals(false, selected.rulesetAssumed)

        assertThrows(IllegalArgumentException::class.java) {
            runtime.action("SETTINGS", mapOf("ruleset" to "missing"))
        }
        assertEquals("modern", runtime.stateView().activeRulesetId)
    }

    @Test
    fun generateFailsClosedForUnresolvedMultiTableAutoButAllowsManualSingleAndNoRulesetCases() {
        val runtime = DualDexRuntime()
        runtime.loadCatalog("multi.gba", simulationCatalog(multiTable = true))

        val unresolved = assertThrows(IllegalArgumentException::class.java) {
            runtime.action("GENERATE", mapOf("seed" to "1", "minimumLevel" to "10", "maximumLevel" to "10"))
        }
        assertEquals("level-up ruleset is unresolved for this multi-table catalog", unresolved.message)

        runtime.action("SETTINGS", mapOf("ruleset" to "modern"))
        val manual = runtime.action("GENERATE", mapOf("seed" to "1", "minimumLevel" to "10", "maximumLevel" to "10"))
        assertEquals(listOf(2), manual.battle?.opponents?.single()?.moves?.map { it.moveId })

        runtime.loadCatalog("changed.gba", simulationCatalog(multiTable = false))
        val staleManual = assertThrows(IllegalArgumentException::class.java) {
            runtime.action("GENERATE", mapOf("seed" to "1"))
        }
        assertEquals("unknown catalog ruleset: modern", staleManual.message)
        runtime.close()

        val single = DualDexRuntime()
        single.loadCatalog("single.gba", simulationCatalog(multiTable = false))
        assertTrue(single.action("GENERATE", mapOf("seed" to "1")).battle != null)
        single.close()

        val none = DualDexRuntime()
        none.loadCatalog("none.gba", simulationCatalog(multiTable = false).copy(learnsetRulesets = emptyList()))
        assertTrue(none.action("GENERATE", mapOf("seed" to "1")).battle != null)
        none.close()
    }

    @Test
    fun parserFailuresNeverPublishPartialAuthorityAndLaterLoadsRecover() {
        val valid = simulationCatalog(multiTable = false).copy(romSha256 = "valid-catalog")
        listOf(
            false to IllegalStateException("ordinary parser detail"),
            true to IllegalStateException("ordinary parser detail"),
            false to OutOfMemoryError("allocator detail"),
            true to OutOfMemoryError("allocator detail"),
        ).forEach { (publishProgress, failure) ->
            val enteredFailure = CountDownLatch(1)
            val releaseFailure = CountDownLatch(1)
            val terminalFailure = CountDownLatch(1)
            val completed = CountDownLatch(1)
            val attempts = AtomicInteger()
            val runtime = DualDexRuntime(
                catalogParser = { _, _, progress ->
                    if (attempts.getAndIncrement() == 0) {
                        if (publishProgress) {
                            progress(
                                CatalogMaterializationProgress(
                                    CatalogMaterializationPhase.ESSENTIAL,
                                    completedUnits = 1,
                                    totalUnits = 5,
                                    catalog = valid.copy(romSha256 = "partial-catalog"),
                                ),
                            )
                        }
                        enteredFailure.countDown()
                        assertTrue(releaseFailure.await(1, TimeUnit.SECONDS))
                        throw failure
                    }
                    valid
                },
            )
            val subscription = runtime.gateway.subscribe { snapshot ->
                if (snapshot.catalogLoading.phase == "FAILED") terminalFailure.countDown()
                if (snapshot.catalogLoading.phase == "COMPLETE") completed.countDown()
            }
            try {
                runtime.load("untrusted.gba", RomImage(byteArrayOf()))
                assertTrue(enteredFailure.await(1, TimeUnit.SECONDS))

                val checkpoint = runtime.bootstrap()
                assertNull(checkpoint.catalog)
                assertFalse(checkpoint.state.catalogReady)
                assertNull(checkpoint.state.catalogHash)
                assertFalse(checkpoint.state.mapperAvailable)
                assertThrows(IllegalArgumentException::class.java) {
                    runtime.action("GENERATE", mapOf("seed" to "1"))
                }

                releaseFailure.countDown()
                assertTrue(terminalFailure.await(1, TimeUnit.SECONDS))
                val failed = runtime.stateView()
                assertEquals("This game guide could not be opened. You can try again.", failed.error)
                assertEquals("FAILED", failed.loading.phase)
                assertFalse(failed.loading.active)
                assertFalse(failed.error.orEmpty().contains("detail"))
                assertNull(runtime.bootstrap().catalog)

                runtime.load("valid.gba", RomImage(byteArrayOf()))
                assertTrue(completed.await(1, TimeUnit.SECONDS))
                assertEquals("valid-catalog", runtime.bootstrap().catalog?.hash)
                assertEquals("valid-catalog", runtime.stateView().catalogHash)
            } finally {
                subscription.close()
                runtime.close()
            }
        }
    }

    @Test
    fun sourceMaterializationFailuresAreSanitizedAtManualAndStartupBoundaries() {
        val valid = simulationCatalog(multiTable = false).copy(romSha256 = "recovered-catalog")
        val completed = CountDownLatch(1)
        val runtime = DualDexRuntime(
            catalogParser = { _, _, _ -> valid },
            romSourceLoader = { name, _ ->
                when (name) {
                    "startup.gba" -> throw OutOfMemoryError("startup allocator detail")
                    "manual.gba" -> throw IllegalStateException("manual source detail")
                    else -> LoadedRom(name, RomImage(byteArrayOf()))
                }
            },
        )
        val subscription = runtime.gateway.subscribe { snapshot ->
            if (snapshot.catalogLoading.phase == "COMPLETE") completed.countDown()
        }
        val startupRom = Files.createTempDirectory("dualdex-startup-rom").resolve("startup.gba")
        Files.write(startupRom, byteArrayOf(1))
        try {
            runtime.load(startupRom)
            assertEquals("FAILED", runtime.stateView().loading.phase)
            assertEquals("This game guide could not be opened. You can try again.", runtime.stateView().error)
            assertFalse(runtime.stateView().error.orEmpty().contains("startup allocator"))

            runtime.load("manual.gba", ByteArrayInputStream(byteArrayOf(1)))
            assertEquals("FAILED", runtime.stateView().loading.phase)
            assertEquals("This game guide could not be opened. You can try again.", runtime.stateView().error)
            assertFalse(runtime.stateView().error.orEmpty().contains("manual source"))

            runtime.load("retry.gba", ByteArrayInputStream(byteArrayOf(1)))
            assertTrue(completed.await(1, TimeUnit.SECONDS))
            assertEquals("recovered-catalog", runtime.bootstrap().catalog?.hash)
        } finally {
            subscription.close()
            runtime.close()
            Files.deleteIfExists(startupRom)
            Files.deleteIfExists(startupRom.parent)
        }
    }

    private fun simulationCatalog(multiTable: Boolean): ParsedCatalog {
        val sprite = RgbaSprite(1, 1, intArrayOf(0xFFFFFFFF.toInt()))
        val species = SpeciesRecord(
            id = 1,
            dexNumber = CatalogField.available(1),
            name = CatalogField.available("SPECIES"),
            typeIds = CatalogField.available(listOf(1)),
            baseStats = CatalogField.available(BaseStats(50, 50, 50, 50, 50, 50)),
            sprite = CatalogField.available(sprite),
            learnset = CatalogField.available(listOf(LearnsetEntry(5, 1))),
        )
        val moves = (1..2).associateWith { id ->
            MoveRecord(
                id,
                CatalogField.available("MOVE$id"),
                CatalogField.available(1),
                CatalogField.available(MoveCategory.PHYSICAL),
                CatalogField.available(40),
                CatalogField.available(100),
                CatalogField.available(35),
            )
        }
        val rulesets = listOf(
            LearnsetRuleset("original", "Original", 1, 1.0, mapOf(1 to listOf(LearnsetEntry(5, 1))), primary = true),
            LearnsetRuleset("modern", "Modern", 2, 1.0, mapOf(1 to listOf(LearnsetEntry(5, 2)))),
        ).let { if (multiTable) it else it.take(1) }
        return ParsedCatalog(
            "hash",
            EngineFamily.EMERALD,
            Platform.GBA,
            speciesById = mapOf(1 to species),
            movesById = moves,
            learnsetRulesets = rulesets,
        )
    }
}
