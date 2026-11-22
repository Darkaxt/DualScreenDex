package com.darkaxt.dualdex.save

import com.darkaxt.dualdex.retroarch.RomIndexEntry
import com.darkaxt.dualdex.retroarch.RomPlatform
import org.junit.Assert.assertEquals
import org.junit.Test

class SaveDocumentResolverTest {
    private val rom = RomIndexEntry(
        sourceId = "content://roms/modern.zip",
        sourceName = "Pokemon Modern Emerald.zip!Pokemon - Modern Emerald Version v3.5.gba",
        archiveEntry = "Pokemon - Modern Emerald Version v3.5.gba",
        platform = RomPlatform.GBA,
        gameBasename = "Pokemon - Modern Emerald Version v3.5",
        crc32 = "12345678",
        sha256 = "a".repeat(64),
    )

    @Test
    fun keepsOnlyExactContentBasenameSaveRamCandidatesAcrossSupportedLayouts() {
        val candidates = listOf(
            candidate("1", "RetroArch/saves/mGBA/Pokemon - Modern Emerald Version v3.5.srm"),
            candidate("2", "Roms/Pokemon - Modern Emerald Version v3.5.SAV"),
            candidate("3", "RetroArch/saves/Pokemon Emerald.srm"),
            candidate("4", "RetroArch/states/Pokemon - Modern Emerald Version v3.5.state"),
        )

        val matches = SaveDocumentResolver.matching(rom, candidates)

        assertEquals(listOf("1", "2"), matches.map { it.id })
    }

    @Test
    fun prefersTheActiveRetroArchBasenameForAnArchivedRom() {
        val archived = rom.copy(gameBasename = "Modern Emerald (v3.5)")
        val candidates = listOf(
            candidate("inner", "RetroArch/saves/mGBA/Modern Emerald (v3.5).srm"),
            candidate("active", "RetroArch/saves/mGBA/Pokemon Modern Emerald (v3.5).srm"),
        )

        val matches = SaveDocumentResolver.matching(
            entry = archived,
            documents = candidates,
            activeGameBasename = "Pokemon Modern Emerald (v3.5)",
        )

        assertEquals(listOf("active"), matches.map { it.id })
    }

    @Test
    fun fallsBackFromMissingActiveNameToOuterArchiveBeforeInnerEntry() {
        val archived = rom.copy(gameBasename = "Pokemon - Modern Emerald Version v3.5")
        val candidates = listOf(
            candidate("outer", "RetroArch/saves/Pokemon Modern Emerald.srm"),
            candidate("inner", "RetroArch/saves/Pokemon - Modern Emerald Version v3.5.srm"),
        )

        val matches = SaveDocumentResolver.matching(
            entry = archived,
            documents = candidates,
            activeGameBasename = "Missing Active Alias",
        )

        assertEquals(listOf("outer"), matches.map { it.id })
    }

    private fun candidate(id: String, path: String) = SaveDocumentSource(
        id = id,
        displayPath = path,
        name = path.substringAfterLast('/'),
        size = 128 * 1024L,
        lastModifiedEpochMs = 1,
        open = { byteArrayOf().inputStream() },
    )
}
