package com.darkaxt.dualdex.save

import com.darkaxt.dualdex.retroarch.RomIndexEntry
import com.darkaxt.dualdex.retroarch.RomPlatform
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DirectSaveDocumentResolverTest {
    private val roots = mutableListOf<File>()
    private val rom = RomIndexEntry(
        sourceId = "file:/storage/emulated/0/Games/ROMs/GBA/Modern%20Emerald.gba",
        sourceName = "Modern Emerald.gba",
        archiveEntry = null,
        platform = RomPlatform.GBA,
        gameBasename = "Modern Emerald",
        crc32 = "12345678",
        sha256 = "a".repeat(64),
    )

    @After
    fun cleanUp() {
        roots.forEach(File::deleteRecursively)
    }

    @Test
    fun `finds only matching SaveRAM below the effective directory and deduplicates overlapping roots`() {
        val root = temporaryRoot()
        val core = File(root, "RetroArch/saves/mGBA").apply { mkdirs() }
        val matching = File(core, "Modern Emerald.srm").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        File(core, "Other Game.srm").writeBytes(byteArrayOf(4))
        File(core, "Modern Emerald.state").writeBytes(byteArrayOf(5))

        val sources = DirectSaveDocumentResolver.discover(rom, listOf(File(root, "RetroArch/saves"), core))

        assertEquals(1, sources.size)
        assertEquals(matching.canonicalFile.toURI().normalize().toString(), sources.single().id)
        assertEquals(matching.canonicalPath, sources.single().displayPath)
        assertArrayEquals(byteArrayOf(1, 2, 3), sources.single().read())
    }

    @Test
    fun `refreshes direct metadata and bytes after RetroArch rewrites a save`() {
        val root = temporaryRoot()
        val matching = File(root, "Modern Emerald.sav").apply { writeBytes(byteArrayOf(1)) }
        val original = DirectSaveDocumentResolver.discover(rom, listOf(root)).single()
        matching.writeBytes(byteArrayOf(8, 9, 10, 11))
        assertTrue(matching.setLastModified(original.lastModifiedEpochMs + 2_000))

        val refreshed = DirectSaveDocumentResolver.refresh(listOf(original)).single()

        assertEquals(4, refreshed.size)
        assertTrue(refreshed.lastModifiedEpochMs > original.lastModifiedEpochMs)
        assertArrayEquals(byteArrayOf(8, 9, 10, 11), refreshed.read())
    }

    private fun temporaryRoot(): File = Files.createTempDirectory("dualdex-direct-save-").toFile().also(roots::add)
}
