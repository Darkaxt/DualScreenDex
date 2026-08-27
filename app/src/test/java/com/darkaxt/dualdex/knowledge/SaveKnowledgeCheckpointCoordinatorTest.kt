package com.darkaxt.dualdex.knowledge

import com.darkaxt.dualdex.save.SaveDocumentSource
import com.darkaxt.dualdex.save.SaveMonitorResult
import com.darkaxt.dualdex.save.SaveMonitorStatus
import com.darkaxt.dualdex.save.SaveObservation
import com.darkaxt.dualdex.save.SaveObservationKind
import com.darkaxt.dualdex.save.SaveSnapshot
import com.darkaxt.dualdex.live.RecoveryApplication
import com.darkaxt.dualdex.catalog.StoredSaveSnapshot
import com.enrpau.dualscreendex.companion.api.SaveRamView
import com.enrpau.dualscreendex.companion.model.KnowledgeLedger
import com.darkaxt.dualdex.progress.PlaythroughJournal
import com.darkaxt.dualdex.progress.PlaythroughJournalCoordinator
import com.darkaxt.dualdex.progress.PlaythroughJournalSession
import com.enrpau.dualscreendex.companion.semantic.PlaythroughKey
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
        open = { byteArrayOf(1, 2, 3, 4).inputStream() },
    )

    @Test
    fun initialObservationReadsButDoesNotWriteCheckpoint() {
        val checkpoints = RecordingCheckpoints(KnowledgeLedger(seenSpecies = setOf(25)))
        var supplied: KnowledgeLedger? = null
        val coordinator = SaveKnowledgeCheckpointCoordinator(
            checkpoints,
            applyRecovery = { projection ->
                supplied = projection.checkpointLedger
                RecoveryApplication(true)
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
        val journal = PlaythroughJournalCoordinator(PlaythroughKey(romSha, saveIdentity)).also {
            it.updatePreferences(mapOf("trainer-progress-section" to "TIMELINE"))
        }
        val coordinator = SaveKnowledgeCheckpointCoordinator(
            checkpoints,
            applyRecovery = { RecoveryApplication(true, frozen) },
            journal = journal,
            clock = { 500 },
        )

        coordinator.apply(result(SaveObservationKind.CHANGED, 2), SaveRamView(status = "MATCHED"))

        assertEquals(0, checkpoints.reads)
        assertEquals(1, checkpoints.writes.size)
        assertEquals(frozen, checkpoints.writes.single().ledger)
        assertEquals("TIMELINE", checkpoints.writes.single().journal?.preferences?.get("trainer-progress-section"))
        assertEquals(500, checkpoints.writes.single().capturedAtEpochMs)
    }

    @Test
    fun rejectedRecoveryIdentityNeverRestoresItsJournal() {
        val rejected = result(SaveObservationKind.INITIAL, 1)
        val restoredJournal = PlaythroughJournal.empty(PlaythroughKey(romSha, saveIdentity)).copy(
            preferences = mapOf("trainer-progress-section" to "REJECTED"),
        )
        var journalRestores = 0
        val journal = object : PlaythroughJournalSession {
            override fun restore(restored: PlaythroughJournal): Boolean {
                journalRestores++
                return true
            }

            override fun current(playthrough: PlaythroughKey): PlaythroughJournal? = null
        }
        val checkpoints = object : KnowledgeCheckpointStore {
            override fun readExact(source: SaveDocumentSource, key: SaveCheckpointKey): KnowledgeLedger? = null
            override fun readCheckpointExact(source: SaveDocumentSource, key: SaveCheckpointKey) = SaveKnowledgeCheckpoint(
                portable = false,
                key = key,
                capturedAtEpochMs = 1,
                ledger = KnowledgeLedger(),
                journal = restoredJournal,
            )

            override fun write(source: SaveDocumentSource, checkpoint: SaveKnowledgeCheckpoint) =
                CheckpointStorage.APP_PRIVATE_FALLBACK
        }
        val coordinator = SaveKnowledgeCheckpointCoordinator(
            checkpoints,
            applyRecovery = { RecoveryApplication(false) },
            journal = journal,
        )

        assertTrue(!coordinator.apply(rejected, SaveRamView(status = "MATCHED")))

        assertEquals(0, journalRestores)
    }

    @Test
    fun unchangedIncludingRetainedSnapshotsApplyButNeverReadOrWrite() {
        val checkpoints = RecordingCheckpoints(KnowledgeLedger(seenSpecies = setOf(25)))
        var applications = 0
        val coordinator = SaveKnowledgeCheckpointCoordinator(
            checkpoints,
            applyRecovery = { applications++; RecoveryApplication(true) },
        )

        coordinator.apply(result(SaveObservationKind.UNCHANGED, 1), SaveRamView(status = "MATCHED"))
        val retainedObservation = result(SaveObservationKind.UNCHANGED, 1).observation
        coordinator.apply(
            SaveMonitorResult(
                SaveMonitorStatus.MATCHED,
                "ON",
                retained = StoredSaveSnapshot(snapshot(1), 1, 1),
                observation = retainedObservation,
            ),
            SaveRamView(status = "MATCHED"),
        )
        coordinator.apply(
            SaveMonitorResult(SaveMonitorStatus.MATCHED, "ON", snapshot = snapshot(1)),
            SaveRamView(status = "MATCHED"),
        )
        coordinator.apply(
            SaveMonitorResult(SaveMonitorStatus.AMBIGUOUS, "ON"),
            SaveRamView(status = "AMBIGUOUS"),
        )

        assertEquals(2, applications)
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
