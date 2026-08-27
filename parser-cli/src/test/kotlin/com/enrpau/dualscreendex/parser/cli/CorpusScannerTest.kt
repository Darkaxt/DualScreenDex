package com.enrpau.dualscreendex.parser.cli

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.StandardOpenOption
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

        val found = CorpusScanner().scan(root).toList()

        assertEquals(
            listOf("Pokemon Pack.zip!Pokemon Hack.gbc", "Pokemon Test.gba"),
            found.map { it.displayName }.sorted(),
        )
        val byName = found.associateBy(CorpusInput::displayName)
        assertArrayEquals(directBytes, byName.getValue("Pokemon Test.gba").loadRom().slice(0, directBytes.size))
        assertArrayEquals(
            ByteArray(0x150),
            byName.getValue("Pokemon Pack.zip!Pokemon Hack.gbc").loadRom().slice(0, 0x150),
        )
        assertArrayEquals(directBytes, Files.readAllBytes(direct))
        assertArrayEquals(archiveBefore, Files.readAllBytes(archive))
    }

    @Test
    fun defersDirectRomLoadingUntilTheInputIsProcessed() {
        val root = temporaryFolder.newFolder("lazy-roms").toPath()
        val path = root.resolve("Pokemon Lazy.gba")
        Files.write(path, byteArrayOf(1, 2, 3))

        val input = CorpusScanner().scan(root).single()
        val replacement = byteArrayOf(4, 5, 6, 7)
        Files.write(path, replacement)

        assertArrayEquals(replacement, input.loadRom().slice(0, replacement.size))
    }

    @Test
    fun reportsMalformedPokemonArchiveWithoutAborting() {
        val root = temporaryFolder.newFolder("bad-roms").toPath()
        Files.write(root.resolve("Pokemon Broken.zip"), byteArrayOf(1, 2, 3))

        val result = CorpusScanner().scan(root).single()

        assertEquals("Pokemon Broken.zip", result.displayName)
        assertEquals(true, result.error?.contains("ZipException"))
    }

    @Test
    fun rejectsOversizedArchivesDuringDiscovery() {
        val root = temporaryFolder.newFolder("oversized-archive").toPath()
        val archive = root.resolve("Pokemon Oversized.zip")
        Files.newByteChannel(
            archive,
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE,
        ).use { channel ->
            channel.position(64L * 1024 * 1024)
            channel.write(ByteBuffer.wrap(byteArrayOf(0)))
        }

        val result = CorpusScanner().scan(root).single()

        assertTrue(result.error.orEmpty().contains("64 MiB"))
    }

    @Test
    fun rejectsExcessiveArchiveEntriesBeforePublishingRomInputs() {
        val root = temporaryFolder.newFolder("entry-budget").toPath()
        val archive = root.resolve("Pokemon Entries.zip")
        ZipOutputStream(Files.newOutputStream(archive)).use { zip ->
            zip.putNextEntry(ZipEntry("Pokemon Valid.gba"))
            zip.write(ByteArray(0x150))
            zip.closeEntry()
            repeat(1_024) { index ->
                zip.putNextEntry(ZipEntry("notes/$index.txt"))
                zip.closeEntry()
            }
        }

        val result = CorpusScanner().scan(root).single()

        assertTrue(result.error.orEmpty().contains("entry count"))
        assertNull(result.path)
    }

    @Test
    fun excludesNonMainlineOfficialGamesFromCurrentStudy() {
        val root = temporaryFolder.newFolder("non-mainline").toPath()
        Files.write(root.resolve("Pokemon Pinball.gbc"), ByteArray(0x150))
        Files.write(root.resolve("Pokemon Puzzle Challenge.gbc"), ByteArray(0x150))
        Files.write(root.resolve("Pokemon Trading Card Game.gbc"), ByteArray(0x150))
        val archive = root.resolve("Pokemon Mystery Dungeon.zip")
        ZipOutputStream(Files.newOutputStream(archive)).use { zip ->
            zip.putNextEntry(ZipEntry("Pokemon Mystery Dungeon.gba"))
            zip.write(ByteArray(0xC0))
            zip.closeEntry()
        }

        assertEquals(emptyList<CorpusInput>(), CorpusScanner().scan(root).toList())
    }

    @Test
    fun explicitCorpusModeIncludesHackNamesWithoutPokemonButStillExcludesKnownSpinOffs() {
        val root = temporaryFolder.newFolder("named-hacks").toPath()
        Files.write(root.resolve("Gaia.gba"), ByteArray(0xC0))
        Files.write(root.resolve("Pokemon Pinball.gbc"), ByteArray(0x150))

        val found = CorpusScanner(includeAllRomNames = true).scan(root).toList()

        assertEquals(listOf("Gaia.gba"), found.map(CorpusInput::displayName))
    }
}
