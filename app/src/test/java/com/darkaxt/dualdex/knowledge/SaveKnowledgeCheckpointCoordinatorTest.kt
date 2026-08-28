package com.darkaxt.dualdex.knowledge

import com.darkaxt.dualdex.save.SaveDocumentSource
import com.darkaxt.dualdex.save.SaveMonitorResult
import com.darkaxt.dualdex.save.SaveMonitorStatus
import com.darkaxt.dualdex.save.SaveObservation
import com.darkaxt.dualdex.save.SaveObservationKind
import com.darkaxt.dualdex.save.SaveSnapshot
import com.darkaxt.dualdex.live.RecoveryApplication
import com.darkaxt.dualdex.live.RecoveryProjection
import com.darkaxt.dualdex.catalog.StoredSaveSnapshot
import com.enrpau.dualscreendex.companion.api.SaveRamView
import com.enrpau.dualscreendex.companion.model.KnowledgeLedger
import com.darkaxt.dualdex.progress.PlaythroughJournal
import com.darkaxt.dualdex.progress.PlaythroughJournalCoordinator
import com.darkaxt.dualdex.progress.PlaythroughJournalSession
import com.enrpau.dualscreendex.companion.semantic.GameEvent
import com.enrpau.dualscreendex.companion.semantic.PlaythroughKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun initialObservationReadsAndDurablyRebindsCheckpointToTheAcceptedSnapshot() {
        val checkpoints = RecordingCheckpoints(KnowledgeLedger(seenSpecies = setOf(25)))
        var supplied: KnowledgeLedger? = null
        val coordinator = SaveKnowledgeCheckpointCoordinator(
            checkpoints,
            prepareRecovery = prepare { projection ->
                supplied = projection.checkpointLedger
                RecoveryApplication(true)
            },
            clock = { 500 },
        )

        val versionId = "01234567-89ab-cdef-0123-456789abcdef"
        val stagedSnapshot = object : com.darkaxt.dualdex.catalog.StagedSaveSnapshot {
            override val snapshot = snapshot(1)
            override val versionId = versionId
            override val snapshotDigestSha256 = requireNotNull(safeSaveSnapshotDigest(snapshot))
        }
        assertTrue(
            coordinator.apply(
                result(SaveObservationKind.INITIAL, 1),
                SaveRamView(status = "MATCHED"),
                stagePrepared = {
                    com.darkaxt.dualdex.save.PreparedSavePersistence(stagedSnapshot, romSha, source.id)
                },
                commitPrepared = { _, publishAuthority -> publishAuthority() },
            ),
        )

        assertEquals(1, checkpoints.reads)
        assertEquals(1, checkpoints.writes.size)
        assertEquals(setOf(25), checkpoints.writes.single().ledger.seenSpecies)
        assertEquals(safeSaveSnapshotDigest(snapshot(1)), checkpoints.writes.single().snapshotDigestSha256)
        assertEquals(versionId, checkpoints.writes.single().snapshotVersionId)
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
            prepareRecovery = prepare { RecoveryApplication(true, frozen) },
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
            override fun read(source: SaveDocumentSource, key: SaveCheckpointKey) = CheckpointReadResult.Present(
                SaveKnowledgeCheckpoint(
                    portable = false,
                    key = key,
                    capturedAtEpochMs = 1,
                    ledger = KnowledgeLedger(),
                    journal = restoredJournal,
                ),
            )

            override fun write(source: SaveDocumentSource, checkpoint: SaveKnowledgeCheckpoint) =
                CheckpointWriteResult.Durable(CheckpointStorage.APP_PRIVATE_FALLBACK)
        }
        val coordinator = SaveKnowledgeCheckpointCoordinator(
            checkpoints,
            prepareRecovery = prepare { RecoveryApplication(false) },
            journal = journal,
        )

        assertTrue(!coordinator.apply(rejected, SaveRamView(status = "MATCHED")))

        assertEquals(0, journalRestores)
    }

    @Test
    fun recoveryJournalRestoreMergesAnEventAcceptedDuringPreparedRecovery() {
        val playthrough = PlaythroughKey(romSha, saveIdentity)
        val journal = PlaythroughJournalCoordinator(playthrough)
        val persistedJournal = PlaythroughJournal.empty(playthrough).copy(
            preferences = mapOf("section" to "persisted"),
        )
        val checkpoints = object : KnowledgeCheckpointStore {
            override fun read(source: SaveDocumentSource, key: SaveCheckpointKey) = CheckpointReadResult.Present(
                SaveKnowledgeCheckpoint(
                    portable = false,
                    key = key,
                    capturedAtEpochMs = 1,
                    ledger = KnowledgeLedger(),
                    journal = persistedJournal,
                ),
            )

            override fun write(source: SaveDocumentSource, checkpoint: SaveKnowledgeCheckpoint) =
                CheckpointWriteResult.Durable(CheckpointStorage.APP_PRIVATE_FALLBACK)
        }
        val coordinator = SaveKnowledgeCheckpointCoordinator(
            checkpoints,
            prepareRecovery = prepare {
                journal.accept(listOf(GameEvent.Captured(133)))
                RecoveryApplication(true)
            },
            journal = journal,
        )

        assertTrue(coordinator.apply(result(SaveObservationKind.INITIAL, 1), SaveRamView(status = "MATCHED")))

        assertEquals(setOf(133), journal.current().capturedDexNumbers)
        assertEquals(1L, journal.current().trackedCounts["captures"])
        assertEquals("persisted", journal.current().preferences["section"])
    }

    @Test
    fun journalRestoreMemoryFailureCannotEscapeAfterCoreAuthorityIsAccepted() {
        val playthrough = PlaythroughKey(romSha, saveIdentity)
        val restoredJournal = PlaythroughJournal.empty(playthrough).copy(
            preferences = mapOf("section" to "persisted"),
        )
        val checkpoints = object : KnowledgeCheckpointStore {
            override fun read(source: SaveDocumentSource, key: SaveCheckpointKey) = CheckpointReadResult.Present(
                SaveKnowledgeCheckpoint(
                    portable = false,
                    key = key,
                    capturedAtEpochMs = 1,
                    ledger = KnowledgeLedger(),
                    journal = restoredJournal,
                ),
            )

            override fun write(source: SaveDocumentSource, checkpoint: SaveKnowledgeCheckpoint) =
                CheckpointWriteResult.Durable(CheckpointStorage.APP_PRIVATE_FALLBACK)
        }
        val journal = object : PlaythroughJournalSession {
            override fun restore(restored: PlaythroughJournal): Boolean =
                throw OutOfMemoryError("injected journal restore allocation failure")

            override fun current(playthrough: PlaythroughKey): PlaythroughJournal? = null
        }
        val coordinator = SaveKnowledgeCheckpointCoordinator(
            checkpoints,
            prepareRecovery = prepare { RecoveryApplication(true) },
            journal = journal,
        )

        assertTrue(coordinator.apply(result(SaveObservationKind.INITIAL, 1), SaveRamView(status = "MATCHED")))
    }

    @Test
    fun staleTokenFencePreventsCheckpointJournalRecoveryAndStatusMutation() {
        var reads = 0
        var writes = 0
        var preparations = 0
        var stagedSnapshots = 0
        var snapshotAuthorityCommits = 0
        val completedSnapshotStages = mutableListOf<Boolean>()
        var recoveries = 0
        var journalRestores = 0
        var statusPublications = 0
        val checkpoints = object : KnowledgeCheckpointStore {
            override fun read(source: SaveDocumentSource, key: SaveCheckpointKey): CheckpointReadResult {
                reads++
                return CheckpointReadResult.Absent
            }

            override fun write(source: SaveDocumentSource, checkpoint: SaveKnowledgeCheckpoint): CheckpointWriteResult {
                writes++
                return CheckpointWriteResult.Durable(CheckpointStorage.APP_PRIVATE_FALLBACK)
            }
        }
        val journal = object : PlaythroughJournalSession {
            override fun restore(restored: PlaythroughJournal): Boolean {
                journalRestores++
                return true
            }

            override fun current(playthrough: PlaythroughKey): PlaythroughJournal? = null
        }
        val coordinator = SaveKnowledgeCheckpointCoordinator(
            checkpoints = checkpoints,
            prepareRecovery = {
                preparations++
                val application = RecoveryApplication(true, KnowledgeLedger())
                RecoveryPreparation(application) { publishAuthority ->
                    if (publishAuthority()) {
                        recoveries++
                        application
                    } else {
                        RecoveryApplication(false)
                    }
                }
            },
            journal = journal,
            publishRecoveryStatus = { statusPublications++ },
        )

        assertFalse(
            coordinator.apply(
                result(SaveObservationKind.CHANGED, 2),
                SaveRamView(status = "MATCHED"),
                commitIfCurrent = { false },
                stagePrepared = {
                    stagedSnapshots++
                    com.darkaxt.dualdex.save.PreparedSavePersistence.none()
                },
                commitPrepared = { _, publishAuthority ->
                    snapshotAuthorityCommits++
                    publishAuthority()
                },
                completePrepared = { _, accepted -> completedSnapshotStages += accepted },
            ),
        )

        assertEquals(0, reads)
        assertEquals("stale work must stage only and never publish an accepted checkpoint", 0, writes)
        assertEquals(1, stagedSnapshots)
        assertEquals(0, snapshotAuthorityCommits)
        assertEquals(listOf(false), completedSnapshotStages)
        assertEquals(1, preparations)
        assertEquals(0, recoveries)
        assertEquals(0, journalRestores)
        assertEquals(0, statusPublications)
    }

    @Test
    fun unavailableInitialCheckpointRetainsKnowledgeAndRecoversOnRetry() {
        var readResult: CheckpointReadResult = CheckpointReadResult.Unavailable(
            CheckpointStorage.PORTABLE_SIDECAR,
        )
        var recoveryApplications = 0
        val statuses = mutableListOf<SaveRamView>()
        val checkpoints = object : KnowledgeCheckpointStore {
            override fun read(source: SaveDocumentSource, key: SaveCheckpointKey) = readResult
            override fun write(source: SaveDocumentSource, checkpoint: SaveKnowledgeCheckpoint) =
                CheckpointWriteResult.Durable(CheckpointStorage.PORTABLE_SIDECAR)
        }
        val coordinator = SaveKnowledgeCheckpointCoordinator(
            checkpoints = checkpoints,
            prepareRecovery = prepare { recoveryApplications++; RecoveryApplication(true) },
            publishRecoveryStatus = { status -> statuses.add(status) },
        )

        assertFalse(coordinator.apply(result(SaveObservationKind.INITIAL, 1), SaveRamView(status = "MATCHED")))
        assertEquals(0, recoveryApplications)
        assertEquals("STALE", statuses.single().status)

        readResult = CheckpointReadResult.Present(
            SaveKnowledgeCheckpoint(
                portable = true,
                key = requireNotNull(result(SaveObservationKind.INITIAL, 1).observation).key(snapshot(1)),
                capturedAtEpochMs = 1,
                ledger = KnowledgeLedger(seenSpecies = setOf(25)),
            ),
        )

        assertTrue(coordinator.apply(result(SaveObservationKind.INITIAL, 1), SaveRamView(status = "MATCHED")))
        assertEquals(1, recoveryApplications)
    }

    @Test
    fun pendingInitialReadRetriesAcrossRepeatedUnchangedObservationsUntilTerminal() {
        var reads = 0
        val applications = mutableListOf<KnowledgeLedger?>()
        val checkpoints = object : KnowledgeCheckpointStore {
            override fun read(source: SaveDocumentSource, key: SaveCheckpointKey): CheckpointReadResult {
                reads++
                return if (reads < 3) {
                    CheckpointReadResult.Unavailable(CheckpointStorage.APP_PRIVATE_FALLBACK)
                } else {
                    CheckpointReadResult.Present(
                        SaveKnowledgeCheckpoint(
                            portable = false,
                            key = key,
                            capturedAtEpochMs = 1,
                            ledger = KnowledgeLedger(seenSpecies = setOf(25)),
                        ),
                    )
                }
            }

            override fun write(source: SaveDocumentSource, checkpoint: SaveKnowledgeCheckpoint) =
                CheckpointWriteResult.Durable(CheckpointStorage.APP_PRIVATE_FALLBACK)
        }
        val coordinator = SaveKnowledgeCheckpointCoordinator(
            checkpoints,
            prepareRecovery = prepare { projection ->
                applications += projection.checkpointLedger
                RecoveryApplication(true)
            },
        )

        assertFalse(coordinator.apply(result(SaveObservationKind.INITIAL, 1), SaveRamView(status = "MATCHED")))
        assertFalse(coordinator.apply(result(SaveObservationKind.UNCHANGED, 1), SaveRamView(status = "MATCHED")))
        assertTrue(coordinator.apply(result(SaveObservationKind.UNCHANGED, 1), SaveRamView(status = "MATCHED")))

        assertEquals(3, reads)
        assertEquals(listOf(setOf(25)), applications.map { it?.seenSpecies })
    }

    @Test
    fun persistedSnapshotUsesTheSameTypedCheckpointGateBeforePublishingMatched() {
        val persisted = SaveKnowledgeCheckpoint(
            portable = false,
            key = requireNotNull(result(SaveObservationKind.CHANGED, 2).observation).key(snapshot(2)),
            capturedAtEpochMs = 2,
            ledger = KnowledgeLedger(seenSpecies = setOf(25)),
            sourceId = source.id,
            snapshotDigestSha256 = safeSaveSnapshotDigest(snapshot(2)),
        )
        var latest: CheckpointReadResult = CheckpointReadResult.Unavailable(
            CheckpointStorage.APP_PRIVATE_FALLBACK,
        )
        var applications = 0
        val statuses = mutableListOf<SaveRamView>()
        val checkpoints = object : KnowledgeCheckpointStore {
            override fun read(source: SaveDocumentSource, key: SaveCheckpointKey) = latest
            override fun readLatest(romSha256: String) = latest
            override fun write(source: SaveDocumentSource, checkpoint: SaveKnowledgeCheckpoint) =
                CheckpointWriteResult.Durable(CheckpointStorage.APP_PRIVATE_FALLBACK)
        }
        val coordinator = SaveKnowledgeCheckpointCoordinator(
            checkpoints,
            prepareRecovery = prepare { projection ->
                applications++
                assertEquals(setOf(25), projection.checkpointLedger?.seenSpecies)
                RecoveryApplication(true)
            },
            publishRecoveryStatus = statuses::add,
        )
        val restored = SaveMonitorResult(
            status = SaveMonitorStatus.MATCHED,
            autosaveStatus = "ON",
            snapshot = snapshot(2),
            retained = StoredSaveSnapshot(snapshot(2), 2, 2),
        )

        assertFalse(coordinator.applyPersisted(restored, SaveRamView(status = "MATCHED")))
        assertEquals(0, applications)
        assertEquals("STALE", statuses.single().status)
        latest = CheckpointReadResult.Present(persisted)
        val mismatched = restored.copy(
            snapshot = snapshot(3),
            retained = StoredSaveSnapshot(snapshot(3), 3, 3),
        )
        assertFalse(coordinator.applyPersisted(mismatched, SaveRamView(status = "MATCHED")))
        assertEquals(0, applications)
        assertTrue(coordinator.applyPersisted(restored, SaveRamView(status = "MATCHED")))
        assertEquals(1, applications)
    }

    @Test
    fun failedCheckpointWriteCannotClaimRecoverySuccess() {
        val statuses = mutableListOf<SaveRamView>()
        val checkpoints = object : KnowledgeCheckpointStore {
            override fun read(source: SaveDocumentSource, key: SaveCheckpointKey) = CheckpointReadResult.Absent
            override fun write(source: SaveDocumentSource, checkpoint: SaveKnowledgeCheckpoint) =
                CheckpointWriteResult.Failed(CheckpointStorage.APP_PRIVATE_FALLBACK)
        }
        val coordinator = SaveKnowledgeCheckpointCoordinator(
            checkpoints = checkpoints,
            prepareRecovery = prepare { RecoveryApplication(true, KnowledgeLedger(seenSpecies = setOf(25))) },
            publishRecoveryStatus = { status -> statuses.add(status) },
        )

        assertFalse(coordinator.apply(result(SaveObservationKind.CHANGED, 2), SaveRamView(status = "MATCHED")))
        assertEquals("STALE", statuses.single().status)
    }

    @Test
    fun failedSnapshotPreparationCannotPublishCheckpointOrRecoveryAuthority() {
        val checkpoints = RecordingCheckpoints(null)
        val statuses = mutableListOf<SaveRamView>()
        var recoveryCommits = 0
        val coordinator = SaveKnowledgeCheckpointCoordinator(
            checkpoints,
            prepareRecovery = {
                val application = RecoveryApplication(true, KnowledgeLedger(seenSpecies = setOf(25)))
                RecoveryPreparation(application) { publishAuthority ->
                    if (publishAuthority()) {
                        recoveryCommits++
                        application
                    } else {
                        RecoveryApplication(false)
                    }
                }
            },
            publishRecoveryStatus = statuses::add,
        )

        assertFalse(
            coordinator.apply(
                result(SaveObservationKind.CHANGED, 2),
                SaveRamView(status = "MATCHED"),
                stagePrepared = { com.darkaxt.dualdex.save.PreparedSavePersistence.none() },
                commitPrepared = { _, _ -> false },
            ),
        )

        assertEquals(0, checkpoints.writes.size)
        assertEquals(0, recoveryCommits)
        assertEquals("STALE", statuses.single().status)
    }

    @Test
    fun restartAfterFailedWriteReopensOldDurableKnowledgeInsteadOfPendingMutation() {
        val oldResult = result(SaveObservationKind.INITIAL, 1)
        val oldKey = requireNotNull(oldResult.observation).key(snapshot(1))
        val old = SaveKnowledgeCheckpoint(
            portable = false,
            key = oldKey,
            capturedAtEpochMs = 1,
            ledger = KnowledgeLedger(seenSpecies = setOf(25)),
            sourceId = source.id,
            snapshotDigestSha256 = safeSaveSnapshotDigest(snapshot(1)),
        )
        val store = object : KnowledgeCheckpointStore {
            override fun read(source: SaveDocumentSource, key: SaveCheckpointKey) =
                if (key == oldKey) CheckpointReadResult.Present(old) else CheckpointReadResult.Absent

            override fun readLatest(romSha256: String) = CheckpointReadResult.Present(old)

            override fun write(source: SaveDocumentSource, checkpoint: SaveKnowledgeCheckpoint) =
                CheckpointWriteResult.Failed(CheckpointStorage.APP_PRIVATE_FALLBACK)
        }
        var failedCommitApplications = 0
        val failed = SaveKnowledgeCheckpointCoordinator(
            store,
            prepareRecovery = {
                val application = RecoveryApplication(true, KnowledgeLedger(seenSpecies = setOf(133)))
                RecoveryPreparation(application) { publishAuthority ->
                    if (publishAuthority()) {
                        failedCommitApplications++
                        application
                    } else {
                        RecoveryApplication(false)
                    }
                }
            },
        )

        assertFalse(failed.apply(result(SaveObservationKind.CHANGED, 2), SaveRamView(status = "MATCHED")))
        assertEquals(0, failedCommitApplications)

        var restoredLedger: KnowledgeLedger? = null
        val reopened = SaveKnowledgeCheckpointCoordinator(
            store,
            prepareRecovery = prepare { projection ->
                restoredLedger = projection.checkpointLedger
                RecoveryApplication(true)
            },
        )
        val persisted = SaveMonitorResult(
            status = SaveMonitorStatus.MATCHED,
            autosaveStatus = "ON",
            snapshot = snapshot(1),
            retained = StoredSaveSnapshot(snapshot(1), 1, 1),
        )

        assertTrue(reopened.applyPersisted(persisted, SaveRamView(status = "MATCHED")))
        assertEquals(setOf(25), restoredLedger?.seenSpecies)
    }

    @Test
    fun failedCheckpointWriteRetriesAfterStorageRecoversWithoutANewSaveChange() {
        var writable = false
        var writes = 0
        val checkpoints = object : KnowledgeCheckpointStore {
            override fun read(source: SaveDocumentSource, key: SaveCheckpointKey) = CheckpointReadResult.Absent

            override fun write(
                source: SaveDocumentSource,
                checkpoint: SaveKnowledgeCheckpoint,
            ): CheckpointWriteResult {
                writes++
                return if (writable) {
                    CheckpointWriteResult.Durable(CheckpointStorage.APP_PRIVATE_FALLBACK)
                } else {
                    CheckpointWriteResult.Failed(CheckpointStorage.APP_PRIVATE_FALLBACK)
                }
            }
        }
        val coordinator = SaveKnowledgeCheckpointCoordinator(
            checkpoints = checkpoints,
            prepareRecovery = prepare { RecoveryApplication(true, KnowledgeLedger(seenSpecies = setOf(25))) },
        )

        assertFalse(coordinator.apply(result(SaveObservationKind.CHANGED, 2), SaveRamView(status = "MATCHED")))
        writable = true
        assertTrue(coordinator.apply(result(SaveObservationKind.UNCHANGED, 2), SaveRamView(status = "MATCHED")))

        assertEquals(2, writes)
    }

    @Test
    fun unchangedIncludingRetainedSnapshotsApplyButNeverReadOrWrite() {
        val checkpoints = RecordingCheckpoints(KnowledgeLedger(seenSpecies = setOf(25)))
        var applications = 0
        val coordinator = SaveKnowledgeCheckpointCoordinator(
            checkpoints,
            prepareRecovery = prepare { applications++; RecoveryApplication(true) },
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

    private fun prepare(
        block: (RecoveryProjection) -> RecoveryApplication,
    ): (RecoveryProjection) -> RecoveryPreparation = { projection ->
        val application = block(projection)
        RecoveryPreparation(application) { publishAuthority ->
            if (publishAuthority()) application else RecoveryApplication(false)
        }
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

        override fun read(source: SaveDocumentSource, key: SaveCheckpointKey): CheckpointReadResult {
            reads++
            return restored?.let { ledger ->
                CheckpointReadResult.Present(
                    SaveKnowledgeCheckpoint(
                        portable = false,
                        key = key,
                        capturedAtEpochMs = 1,
                        ledger = ledger,
                    ),
                )
            } ?: CheckpointReadResult.Absent
        }

        override fun write(
            source: SaveDocumentSource,
            checkpoint: SaveKnowledgeCheckpoint,
        ): CheckpointWriteResult {
            writes += checkpoint
            return CheckpointWriteResult.Durable(CheckpointStorage.APP_PRIVATE_FALLBACK)
        }
    }
}
