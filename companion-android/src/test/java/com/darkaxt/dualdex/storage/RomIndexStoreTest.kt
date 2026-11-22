package com.darkaxt.dualdex.storage

import com.darkaxt.dualdex.retroarch.RomIndexEntry
import com.darkaxt.dualdex.retroarch.RomPlatform
import java.io.IOException
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RomIndexStoreTest {
    private val roots = mutableListOf<java.io.File>()

    @After
    fun cleanUp() {
        roots.forEach(java.io.File::deleteRecursively)
    }

    @Test
    fun `failed B publication preserves durable A identity and entries`() {
        val store = RomIndexStore(Files.createTempDirectory("dualdex-rom-index-").toFile().also(roots::add).resolve("index.json"))
        val a = entry("A")
        store.write("content://tree/A", listOf(a))

        val outcome = SafRomIndexTransaction { _: List<RomIndexEntry> -> throw IOException("B unavailable") }
            .commit(listOf(entry("B")))

        val active = requireNotNull(store.readActive())
        assertEquals(SafRomIndexCommitResult.Failed, outcome)
        assertEquals("content://tree/A", active.rootUri)
        assertEquals(listOf(a), active.entries)
    }

    @Test
    fun `successful publication advances the version with identity and entries together`() {
        val store = RomIndexStore(Files.createTempDirectory("dualdex-rom-index-").toFile().also(roots::add).resolve("index.json"))
        val first = store.write("content://tree/A", listOf(entry("A")))
        val second = store.write("content://tree/B", listOf(entry("B")))

        assertTrue(second.revision > first.revision)
        assertEquals("content://tree/B", requireNotNull(store.readActive()).rootUri)
        assertEquals(listOf(entry("B")), store.read("content://tree/B"))
        assertTrue(store.read("content://tree/A").isEmpty())
    }

    private fun entry(id: String) = RomIndexEntry(
        sourceId = "file:/$id.gba",
        sourceName = "$id.gba",
        archiveEntry = null,
        platform = RomPlatform.GBA,
        gameBasename = id,
        crc32 = "12345678",
        sha256 = id.lowercase().repeat(64).take(64),
    )
}
