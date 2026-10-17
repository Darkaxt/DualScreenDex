package com.enrpau.dualscreendex.companion.testing

import org.junit.Assume.assumeTrue

/** Test-only opt-in; it does not replace a separately reviewed input admission. */
internal object OriginalRomTestAccess {
    fun requireEnabled(optIn: String? = System.getenv("DUALDEX_TEST_ORIGINAL_ROM_ACCESS")) {
        readIfEnabled(optIn) { }
    }

    internal fun <T> readIfEnabled(optIn: String?, reader: () -> T): T {
        assumeTrue("original-ROM tests require explicit reviewed opt-in", optIn == "true")
        return reader()
    }
}
