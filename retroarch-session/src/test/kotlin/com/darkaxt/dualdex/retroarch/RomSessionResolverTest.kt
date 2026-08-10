package com.darkaxt.dualdex.retroarch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RomSessionResolverTest {
    private val emerald = RomIndexEntry(
        sourceId = "emerald-direct",
        sourceName = "Pokemon - Emerald Version (USA, Europe).gba",
        archiveEntry = null,
        platform = RomPlatform.GBA,
        gameBasename = "Pokemon - Emerald Version (USA, Europe)",
        crc32 = "1F1C08FB",
        sha256 = "a".repeat(64),
    )

    @Test
    fun exactCrcAndSystemResolveBeforeBasenameVariations() {
        val result = RomSessionResolver.resolve(
            RetroArchStatus.Running(false, "Nintendo - Game Boy Advance", "Pokemon Emerald", "1f1c08fb"),
            listOf(emerald),
        )

        assertEquals(SessionResolution.Resolved(emerald), result)
    }

    @Test
    fun currentRetroArchSystemSlugAndBasenameResolveAnIndexedArchiveMember() {
        val modernEmerald = emerald.copy(
            sourceId = "modern-emerald-zip",
            sourceName = "Pokemon - Modern Emerald Version v3.5 (USA, Europe).zip!Pokemon - Modern Emerald Version v3.5 (USA, Europe).gba",
            archiveEntry = "Pokemon - Modern Emerald Version v3.5 (USA, Europe).gba",
            gameBasename = "Pokemon - Modern Emerald Version v3.5 (USA, Europe)",
            crc32 = "8C7DBECA",
        )

        val result = RomSessionResolver.resolve(
            RetroArchStatus.Running(
                paused = false,
                systemId = "game_boy_advance",
                gameBasename = modernEmerald.gameBasename,
                crc32 = null,
            ),
            listOf(emerald, modernEmerald),
        )

        assertEquals(SessionResolution.Resolved(modernEmerald), result)
    }

    @Test
    fun aReportedCrcMismatchNeverFallsBackToBasename() {
        val result = RomSessionResolver.resolve(
            RetroArchStatus.Running(
                false,
                "Nintendo - Game Boy Advance",
                "Pokemon - Emerald Version (USA, Europe)",
                "DEADBEEF",
            ),
            listOf(emerald),
        )

        assertTrue(result is SessionResolution.NotFound)
    }

    @Test
    fun duplicateSourcesWithTheSameIndexedShaResolveDeterministically() {
        val second = emerald.copy(sourceId = "emerald-zip", archiveEntry = "Pokemon Emerald.gba")
        val result = RomSessionResolver.resolve(
            RetroArchStatus.Running(false, "Nintendo - Game Boy Advance", emerald.gameBasename, emerald.crc32),
            listOf(second, emerald),
        )

        assertEquals(SessionResolution.Resolved(emerald), result)
    }

    @Test
    fun matchingEvidenceWithDifferentIndexedHashesRemainsAmbiguous() {
        val second = emerald.copy(sourceId = "emerald-zip", sha256 = "b".repeat(64))
        val result = RomSessionResolver.resolve(
            RetroArchStatus.Running(false, "Nintendo - Game Boy Advance", emerald.gameBasename, emerald.crc32),
            listOf(second, emerald),
        )

        assertEquals(SessionResolution.Ambiguous(listOf(emerald, second)), result)
    }

    @Test
    fun shaVerificationIsTheFinalCatalogAuthority() {
        assertTrue(RomSessionResolver.verifySha(emerald, "A".repeat(64)))
        assertTrue(!RomSessionResolver.verifySha(emerald, "b".repeat(64)))
    }
}
