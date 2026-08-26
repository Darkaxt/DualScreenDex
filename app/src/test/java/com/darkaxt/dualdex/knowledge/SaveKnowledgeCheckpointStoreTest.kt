package com.darkaxt.dualdex.knowledge

import com.darkaxt.dualdex.retroarch.RomIndexEntry
import com.darkaxt.dualdex.retroarch.RomPlatform
import com.darkaxt.dualdex.save.DirectSaveDocumentResolver
import com.darkaxt.dualdex.save.SaveDocumentSource
import com.enrpau.dualscreendex.companion.model.KnowledgeLedger
import com.darkaxt.dualdex.progress.PlaythroughJournal
import com.enrpau.dualscreendex.companion.semantic.PlaythroughKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SaveKnowledgeCheckpointStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    private val key = SaveCheckpointKey(
        romSha256 = "a".repeat(64),
        saveIdentity = "b".repeat(64),
        saveFileSha256 = "c".repeat(64),
        saveSize = 4,
        saveLastModifiedEpochMs = 100,
    )
    private val checkpoint = SaveKnowledgeCheckpoint(
        portable = true,
        key = key,
        capturedAtEpochMs = 200,
        ledger = KnowledgeLedger(seenSpecies = setOf(25)),
        journal = PlaythroughJournal.empty(PlaythroughKey(key.romSha256, key.saveIdentity)).copy(
            trackedCounts = mapOf("captures" to 1),
        ),
    )

    @Test
    fun directSaveWritesCompleteSiblingAndLeavesNoTemporaryFile() {
        val root = temporary.newFolder("direct")
        root.resolve("Game.srm").writeBytes(byteArrayOf(1, 2, 3, 4))
        val source = DirectSaveDocumentResolver.discover(rom(), listOf(root)).single()
        val store = SaveKnowledgeCheckpointStore(temporary.newFolder("fallback"))

        assertEquals(CheckpointStorage.PORTABLE_SIDECAR, store.write(source, checkpoint))
        assertTrue(root.resolve("Game.srm.dualdex.json").isFile)
        assertFalse(root.listFiles().orEmpty().any { it.name.contains("dualdex.tmp") })
        assertEquals(checkpoint.ledger, store.readExact(source, key))
        assertEquals(checkpoint, store.readCheckpointExact(source, key))
        assertNull(store.readExact(source, key.copy(saveFileSha256 = "d".repeat(64))))
    }

    @Test
    fun sourceWithoutAtomicSiblingUsesIsolatedFallback() {
        val fallback = temporary.newFolder("private", "knowledge-checkpoints")
        val source = SaveDocumentSource("content://save", "Game.srm", "Game.srm", 4, 100, { byteArrayOf() })
        val store = SaveKnowledgeCheckpointStore(fallback)

        assertEquals(CheckpointStorage.APP_PRIVATE_FALLBACK, store.write(source, checkpoint.copy(portable = false)))
        assertEquals(checkpoint.ledger, store.readExact(source, key))
        assertEquals(1, fallback.listFiles().orEmpty().count { it.extension == "json" })
        assertFalse(fallback.listFiles().orEmpty().any { it.name.contains("dualdex.tmp") })
    }

    @Test
    fun legacyKnowledgeDirectoryIsNeverReadAsCheckpoint() {
        val fallback = temporary.newFolder("knowledge-checkpoints")
        val legacy = temporary.newFolder("knowledge")
        legacy.resolve("${key.romSha256}.${key.saveIdentity}.json").writeBytes(
            KnowledgeLedgerJsonCodec().encode(KnowledgeLedger(seenSpecies = setOf(25))),
        )
        val source = SaveDocumentSource("content://save", "Game.srm", "Game.srm", 4, 100, { byteArrayOf() })

        assertNull(SaveKnowledgeCheckpointStore(fallback).readExact(source, key))
    }

    private fun rom() = RomIndexEntry(
        sourceId = "file:///Game.gba",
        sourceName = "Game.gba",
        archiveEntry = null,
        platform = RomPlatform.GBA,
        gameBasename = "Game",
        crc32 = "12345678",
        sha256 = key.romSha256,
    )
}
