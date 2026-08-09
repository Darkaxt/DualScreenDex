package com.darkaxt.dualdex.web

import com.darkaxt.dualdex.catalog.CatalogRepository
import com.darkaxt.dualdex.catalog.CatalogSourceMetadata
import com.darkaxt.dualdex.catalog.CatalogWriteProgress
import com.darkaxt.dualdex.catalog.StoredCatalog
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

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

    private class FakeCatalogRepository(private val stored: StoredCatalog) : CatalogRepository {
        override fun write(
            catalog: ParsedCatalog,
            source: CatalogSourceMetadata,
            progress: CatalogWriteProgress,
        ) = Unit

        override fun readComplete(sha256: String): StoredCatalog? = stored.takeIf { it.catalog.romSha256 == sha256 }

        override fun findCompleted(crc32: String, romSize: Int, romTitle: String?): List<StoredCatalog> = emptyList()
    }
}
