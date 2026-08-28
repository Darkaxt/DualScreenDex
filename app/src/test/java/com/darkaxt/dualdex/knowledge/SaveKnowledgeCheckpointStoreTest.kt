package com.darkaxt.dualdex.knowledge

import com.darkaxt.dualdex.retroarch.RomIndexEntry
import com.darkaxt.dualdex.retroarch.RomPlatform
import com.darkaxt.dualdex.save.AtomicSiblingTarget
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
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

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

        assertEquals(
            CheckpointWriteResult.Durable(CheckpointStorage.PORTABLE_SIDECAR),
            store.write(source, checkpoint),
        )
        assertTrue(root.resolve("Game.srm.dualdex.json").isFile)
        assertFalse(root.listFiles().orEmpty().any { it.name.contains("dualdex.tmp") })
        assertEquals(CheckpointReadResult.Present(checkpoint.copy(portable = false)), store.read(source, key))
        assertEquals(CheckpointReadResult.Absent, store.read(source, key.copy(saveFileSha256 = "d".repeat(64))))
    }

    @Test
    fun sourceWithoutAtomicSiblingUsesIsolatedFallback() {
        val fallback = temporary.newFolder("private", "knowledge-checkpoints")
        val source = SaveDocumentSource("content://save", "Game.srm", "Game.srm", 4, 100, { byteArrayOf().inputStream() })
        val store = SaveKnowledgeCheckpointStore(fallback)

        assertEquals(
            CheckpointWriteResult.Durable(CheckpointStorage.APP_PRIVATE_FALLBACK),
            store.write(source, checkpoint.copy(portable = false)),
        )
        assertEquals(CheckpointReadResult.Present(checkpoint.copy(portable = false)), store.read(source, key))
        assertEquals(1, fallback.listFiles().orEmpty().count { it.extension == "json" })
        assertFalse(fallback.listFiles().orEmpty().any { it.name.contains("dualdex.tmp") })
    }

    @Test
    fun acceptedAppPrivateCheckpointOutranksACorruptOptionalMirror() {
        val fallback = temporary.newFolder("corrupt-fallback")
        val fallbackSource = SaveDocumentSource(
            "content://save",
            "Game.srm",
            "Game.srm",
            4,
            100,
            { byteArrayOf().inputStream() },
        )
        val store = SaveKnowledgeCheckpointStore(fallback)
        assertTrue(store.write(fallbackSource, checkpoint.copy(portable = false)) is CheckpointWriteResult.Durable)
        val corruptSource = fallbackSource.copy(
            id = "file:///Game.srm",
            atomicSiblingTarget = object : AtomicSiblingTarget {
                override fun read(name: String) = "not-json".toByteArray()
                override fun replace(name: String, bytes: ByteArray) = Unit
            },
        )

        val result = store.read(corruptSource, key)

        assertEquals(CheckpointReadResult.Present(checkpoint.copy(portable = false)), result)
    }

    @Test
    fun unavailablePortableCheckpointDoesNotFallThroughToPotentiallyStaleFallback() {
        val source = SaveDocumentSource(
            "file:///Game.srm",
            "Game.srm",
            "Game.srm",
            4,
            100,
            { byteArrayOf().inputStream() },
            object : AtomicSiblingTarget {
                override fun read(name: String): ByteArray? = error("injected read failure")
                override fun replace(name: String, bytes: ByteArray) = Unit
            },
        )

        val result = SaveKnowledgeCheckpointStore(temporary.newFolder("unavailable-fallback")).read(source, key)

        assertEquals(CheckpointReadResult.Unavailable(CheckpointStorage.PORTABLE_SIDECAR), result)
    }

    @Test
    fun portableCheckpointReadOutOfMemoryIsTypedUnavailable() {
        val source = SaveDocumentSource(
            "file:///Game.srm",
            "Game.srm",
            "Game.srm",
            4,
            100,
            { byteArrayOf().inputStream() },
            object : AtomicSiblingTarget {
                override fun read(name: String): ByteArray? = throw OutOfMemoryError("injected allocation failure")
                override fun replace(name: String, bytes: ByteArray) = Unit
            },
        )

        val result = SaveKnowledgeCheckpointStore(temporary.newFolder("oom-fallback")).read(source, key)

        assertEquals(CheckpointReadResult.Unavailable(CheckpointStorage.PORTABLE_SIDECAR), result)
    }

    @Test
    fun checkpointDecodeOutOfMemoryIsTypedUnavailable() {
        val source = SaveDocumentSource(
            "file:///Game.srm",
            "Game.srm",
            "Game.srm",
            4,
            100,
            { byteArrayOf().inputStream() },
            object : AtomicSiblingTarget {
                override fun read(name: String): ByteArray? = byteArrayOf(1)
                override fun replace(name: String, bytes: ByteArray) = Unit
            },
        )
        val store = SaveKnowledgeCheckpointStore(
            temporary.newFolder("decode-oom-fallback"),
            decodeCheckpoint = { throw OutOfMemoryError("injected decode allocation failure") },
        )

        val result = store.read(source, key)

        assertEquals(CheckpointReadResult.Unavailable(CheckpointStorage.PORTABLE_SIDECAR), result)
    }

    @Test
    fun oversizedPortableCheckpointIsTypedUnavailableBeforeDecode() {
        var requestedLimit = 0
        val source = SaveDocumentSource(
            "file:///Game.srm",
            "Game.srm",
            "Game.srm",
            4,
            100,
            { byteArrayOf().inputStream() },
            object : AtomicSiblingTarget {
                override fun read(name: String): ByteArray? = error("unbounded read must not be used")

                override fun read(name: String, maximumBytes: Int): ByteArray? {
                    requestedLimit = maximumBytes
                    return ByteArray(maximumBytes + 1)
                }

                override fun replace(name: String, bytes: ByteArray) = Unit
            },
        )

        val result = SaveKnowledgeCheckpointStore(temporary.newFolder("oversized-fallback")).read(source, key)

        assertEquals(SaveKnowledgeCheckpointCodec.MAXIMUM_ENCODED_BYTES, requestedLimit)
        assertEquals(CheckpointReadResult.Unavailable(CheckpointStorage.PORTABLE_SIDECAR), result)
    }

    @Test
    fun oversizedFallbackCheckpointIsTypedUnavailableBeforeDecode() {
        val fallback = temporary.newFolder("oversized-private-fallback")
        fallback.resolve("${key.romSha256}.accepted.json").writeBytes(ByteArray(SaveKnowledgeCheckpointCodec.MAXIMUM_ENCODED_BYTES + 1))
        val source = SaveDocumentSource(
            "content://save",
            "Game.srm",
            "Game.srm",
            4,
            100,
            { byteArrayOf().inputStream() },
        )

        val result = SaveKnowledgeCheckpointStore(fallback).read(source, key)

        assertEquals(CheckpointReadResult.Unavailable(CheckpointStorage.APP_PRIVATE_FALLBACK), result)
    }

    @Test
    fun oversizedCheckpointSemanticCollectionIsCorrupt() {
        val valid = SaveKnowledgeCheckpointCodec().encode(checkpoint).toString(Charsets.UTF_8)
        val oversized = valid.replace(
            "\"seenSpecies\":[25]",
            "\"seenSpecies\":[${List(65_537) { "25" }.joinToString(",")} ]",
        ).toByteArray()
        val source = SaveDocumentSource(
            "file:///Game.srm",
            "Game.srm",
            "Game.srm",
            4,
            100,
            { byteArrayOf().inputStream() },
            object : AtomicSiblingTarget {
                override fun read(name: String): ByteArray? = oversized
                override fun replace(name: String, bytes: ByteArray) = Unit
            },
        )

        val result = SaveKnowledgeCheckpointStore(temporary.newFolder("semantic-fallback")).read(source, key)

        assertEquals(CheckpointReadResult.Corrupt(CheckpointStorage.PORTABLE_SIDECAR), result)
    }

    @Test
    fun checkpointEncodeOutOfMemoryIsTypedFailedWithoutReplacingValidState() {
        val fallback = temporary.newFolder("encode-oom-fallback")
        val source = SaveDocumentSource(
            "content://save",
            "Game.srm",
            "Game.srm",
            4,
            100,
            { byteArrayOf().inputStream() },
        )
        val store = SaveKnowledgeCheckpointStore(
            fallback,
            encodeCheckpoint = { throw OutOfMemoryError("injected encode allocation failure") },
        )

        val result = store.write(source, checkpoint.copy(portable = false))

        assertEquals(CheckpointWriteResult.Failed(CheckpointStorage.APP_PRIVATE_FALLBACK), result)
        assertTrue(fallback.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun latestCheckpointCanBeReopenedByRomAfterStoreRecreation() {
        val fallback = temporary.newFolder("latest-fallback")
        val source = SaveDocumentSource(
            "content://save",
            "Game.srm",
            "Game.srm",
            4,
            100,
            { byteArrayOf().inputStream() },
        )
        assertTrue(SaveKnowledgeCheckpointStore(fallback).write(source, checkpoint) is CheckpointWriteResult.Durable)

        val reopened = SaveKnowledgeCheckpointStore(fallback).readLatest(key.romSha256)

        assertEquals(CheckpointReadResult.Present(checkpoint.copy(portable = false)), reopened)
        assertEquals(1, fallback.listFiles().orEmpty().count { it.extension == "json" })
    }

    @Test
    fun stagedCheckpointDoesNotReplaceAcceptedAuthorityAndCanBeDiscarded() {
        val fallback = temporary.newFolder("staged-authority")
        val source = SaveDocumentSource(
            "content://save",
            "Game.srm",
            "Game.srm",
            4,
            100,
            { byteArrayOf().inputStream() },
        )
        val store = SaveKnowledgeCheckpointStore(fallback)
        assertTrue(store.write(source, checkpoint) is CheckpointWriteResult.Durable)
        val replacement = checkpoint.copy(
            key = key.copy(saveFileSha256 = "d".repeat(64), saveLastModifiedEpochMs = 200),
            capturedAtEpochMs = 300,
            ledger = KnowledgeLedger(seenSpecies = setOf(133)),
        )

        val staged = store.stage(source, replacement) as CheckpointStageResult.Staged

        assertEquals(CheckpointReadResult.Present(checkpoint.copy(portable = false)), store.readLatest(key.romSha256))
        store.discard(staged.checkpoint)
        assertEquals(
            CheckpointReadResult.Present(checkpoint.copy(portable = false)),
            SaveKnowledgeCheckpointStore(fallback).readLatest(key.romSha256),
        )
    }

    @Test
    fun failedAcceptedPointerSwitchKeepsPreviousCheckpointAfterRestart() {
        val fallback = temporary.newFolder("pointer-failure")
        val source = SaveDocumentSource(
            "content://save",
            "Game.srm",
            "Game.srm",
            4,
            100,
            { byteArrayOf().inputStream() },
        )
        var rejectPointer = false
        val store = SaveKnowledgeCheckpointStore(
            fallback,
            acceptedPointerPublisher = { pending, accepted ->
                if (rejectPointer) throw IOException("injected accepted-pointer failure")
                Files.move(
                    pending.toPath(),
                    accepted.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            },
        )
        assertTrue(store.write(source, checkpoint) is CheckpointWriteResult.Durable)
        val replacement = checkpoint.copy(
            key = key.copy(saveFileSha256 = "d".repeat(64), saveLastModifiedEpochMs = 200),
            capturedAtEpochMs = 300,
            ledger = KnowledgeLedger(seenSpecies = setOf(133)),
        )
        val staged = store.stage(source, replacement) as CheckpointStageResult.Staged
        rejectPointer = true

        assertEquals(
            CheckpointWriteResult.Failed(CheckpointStorage.APP_PRIVATE_FALLBACK),
            store.commit(staged.checkpoint),
        )
        store.discard(staged.checkpoint)

        assertEquals(
            CheckpointReadResult.Present(checkpoint.copy(portable = false)),
            SaveKnowledgeCheckpointStore(fallback).readLatest(key.romSha256),
        )
    }

    @Test
    fun legacyKnowledgeDirectoryIsNeverReadAsCheckpoint() {
        val fallback = temporary.newFolder("knowledge-checkpoints")
        val legacy = temporary.newFolder("knowledge")
        legacy.resolve("${key.romSha256}.${key.saveIdentity}.json").writeBytes(
            KnowledgeLedgerJsonCodec().encode(KnowledgeLedger(seenSpecies = setOf(25))),
        )
        val source = SaveDocumentSource("content://save", "Game.srm", "Game.srm", 4, 100, { byteArrayOf().inputStream() })

        assertEquals(CheckpointReadResult.Absent, SaveKnowledgeCheckpointStore(fallback).read(source, key))
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
