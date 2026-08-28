package com.enrpau.dualscreendex.parser.cli

import java.nio.file.Files
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecutionReceiptTest {
    @Test
    fun `execution receipt binds embedded source generator artifact and raw report bytes`() {
        val directory = Files.createTempDirectory("dualdex-execution-receipt-")
        try {
            val rawReport = directory.resolve("compatibility.json")
            val generator = directory.resolve("parser-cli.jar")
            val dependency = directory.resolve("parser-core.jar")
            Files.writeString(rawReport, "raw-report")
            Files.writeString(generator, "generator-artifact")
            Files.writeString(dependency, "dependency-artifact")
            val artifacts = listOf(generator, dependency)
            val identity = CorpusExecutionIdentity(
                sourceCommit = "a".repeat(40),
                generatorSha256 = runtimeClasspathSha256(artifacts),
            )

            val receipt = CorpusExecutionReceipt.fromFiles(
                rawReport = rawReport,
                generatorArtifacts = artifacts,
                identity = identity,
                inputCount = 334,
            )
            val encoded = ReportWriter.executionReceiptJson(receipt)

            assertEquals(1, receipt.schemaVersion)
            assertEquals(13, receipt.generator.schemaVersion)
            assertEquals(identity.sourceCommit, receipt.sourceCommit)
            assertEquals(identity.generatorSha256, receipt.generator.sha256)
            assertEquals(sha256(Files.readAllBytes(rawReport)), receipt.rawReportSha256)
            assertEquals(334, receipt.inputCount)
            assertFalse(encoded.contains(directory.toString()))
            assertTrue(encoded.endsWith("\n"))
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `receipt rejects a generator artifact that differs from report identity`() {
        val directory = Files.createTempDirectory("dualdex-execution-receipt-mismatch-")
        try {
            val rawReport = directory.resolve("compatibility.json")
            val generator = directory.resolve("parser-cli.jar")
            Files.writeString(rawReport, "raw-report")
            Files.writeString(generator, "generator-artifact")

            CorpusExecutionReceipt.fromFiles(
                rawReport = rawReport,
                generatorArtifacts = listOf(generator),
                identity = CorpusExecutionIdentity("a".repeat(40), "b".repeat(64)),
                inputCount = 334,
            )
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `runtime classpath digest changes when a dependency jar changes`() {
        val directory = Files.createTempDirectory("dualdex-runtime-classpath-")
        try {
            val generator = directory.resolve("parser-cli.jar")
            val dependency = directory.resolve("parser-core.jar")
            Files.writeString(generator, "generator-artifact")
            Files.writeString(dependency, "dependency-before")
            val artifacts = listOf(generator, dependency)
            val before = runtimeClasspathSha256(artifacts)

            Files.writeString(dependency, "dependency-after")

            val after = runtimeClasspathSha256(artifacts)
            assertTrue(before != after)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte) }
}
