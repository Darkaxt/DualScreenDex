package com.darkaxt.dualdex.storage

import com.darkaxt.dualdex.retroarch.RomPlatform
import com.enrpau.dualscreendex.parser.io.RomSourceLoader
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.net.URI
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

    @Test
    fun `streams the exact official Emerald identity without materializing the ROM`() {
        val configured = System.getenv("DUALDEX_OFFICIAL_EMERALD_ROM")
        assumeTrue("set DUALDEX_OFFICIAL_EMERALD_ROM to run this real-ROM control", !configured.isNullOrBlank())
        val source = File(requireNotNull(configured))
        assumeTrue("official Emerald ROM does not exist: $source", source.isFile)

        val identity = StreamingRomSourceReader.read(source)

        assertEquals(source.name, identity.displayName)
        assertEquals(RomPlatform.GBA, identity.platform)
        assertEquals("1F1C08FB", identity.crc32)
        assertEquals("a9dec84dfe7f62ab2220bafaef7479da0929d066ece16a6885f6226db19085af", identity.sha256)
    }

    @Test
    fun `indexes real Unbound ZIP and 7z as the same SHA authoritative ROM`() {
        val zip = configuredFile("DUALDEX_UNBOUND_ZIP")
        val sevenZip = configuredFile("DUALDEX_UNBOUND_7Z")

        val result = DirectRomLibraryIndexer().index(listOf(zip, sevenZip))

        assertEquals(2, result.entries.size)
        assertTrue(result.warnings.isEmpty())
        assertEquals(setOf("zip", "7z"), result.entries.map { File(URI(it.sourceId)).extension }.toSet())
        assertEquals(setOf("7aa25bbf568f7cfcf6ee1cf2e9e6ff637350b3d0705c2375cabb6baa7d9739f7"), result.entries.map { it.sha256 }.toSet())
        assertEquals(setOf("4B3D4957"), result.entries.map { it.crc32 }.toSet())
    }

    private fun configuredFile(name: String): File {
        val configured = System.getenv(name)
        assumeTrue("set $name to run this real-ROM control", !configured.isNullOrBlank())
        return File(requireNotNull(configured)).also { source ->
            assumeTrue("configured ROM archive does not exist: $source", source.isFile)
        }
    }

    private fun temporaryRoot(): File = Files.createTempDirectory("dualdex-direct-rom-").toFile().also(roots::add)

    private fun gameBoyAdvanceRom(): ByteArray = ByteArray(0xC0).also { bytes ->
        byteArrayOf(
            0x24, 0xFF.toByte(), 0xAE.toByte(), 0x51, 0x69, 0x9A.toByte(), 0xA2.toByte(), 0x21,
            0x3D, 0x84.toByte(), 0x82.toByte(), 0x0A,
        ).copyInto(bytes, 0x04)
        "POKEMON EMER".toByteArray().copyInto(bytes, 0xA0)
        "BPEE".toByteArray().copyInto(bytes, 0xAC)
    }

    private fun gameBoyRom(title: String, color: Boolean): ByteArray = ByteArray(0x150).also { bytes ->
        title.toByteArray().copyInto(bytes, 0x134, endIndex = title.length.coerceAtMost(if (color) 15 else 16))
        if (color) bytes[0x143] = 0x80.toByte()
    }
}
