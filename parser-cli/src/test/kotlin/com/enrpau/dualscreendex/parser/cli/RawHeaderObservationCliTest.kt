package com.enrpau.dualscreendex.parser.cli

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.jar.JarFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/** Fabricated inputs through the packaged production main, not a second parser runner. */
class RawHeaderObservationCliTest {
    @Test fun gbHashesExactlyTheCompleteHeader() {
        assertObservation("gb.gba", "GB", GB_HASH, 80)
        assertObservation("gb-minimum.gb", "GB", GB_HASH, 80)
    }

    @Test fun bothCgbFlagsUseTheGbHeaderWindow() {
        assertObservation("gbc80.gb", "GBC", "dfc267150d87105e958df10df65d1d9631b343faf946d775e7d7fce4ac8e1b69", 80)
        assertObservation("gbcC0.gba", "GBC", "dc1cb87d46caff08d74ec91ee3a80593b3c80d22c80bf3695ae3e0c185f0f556", 80)
    }

    @Test fun gbaHashesExactlyTheCompleteHeader() {
        assertObservation("gba.gb", "GBA", GBA_HASH, 192)
        assertObservation("gba-minimum.gba", "GBA", GBA_HASH, 192)
    }

    @Test fun irrelevantBytesDoNotChangeGbObservation() {
        assertObservation("gb-outside.gb", "GB", GB_HASH, 80)
        assertTrue(analysis("gb.gba")["sha256"] != analysis("gb-outside.gb")["sha256"])
    }

    @Test fun irrelevantBytesDoNotChangeGbaObservation() {
        assertObservation("gba-outside.gba", "GBA", GBA_HASH, 192)
        assertTrue(analysis("gba.gb")["sha256"] != analysis("gba-outside.gba")["sha256"])
    }

    @Test fun gbFirstAndLastHeaderBytesAreIncluded() {
        assertObservation("gb-first.gb", "GB", "5deb941177a517ea33ef6c7c2d21f2a1723a32811591831a84c15da18470ed32", 80)
        assertObservation("gb-last.gb", "GB", "4f7b3b0b5794d753eb8119aff98d83994e04105c055ec51f8ecbd414a3a85dc5", 80)
    }

    @Test fun gbaFirstAndLastHeaderBytesAreIncluded() {
        assertObservation("gba-first.gba", "GBA", "9fec6a94324be60b75d115c2040257e0e4244f1dbb79b587c3689cc1c863bad6", 192)
        assertObservation("gba-last.gba", "GBA", "eb846f2c9ed81d76204e4622d263e48729586a11a93670eba4026fa891f1e82d", 192)
    }

    @Test fun sanitizedTitleIsNotTheHashedEvidence() {
        assertEquals(analysis("gb.gba").getAsJsonObject("header")["title"],
            analysis("gb-hidden-title.gb").getAsJsonObject("header")["title"])
        assertObservation("gb-hidden-title.gb", "GB", "711f39c4981c44e420b6f8a509a1c5a2c4de8df9dc649887d5feaf9ddf9b2681", 80)
    }

    @Test fun unknownPlatformHasNoHeaderDigestRegardlessOfExtension() {
        for (name in listOf("unknown.gb", "unknown.gbc", "unknown.gba", "empty.gb")) {
            assertEquals("UNKNOWN", analysis(name).getAsJsonObject("header")["platform"].asString)
            assertFalse("misleading raw header observation for $name", rows.getValue(name).has("rawHeader"))
        }
    }

    @Test fun truncatedHeadersHaveNoPartialOrPaddedDigest() {
        for (name in listOf("gb-truncated.gb", "gbc-truncated.gbc", "gba-truncated.gba")) {
            assertEquals("UNKNOWN", analysis(name).getAsJsonObject("header")["platform"].asString)
            assertFalse("misleading truncated header observation for $name", rows.getValue(name).has("rawHeader"))
        }
    }

    @Test fun publicObservationIsOnlyHashAndCountAtExplicitSchema16() {
        assertEquals(16, report["schemaVersion"].asInt)
        assertEquals(1, receipt["schemaVersion"].asInt)
        assertEquals(16, receipt.getAsJsonObject("generator")["schemaVersion"].asInt)
        val observation = observation("gb.gba")
        assertEquals(setOf("rawHeaderSha256", "byteCount"), observation.keySet())
        assertEquals(64, observation["rawHeaderSha256"].asString.length)
        assertFalse(Files.readString(output.resolve("report.json")).contains(inputs.toString()))
    }

