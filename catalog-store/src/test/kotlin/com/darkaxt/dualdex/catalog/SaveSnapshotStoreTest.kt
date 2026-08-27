package com.darkaxt.dualdex.catalog

import com.darkaxt.dualdex.save.OwnedIndividual
import com.darkaxt.dualdex.save.SaveCapability
import com.darkaxt.dualdex.save.SaveCapabilityEvidence
import com.darkaxt.dualdex.save.SaveCapabilityStatus
import com.darkaxt.dualdex.save.SaveSnapshot
import com.darkaxt.dualdex.save.SavedArea
import com.google.gson.Gson
import java.nio.file.Files
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
}
