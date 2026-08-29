package com.enrpau.dualscreendex.parser.cli

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CliOptionsTest {
    @Test
    fun parsesExplicitAllRomCorpusMode() {
        val options = CliOptions.parse(
            arrayOf(
                "roms", "--json", "report.json", "--markdown", "report.md",
                "--execution-receipt", "receipt.json", "--source-commit", "a".repeat(40),
                "--all-roms", "--jobs", "6",
            ),
        )

        assertEquals(listOf("roms"), options.roots.map { it.toString() })
        assertTrue(options.includeAllRomNames)
        assertEquals(6, options.jobs)
        assertEquals("a".repeat(40), options.sourceCommit)
        assertEquals("receipt.json", options.executionReceipt.toString())
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
                "--execution-receipt",
                "receipt.json",
                "--source-commit",
                "a".repeat(40),
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

    @Test
    fun requiresExecutionReceiptAndFullSourceCommit() {
        val missingReceipt = assertThrows(IllegalArgumentException::class.java) {
            CliOptions.parse(
                arrayOf(
                    "roms", "--json", "report.json", "--markdown", "report.md",
                    "--source-commit", "a".repeat(40),
                ),
            )
        }
        assertEquals("--execution-receipt is required", missingReceipt.message)

        val invalidCommit = assertThrows(IllegalArgumentException::class.java) {
            CliOptions.parse(
                arrayOf(
                    "roms", "--json", "report.json", "--markdown", "report.md",
                    "--execution-receipt", "receipt.json", "--source-commit", "short",
                ),
            )
        }
        assertEquals("--source-commit requires a full lowercase commit", invalidCommit.message)
    }
}
