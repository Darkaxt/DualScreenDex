package com.darkaxt.dualdex.storage

import com.darkaxt.dualdex.retroarch.RomIndexEntry
import com.darkaxt.dualdex.retroarch.RomPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SafRomIndexRetentionTest {
    @Test
    fun `result quota rejection propagates instead of producing a partial SAF index`() {
        val operation = StorageTraversalOperation(
            StorageTraversalQuota(
                maximumNodes = 4,
                maximumDirectories = 2,
                maximumFiles = 2,
                maximumResults = 1,
            ),
        )
        val entries = mutableListOf<RomIndexEntry>()

        SafRomIndexRetention.retain(operation, entries, entry("A"))
        val failure = runCatching { SafRomIndexRetention.retain(operation, entries, entry("B")) }.exceptionOrNull()

        assertTrue(failure is StorageTraversalLimitExceeded)
        assertEquals(listOf(entry("A")), entries)
    }

    private fun entry(id: String) = RomIndexEntry(
        sourceId = "content://tree/$id",
        sourceName = "$id.gba",
        archiveEntry = null,
        platform = RomPlatform.GBA,
        gameBasename = id,
        crc32 = "12345678",
        sha256 = id.lowercase().repeat(64).take(64),
    )
}
