package com.enrpau.dualscreendex.parser.cli

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportPublicationTest {
    @Test
    fun publishesCompleteReportByReplacingTheDestination() {
        withOutputDirectory { directory ->
            val target = directory.resolve("report.json")
            Files.writeString(target, "old")

            writeAtomically(target) { it.write("complete") }

            assertEquals("complete", Files.readString(target))
            assertEquals(listOf("report.json"), Files.list(directory).use { paths -> paths.map { it.fileName.toString() }.toList() })
        }
    }

    @Test
    fun failedReportWritePreservesDestinationAndRemovesTemporaryFile() {
        withOutputDirectory { directory ->
            val target = directory.resolve("report.json")
            Files.writeString(target, "old")

            val failure = runCatching {
                writeAtomically(target) {
                    it.write("partial")
                    error("synthetic write failure")
                }
            }.exceptionOrNull()

            assertTrue(failure is IllegalStateException)
            assertEquals("old", Files.readString(target))
            assertEquals(listOf("report.json"), Files.list(directory).use { paths -> paths.map { it.fileName.toString() }.toList() })
        }
    }

    private fun withOutputDirectory(block: (Path) -> Unit) {
        val parent = Path.of("build", "tmp", "report-publication-test").toAbsolutePath()
        Files.createDirectories(parent)
        val directory = Files.createTempDirectory(parent, "case-")
        try {
            block(directory)
        } finally {
            Files.walk(directory).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }
}
