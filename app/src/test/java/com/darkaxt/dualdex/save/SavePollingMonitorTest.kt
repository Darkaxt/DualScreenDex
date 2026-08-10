package com.darkaxt.dualdex.save

import com.darkaxt.dualdex.catalog.StoredSaveSnapshot
import com.darkaxt.dualdex.catalog.SaveSnapshotRepository
import com.darkaxt.dualdex.save.SaveParseContext
import com.darkaxt.dualdex.save.SaveParseResult
import com.darkaxt.dualdex.save.SaveSnapshot
import com.darkaxt.dualdex.save.SaveSpeciesContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
        val monitor = SavePollingMonitor(
            associations,
            repository,
            parser = { _, parseContext -> parses++; SaveParseResult.Parsed(snapshot(parseContext.romIdentity, 7)) },
            clock = { 900L },
        )
        val source = source("save", modified = 100L, bytes = byteArrayOf(1, 2, 3))

        val first = monitor.poll(context, listOf(source), "VERIFIED")
        val second = monitor.poll(context, listOf(source), "VERIFIED")

        assertEquals(SaveMonitorStatus.MATCHED, first.status)
        assertEquals(7L, first.snapshot?.saveCounter)
        assertEquals(SaveMonitorStatus.MATCHED, second.status)
        assertNull(second.snapshot)
        assertEquals(1, parses)
        assertEquals(1, repository.writes)
        assertEquals("save", associations.selectedFor(context.romIdentity))
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
    fun restoresTheLastCommittedSnapshotWithoutNeedingRetroArchToBeRunning() {
        val repository = FakeSnapshots().apply { write(snapshot(context.romIdentity, 11), 100, 200) }
        val monitor = SavePollingMonitor(FakeAssociations(), repository, clock = { 999 })

        val restored = monitor.restore(context, "UNVERIFIED")

        assertEquals(SaveMonitorStatus.MATCHED, restored?.status)
        assertEquals(11L, restored?.snapshot?.saveCounter)
        assertEquals(200L, restored?.refreshedAtEpochMs)
        assertEquals(1, repository.writes)
    }

    private fun source(id: String, modified: Long, bytes: ByteArray) = SaveDocumentSource(
        id = id,
        displayPath = "RetroArch/saves/$id.srm",
        name = "$id.srm",
        size = bytes.size.toLong(),
        lastModifiedEpochMs = modified,
        read = { bytes.copyOf() },
    )

    private fun snapshot(rom: String, counter: Long) = SaveSnapshot(
        romIdentity = rom,
        saveIdentity = "save",
        saveGeneration = 3,
        saveCounter = counter,
        currentArea = null,
        seenDexNumbers = emptySet(),
        caughtDexNumbers = emptySet(),
        party = emptyList(),
        storedIndividuals = emptyList(),
        capabilities = emptyMap(),
    )

    private class FakeAssociations : SaveAssociationRepository {
        private val values = mutableMapOf<String, String>()
        override fun selectedFor(romSha256: String): String? = values[romSha256.lowercase()]
        override fun remember(romSha256: String, documentId: String) { values[romSha256.lowercase()] = documentId }
    }

    private class FakeSnapshots : SaveSnapshotRepository {
        private val values = mutableMapOf<String, StoredSaveSnapshot>()
        var writes = 0
        override fun read(romSha256: String): StoredSaveSnapshot? = values[romSha256.lowercase()]
        override fun write(snapshot: SaveSnapshot, sourceLastModifiedEpochMs: Long, refreshedAtEpochMs: Long) {
            writes++
            values[snapshot.romIdentity.lowercase()] = StoredSaveSnapshot(snapshot, sourceLastModifiedEpochMs, refreshedAtEpochMs)
        }
    }
}
