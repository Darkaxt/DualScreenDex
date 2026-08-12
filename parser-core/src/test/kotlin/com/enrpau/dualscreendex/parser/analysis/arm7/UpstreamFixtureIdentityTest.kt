package com.enrpau.dualscreendex.parser.analysis.arm7

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpstreamFixtureIdentityTest {
    @Test
    fun committedMitCpuFixturesMatchThePinnedUpstreamCommit() {
        EXPECTED_SHA256.forEach { (resourceName, expectedDigest) ->
            val stream = javaClass.getResourceAsStream("/arm7/$resourceName")
            assertNotNull("missing committed ARM7 fixture $resourceName", stream)
            val bytes = requireNotNull(stream).use { it.readBytes() }
            assertEquals(expectedDigest, sha256(bytes))
        }
    }

    @Test
    fun noticePinsProvenanceAndExcludesCommercialData() {
        val notice = Files.readString(repositoryRoot().resolve("third_party/gba-recomp/NOTICE.md"))

        assertTrue(notice.contains("agnt-gg/gba-pokemon-rom-to-wasm"))
        assertTrue(notice.contains(PINNED_COMMIT))
        assertTrue(notice.contains("build/arm.gba"))
        assertTrue(notice.contains("build/thumb.gba"))
        assertTrue(notice.contains("no commercial ROM, BIOS, game asset, or save data", ignoreCase = true))
    }

    private fun repositoryRoot(): Path = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
        .first { Files.isRegularFile(it.resolve("settings.gradle.kts")) }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private companion object {
        const val PINNED_COMMIT = "91b814c0ff63ded6fbf0c47d082dac2332d4a7f3"
        val EXPECTED_SHA256 = linkedMapOf(
            "arm.gba" to "77ee88662552bdc885c1080c0172ff119d54db791bd73b21808cf1ff1fe5b40e",
            "thumb.gba" to "b5cb2291df4ab314b31c598acd9bff2ccfa0b38efff29daadfe97422ce369b67",
        )
    }
}
