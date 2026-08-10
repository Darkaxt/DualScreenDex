package com.enrpau.dualscreendex.parser.cli

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CliOptionsTest {
    @Test
    fun parsesExplicitAllRomCorpusMode() {
        val options = CliOptions.parse(
            arrayOf("roms", "--json", "report.json", "--markdown", "report.md", "--all-roms"),
        )

        assertEquals(listOf("roms"), options.roots.map { it.toString() })
        assertTrue(options.includeAllRomNames)
    }
}
