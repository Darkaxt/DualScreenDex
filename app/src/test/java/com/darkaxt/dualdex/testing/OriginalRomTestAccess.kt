package com.darkaxt.dualdex.testing

import org.junit.Assume.assumeTrue

/** Test-only opt-in; it does not replace a separately reviewed input admission. */
internal object OriginalRomTestAccess {
    fun requireEnabled(optIn: String? = System.getenv("DUALDEX_TEST_ORIGINAL_ROM_ACCESS")) {
        readIfEnabled(optIn) { }
    }

    fun <T> read(reader: () -> T): T =
        readIfEnabled(System.getenv("DUALDEX_TEST_ORIGINAL_ROM_ACCESS"), reader)

    internal fun <T> readIfEnabled(optIn: String?, reader: () -> T): T {
        assumeTrue("original-ROM tests require explicit reviewed opt-in", optIn == "true")
        return reader()
    }
}
