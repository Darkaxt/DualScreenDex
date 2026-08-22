package com.darkaxt.dualdex.knowledge

import com.darkaxt.dualdex.retroarch.RomIndexEntry
import com.darkaxt.dualdex.retroarch.RomPlatform
import com.darkaxt.dualdex.save.DirectSaveDocumentResolver
import com.darkaxt.dualdex.save.SaveMonitorResult
import com.darkaxt.dualdex.save.SaveMonitorStatus
import com.darkaxt.dualdex.save.SaveObservation
import com.darkaxt.dualdex.save.SaveObservationKind
import com.darkaxt.dualdex.save.SaveSnapshot
import com.darkaxt.dualdex.web.ProductionCompanionRuntime
import com.enrpau.dualscreendex.companion.api.SaveRamView
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.MessageDigest

class SaveKnowledgeCheckpointRestartIntegrationTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun directSaveChangeRestoresTheExactCheckpointAfterRuntimeRestart() {
        val romSha = "a".repeat(64)
        val saveIdentity = "b".repeat(64)
        val root = temporary.newFolder("saves")
        val save = root.resolve("Game.srm").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val fallback = temporary.newFolder("fallback")
        val rom = RomIndexEntry(
            sourceId = "file:///Game.gba",
            sourceName = "Game.gba",
            archiveEntry = null,
            platform = RomPlatform.GBA,
            gameBasename = "Game",
            crc32 = "12345678",
            sha256 = romSha,
        )
        val catalog = ParsedCatalog(romSha, EngineFamily.EMERALD, Platform.GBA)
        val store = SaveKnowledgeCheckpointStore(fallback)
        val firstRuntime = ProductionCompanionRuntime().apply { loadCatalog("Game.gba", catalog) }
        val firstCoordinator = SaveKnowledgeCheckpointCoordinator(store, firstRuntime::applySaveObservation)
        val firstSource = DirectSaveDocumentResolver.discover(rom, listOf(root)).single()
        val firstSnapshot = snapshot(romSha, saveIdentity, 1)

        firstCoordinator.apply(
            result(firstSource, firstSnapshot, SaveObservationKind.INITIAL),
            SaveRamView(status = "MATCHED"),
        )
        firstRuntime.action("MAP_POI_SETTINGS", mapOf("showPlaces" to "false"))
        save.writeBytes(byteArrayOf(5, 6, 7, 8))
        assertTrue(save.setLastModified(firstSource.lastModifiedEpochMs + 2_000))
        val changedSource = DirectSaveDocumentResolver.refresh(listOf(firstSource)).single()
        val changedSnapshot = snapshot(romSha, saveIdentity, 2)
        firstCoordinator.apply(
            result(changedSource, changedSnapshot, SaveObservationKind.CHANGED),
            SaveRamView(status = "MATCHED"),
        )
        firstRuntime.close()

        assertTrue(root.resolve("Game.srm.dualdex.json").isFile)
        val reopenedRuntime = ProductionCompanionRuntime().apply { loadCatalog("Game.gba", catalog) }
        val reopenedCoordinator = SaveKnowledgeCheckpointCoordinator(store, reopenedRuntime::applySaveObservation)
        reopenedCoordinator.apply(
            result(changedSource, changedSnapshot, SaveObservationKind.INITIAL),
            SaveRamView(status = "MATCHED"),
        )

        assertFalse(reopenedRuntime.stateView().localMapPoiPreferences.showPlaces)
        reopenedRuntime.close()
    }

    private fun result(
        source: com.darkaxt.dualdex.save.SaveDocumentSource,
        snapshot: SaveSnapshot,
        kind: SaveObservationKind,
    ): SaveMonitorResult {
        val bytes = source.read()
        val observation = SaveObservation(
            kind,
            source,
            SaveFileFingerprint(sha256(bytes), bytes.size.toLong(), source.lastModifiedEpochMs),
        )
        return SaveMonitorResult(
            status = SaveMonitorStatus.MATCHED,
            autosaveStatus = "ON",
            source = source,
            snapshot = snapshot,
            observation = observation,
        )
    }

    private fun snapshot(romSha: String, saveIdentity: String, counter: Long) = SaveSnapshot(
        romIdentity = romSha,
        saveIdentity = saveIdentity,
        saveGeneration = 3,
        saveCounter = counter,
        currentArea = null,
        seenDexNumbers = emptySet(),
        caughtDexNumbers = emptySet(),
        party = emptyList(),
        storedIndividuals = emptyList(),
        capabilities = emptyMap(),
    )

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }
}
