package com.darkaxt.dualdex.catalog

import com.darkaxt.dualdex.save.OwnedIndividual
import com.darkaxt.dualdex.save.SaveCapability
import com.darkaxt.dualdex.save.SaveCapabilityEvidence
import com.darkaxt.dualdex.save.SaveCapabilityStatus
import com.darkaxt.dualdex.save.SaveSnapshot
import com.darkaxt.dualdex.save.SavedArea
import com.google.gson.Gson
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveSnapshotStoreTest {
    @Test
    fun atomicallyReplacesAndReopensTheLastGoodSnapshotByRomHash() {
        val directory = Files.createTempDirectory("dualdex-save-store").toFile()
        try {
            val romHash = "a".repeat(64)
            val store = SaveSnapshotStore(directory, JdbcCatalogDatabaseFactory)
            assertNull(store.read(romHash))
            val first = fixture(romHash, counter = 4, species = 6)
            val second = fixture(romHash, counter = 5, species = 25).copy(
                detectedLevelUpRulesetId = "modern",
                levelUpRulesetDetectionResolved = true,
                levelUpRulesetDetectionFingerprint = "current-fingerprint",
            )

            store.write(first, sourceLastModifiedEpochMs = 100, refreshedAtEpochMs = 200)
            store.write(second, sourceLastModifiedEpochMs = 300, refreshedAtEpochMs = 400)

            val reopened = requireNotNull(store.read(romHash))
            assertEquals(second, reopened.snapshot)
            assertEquals(300L, reopened.sourceLastModifiedEpochMs)
            assertEquals(400L, reopened.refreshedAtEpochMs)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun legacySnapshotWithoutLevelUpDetectionFieldsReopensAsUnresolved() {
        val directory = Files.createTempDirectory("dualdex-save-store-legacy").toFile()
        try {
            val romHash = "b".repeat(64)
            val store = SaveSnapshotStore(directory, JdbcCatalogDatabaseFactory)
            store.write(
                fixture(romHash, counter = 6, species = 25).copy(
                    detectedLevelUpRulesetId = "modern",
                    levelUpRulesetDetectionResolved = true,
                    levelUpRulesetDetectionFingerprint = "legacy-fingerprint",
                ),
                sourceLastModifiedEpochMs = 500,
                refreshedAtEpochMs = 600,
            )
            JdbcCatalogDatabaseFactory.open(directory.resolve("save-snapshots/$romHash.sqlite")).use { database ->
                val current = database.query("SELECT payload_json FROM save_snapshot WHERE id = 1") { row ->
                    requireNotNull(row.string("payload_json"))
                }.single()
                val legacy = current
                    .replace(",\"detectedLevelUpRulesetId\":\"modern\"", "")
                    .replace(",\"levelUpRulesetDetectionResolved\":true", "")
                    .replace(",\"levelUpRulesetDetectionFingerprint\":\"legacy-fingerprint\"", "")
                assertTrue(legacy != current)
                database.execute("UPDATE save_snapshot SET payload_json = ? WHERE id = 1", listOf(legacy))
            }

            val reopened = requireNotNull(store.read(romHash)).snapshot

            assertNull(reopened.detectedLevelUpRulesetId)
            assertFalse(reopened.levelUpRulesetDetectionResolved)
            assertNull(reopened.levelUpRulesetDetectionFingerprint)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun inactiveCatalogCleanupDoesNotDeleteRecoverySnapshots() {
        val directory = Files.createTempDirectory("dualdex-save-store-cleanup").toFile()
        try {
            val romHash = "c".repeat(64)
            val snapshot = fixture(romHash, counter = 7, species = 133)
            val store = SaveSnapshotStore(directory, JdbcCatalogDatabaseFactory)
            store.write(snapshot, sourceLastModifiedEpochMs = 700, refreshedAtEpochMs = 800)

            CatalogCache(directory, JdbcCatalogDatabaseFactory).clearInactive(activeSha256 = null)

            assertEquals(snapshot, store.read(romHash)?.snapshot)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun corruptCatalogRemovalDoesNotDeleteRecoverySnapshots() {
        val directory = Files.createTempDirectory("dualdex-save-store-corrupt").toFile()
        try {
            val romHash = "d".repeat(64)
            val snapshot = fixture(romHash, counter = 8, species = 150)
            val store = SaveSnapshotStore(directory, JdbcCatalogDatabaseFactory)
            store.write(snapshot, sourceLastModifiedEpochMs = 900, refreshedAtEpochMs = 1_000)
            JdbcCatalogDatabaseFactory.open(directory.resolve("$romHash.sqlite")).use { database ->
                CatalogMigration.prepare(database)
                database.execute(
                    """
                    INSERT OR REPLACE INTO catalog_metadata (
                        id, schema_version, parser_schema_version, sha256, crc32, rom_size, rom_title,
                        source_name, source_kind, source_entry, family, platform, phase,
                        completed_units, total_units, is_complete, written_at_epoch_ms
                    ) VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?, ?, ?, ?, 1, ?)
                    """.trimIndent(),
                    listOf(
                        CatalogSchema.version,
                        CatalogSchema.parserSchemaVersion,
                        romHash,
                        "12345678",
                        1024,
                        "CONTROL",
                        "Control.gba",
                        "INVALID_SOURCE_KIND",
                        "GEN3_DECOMP",
                        "GBA",
                        "COMPLETE",
                        1,
                        1,
                        1_100,
                    ),
                )
            }

            assertNull(CatalogCache(directory, JdbcCatalogDatabaseFactory).readComplete(romHash))
            assertEquals(snapshot, store.read(romHash)?.snapshot)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun malformedSnapshotIsQuarantinedWithoutRemovingTheStore() {
        val directory = Files.createTempDirectory("dualdex-save-store-malformed").toFile()
        try {
            val romHash = "f".repeat(64)
            val diagnostics = mutableListOf<SaveSnapshotCorruption>()
            val store = SaveSnapshotStore(
                directory,
                JdbcCatalogDatabaseFactory,
                onCorruptSnapshot = diagnostics::add,
            )
            store.write(
                fixture(romHash, counter = 10, species = 25),
                sourceLastModifiedEpochMs = 1_400,
                refreshedAtEpochMs = 1_500,
            )
            val databaseFile = directory.resolve("save-snapshots/$romHash.sqlite")
            JdbcCatalogDatabaseFactory.open(databaseFile).use { database ->
                database.execute(
                    "UPDATE save_snapshot SET payload_json = ? WHERE id = 1",
                    listOf("{"),
                )
            }

            assertNull(store.read(romHash))
            assertEquals(
                listOf(SaveSnapshotCorruption("JsonSyntaxException")),
                diagnostics,
            )
            assertTrue(databaseFile.isFile)
            JdbcCatalogDatabaseFactory.open(databaseFile).use { database ->
                assertEquals(
                    listOf(0L),
                    database.query("SELECT COUNT(*) AS count FROM save_snapshot") { row ->
                        requireNotNull(row.long("count"))
                    },
                )
            }

            val replacement = fixture(romHash, counter = 11, species = 133)
            store.write(
                replacement,
                sourceLastModifiedEpochMs = 1_600,
                refreshedAtEpochMs = 1_700,
            )
            assertEquals(replacement, store.read(romHash)?.snapshot)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun snapshotSchemaFailureIsNotMisclassifiedAsPayloadCorruption() {
        val directory = Files.createTempDirectory("dualdex-save-store-schema").toFile()
        try {
            val romHash = "9".repeat(64)
            val expected = fixture(romHash, counter = 12, species = 150)
            val store = SaveSnapshotStore(directory, JdbcCatalogDatabaseFactory)
            store.write(expected, sourceLastModifiedEpochMs = 1_800, refreshedAtEpochMs = 1_900)
            val databaseFile = directory.resolve("save-snapshots/$romHash.sqlite")
            JdbcCatalogDatabaseFactory.open(databaseFile).use { database ->
                database.execute("PRAGMA user_version = 999")
            }

            assertThrows(IllegalArgumentException::class.java) {
                store.read(romHash)
            }

            JdbcCatalogDatabaseFactory.open(databaseFile).use { database ->
                assertEquals(
                    listOf(1L),
                    database.query("SELECT COUNT(*) AS count FROM save_snapshot") { row ->
                        requireNotNull(row.long("count"))
                    },
                )
                database.execute("PRAGMA user_version = ${SaveSnapshotSchema.version}")
            }
            assertEquals(expected, store.read(romHash)?.snapshot)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun legacySnapshotMigratesBeforeCatalogSchemaRebuild() {
        val directory = Files.createTempDirectory("dualdex-save-store-migration").toFile()
        try {
            val romHash = "e".repeat(64)
            val snapshot = fixture(romHash, counter = 9, species = 151)
            writeLegacySnapshot(directory, snapshot, sourceLastModifiedEpochMs = 1_200, refreshedAtEpochMs = 1_300)

            val store = SaveSnapshotStore(directory, JdbcCatalogDatabaseFactory)
            JdbcCatalogDatabaseFactory.open(directory.resolve("$romHash.sqlite")).use { database ->
                database.execute("PRAGMA user_version = 999")
                CatalogMigration.prepare(database)
            }

            val reopened = requireNotNull(store.read(romHash))
            assertEquals(snapshot, reopened.snapshot)
            assertEquals(1_200L, reopened.sourceLastModifiedEpochMs)
            assertEquals(1_300L, reopened.refreshedAtEpochMs)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun snapshotReaderKeepsJsonOutOfCursorBackedRowQueries() {
        val directory = Files.createTempDirectory("dualdex-save-store-cursor-contract").toFile()
        try {
            val romHash = "b".repeat(64)
            val snapshot = fixture(romHash, counter = 26, species = 25)
            SaveSnapshotStore(directory, JdbcCatalogDatabaseFactory).write(
                snapshot,
                sourceLastModifiedEpochMs = 4_600,
                refreshedAtEpochMs = 4_700,
            )
            val guardedFactory = SnapshotPayloadProjectionRejectingFactory()

            val reopened = SaveSnapshotStore(directory, guardedFactory).read(romHash)

            assertEquals(snapshot, reopened?.snapshot)
            assertEquals(0, guardedFactory.cursorPayloadProjections)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun oversizedSnapshotJsonIsRejectedBeforeStringRetrieval() {
        val directory = Files.createTempDirectory("dualdex-save-store-payload-bound").toFile()
        try {
            val romHash = "1".repeat(64)
            val snapshot = fixture(romHash, counter = 13, species = 25)
            SaveSnapshotStore(directory, JdbcCatalogDatabaseFactory).write(
                snapshot,
                sourceLastModifiedEpochMs = 2_000,
                refreshedAtEpochMs = 2_100,
            )
            val guardedFactory = OversizedPayloadGuardFactory()
            val guardedStore = SaveSnapshotStore(directory, guardedFactory)

            assertNull(guardedStore.read(romHash))
            assertEquals(0, guardedFactory.payloadStringReads)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun excessiveSemanticCollectionsAreRejectedBeforeGsonMaterialization() {
        val directory = Files.createTempDirectory("dualdex-save-store-collection-bound").toFile()
        try {
            val romHash = "2".repeat(64)
            val store = SaveSnapshotStore(directory, JdbcCatalogDatabaseFactory)
            store.write(
                fixture(romHash, counter = 14, species = 25),
                sourceLastModifiedEpochMs = 2_200,
                refreshedAtEpochMs = 2_300,
            )
            val excessiveParty = List(7) { "null" }.joinToString(",")
            val payload =
                """{"romIdentity":"$romHash","saveIdentity":"save-14","saveGeneration":3,"saveCounter":14,"currentArea":null,"seenDexNumbers":[],"caughtDexNumbers":[],"party":[$excessiveParty],"storedIndividuals":[],"capabilities":{},"schemaId":"gen3-v1","bag":[]}"""
            JdbcCatalogDatabaseFactory.open(directory.resolve("save-snapshots/$romHash.sqlite")).use { database ->
                database.execute("UPDATE save_snapshot SET payload_json = ? WHERE id = 1", listOf(payload))
            }

            assertNull(store.read(romHash))
            JdbcCatalogDatabaseFactory.open(directory.resolve("save-snapshots/$romHash.sqlite")).use { database ->
                assertEquals(
                    listOf(0L),
                    database.query("SELECT COUNT(*) AS count FROM save_snapshot") { row -> row.long("count") },
                )
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun aggregateBagEntriesAreRejectedBeforeGsonAcrossAllPockets() {
        val directory = Files.createTempDirectory("dualdex-save-store-bag-aggregate").toFile()
        try {
            val romHash = "3".repeat(64)
            val snapshot = fixture(romHash, counter = 19, species = 25)
            SaveSnapshotStore(directory, JdbcCatalogDatabaseFactory).write(
                snapshot,
                sourceLastModifiedEpochMs = 3_200,
                refreshedAtEpochMs = 3_300,
            )
            val entries = (1..13_108).joinToString(",") { itemId ->
                "{\"itemId\":$itemId,\"quantity\":1}"
            }
            val bag = listOf("ITEMS", "KEY_ITEMS", "BALLS", "TM_HM", "BERRIES").joinToString(",") { pocket ->
                "{\"pocket\":\"$pocket\",\"entries\":[$entries]}"
            }
            val payload =
                """{"romIdentity":"$romHash","saveIdentity":"save-19","saveGeneration":3,"saveCounter":19,"currentArea":null,"seenDexNumbers":[],"caughtDexNumbers":[],"party":[],"storedIndividuals":[],"capabilities":{},"schemaId":"gen3-v1","bag":[$bag]}"""
            assertTrue(payload.toByteArray().size < MAX_TEST_SNAPSHOT_BYTES)
            JdbcCatalogDatabaseFactory.open(directory.resolve("save-snapshots/$romHash.sqlite")).use { database ->
                database.execute("UPDATE save_snapshot SET payload_json = ? WHERE id = 1", listOf(payload))
            }
            var decodeCalls = 0
            val store = SaveSnapshotStore(
                directory,
                JdbcCatalogDatabaseFactory,
                decodeSnapshot = {
                    decodeCalls++
                    throw AssertionError("snapshot reached Gson before aggregate bag-entry validation")
                },
            )

            assertNull(store.read(romHash))
            assertEquals(0, decodeCalls)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun validDestinationWinsOverMalformedLegacyWithoutFailingStartup() {
        val directory = Files.createTempDirectory("dualdex-save-store-valid-destination").toFile()
        try {
            val romHash = "4".repeat(64)
            val current = fixture(romHash, counter = 20, species = 133)
            SaveSnapshotStore(directory, JdbcCatalogDatabaseFactory).write(
                current,
                sourceLastModifiedEpochMs = 3_400,
                refreshedAtEpochMs = 3_500,
            )
            val malformedLegacy = fixture(romHash, counter = 19, species = 25)
            writeLegacySnapshot(
                directory,
                malformedLegacy,
                sourceLastModifiedEpochMs = 3_200,
                refreshedAtEpochMs = 3_300,
            )
            JdbcCatalogDatabaseFactory.open(directory.resolve("$romHash.sqlite")).use { database ->
                database.execute(
                    "UPDATE save_snapshot SET payload_json = ? WHERE id = 1",
                    listOf(MALFORMED_PAYLOAD),
                )
            }

            val reopened = SaveSnapshotStore(directory, JdbcCatalogDatabaseFactory)

            assertEquals(current, reopened.read(romHash)?.snapshot)
            assertTrue(directory.resolve("$romHash.sqlite").isFile)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun heldCrossProcessMigrationLockDefersRecoveryWithoutReplacingDestination() {
        val directory = Files.createTempDirectory("dualdex-save-store-file-lock").toFile()
        try {
            val romHash = "5".repeat(64)
            val legacy = fixture(romHash, counter = 21, species = 25)
            writeLegacySnapshot(directory, legacy, sourceLastModifiedEpochMs = 3_600, refreshedAtEpochMs = 3_700)
            val snapshotDirectory = directory.resolve("save-snapshots").also(File::mkdirs)
            val destination = snapshotDirectory.resolve("$romHash.sqlite")
            assertTrue(destination.createNewFile())
            val lockFile = snapshotDirectory.resolve("$romHash.sqlite.migration.lock")
            lateinit var deferred: SaveSnapshotStore

            RandomAccessFile(lockFile, "rw").channel.use { channel ->
                channel.lock().use {
                    deferred = SaveSnapshotStore(directory, JdbcCatalogDatabaseFactory)
                    assertNull(deferred.read(romHash))
                    JdbcCatalogDatabaseFactory.open(destination).use { database ->
                        assertEquals(
                            listOf(0L),
                            database.query("SELECT COUNT(*) AS count FROM save_snapshot") { row -> row.long("count") },
                        )
                    }
                    assertTrue(directory.resolve("$romHash.sqlite").isFile)
                }
            }

            assertEquals(legacy, deferred.read(romHash)?.snapshot)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun transientDestinationProbeCannotAuthorizeLegacyReplacement() {
        val directory = Files.createTempDirectory("dualdex-save-store-probe-unavailable").toFile()
        try {
            val romHash = "6".repeat(64)
            val newer = fixture(romHash, counter = 23, species = 133)
            SaveSnapshotStore(directory, JdbcCatalogDatabaseFactory).write(
                newer,
                sourceLastModifiedEpochMs = 4_000,
                refreshedAtEpochMs = 4_100,
            )
            writeLegacySnapshot(
                directory,
                fixture(romHash, counter = 22, species = 25),
                sourceLastModifiedEpochMs = 3_800,
                refreshedAtEpochMs = 3_900,
            )
            val destination = directory.resolve("save-snapshots/$romHash.sqlite")
            SaveSnapshotStore(
                directory,
                UnavailableDestinationProbeFactory(destination),
            )

            assertEquals(newer, SaveSnapshotStore(directory, JdbcCatalogDatabaseFactory).read(romHash)?.snapshot)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun transientInitialBoundedReadDefersMigrationAndCanRetry() {
        val directory = Files.createTempDirectory("dualdex-save-store-initial-read-unavailable").toFile()
        try {
            val romHash = "8".repeat(64)
            val newer = fixture(romHash, counter = 26, species = 133)
            SaveSnapshotStore(directory, JdbcCatalogDatabaseFactory).write(
                newer,
                sourceLastModifiedEpochMs = 4_600,
                refreshedAtEpochMs = 4_700,
            )
            writeLegacySnapshot(
                directory,
                fixture(romHash, counter = 25, species = 25),
                sourceLastModifiedEpochMs = 4_400,
                refreshedAtEpochMs = 4_500,
            )
            val destination = directory.resolve("save-snapshots/$romHash.sqlite")
            val factory = TransientDestinationBlobFactory(destination, failOnCalls = setOf(1))

            val store = SaveSnapshotStore(directory, factory)

            assertEquals(1, factory.destinationBlobReads.get())
            assertEquals(newer, store.read(romHash)?.snapshot)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun transientFinalBoundedReadCannotPublishLegacyAndCanRetry() {
        val directory = Files.createTempDirectory("dualdex-save-store-final-read-unavailable").toFile()
        try {
            val romHash = "9".repeat(64)
            val malformed = fixture(romHash, counter = 28, species = 133)
            val legacy = fixture(romHash, counter = 27, species = 25)
            SaveSnapshotStore(directory, JdbcCatalogDatabaseFactory).write(
                malformed,
                sourceLastModifiedEpochMs = 5_000,
                refreshedAtEpochMs = 5_100,
            )
            val destination = directory.resolve("save-snapshots/$romHash.sqlite")
            JdbcCatalogDatabaseFactory.open(destination).use { database ->
                database.execute(
                    "UPDATE save_snapshot SET payload_json = ? WHERE id = 1",
                    listOf(MALFORMED_PAYLOAD),
                )
            }
            writeLegacySnapshot(
                directory,
                legacy,
                sourceLastModifiedEpochMs = 4_800,
                refreshedAtEpochMs = 4_900,
            )
            val factory = TransientDestinationBlobFactory(destination, failOnCalls = setOf(2))

            val store = SaveSnapshotStore(directory, factory)

            JdbcCatalogDatabaseFactory.open(destination).use { database ->
                assertEquals(
                    listOf(MALFORMED_PAYLOAD),
                    database.query("SELECT payload_json FROM save_snapshot WHERE id = 1") { row ->
                        row.string("payload_json")
                    },
                )
            }
            assertEquals(legacy, store.read(romHash)?.snapshot)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun newerDestinationPublishedDuringMigrationWinsFinalRevalidation() {
        val directory = Files.createTempDirectory("dualdex-save-store-newer-destination").toFile()
        try {
            val romHash = "a".repeat(64)
            val legacy = fixture(romHash, counter = 24, species = 25)
            val newer = fixture(romHash, counter = 25, species = 150)
            writeLegacySnapshot(directory, legacy, sourceLastModifiedEpochMs = 4_200, refreshedAtEpochMs = 4_300)
            val destination = directory.resolve("save-snapshots/$romHash.sqlite")
            destination.parentFile.mkdirs()
            JdbcCatalogDatabaseFactory.open(destination).use(SaveSnapshotMigration::prepare)
            val factory = NewerDestinationBeforePublishFactory(
                destination = destination,
                replacement = newer,
                sourceLastModifiedEpochMs = 4_400,
                refreshedAtEpochMs = 4_500,
            )

            SaveSnapshotStore(directory, factory)

            assertTrue(factory.replaced.get())
            val reopened = requireNotNull(SaveSnapshotStore(directory, JdbcCatalogDatabaseFactory).read(romHash))
            assertEquals(newer, reopened.snapshot)
            assertEquals(4_500L, reopened.refreshedAtEpochMs)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun emptyDestinationDoesNotSuppressAValidLegacySnapshot() {
        assertLegacyRecovery("empty") { destination ->
            assertTrue(destination.createNewFile())
        }
    }

    @Test
    fun corruptDestinationDoesNotSuppressAValidLegacySnapshot() {
        assertLegacyRecovery("corrupt") { destination ->
            destination.writeBytes(byteArrayOf(1, 2, 3, 4))
        }
    }

    @Test
    fun incompatibleDestinationDoesNotSuppressAValidLegacySnapshot() {
        assertLegacyRecovery("incompatible") { destination ->
            JdbcCatalogDatabaseFactory.open(destination).use { database ->
                SaveSnapshotMigration.prepare(database)
                database.execute("PRAGMA user_version = 999")
            }
        }
    }

    @Test
    fun schemaOnlyDestinationDoesNotSuppressAValidLegacySnapshot() {
        assertLegacyRecovery("schema-only") { destination ->
            JdbcCatalogDatabaseFactory.open(destination).use(SaveSnapshotMigration::prepare)
        }
    }

    @Test
    fun interruptedMigrationNeverPublishesItsTemporaryDatabase() {
        val directory = Files.createTempDirectory("dualdex-save-store-interrupted-migration").toFile()
        try {
            val romHash = "7".repeat(64)
            val snapshot = fixture(romHash, counter = 15, species = 151)
            writeLegacySnapshot(directory, snapshot, sourceLastModifiedEpochMs = 2_400, refreshedAtEpochMs = 2_500)
            val factory = FailingTemporaryMigrationFactory()

            assertThrows(IllegalStateException::class.java) {
                SaveSnapshotStore(directory, factory)
            }

            val destination = directory.resolve("save-snapshots/$romHash.sqlite")
            assertFalse(destination.exists())
            assertTrue(directory.resolve("$romHash.sqlite").isFile)
            assertEquals(
                listOf("$romHash.sqlite.migration.lock"),
                directory.resolve("save-snapshots").listFiles().orEmpty().map(File::getName).sorted(),
            )
            assertEquals(snapshot, SaveSnapshotStore(directory, JdbcCatalogDatabaseFactory).read(romHash)?.snapshot)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun quarantineCompareAndDeletePreservesANewerDirectWriterAcrossCanonicalAliases() {
        val directory = Files.createTempDirectory("dualdex-save-store-quarantine-race").toFile()
        try {
            val romHash = "8".repeat(64)
            val corrupt = fixture(romHash, counter = 16, species = 25)
            val replacement = fixture(romHash, counter = 17, species = 133)
            val initialStore = SaveSnapshotStore(directory, JdbcCatalogDatabaseFactory)
            initialStore.write(corrupt, sourceLastModifiedEpochMs = 2_600, refreshedAtEpochMs = 2_700)
            val databaseFile = directory.resolve("save-snapshots/$romHash.sqlite")
            JdbcCatalogDatabaseFactory.open(databaseFile).use { database ->
                database.execute(
                    "UPDATE save_snapshot SET payload_json = ? WHERE id = 1",
                    listOf(MALFORMED_PAYLOAD),
                )
            }
            directory.resolve("alias").mkdirs()
            val racingFactory = ReplacementBeforeQuarantineFactory(
                databaseFile = databaseFile,
                replacement = replacement,
                sourceLastModifiedEpochMs = 2_800,
                refreshedAtEpochMs = 2_900,
            )
            val aliasStore = SaveSnapshotStore(directory.resolve("alias/.."), racingFactory)

            assertNull(aliasStore.read(romHash))
            assertTrue(racingFactory.replaced.get())
            val reopened = requireNotNull(SaveSnapshotStore(directory, JdbcCatalogDatabaseFactory).read(romHash))
            assertEquals(replacement, reopened.snapshot)
            assertEquals(2_900L, reopened.refreshedAtEpochMs)
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun assertLegacyRecovery(
        fixtureName: String,
        prepareDestination: (File) -> Unit,
    ) {
        val directory = Files.createTempDirectory("dualdex-save-store-$fixtureName-destination").toFile()
        try {
            val romHash = fixtureName.hashCode().toUInt().toString(16).padStart(64, '0')
            val snapshot = fixture(romHash, counter = 18, species = 150)
            writeLegacySnapshot(directory, snapshot, sourceLastModifiedEpochMs = 3_000, refreshedAtEpochMs = 3_100)
            val destination = directory.resolve("save-snapshots/$romHash.sqlite")
            destination.parentFile.mkdirs()
            prepareDestination(destination)

            val store = SaveSnapshotStore(directory, JdbcCatalogDatabaseFactory)
            assertEquals(snapshot, store.read(romHash)?.snapshot)

            CatalogCache(directory, JdbcCatalogDatabaseFactory).clearInactive(activeSha256 = null)
            assertEquals(snapshot, store.read(romHash)?.snapshot)
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun writeLegacySnapshot(
        directory: java.io.File,
        snapshot: SaveSnapshot,
        sourceLastModifiedEpochMs: Long,
        refreshedAtEpochMs: Long,
    ) {
        JdbcCatalogDatabaseFactory.open(directory.resolve("${snapshot.romIdentity}.sqlite")).use { database ->
            CatalogMigration.prepare(database)
            SaveSnapshotSchema.createStatements.forEach(database::execute)
            database.execute(
                """
                INSERT OR REPLACE INTO save_snapshot (
                    id, rom_sha256, save_identity, save_schema_id, payload_json,
                    source_last_modified_epoch_ms, refreshed_at_epoch_ms
                ) VALUES (1, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                listOf(
                    snapshot.romIdentity,
                    snapshot.saveIdentity,
                    snapshot.schemaId,
                    Gson().toJson(snapshot),
                    sourceLastModifiedEpochMs,
                    refreshedAtEpochMs,
                ),
            )
        }
    }

    private class TransientDestinationBlobFactory(
        private val destination: File,
        private val failOnCalls: Set<Int>,
    ) : CatalogDatabaseFactory {
        val destinationBlobReads = AtomicInteger()

        override fun open(file: File): CatalogDatabase {
            val delegate = JdbcCatalogDatabaseFactory.open(file)
            if (file.canonicalFile != destination.canonicalFile) return delegate
            return object : CatalogDatabase by delegate {
                override fun readBlob(
                    sql: String,
                    arguments: List<Any?>,
                    maximumBytes: Int,
                ): ByteArray? {
                    val call = destinationBlobReads.incrementAndGet()
                    if (call in failOnCalls) {
                        throw IOException("injected transient bounded-read failure")
                    }
                    return delegate.readBlob(sql, arguments, maximumBytes)
                }
            }
        }
    }

    private class UnavailableDestinationProbeFactory(
        private val destination: File,
    ) : CatalogDatabaseFactory {
        private val failed = AtomicBoolean()

        override fun open(file: File): CatalogDatabase {
            if (file.canonicalFile == destination.canonicalFile && failed.compareAndSet(false, true)) {
                throw IllegalStateException("database is locked")
            }
            return JdbcCatalogDatabaseFactory.open(file)
        }
    }

    private class NewerDestinationBeforePublishFactory(
        private val destination: File,
        private val replacement: SaveSnapshot,
        private val sourceLastModifiedEpochMs: Long,
        private val refreshedAtEpochMs: Long,
    ) : CatalogDatabaseFactory {
        val replaced = AtomicBoolean()
        private var temporaryOpens = 0

        override fun open(file: File): CatalogDatabase {
            if (file.name.contains(".migration-") && ++temporaryOpens == 2 && replaced.compareAndSet(false, true)) {
                JdbcCatalogDatabaseFactory.open(destination).use { direct ->
                    SaveSnapshotMigration.prepare(direct)
                    direct.execute(
                        """
                        INSERT OR REPLACE INTO save_snapshot (
                            id, rom_sha256, save_identity, save_schema_id, payload_json,
                            source_last_modified_epoch_ms, refreshed_at_epoch_ms
                        ) VALUES (1, ?, ?, ?, ?, ?, ?)
                        """.trimIndent(),
                        listOf(
                            replacement.romIdentity,
                            replacement.saveIdentity,
                            replacement.schemaId,
                            Gson().toJson(replacement),
                            sourceLastModifiedEpochMs,
                            refreshedAtEpochMs,
                        ),
                    )
                }
            }
            return JdbcCatalogDatabaseFactory.open(file)
        }
    }

    private class SnapshotPayloadProjectionRejectingFactory : CatalogDatabaseFactory {
        var cursorPayloadProjections = 0
            private set

        override fun open(file: File): CatalogDatabase {
            val delegate = JdbcCatalogDatabaseFactory.open(file)
            return object : CatalogDatabase by delegate {
                override fun <T> query(
                    sql: String,
                    arguments: List<Any?>,
                    map: (CatalogRow) -> T,
                ): List<T> {
                    val projection = sql.substringBefore("FROM", missingDelimiterValue = sql)
                        .replace(
                            Regex(
                                "length\\s*\\(\\s*cast\\s*\\(\\s*payload_json\\s+as\\s+blob\\s*\\)\\s*\\)",
                                RegexOption.IGNORE_CASE,
                            ),
                            "",
                        )
                    if (Regex("\\bpayload_json\\b", RegexOption.IGNORE_CASE).containsMatchIn(projection)) {
                        cursorPayloadProjections++
                        throw AssertionError("cursor-backed query projected snapshot JSON")
                    }
                    return delegate.query(sql, arguments, map)
                }
            }
        }
    }

    private class OversizedPayloadGuardFactory : CatalogDatabaseFactory {
        var payloadStringReads = 0
            private set

        override fun open(file: File): CatalogDatabase {
            val delegate = JdbcCatalogDatabaseFactory.open(file)
            return object : CatalogDatabase by delegate {
                override fun <T> query(
                    sql: String,
                    arguments: List<Any?>,
                    map: (CatalogRow) -> T,
                ): List<T> = delegate.query(sql, arguments) { row ->
                    map(
                        object : CatalogRow by row {
                            override fun long(column: String): Long? = when (column) {
                                "payload_bytes" -> MAX_TEST_SNAPSHOT_BYTES + 1L
                                else -> row.long(column)
                            }

                            override fun string(column: String): String? {
                                if (column == "payload_json") {
                                    payloadStringReads++
                                    throw AssertionError("snapshot JSON was retrieved before its byte length was validated")
                                }
                                return row.string(column)
                            }
                        },
                    )
                }
            }
        }
    }

    private class FailingTemporaryMigrationFactory : CatalogDatabaseFactory {
        override fun open(file: File): CatalogDatabase {
            val delegate = JdbcCatalogDatabaseFactory.open(file)
            if (!file.name.contains(".migration-")) return delegate
            return object : CatalogDatabase by delegate {
                override fun execute(sql: String, arguments: List<Any?>) {
                    if (sql.contains("INSERT OR REPLACE INTO save_snapshot")) {
                        throw IllegalStateException("injected migration interruption")
                    }
                    delegate.execute(sql, arguments)
                }
            }
        }
    }

    private class ReplacementBeforeQuarantineFactory(
        private val databaseFile: File,
        private val replacement: SaveSnapshot,
        private val sourceLastModifiedEpochMs: Long,
        private val refreshedAtEpochMs: Long,
    ) : CatalogDatabaseFactory {
        val replaced = AtomicBoolean()

        override fun open(file: File): CatalogDatabase {
            val delegate = JdbcCatalogDatabaseFactory.open(file)
            return object : CatalogDatabase by delegate {
                override fun execute(sql: String, arguments: List<Any?>) {
                    if (sql.trimStart().startsWith("DELETE FROM save_snapshot") && replaced.compareAndSet(false, true)) {
                        JdbcCatalogDatabaseFactory.open(databaseFile).use { direct ->
                            direct.execute(
                                """
                                INSERT OR REPLACE INTO save_snapshot (
                                    id, rom_sha256, save_identity, save_schema_id, payload_json,
                                    source_last_modified_epoch_ms, refreshed_at_epoch_ms
                                ) VALUES (1, ?, ?, ?, ?, ?, ?)
                                """.trimIndent(),
                                listOf(
                                    replacement.romIdentity,
                                    replacement.saveIdentity,
                                    replacement.schemaId,
                                    Gson().toJson(replacement),
                                    sourceLastModifiedEpochMs,
                                    refreshedAtEpochMs,
                                ),
                            )
                        }
                    }
                    delegate.execute(sql, arguments)
                }
            }
        }
    }

    private fun fixture(romHash: String, counter: Long, species: Int) = SaveSnapshot(
        romIdentity = romHash,
        saveIdentity = "save-$counter",
        saveGeneration = 3,
        saveCounter = counter,
        currentArea = SavedArea(2, 3),
        seenDexNumbers = setOf(species),
        caughtDexNumbers = setOf(species),
        party = listOf(OwnedIndividual("party-0", species, level = 12, ivs = List(6) { 20 }, captureBallId = 4)),
        storedIndividuals = emptyList(),
        capabilities = mapOf(
            SaveCapability.SAVE_SLOT to SaveCapabilityEvidence(SaveCapability.SAVE_SLOT, SaveCapabilityStatus.AVAILABLE, 14),
        ),
    )

    private companion object {
        const val MAX_TEST_SNAPSHOT_BYTES = 4L * 1024L * 1024L
        const val MALFORMED_PAYLOAD = "{"
    }
}