    @Test fun reportAndReceiptRetainExactRuntimeAndInputBindings() {
        val execution = report.getAsJsonObject("execution")
        val generator = receipt.getAsJsonObject("generator")
        val manifest = jars.sortedBy { it.fileName.toString() }.joinToString("") {
            "${it.fileName}\t${Files.size(it)}\t${digest(Files.readAllBytes(it))}\n"
        }
        val runtimeDigest = digest(manifest.toByteArray(Charsets.UTF_8))
        assertEquals(sourceCommit, execution["sourceCommit"].asString)
        assertEquals(sourceCommit, receipt["sourceCommit"].asString)
        assertEquals(runtimeDigest, execution["generatorSha256"].asString)
        assertEquals(runtimeDigest, generator["sha256"].asString)
        assertEquals("parser-cli", generator["name"].asString)
        assertEquals(digest(Files.readAllBytes(output.resolve("report.json"))), receipt["rawReportSha256"].asString)
        assertEquals(fixtures.size, receipt["inputCount"].asInt)
        assertEquals(fixtures.keys, rows.keys)
        fixtures.forEach { (name, bytes) ->
            assertEquals(digest(bytes), analysis(name)["sha256"].asString)
            assertEquals(bytes.size, analysis(name)["size"].asInt)
        }
    }

    @Test fun packagedMainRejectsWrongSourceCommitBeforeInputDiscovery() {
        val bad = if (sourceCommit == "a".repeat(40)) "b".repeat(40) else "a".repeat(40)
        val directory = output.resolve("wrong-source")
        assertTrue(launch(directory, bad) != 0)
        assertTrue(Files.readString(directory.resolve("process.log")).contains("does not match the parser CLI build source"))
        assertFalse(Files.exists(directory.resolve("report.json")))
        assertFalse(Files.exists(directory.resolve("receipt.json")))
    }

    @Test fun unpackagedMainStillFailsClosed() {
        val directory = output.resolve("unpackaged")
        Files.createDirectories(directory)
        val classes = directory.resolve("classes")
        Files.createDirectories(classes)
        JarFile(jars.single { it.fileName.toString() == "parser-cli.jar" || it.fileName.toString().startsWith("parser-cli-") }.toFile()).use { jar ->
            jar.entries().asSequence().filter { !it.isDirectory && it.name.endsWith(".class") }.forEach { entry ->
                val target = classes.resolve(entry.name)
                Files.createDirectories(target.parent)
                jar.getInputStream(entry).use { Files.copy(it, target) }
            }
        }
        val classpath = (listOf(classes) + jars).joinToString(File.pathSeparator)
        assertTrue(launch(directory, sourceCommit, classpath) != 0)
        assertTrue(Files.readString(directory.resolve("process.log")).contains("must run from a packaged generator artifact"))
        assertFalse(Files.exists(directory.resolve("report.json")))
        assertFalse(Files.exists(directory.resolve("receipt.json")))
    }

    private fun analysis(name: String): JsonObject = rows.getValue(name).getAsJsonObject("result")

    private fun observation(name: String): JsonObject {
        val row = rows.getValue(name)
        assertTrue("missing rawHeader observation for $name", row.has("rawHeader"))
        return row.getAsJsonObject("rawHeader")
    }

    private fun assertObservation(name: String, platform: String, hash: String, count: Int) {
        assertEquals(platform, analysis(name).getAsJsonObject("header")["platform"].asString)
        val actual = observation(name)
        assertEquals(setOf("rawHeaderSha256", "byteCount"), actual.keySet())
        assertEquals(hash, actual["rawHeaderSha256"].asString)
        assertEquals(count, actual["byteCount"].asInt)
    }

