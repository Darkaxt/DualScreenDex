package com.darkaxt.dualdex.web

import com.darkaxt.dualdex.catalog.CatalogCache
import com.darkaxt.dualdex.catalog.CatalogDatabase
import com.darkaxt.dualdex.catalog.CatalogDatabaseFactory
import com.darkaxt.dualdex.catalog.CatalogRow
import com.darkaxt.dualdex.catalog.CatalogSourceMetadata
import com.darkaxt.dualdex.catalog.CatalogWriteProgress
import com.darkaxt.dualdex.save.SaveCapabilityStatus
import com.darkaxt.dualdex.save.SaveParseResult
import com.darkaxt.dualdex.save.SaveParser
import com.enrpau.dualscreendex.companion.api.SaveRamView
import com.enrpau.dualscreendex.parser.catalog.CatalogGen3BagDataSource
import com.enrpau.dualscreendex.parser.catalog.CatalogGen3BagPocket
import com.enrpau.dualscreendex.parser.catalog.CatalogParser
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.enrpau.dualscreendex.parser.detect.RomHeaderReader
import com.enrpau.dualscreendex.parser.io.RomImage
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.sql.Connection
import java.sql.DriverManager
import java.util.Comparator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class UnboundOdysseySaveCompletionLiveRomTest {
    @Test
    fun odysseyPublishesACompleteTypedSaveRuntimeAbiAndDecodesAllFourteenDomains() {
        val romPath = configuredPath("DUALDEX_ODYSSEY_ROM")
        val savePath = configuredPath("DUALDEX_ODYSSEY_SAVE")
        val rom = RomImage(Files.readAllBytes(romPath))
        val save = Files.readAllBytes(savePath)
        assertEquals(ODYSSEY_ROM_SHA, rom.sha256)
        assertEquals(ODYSSEY_SAVE_SHA, sha256(save))

        val parsedCatalog = requireNotNull(CatalogParser.parse(rom).catalog)
        withPersistedRuntime(romPath, rom, parsedCatalog) { runtime, catalog ->
            assertEquals(parsedCatalog.runtimeMetadata, catalog.runtimeMetadata)
            val context = requireNotNull(runtime.saveParseContext())
            assertNotNull(context.gen3SaveRuntimeAbi)

            val first = SaveParser.parse(save.copyOf(), context) as SaveParseResult.Parsed
            val second = SaveParser.parse(save.copyOf(), context) as SaveParseResult.Parsed
            assertEquals(first.snapshot, second.snapshot)
            assertEquals(14, first.snapshot.capabilities.size)
            assertTrue(
                first.snapshot.capabilities.values.joinToString { "${it.capability}=${it.status}" },
                first.snapshot.capabilities.values.all { it.status == SaveCapabilityStatus.AVAILABLE },
            )
            val trainer = requireNotNull(first.snapshot.trainer)
            assertEquals(5L, first.snapshot.saveCounter)
            assertEquals(41, first.snapshot.seenDexNumbers.size)
            assertEquals(40, first.snapshot.caughtDexNumbers.size)
            assertEquals(6, first.snapshot.party.size)
            assertEquals(26, first.snapshot.storedIndividuals.size)
            assertEquals(0, trainer.gender)
            assertEquals(6_140L, trainer.money)
            assertEquals(3, trainer.playTimeHours)
            assertEquals(12, trainer.playTimeMinutes)
            assertEquals(0, trainer.badgeFlags)
            assertEquals(5, first.snapshot.bag.size)
            assertEquals(
                mapOf(
                    com.darkaxt.dualdex.save.BagPocket.ITEMS to 1,
                    com.darkaxt.dualdex.save.BagPocket.KEY_ITEMS to 0,
                    com.darkaxt.dualdex.save.BagPocket.BALLS to 0,
                    com.darkaxt.dualdex.save.BagPocket.TM_HM to 3,
                    com.darkaxt.dualdex.save.BagPocket.BERRIES to 0,
                ),
                first.snapshot.bag.associate { it.pocket to it.entries.size },
            )
            assertTrue(first.snapshot.bag.flatMap { it.entries }.all { it.itemId > 0 && it.quantity > 0 })
            assertTrue(runtime.applySaveSnapshot(first.snapshot, SaveRamView(status = "MATCHED")))
            val state = runtime.stateView()
            assertEquals(6_140L, state.trainer?.money)
            assertEquals(3, state.trainer?.playTimeHours)
            assertEquals(12, state.trainer?.playTimeMinutes)
            assertEquals(6, state.party.count { it.occupied })
        }
    }

    @Test
    fun unboundPublishesItsTypedSaveAbiButRejectsTheSuppliedErasedFlash() {
        val romPath = configuredPath("DUALDEX_UNBOUND_ROM")
        val savePath = configuredPath("DUALDEX_UNBOUND_SAVE")
        val rom = RomImage(Files.readAllBytes(romPath))
        val save = Files.readAllBytes(savePath)
        assertEquals(UNBOUND_ROM_SHA, rom.sha256)
        assertEquals(UNBOUND_ERASED_SAVE_SHA, sha256(save))
        assertTrue(save.all { it.toInt() and 0xff == 0xff })

        val parsedCatalog = requireNotNull(CatalogParser.parse(rom).catalog)
        withPersistedRuntime(romPath, rom, parsedCatalog) { runtime, catalog ->
            val layout = requireNotNull(catalog.runtimeMetadata.gen3RuntimeMemoryLayout)
            assertEquals(0x03005008L, layout.saveBlock1PointerAddress)
            assertEquals(0x0300500CL, layout.saveBlock2PointerAddress)
            assertEquals(0x0203B174L, layout.extendedSaveAddress)
            val abi = requireNotNull(layout.saveRuntimeAbi)
            assertEquals(0x3D68, abi.saveBlock1Size)
            assertEquals(0x0F24, abi.saveBlock2Size)
            assertEquals(0x2EA4, abi.extendedSaveDataSize)
            assertEquals(
                listOf(
                    Pocket(CatalogGen3BagPocket.ITEMS, 0x09AC, 450),
                    Pocket(CatalogGen3BagPocket.KEY_ITEMS, 0x10B4, 75),
                    Pocket(CatalogGen3BagPocket.BALLS, 0x11E0, 50),
                    Pocket(CatalogGen3BagPocket.TM_HM, 0x12A8, 128),
                    Pocket(CatalogGen3BagPocket.BERRIES, 0x14A8, 75),
                ),
                abi.bag.pockets.map {
                    assertEquals(CatalogGen3BagDataSource.EXTENDED_SAVE, it.dataSource)
                    Pocket(it.pocket, it.byteOffset, it.capacity)
                },
            )
            assertNotNull(requireNotNull(runtime.saveParseContext()).gen3SaveRuntimeAbi)
            val result = SaveParser.parse(save.copyOf(), requireNotNull(runtime.saveParseContext()))
            assertTrue(result is SaveParseResult.Unsupported)
            assertFalse(result is SaveParseResult.Parsed)
        }
    }

    private fun withPersistedRuntime(
        romPath: Path,
        rom: RomImage,
        catalog: ParsedCatalog,
        block: (ProductionCompanionRuntime, ParsedCatalog) -> Unit,
    ) {
        val root = newTemporaryRoot()
        try {
            val cache = CatalogCache(root.toFile(), JdbcCatalogDatabaseFactory)
            cache.write(
                catalog,
                CatalogSourceMetadata.direct(
                    displayName = romPath.fileName.toString(),
                    romSize = rom.size,
                    romTitle = RomHeaderReader.read(rom).title,
                ),
                CatalogWriteProgress.complete(),
            )
            val reopened = requireNotNull(cache.readComplete(rom.sha256)).catalog
            ProductionCompanionRuntime(catalogRepository = cache).use { runtime ->
                assertTrue(runtime.restoreCatalog(rom.sha256))
                block(runtime, reopened)
            }
        } finally {
            Files.walk(root).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }

    private fun newTemporaryRoot(): Path {
        val configured = System.getenv("DUALDEX_TEST_TEMP_ROOT")?.takeIf(String::isNotBlank)?.let(Path::of)
        configured?.let(Files::createDirectories)
        return if (configured == null) Files.createTempDirectory("dualdex-unbound-odyssey-")
        else Files.createTempDirectory(configured, "dualdex-unbound-odyssey-")
    }

    private fun configuredPath(environmentVariable: String): Path {
        val configured = System.getenv(environmentVariable)
        assumeTrue("set $environmentVariable to run this real control", !configured.isNullOrBlank())
        return Path.of(requireNotNull(configured)).also { path ->
            assumeTrue("real control does not exist: $path", Files.isRegularFile(path))
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private data class Pocket(val pocket: CatalogGen3BagPocket, val offset: Int, val capacity: Int)

    private object JdbcCatalogDatabaseFactory : CatalogDatabaseFactory {
        override fun open(file: File): CatalogDatabase {
            Class.forName("org.sqlite.JDBC")
            return JdbcCatalogDatabase(DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}"))
        }
    }

    private class JdbcCatalogDatabase(private val connection: Connection) : CatalogDatabase {
        override fun <T> transaction(block: () -> T): T {
            val originalAutoCommit = connection.autoCommit
            connection.autoCommit = false
            return try {
                block().also { connection.commit() }
            } catch (failure: Throwable) {
                connection.rollback()
                throw failure
            } finally {
                connection.autoCommit = originalAutoCommit
            }
        }

        override fun execute(sql: String, arguments: List<Any?>) {
            connection.prepareStatement(sql).use { statement ->
                arguments.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
                statement.executeUpdate()
            }
        }

        override fun <T> query(
            sql: String,
            arguments: List<Any?>,
            map: (CatalogRow) -> T,
        ): List<T> = connection.prepareStatement(sql).use { statement ->
            arguments.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) {
                        add(map(object : CatalogRow {
                            override fun string(column: String): String? = result.getString(column)
                            override fun long(column: String): Long? = result.getLong(column).takeUnless {
                                result.wasNull()
                            }
                            override fun bytes(column: String): ByteArray? = result.getBytes(column)
                        }))
                    }
                }
            }
        }

        override fun close() = connection.close()
    }

    private companion object {
        const val UNBOUND_ROM_SHA = "7aa25bbf568f7cfcf6ee1cf2e9e6ff637350b3d0705c2375cabb6baa7d9739f7"
        const val UNBOUND_ERASED_SAVE_SHA = "b5a41c3758763bbec72769fab4a2533bf2db0b6312d93d25a695f9e4b9e02260"
        const val ODYSSEY_ROM_SHA = "44c7e3eafab19c39df7c39d54bafb78a1d9caf7c371244b6f5efb12cfd98d0d0"
        const val ODYSSEY_SAVE_SHA = "645282db3d0f6e5723930cc35793a39f2044f96cf1d658f139f04af5482fdcf3"
    }
}
