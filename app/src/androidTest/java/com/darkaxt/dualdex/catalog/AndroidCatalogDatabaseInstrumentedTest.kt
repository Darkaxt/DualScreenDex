package com.darkaxt.dualdex.catalog

import androidx.test.platform.app.InstrumentationRegistry
import com.darkaxt.dualdex.catalog.CatalogCache
import com.darkaxt.dualdex.catalog.CatalogSourceMetadata
import com.darkaxt.dualdex.catalog.CatalogWriteProgress
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidCatalogDatabaseInstrumentedTest {
    @Test
    fun persistsAndReopensWithTheNativeAndroidSqliteDriver() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "catalog-driver-test")
        root.deleteRecursively()
        try {
            val cache = CatalogCache(root, AndroidCatalogDatabaseFactory)
            val catalog = ParsedCatalog(
                romSha256 = "f".repeat(64),
                family = EngineFamily.CRYSTAL,
                platform = Platform.GBC,
                romCrc32 = "1234ABCD",
            )
            cache.write(
                catalog,
                CatalogSourceMetadata.direct("Crystal.gbc", 2_097_152, "PM_CRYSTAL"),
                CatalogWriteProgress.complete(),
            )

            val reopened = CatalogCache(root, AndroidCatalogDatabaseFactory).readComplete(catalog.romSha256)

            assertEquals(catalog, reopened?.catalog)
            assertTrue(cache.fileFor(catalog.romSha256).isFile)
        } finally {
            root.deleteRecursively()
        }
    }
}
