package com.darkaxt.dualdex.knowledge

import com.enrpau.dualscreendex.companion.model.KnowledgeLedger
import com.darkaxt.dualdex.progress.PlaythroughJournal
import com.enrpau.dualscreendex.companion.semantic.PlaythroughKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class SaveKnowledgeCheckpointCodecTest {
    private val codec = SaveKnowledgeCheckpointCodec()
    private val romSha = "a".repeat(64)
    private val saveSha = "b".repeat(64)
    private val saveIdentity = "c".repeat(64)

    @Test
    fun checkpointRequiresEveryExactIdentity() {
        val checkpoint = checkpointFixture()
        val bytes = codec.encode(checkpoint)

        assertEquals(checkpoint, codec.decodeExact(bytes, checkpoint.key))
        assertNull(codec.decodeExact(bytes, checkpoint.key.copy(romSha256 = "d".repeat(64))))
        assertNull(codec.decodeExact(bytes, checkpoint.key.copy(saveIdentity = "e".repeat(64))))
        assertNull(codec.decodeExact(bytes, checkpoint.key.copy(saveFileSha256 = "f".repeat(64))))
        assertNull(codec.decodeExact(bytes, checkpoint.key.copy(saveSize = 1)))
        assertNull(codec.decodeExact(bytes, checkpoint.key.copy(saveLastModifiedEpochMs = 1)))
    }

    @Test
    fun normalizesHashesAndRejectsMalformedEnvelopeValues() {
        val checkpoint = checkpointFixture().copy(
            key = checkpointFixture().key.copy(
                romSha256 = romSha.uppercase(),
                saveIdentity = saveIdentity.uppercase(),
                saveFileSha256 = saveSha.uppercase(),
            ),
        )
        val decoded = codec.decodeExact(codec.encode(checkpoint), checkpoint.key)

        assertNotNull(decoded)
        assertEquals(romSha, decoded?.key?.romSha256)
        assertNull(codec.decodeExact(codec.encode(checkpointFixture()), checkpointFixture().key.copy(saveSize = -1)))
        assertNull(codec.decodeExact(codec.encode(checkpointFixture()), checkpointFixture().key.copy(saveLastModifiedEpochMs = -1)))
    }

    @Test
    fun preservesTheImmutableSnapshotVersionReference() {
        val checkpoint = checkpointFixture().copy(
            sourceId = "file:///Game.srm",
            snapshotDigestSha256 = "d".repeat(64),
            snapshotVersionId = "01234567-89ab-cdef-0123-456789abcdef",
        )

        assertEquals(checkpoint, codec.decode(codec.encode(checkpoint)))
    }

    @Test
    fun legacyLedgerIsNotACheckpoint() {
        val legacy = KnowledgeLedgerJsonCodec().encode(KnowledgeLedger(seenSpecies = setOf(25)))

        assertNull(codec.decodeExact(legacy, checkpointFixture().key))
    }

    @Test
    fun schemaOneCheckpointMigratesWithoutInventingJournalHistory() {
        val fixture = checkpointFixture().copy(journal = null)
        val legacy = codec.encode(fixture).toString(Charsets.UTF_8)
            .replaceFirst("\"schema\":2", "\"schema\":1")
            .toByteArray()

        val decoded = codec.decodeExact(legacy, fixture.key)

        assertEquals(2, decoded?.schema)
        assertNull(decoded?.journal)
    }

    private fun checkpointFixture() = SaveKnowledgeCheckpoint(
        portable = true,
        key = SaveCheckpointKey(
            romSha256 = romSha,
            saveIdentity = saveIdentity,
            saveFileSha256 = saveSha,
            saveSize = 32_768,
            saveLastModifiedEpochMs = 123_456,
        ),
        capturedAtEpochMs = 123_500,
        ledger = KnowledgeLedger(
            seenSpecies = setOf(25, 133),
            caughtSpecies = setOf(25),
            knownMoves = setOf(33, 84),
        ),
        journal = PlaythroughJournal.empty(PlaythroughKey(romSha, saveIdentity)).copy(
            trackedCounts = mapOf("captures" to 1),
        ),
    )
}
