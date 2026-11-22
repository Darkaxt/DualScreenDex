package com.darkaxt.dualdex.save

import com.darkaxt.dualdex.catalog.SaveSnapshotRepository
import com.darkaxt.dualdex.catalog.StoredSaveSnapshot
import com.darkaxt.dualdex.knowledge.SaveFileFingerprint
import com.darkaxt.dualdex.setup.SessionEpochGate
import com.darkaxt.dualdex.setup.VerifiedSessionIdentity
import com.darkaxt.dualdex.save.SaveParseContext
import com.darkaxt.dualdex.save.SaveParseResult
import com.darkaxt.dualdex.save.SaveSnapshot
import com.darkaxt.dualdex.save.SaveSpeciesContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SavePollingMonitorTest {
    private val context = SaveParseContext(
        romIdentity = "a".repeat(64),
        speciesById = mapOf(1 to SaveSpeciesContext(1, 1, 0)),
    )

    @Test
    fun publishesOneValidatedSnapshotAndSkipsAnUnchangedDocument() {
        val repository = FakeSnapshots()
        val associations = FakeAssociations()
        var parses = 0
        var sourceReads = 0
        val monitor = SavePollingMonitor(
            associations,
            repository,
            parser = { _, parseContext -> parses++; SaveParseResult.Parsed(snapshot(parseContext.romIdentity, 7)) },
            clock = { 900L },
        )
        val source = source("save", modified = 100L, bytes = byteArrayOf(1, 2, 3)) { sourceReads++ }

        val first = monitor.poll(context, listOf(source), "VERIFIED")
        val second = monitor.poll(context, listOf(source), "VERIFIED")

        assertEquals(SaveMonitorStatus.MATCHED, first.status)
        assertEquals(SaveObservationKind.INITIAL, first.observation?.kind)
        assertEquals(sha256(byteArrayOf(1, 2, 3)), first.observation?.fingerprint?.sha256)
        assertEquals(7L, first.snapshot?.saveCounter)
        assertEquals(SaveMonitorStatus.MATCHED, second.status)
        assertEquals(SaveObservationKind.UNCHANGED, second.observation?.kind)
        assertNull(second.snapshot)
        assertEquals(1, parses)
        assertEquals(2, sourceReads)
        assertEquals(1, repository.reads)
        assertEquals(1, associations.selections)
        assertEquals(1, repository.writes)
        assertEquals("save", associations.selectedFor(context.romIdentity))
    }

    @Test
    fun sameMetadataCannotHideChangedBytes() {
        val repository = FakeSnapshots()
        var bytes = byteArrayOf(1, 2, 3)
        var parses = 0
        val monitor = SavePollingMonitor(
            FakeAssociations(),
            repository,
            parser = { input, parseContext ->
                parses++
                SaveParseResult.Parsed(snapshot(parseContext.romIdentity, input.first().toLong()))
            },
        )
        val source = source("save", 100L, bytes) { }
        val mutableSource = source.copy(open = { bytes.inputStream() })
        monitor.poll(context, listOf(mutableSource), "VERIFIED")

        bytes = byteArrayOf(2, 2, 3)
        val changed = monitor.poll(context, listOf(mutableSource), "VERIFIED")

        assertEquals(SaveMonitorStatus.MATCHED, changed.status)
        assertEquals(SaveObservationKind.CHANGED, changed.observation?.kind)
        assertEquals(2L, changed.snapshot?.saveCounter)
        assertEquals(2, parses)
        assertEquals(2, repository.writes)
    }

    @Test
    fun sameMetadataInvalidBytesRetainTheLastGoodSnapshot() {
        val repository = FakeSnapshots()
        var bytes = byteArrayOf(1, 2, 3)
        val monitor = SavePollingMonitor(
            FakeAssociations(),
            repository,
            parser = { input, parseContext ->
                if (input.first() == 0.toByte()) SaveParseResult.Unsupported(listOf("partial write"))
                else SaveParseResult.Parsed(snapshot(parseContext.romIdentity, input.first().toLong()))
            },
        )
        val source = source("save", 100L, bytes).copy(open = { bytes.inputStream() })
        monitor.poll(context, listOf(source), "VERIFIED")

        bytes = byteArrayOf(0, 2, 3)
        val stale = monitor.poll(context, listOf(source), "VERIFIED")

        assertEquals(SaveMonitorStatus.STALE, stale.status)
        assertEquals(1L, stale.retained?.snapshot?.saveCounter)
        assertEquals(1, repository.writes)
    }

    @Test
    fun classifiesChangedBytesOnceAndSwitchesOnSourceOrSaveIdentity() {
        val repository = FakeSnapshots()
        var saveIdentity = "b".repeat(64)
        var parses = 0
        val monitor = SavePollingMonitor(
            FakeAssociations(),
            repository,
            parser = { bytes, parseContext ->
                parses++
                SaveParseResult.Parsed(snapshot(parseContext.romIdentity, bytes.first().toLong(), saveIdentity))
            },
            clock = { 904L },
        )

        monitor.poll(context, listOf(source("first", 10, byteArrayOf(1))), "VERIFIED")
        val changed = monitor.poll(context, listOf(source("first", 20, byteArrayOf(2))), "VERIFIED")
        val switchedSource = monitor.poll(context, listOf(source("second", 30, byteArrayOf(3))), "VERIFIED")
        saveIdentity = "c".repeat(64)
        val switchedIdentity = monitor.poll(context, listOf(source("second", 40, byteArrayOf(4))), "VERIFIED")

        assertEquals(SaveObservationKind.CHANGED, changed.observation?.kind)
        assertEquals(sha256(byteArrayOf(2)), changed.observation?.fingerprint?.sha256)
        assertEquals(SaveObservationKind.SWITCHED, switchedSource.observation?.kind)
        assertEquals(SaveObservationKind.SWITCHED, switchedIdentity.observation?.kind)
        assertEquals(4, parses)
    }

    @Test
    fun retainsLastGoodSnapshotWhenTheNextCompleteReadIsInvalid() {
        val repository = FakeSnapshots()
        val associations = FakeAssociations()
        var valid = true
        val monitor = SavePollingMonitor(
            associations,
            repository,
            parser = { _, parseContext ->
                if (valid) SaveParseResult.Parsed(snapshot(parseContext.romIdentity, 3))
                else SaveParseResult.Unsupported(listOf("partial write"))
            },
            clock = { 901L },
        )

        monitor.poll(context, listOf(source("save", 100L, byteArrayOf(1))), "UNVERIFIED")
        valid = false
        val failed = monitor.poll(context, listOf(source("save", 101L, byteArrayOf(2))), "UNVERIFIED")

        assertEquals(SaveMonitorStatus.STALE, failed.status)
        assertEquals(3L, failed.retained?.snapshot?.saveCounter)
        assertEquals(1, repository.writes)
        assertEquals("UNVERIFIED", failed.autosaveStatus)
    }

    @Test
    fun validCandidateMatchesAfterACorruptRetainedSnapshotWasQuarantined() {
        val repository = FakeSnapshots()
        val monitor = SavePollingMonitor(
            FakeAssociations(),
            repository,
            parser = { _, parseContext ->
                SaveParseResult.Parsed(snapshot(parseContext.romIdentity, 12))
            },
            clock = { 950L },
        )

        val result = monitor.poll(
            context,
            listOf(source("current", 300, byteArrayOf(1, 2, 3))),
            "VERIFIED",
        )

        assertEquals(SaveMonitorStatus.MATCHED, result.status)
        assertEquals(12L, result.snapshot?.saveCounter)
        assertEquals(1, repository.writes)
        assertEquals(12L, repository.read(context.romIdentity)?.snapshot?.saveCounter)
    }

    @Test
    fun reportsEveryStructurallyValidCandidateInsteadOfGuessingWhenAmbiguous() {
        val monitor = SavePollingMonitor(
            FakeAssociations(),
            FakeSnapshots(),
            parser = { bytes, parseContext -> SaveParseResult.Parsed(snapshot(parseContext.romIdentity, bytes[0].toLong())) },
            clock = { 902L },
        )

        val result = monitor.poll(
            context,
            listOf(source("first", 10, byteArrayOf(1)), source("second", 20, byteArrayOf(2))),
            "DISABLED",
        )

        assertEquals(SaveMonitorStatus.AMBIGUOUS, result.status)
        assertEquals(listOf("first", "second"), result.candidates.map { it.id })
        assertNull(result.snapshot)
    }

    @Test
    fun fallsBackToAnotherValidCandidateWhenTheRememberedDocumentStopsValidating() {
        val associations = FakeAssociations().apply { remember(context.romIdentity, "old") }
        val monitor = SavePollingMonitor(
            associations,
            FakeSnapshots(),
            parser = { bytes, parseContext ->
                if (bytes.first() == 0.toByte()) SaveParseResult.Unsupported(listOf("invalid"))
                else SaveParseResult.Parsed(snapshot(parseContext.romIdentity, bytes.first().toLong()))
            },
            clock = { 903L },
        )

        val result = monitor.poll(
            context,
            listOf(source("old", 10, byteArrayOf(0)), source("replacement", 20, byteArrayOf(9))),
            "VERIFIED",
        )

        assertEquals(SaveMonitorStatus.MATCHED, result.status)
        assertEquals("replacement", result.source?.id)
        assertEquals(9L, result.snapshot?.saveCounter)
        assertEquals("replacement", associations.selectedFor(context.romIdentity))
    }

    @Test
    fun rejectsOversizedMetadataWithoutOpeningTheSource() {
        var opens = 0
        var parses = 0
        val monitor = SavePollingMonitor(
            FakeAssociations(),
            FakeSnapshots(),
            parser = { _, parseContext ->
                parses++
                SaveParseResult.Parsed(snapshot(parseContext.romIdentity, 1))
            },
        )
        val source = streamSource(
            id = "oversized",
            modified = 1,
            size = Long.MAX_VALUE,
        ) {
            opens++
            byteArrayOf(1).inputStream()
        }

        val result = monitor.poll(context, listOf(source), "VERIFIED")

        assertEquals(SaveMonitorStatus.UNAVAILABLE, result.status)
        assertEquals(0, opens)
        assertEquals(0, parses)
    }

    @Test
    fun falseMetadataCannotHideAnEndlessSourceAndTheLastValidSnapshotIsRetained() {
        val repository = FakeSnapshots()
        var parses = 0
        val monitor = SavePollingMonitor(
            FakeAssociations(),
            repository,
            parser = { _, parseContext ->
                parses++
                SaveParseResult.Parsed(snapshot(parseContext.romIdentity, 9))
            },
        )
        monitor.poll(context, listOf(source("save", 1, byteArrayOf(1, 2, 3))), "VERIFIED")
        var bytesRead = 0
        val endless = streamSource(id = "save", modified = 2, size = 0) {
            object : InputStream() {
                override fun read(): Int {
                    bytesRead++
                    return 1
                }

                override fun read(target: ByteArray, offset: Int, length: Int): Int {
                    target.fill(1, offset, offset + length)
                    bytesRead += length
                    return length
                }
            }
        }

        val result = monitor.poll(context, listOf(endless), "VERIFIED")

        assertEquals(SaveMonitorStatus.STALE, result.status)
        assertEquals(9L, result.retained?.snapshot?.saveCounter)
        assertEquals(MAX_SUPPORTED_SAVE_BYTES + 1, bytesRead)
        assertEquals(1, parses)
        assertEquals(1, repository.writes)
    }

    @Test
    fun validMaximumSaveParsesWhenProviderSizeMetadataIsMissing() {
        var parsedSize = 0
        val monitor = SavePollingMonitor(
            FakeAssociations(),
            FakeSnapshots(),
            parser = { bytes, parseContext ->
                parsedSize = bytes.size
                SaveParseResult.Parsed(snapshot(parseContext.romIdentity, 10))
            },
        )
        val source = streamSource(id = "maximum", modified = 1, size = 0) {
            ByteArray(MAX_SUPPORTED_SAVE_BYTES).inputStream()
        }

        val result = monitor.poll(context, listOf(source), "VERIFIED")

        assertEquals(SaveMonitorStatus.MATCHED, result.status)
        assertEquals(MAX_SUPPORTED_SAVE_BYTES, parsedSize)
    }

    @Test
    fun stagedObservationDoesNotAdvanceAuthorityUntilPersistenceAndAcceptanceComplete() {
        val repository = FakeSnapshots()
        val associations = FakeAssociations()
        val monitor = SavePollingMonitor(
            associations,
            repository,
            parser = { _, parseContext -> SaveParseResult.Parsed(snapshot(parseContext.romIdentity, 7)) },
        )
        val candidate = source("save", 100, byteArrayOf(1, 2, 3))

        val first = requireNotNull(
            monitor.poll(
                context,
                listOf(candidate),
                "VERIFIED",
                isCurrent = { true },
                persistAcceptance = false,
            ),
        )
        val repeated = requireNotNull(
            monitor.poll(
                context,
                listOf(candidate),
                "VERIFIED",
                isCurrent = { true },
                persistAcceptance = false,
            ),
        )

        assertEquals(SaveObservationKind.INITIAL, first.observation?.kind)
        assertEquals(SaveObservationKind.INITIAL, repeated.observation?.kind)
        assertEquals(0, repository.writes)
        assertTrue(monitor.persistPrepared(first) { true })
        assertTrue(monitor.acceptPrepared(first))
        assertEquals(SaveObservationKind.UNCHANGED, monitor.poll(context, listOf(candidate), "VERIFIED").observation?.kind)
    }

    @Test
    fun selectionNeverWaitsForTheSessionOwnerWhileHoldingTheSaveMonitor() {
        val monitor = SavePollingMonitor(FakeAssociations(), FakeSnapshots())
        val gate = SessionEpochGate()
        val token = requireNotNull(
            gate.observe(VerifiedSessionIdentity(context.romIdentity, "file:///game.gba")),
        )
        val ownerEntered = CountDownLatch(1)
        val selectCommitEntered = CountDownLatch(1)
        val allowOwnerToEnterMonitor = CountDownLatch(1)
        val workers = Executors.newFixedThreadPool(2)
        val prepared = SaveMonitorResult(
            status = SaveMonitorStatus.MATCHED,
            autosaveStatus = "VERIFIED",
            source = source("save", 100, byteArrayOf(1)),
            snapshot = snapshot(context.romIdentity, 1),
            observation = SaveObservation(
                SaveObservationKind.INITIAL,
                source("save", 100, byteArrayOf(1)),
                SaveFileFingerprint("1".repeat(64), 1, 100),
            ),
            acceptanceRevision = 0,
        )
        try {
            val owner = workers.submit<Boolean> {
                gate.commitIfCurrent(token) {
                    ownerEntered.countDown()
                    assertTrue(allowOwnerToEnterMonitor.await(2, TimeUnit.SECONDS))
                    monitor.canAcceptPrepared(prepared)
                }
            }
            assertTrue(ownerEntered.await(2, TimeUnit.SECONDS))
            val selection = workers.submit<Boolean> {
                monitor.select(context.romIdentity, "save") { commit ->
                    selectCommitEntered.countDown()
                    allowOwnerToEnterMonitor.countDown()
                    gate.commitIfCurrent(token, commit)
                }
            }

            assertTrue(selectCommitEntered.await(2, TimeUnit.SECONDS))
            assertTrue(owner.get(2, TimeUnit.SECONDS))
            assertTrue(selection.get(2, TimeUnit.SECONDS))
        } finally {
            allowOwnerToEnterMonitor.countDown()
            workers.shutdownNow()
            gate.close()
        }
    }

    @Test
    fun tokenFenceRejectsSelectionPreferenceMutation() {
        val associations = FakeAssociations()
        val monitor = SavePollingMonitor(associations, FakeSnapshots())

        val selected = monitor.select(
            context.romIdentity,
            "save",
            commitIfCurrent = { false },
        )

        assertFalse(selected)
        assertNull(associations.selectedFor(context.romIdentity))
    }

    @Test
    fun tokenFenceRejectsPersistenceAfterPreparationCompletes() {
        val repository = FakeSnapshots()
        val associations = FakeAssociations()
        val monitor = SavePollingMonitor(
            associations,
            repository,
            parser = { _, parseContext -> SaveParseResult.Parsed(snapshot(parseContext.romIdentity, 12)) },
        )

        val result = monitor.poll(
            context = context,
            candidates = listOf(source("save", 100, byteArrayOf(1, 2, 3))),
            autosaveStatus = "VERIFIED",
            isCurrent = { true },
            commitIfCurrent = { false },
        )

        assertNull(result)
        assertEquals(0, repository.writes)
        assertNull(associations.selectedFor(context.romIdentity))
    }

    @Test
    fun sessionExpiryDuringSaveReadPreventsPersistenceAndPublication() {
        val repository = FakeSnapshots()
        val associations = FakeAssociations()
        var current = true
        val monitor = SavePollingMonitor(
            associations,
            repository,
            parser = { _, parseContext -> SaveParseResult.Parsed(snapshot(parseContext.romIdentity, 12)) },
        )
        val candidate = source("save", 100, byteArrayOf(1, 2, 3)) { current = false }

        val result = monitor.poll(context, listOf(candidate), "VERIFIED", isCurrent = { current })

        assertNull(result)
        assertEquals(0, repository.writes)
        assertNull(associations.selectedFor(context.romIdentity))
    }

    @Test
    fun expiredSessionDoesNotReadARecoverySnapshot() {
        val repository = FakeSnapshots().apply { write(snapshot(context.romIdentity, 11), 100, 200) }
        val monitor = SavePollingMonitor(FakeAssociations(), repository)
        val readsBeforeRestore = repository.reads

        val restored = monitor.restore(context, "UNVERIFIED") { false }

        assertNull(restored)
        assertEquals(readsBeforeRestore, repository.reads)
    }

    @Test
    fun restoreUsesTheCheckpointSelectedImmutableSnapshotVersion() {
        val legacy = StoredSaveSnapshot(snapshot(context.romIdentity, 10), 100, 200)
        val accepted = StoredSaveSnapshot(snapshot(context.romIdentity, 11), 300, 400)
        var legacyReads = 0
        var versionReads = 0
        val repository = object : SaveSnapshotRepository {
            override fun write(snapshot: SaveSnapshot, sourceLastModifiedEpochMs: Long, refreshedAtEpochMs: Long) = Unit

            override fun read(romSha256: String): StoredSaveSnapshot? {
                legacyReads++
                return legacy
            }

            override fun readVersion(
                romSha256: String,
                versionId: String,
                snapshotDigestSha256: String,
            ): StoredSaveSnapshot? {
                versionReads++
                return accepted.takeIf {
                    versionId == "01234567-89ab-cdef-0123-456789abcdef" && snapshotDigestSha256 == "b".repeat(64)
                }
            }
        }
        val monitor = SavePollingMonitor(FakeAssociations(), repository)

        val restored = monitor.restore(
            context,
            "UNVERIFIED",
            snapshotVersionId = "01234567-89ab-cdef-0123-456789abcdef",
            snapshotDigestSha256 = "b".repeat(64),
            isCurrent = { true },
        )

        assertEquals(11L, restored?.snapshot?.saveCounter)
        assertEquals(0, legacyReads)
        assertEquals(1, versionReads)
    }

    @Test
    fun restoresTheLastCommittedSnapshotWithoutNeedingRetroArchToBeRunning() {
        val repository = FakeSnapshots().apply { write(snapshot(context.romIdentity, 11), 100, 200) }
        val monitor = SavePollingMonitor(FakeAssociations(), repository, clock = { 999 })

        val restored = monitor.restore(context, "UNVERIFIED")

        assertEquals(SaveMonitorStatus.MATCHED, restored?.status)
        assertEquals(11L, restored?.snapshot?.saveCounter)
        assertEquals(200L, restored?.refreshedAtEpochMs)
        assertEquals(1, repository.writes)
    }

    private fun streamSource(
        id: String,
        modified: Long,
        size: Long,
        open: () -> InputStream,
    ) = SaveDocumentSource(
        id = id,
        displayPath = "RetroArch/saves/$id.srm",
        name = "$id.srm",
        size = size,
        lastModifiedEpochMs = modified,
        open = open,
    )

    private fun source(id: String, modified: Long, bytes: ByteArray, onRead: () -> Unit = {}) = SaveDocumentSource(
        id = id,
        displayPath = "RetroArch/saves/$id.srm",
        name = "$id.srm",
        size = bytes.size.toLong(),
        lastModifiedEpochMs = modified,
        open = { onRead(); bytes.inputStream() },
    )

    private fun snapshot(rom: String, counter: Long, identity: String = "b".repeat(64)) = SaveSnapshot(
        romIdentity = rom,
        saveIdentity = identity,
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

    private class FakeAssociations : SaveAssociationRepository {
        private val values = mutableMapOf<String, String>()
        var selections = 0
        override fun selectedFor(romSha256: String): String? {
            selections++
            return values[romSha256.lowercase()]
        }
        override fun remember(romSha256: String, documentId: String) { values[romSha256.lowercase()] = documentId }
    }

    private class FakeSnapshots : SaveSnapshotRepository {
        private val values = mutableMapOf<String, StoredSaveSnapshot>()
        var reads = 0
        var writes = 0
        override fun read(romSha256: String): StoredSaveSnapshot? {
            reads++
            return values[romSha256.lowercase()]
        }
        override fun write(snapshot: SaveSnapshot, sourceLastModifiedEpochMs: Long, refreshedAtEpochMs: Long) {
            writes++
            values[snapshot.romIdentity.lowercase()] = StoredSaveSnapshot(snapshot, sourceLastModifiedEpochMs, refreshedAtEpochMs)
        }
    }
}
