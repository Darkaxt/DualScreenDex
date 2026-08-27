package com.darkaxt.dualdex.save

import com.darkaxt.dualdex.retroarch.RomIndexEntry
import com.darkaxt.dualdex.retroarch.RomPlatform
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
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
        assertArrayEquals(byteArrayOf(1, 2, 3), sources.single().open().use { it.readBytes() })
    }

    @Test
    fun `uses active RetroArch basename before archived inner basename`() {
        val root = temporaryRoot()
        File(root, "Modern Emerald.srm").writeBytes(byteArrayOf(1))
        val active = File(root, "Pokemon Modern Emerald.srm").apply { writeBytes(byteArrayOf(2)) }

        val sources = DirectSaveDocumentResolver.discover(
            entry = rom,
            directories = listOf(root),
            activeGameBasename = "Pokemon Modern Emerald",
        )

        assertEquals(listOf(active.canonicalPath), sources.map { it.displayPath })
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
        assertArrayEquals(byteArrayOf(8, 9, 10, 11), refreshed.open().use { it.readBytes() })
    }

    @Test
    fun `atomic sibling target rejects traversal and replaces a complete sibling`() {
        val root = temporaryRoot()
        File(root, "Modern Emerald.srm").writeBytes(byteArrayOf(1))
        val source = DirectSaveDocumentResolver.discover(rom, listOf(root)).single()
        val target = requireNotNull(source.atomicSiblingTarget)

        target.replace("Modern Emerald.srm.dualdex.json", "first".toByteArray())
        target.replace("Modern Emerald.srm.dualdex.json", "second".toByteArray())

        assertEquals("second", target.read("Modern Emerald.srm.dualdex.json")?.toString(Charsets.UTF_8))
        assertThrows(IllegalArgumentException::class.java) { target.replace("../escape.json", byteArrayOf()) }
        assertTrue(root.listFiles().orEmpty().none { it.name.contains("dualdex.tmp") })
    }

    private fun temporaryRoot(): File = Files.createTempDirectory("dualdex-direct-save-").toFile().also(roots::add)
}