    companion object {
        // Independently computed with Python hashlib over the byte formulas below; no production hash helper.
        private const val GB_HASH = "a2e47919e1b3b8e5b8562655908a1efd28fe0fb575b3ff33ddcf75b4f872d713"
        private const val GBA_HASH = "403cc6f01514b00976f41a377290b3000c975920e23a753ddaeeca754154090e"
        private lateinit var output: Path
        private lateinit var inputs: Path
        private lateinit var jars: List<Path>
        private lateinit var sourceCommit: String
        private lateinit var fixtures: Map<String, ByteArray>
        private lateinit var report: JsonObject
        private lateinit var receipt: JsonObject
        private lateinit var rows: Map<String, JsonObject>

        @JvmStatic @BeforeClass fun runFabricatedCli() {
            output = System.getProperty("dualdex.test.cliEvidence")?.let(Path::of)
                ?: Files.createTempDirectory("dualdex-header-cli-")
            Files.createDirectories(output)
            inputs = output.resolve("fabricated-inputs")
            Files.createDirectories(inputs)
            val distribution = Path.of(requireNotNull(System.getProperty("dualdex.test.cliDistribution")))
            jars = Files.list(distribution.resolve("lib")).use { paths ->
                paths.filter { it.fileName.toString().endsWith(".jar") }.toList()
            }
            sourceCommit = JarFile(jars.single { it.fileName.toString() == "parser-cli.jar" || it.fileName.toString().startsWith("parser-cli-") }.toFile()).use {
                it.manifest.mainAttributes.getValue("DualDex-Source-Commit")
            }
            val gb = pattern().also {
                "SYNTHETIC".toByteArray(Charsets.US_ASCII).copyInto(it, 0x134)
                it.fill(0, 0x13D, 0x144)
            }
            val gbc = gb.copyOf().also { it[0x143] = 0x80.toByte() }
            val gba = pattern().also {
                byteArrayOf(0x24, 0xFF.toByte(), 0xAE.toByte(), 0x51, 0x69, 0x9A.toByte(),
                    0xA2.toByte(), 0x21, 0x3D, 0x84.toByte(), 0x82.toByte(), 0x0A).copyInto(it, 4)
                "SYNTHETIC".toByteArray(Charsets.US_ASCII).copyInto(it, 0xA0)
                it.fill(0, 0xA9, 0xAC)
                "ZXQJ".toByteArray(Charsets.US_ASCII).copyInto(it, 0xAC)
            }
            fixtures = linkedMapOf(
                "gb.gba" to gb, "gb-minimum.gb" to gb.copyOf(0x150),
                "gbc80.gb" to gbc, "gbcC0.gba" to gbc.copyOf().also { it[0x143] = 0xC0.toByte() },
                "gba.gb" to gba, "gba-minimum.gba" to gba.copyOf(0xC0),
                "gb-outside.gb" to gb.changed(0, 0xFF, 0x150, 0x1FF),
                "gba-outside.gba" to gba.changed(0xC0, 0x1FF),
                "gb-first.gb" to gb.changed(0x100), "gb-last.gb" to gb.changed(0x14F),
                "gba-first.gba" to gba.changed(0), "gba-last.gba" to gba.changed(0xBF),
                "gb-hidden-title.gb" to gb.copyOf().also { it[0x13D] = 0x80.toByte() },
                "unknown.gb" to ByteArray(512), "unknown.gbc" to ByteArray(512),
                "unknown.gba" to ByteArray(512), "empty.gb" to ByteArray(0),
                "gb-truncated.gb" to gb.copyOf(0x14F), "gbc-truncated.gbc" to gbc.copyOf(0x14F),
                "gba-truncated.gba" to gba.copyOf(0xBF),
            )
            fixtures.forEach { (name, bytes) -> Files.write(inputs.resolve(name), bytes) }
            assertEquals("fabricated CLI process failed; inspect $output/process.log", 0, launch(output, sourceCommit))
            report = JsonParser.parseString(Files.readString(output.resolve("report.json"))).asJsonObject
            receipt = JsonParser.parseString(Files.readString(output.resolve("receipt.json"))).asJsonObject
            rows = report.getAsJsonArray("results").associate { element ->
                val row = element.asJsonObject
                assertFalse("fabricated fixture read/parse failure: $row", row.has("error"))
                row["displayName"].asString to row
            }
        }

        private fun pattern() = ByteArray(512) { ((it * 73 + 19) and 0xFF).toByte() }
        private fun ByteArray.changed(vararg offsets: Int) = copyOf().also { bytes ->
            offsets.forEach { bytes[it] = (bytes[it].toInt() xor 1).toByte() }
        }

        private fun launch(directory: Path, commit: String, classpath: String = jars.joinToString(File.pathSeparator)): Int {
            Files.createDirectories(directory)
            val command = listOf(
                Path.of(System.getProperty("java.home"), "bin",
                    if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java").toString(),
                "-Xmx512m", "-Djava.io.tmpdir=${System.getProperty("java.io.tmpdir")}",
                "-cp", classpath, "com.enrpau.dualscreendex.parser.cli.MainKt", inputs.toString(),
                "--all-roms", "--jobs", "1", "--json", directory.resolve("report.json").toString(),
                "--markdown", directory.resolve("report.md").toString(),
                "--execution-receipt", directory.resolve("receipt.json").toString(), "--source-commit", commit,
            )
            Files.writeString(directory.resolve("command.json"), GsonBuilder().setPrettyPrinting().create().toJson(command))
            val process = ProcessBuilder(command).redirectErrorStream(true)
                .redirectOutput(directory.resolve("process.log").toFile()).start()
            try {
                assertTrue("fabricated CLI timed out", process.waitFor(60, TimeUnit.SECONDS))
                return process.exitValue()
            } finally {
                if (process.isAlive) {
                    process.destroyForcibly()
                    process.waitFor(10, TimeUnit.SECONDS)
                }
            }
        }

        private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
