package com.enrpau.dualscreendex.parser.cli

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assume.assumeTrue

/** Test-only opt-in; setting it does not replace a separately reviewed input admission. */
internal object OriginalRomTestAccess {
    fun readAllBytes(
        path: Path,
        optIn: String? = System.getenv("DUALDEX_TEST_ORIGINAL_ROM_ACCESS"),
    ): ByteArray = readIfEnabled(optIn) { Files.readAllBytes(path) }

    fun <T> read(reader: () -> T): T =
        readIfEnabled(System.getenv("DUALDEX_TEST_ORIGINAL_ROM_ACCESS"), reader)

    internal fun <T> readIfEnabled(optIn: String?, reader: () -> T): T {
        assumeTrue("original-ROM tests require explicit reviewed opt-in", optIn == "true")
        return reader()
    }
}
