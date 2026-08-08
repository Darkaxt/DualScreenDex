package com.enrpau.dualscreendex.parser.cli

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CorpusScannerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun discoversOnlyPokemonRomInputsWithoutChangingSources() {
        val root = temporaryFolder.newFolder("roms").toPath()
        val direct = root.resolve("Pokemon Test.gba")
        val directBytes = ByteArray(0xC0).also {
            "POKEMON TEST".toByteArray().copyInto(it, 0xA0)
            "BPEE".toByteArray().copyInto(it, 0xAC)
        }
        Files.write(direct, directBytes)
        Files.write(root.resolve("Other Game.gba"), directBytes)
        Files.write(root.resolve("Pokemon Test.sav"), byteArrayOf(1, 2, 3))
        Files.write(root.resolve("Pokemon Cover.png"), byteArrayOf(4, 5, 6))

        val archive = root.resolve("Pokemon Pack.zip")
        ZipOutputStream(Files.newOutputStream(archive)).use { zip ->
            zip.putNextEntry(ZipEntry("Pokemon Hack.gbc"))
            zip.write(ByteArray(0x150))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("manual.pdf"))
            zip.write(byteArrayOf(7, 8, 9))
            zip.closeEntry()
        }
        val archiveBefore = Files.readAllBytes(archive)

        val found = CorpusScanner().scan(root)

        assertEquals(
            listOf("Pokemon Pack.zip!Pokemon Hack.gbc", "Pokemon Test.gba"),
            found.map { it.displayName }.sorted(),
        )
        assertArrayEquals(directBytes, Files.readAllBytes(direct))
        assertArrayEquals(archiveBefore, Files.readAllBytes(archive))
    }

    @Test
    fun reportsMalformedPokemonArchiveWithoutAborting() {
        val root = temporaryFolder.newFolder("bad-roms").toPath()
        Files.write(root.resolve("Pokemon Broken.zip"), byteArrayOf(1, 2, 3))

        val result = CorpusScanner().scan(root).single()

        assertEquals("Pokemon Broken.zip", result.displayName)
        assertEquals(null, result.bytes)
        assertEquals(true, result.error?.contains("ZipException"))
    }
}
