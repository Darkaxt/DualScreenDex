package com.darkaxt.dualdex.web

import com.darkaxt.dualdex.catalog.CatalogRepository
import com.darkaxt.dualdex.catalog.CatalogSourceMetadata
import com.darkaxt.dualdex.catalog.CatalogWriteProgress
import com.darkaxt.dualdex.catalog.StoredCatalog
import com.enrpau.dualscreendex.companion.api.RetroArchView
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.enrpau.dualscreendex.parser.io.LoadedRom
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit

class ProductionCompanionRuntimeTest {
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
    fun exposesRetroArchSetupAndSessionStateWithoutRequiringACatalog() {
        val runtime = ProductionCompanionRuntime()
        runtime.updateRetroArch(
            RetroArchView(
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

    private class FakeCatalogRepository(private val stored: StoredCatalog) : CatalogRepository {
        override fun write(
            catalog: ParsedCatalog,
            source: CatalogSourceMetadata,
            progress: CatalogWriteProgress,
        ) = Unit

        override fun readComplete(sha256: String): StoredCatalog? = stored.takeIf { it.catalog.romSha256 == sha256 }

        override fun findCompleted(crc32: String, romSize: Int, romTitle: String?): List<StoredCatalog> = emptyList()
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
