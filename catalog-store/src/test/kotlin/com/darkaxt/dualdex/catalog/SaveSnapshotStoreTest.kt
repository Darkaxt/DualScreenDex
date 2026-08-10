package com.darkaxt.dualdex.catalog

import com.darkaxt.dualdex.save.OwnedIndividual
import com.darkaxt.dualdex.save.SaveCapability
import com.darkaxt.dualdex.save.SaveCapabilityEvidence
import com.darkaxt.dualdex.save.SaveCapabilityStatus
import com.darkaxt.dualdex.save.SaveSnapshot
import com.darkaxt.dualdex.save.SavedArea
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
            val second = fixture(romHash, counter = 5, species = 25)

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
