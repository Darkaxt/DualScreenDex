package com.darkaxt.dualdex.knowledge

import com.darkaxt.dualdex.save.SaveDocumentSource
import com.darkaxt.dualdex.save.SaveMonitorResult
import com.darkaxt.dualdex.save.SaveMonitorStatus
import com.darkaxt.dualdex.save.SaveObservation
import com.darkaxt.dualdex.save.SaveObservationKind
import com.darkaxt.dualdex.save.SaveSnapshot
import com.darkaxt.dualdex.web.SaveKnowledgeApplication
import com.enrpau.dualscreendex.companion.api.SaveRamView
import com.enrpau.dualscreendex.companion.model.KnowledgeLedger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveKnowledgeCheckpointCoordinatorTest {
    private val romSha = "a".repeat(64)
    private val saveIdentity = "b".repeat(64)
    private val source = SaveDocumentSource(
        id = "file:///Game.srm",
        displayPath = "Game.srm",
        name = "Game.srm",
        size = 4,
        lastModifiedEpochMs = 100,
        read = { byteArrayOf(1, 2, 3, 4) },
    )

    @Test
    fun initialObservationReadsButDoesNotWriteCheckpoint() {
        val checkpoints = RecordingCheckpoints(KnowledgeLedger(seenSpecies = setOf(25)))
        var supplied: KnowledgeLedger? = null
        val coordinator = SaveKnowledgeCheckpointCoordinator(
            checkpoints,
            applyRuntime = { _, _, _, checkpoint ->
                supplied = checkpoint
                SaveKnowledgeApplication(true)
            },
            clock = { 500 },
        )

        assertTrue(coordinator.apply(result(SaveObservationKind.INITIAL, 1), SaveRamView(status = "MATCHED")))

        assertEquals(1, checkpoints.reads)
        assertEquals(0, checkpoints.writes.size)
        assertEquals(setOf(25), supplied?.seenSpecies)
    }

    @Test
    fun changedObservationWritesExactlyTheRuntimeFrozenLedger() {
        val checkpoints = RecordingCheckpoints(null)
        val frozen = KnowledgeLedger(seenSpecies = setOf(25, 133))
        val coordinator = SaveKnowledgeCheckpointCoordinator(
            checkpoints,
            applyRuntime = { _, _, _, _ -> SaveKnowledgeApplication(true, frozen) },
            clock = { 500 },
        )

        coordinator.apply(result(SaveObservationKind.CHANGED, 2), SaveRamView(status = "MATCHED"))

        assertEquals(0, checkpoints.reads)
        assertEquals(1, checkpoints.writes.size)
        assertEquals(frozen, checkpoints.writes.single().ledger)
        assertEquals(500, checkpoints.writes.single().capturedAtEpochMs)
    }

    @Test
    fun unchangedAndResultsWithoutLiveObservationsNeverReadOrWrite() {
        val checkpoints = RecordingCheckpoints(KnowledgeLedger(seenSpecies = setOf(25)))
        var applications = 0
        val coordinator = SaveKnowledgeCheckpointCoordinator(
            checkpoints,
            applyRuntime = { _, _, _, _ -> applications++; SaveKnowledgeApplication(true) },
        )

        coordinator.apply(result(SaveObservationKind.UNCHANGED, 1), SaveRamView(status = "MATCHED"))
        coordinator.apply(
            SaveMonitorResult(SaveMonitorStatus.MATCHED, "ON", snapshot = snapshot(1)),
            SaveRamView(status = "MATCHED"),
        )
        coordinator.apply(
            SaveMonitorResult(SaveMonitorStatus.AMBIGUOUS, "ON"),
            SaveRamView(status = "AMBIGUOUS"),
        )

        assertEquals(1, applications)
        assertEquals(0, checkpoints.reads)
        assertEquals(0, checkpoints.writes.size)
    }

    private fun result(kind: SaveObservationKind, version: Int): SaveMonitorResult {
        val observation = SaveObservation(
            kind,
            source.copy(lastModifiedEpochMs = version.toLong()),
            SaveFileFingerprint(version.toString(16).padStart(64, '0'), 4, version.toLong()),
        )
        return SaveMonitorResult(
            status = SaveMonitorStatus.MATCHED,
            autosaveStatus = "ON",
            source = observation.source,
            snapshot = snapshot(version.toLong()),
            observation = observation,
        )
    }

    private fun snapshot(counter: Long) = SaveSnapshot(
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

    private class RecordingCheckpoints(
        private val restored: KnowledgeLedger?,
    ) : KnowledgeCheckpointStore {
        var reads = 0
        val writes = mutableListOf<SaveKnowledgeCheckpoint>()

        override fun readExact(source: SaveDocumentSource, key: SaveCheckpointKey): KnowledgeLedger? {
            reads++
            return restored
        }

        override fun write(
            source: SaveDocumentSource,
            checkpoint: SaveKnowledgeCheckpoint,
        ): CheckpointStorage {
            writes += checkpoint
            return CheckpointStorage.APP_PRIVATE_FALLBACK
        }
    }
}
