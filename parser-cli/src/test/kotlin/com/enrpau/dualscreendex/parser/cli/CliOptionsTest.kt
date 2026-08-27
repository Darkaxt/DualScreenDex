package com.enrpau.dualscreendex.parser.cli

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CliOptionsTest {
    @Test
    fun parsesExplicitAllRomCorpusMode() {
        val options = CliOptions.parse(
            arrayOf("roms", "--json", "report.json", "--markdown", "report.md", "--all-roms", "--jobs", "6"),
        )

        assertEquals(listOf("roms"), options.roots.map { it.toString() })
        assertTrue(options.includeAllRomNames)
        assertEquals(6, options.jobs)
    }

    @Test
    fun capsExcessiveJobCountAtTheDocumentedMaximum() {
        val options = CliOptions.parse(
            arrayOf(
                "roms",
                "--json",
                "report.json",
                "--markdown",
                "report.md",
                "--jobs",
                Int.MAX_VALUE.toString(),
            ),
        )

        assertEquals(8, options.jobs)
    }

    @Test
    fun rejectsNonPositiveJobCount() {
        val failure = assertThrows(IllegalArgumentException::class.java) {
            CliOptions.parse(
                arrayOf("roms", "--json", "report.json", "--markdown", "report.md", "--jobs", "0"),
            )
        }

        assertEquals("--jobs requires a positive integer", failure.message)
    }
}
