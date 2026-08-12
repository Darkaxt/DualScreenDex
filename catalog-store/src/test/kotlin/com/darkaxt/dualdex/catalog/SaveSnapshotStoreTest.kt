package com.darkaxt.dualdex.catalog

import com.darkaxt.dualdex.save.OwnedIndividual
import com.darkaxt.dualdex.save.SaveCapability
import com.darkaxt.dualdex.save.SaveCapabilityEvidence
import com.darkaxt.dualdex.save.SaveCapabilityStatus
import com.darkaxt.dualdex.save.SaveSnapshot
import com.darkaxt.dualdex.save.SavedArea
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
            JdbcCatalogDatabaseFactory.open(directory.resolve("$romHash.sqlite")).use { database ->
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
