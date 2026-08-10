package com.darkaxt.dualdex.storage

import com.darkaxt.dualdex.retroarch.RomPlatform
import com.enrpau.dualscreendex.parser.io.RomSourceLoader
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DirectRomLibraryIndexerTest {
    private val roots = mutableListOf<File>()

    @After
    fun cleanUp() {
        roots.forEach(File::deleteRecursively)
    }

    @Test
    fun `indexes supported ROMs across sibling folders without duplicating overlapping roots`() {
        val root = temporaryRoot()
        val gb = File(root, "Games/ROMs/GB").apply { mkdirs() }
        val gbc = File(root, "Games/ROMs/GBC").apply { mkdirs() }
        val gba = File(root, "Games/ROMs/GBA").apply { mkdirs() }
        File(gb, "Pokemon Red.gb").writeBytes(gameBoyRom("POKEMON RED", color = false))
        File(gbc, "Pokemon Crystal.gbc").writeBytes(gameBoyRom("POKEMON CRYSTAL", color = true))
        File(gba, "Pokemon Emerald.gba").writeBytes(gameBoyAdvanceRom())
        File(root, "notes.txt").writeText("not a ROM")
        File(root, "broken.zip").writeText("not a zip")
        File(root, "Android/data/hidden.gba").apply {
            requireNotNull(parentFile).mkdirs()
            writeBytes(gameBoyAdvanceRom())
        }

        val result = DirectRomLibraryIndexer().index(listOf(root, gba))

        assertEquals(listOf("Pokemon Crystal.gbc", "Pokemon Emerald.gba", "Pokemon Red.gb"), result.entries.map { it.sourceName })
        assertEquals(listOf(RomPlatform.GBC, RomPlatform.GBA, RomPlatform.GB), result.entries.map { it.platform })
        assertTrue(result.entries.all { it.sourceId.startsWith("file:") })
        assertEquals(3, result.entries.map { it.sourceId }.distinct().size)
        assertTrue(result.warnings.single().startsWith("broken.zip:"))
    }

    @Test
    fun `opens direct ROM and single-ROM ZIP source identifiers through the shared input adapter`() {
        val root = temporaryRoot()
        val direct = File(root, "Pokemon Emerald.gba").apply { writeBytes(gameBoyAdvanceRom()) }
        val archive = File(root, "Pokemon Emerald.zip")
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("Emerald.gba"))
            zip.write(gameBoyAdvanceRom())
            zip.closeEntry()
        }
        val result = DirectRomLibraryIndexer().index(listOf(root))
        val input = RomSourceInput { error("content opener must not be used for file sources") }

        val loaded = result.entries.associate { entry ->
            entry.sourceName to input.open(entry.sourceId).use { RomSourceLoader.load(entry.sourceName.substringBefore('!'), it) }
        }

        assertEquals(setOf(direct.name, "${archive.name}!Emerald.gba"), loaded.keys)
        assertTrue(loaded.values.all { it.rom.sha256 == loaded.values.first().rom.sha256 })
    }

    private fun temporaryRoot(): File = Files.createTempDirectory("dualdex-direct-rom-").toFile().also(roots::add)

    private fun gameBoyAdvanceRom(): ByteArray = ByteArray(0xC0).also { bytes ->
        "POKEMON EMER".toByteArray().copyInto(bytes, 0xA0)
        "BPEE".toByteArray().copyInto(bytes, 0xAC)
    }

    private fun gameBoyRom(title: String, color: Boolean): ByteArray = ByteArray(0x150).also { bytes ->
        title.toByteArray().copyInto(bytes, 0x134, endIndex = title.length.coerceAtMost(if (color) 15 else 16))
        if (color) bytes[0x143] = 0x80.toByte()
    }
}
