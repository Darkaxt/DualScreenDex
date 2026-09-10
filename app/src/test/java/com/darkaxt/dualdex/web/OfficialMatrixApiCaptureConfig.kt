package com.darkaxt.dualdex.web

import com.google.gson.GsonBuilder
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.WRITE
import java.security.MessageDigest

internal data class MatrixApiCaptureConfig(
    val schemaVersion: Int,
    val cacheDirectory: String,
    val binding: MatrixG3Binding,
    val controls: List<MatrixCacheControl>,
)

internal object OfficialMatrixApiCaptureConfig {
    private const val MAX_CONFIG_BYTES = 1024 * 1024
    private val sha256 = Regex("[0-9a-f]{64}")
    private val gson = GsonBuilder().serializeNulls().create()

    fun read(path: Path, expectedSha256: String): MatrixApiCaptureConfig {
        require(sha256.matches(expectedSha256)) { "invalid configuration digest" }
        val input = path.toAbsolutePath().normalize()
        require(Files.isRegularFile(input, NOFOLLOW_LINKS) && input.toRealPath() == input) {
            "configuration must be a regular unaliased file"
        }
        require(Files.size(input) in 1..MAX_CONFIG_BYTES.toLong()) { "configuration byte bound exceeded" }
        val bytes = Files.newInputStream(input, NOFOLLOW_LINKS).use { it.readNBytes(MAX_CONFIG_BYTES + 1) }
        require(bytes.size in 1..MAX_CONFIG_BYTES && digest(bytes) == expectedSha256) {
            "configuration digest mismatch"
        }
        return gson.fromJson(String(bytes, Charsets.UTF_8), MatrixApiCaptureConfig::class.java).also {
            require(it.schemaVersion == 1) { "unsupported configuration schema" }
            require(it.cacheDirectory.isNotBlank()) { "missing cache directory" }
            require(it.controls.size in 1..44) { "invalid control count" }
        }
    }

    fun digest(path: Path): String = digest(Files.readAllBytes(path))

    private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }
}

/** Explicit opt-in JUnit entrypoint used by the bounded host capture runner. */
class OfficialMatrixApiCaptureRunnerTest {
    @Test fun capturesConfiguredOfficialMatrixPartition() {
        val configPath = System.getProperty("dualdex.matrix.capture.config")
        assumeTrue("set dualdex.matrix.capture.config for an official capture", !configPath.isNullOrBlank())
        val configDigest = requireNotNull(System.getProperty("dualdex.matrix.capture.configSha256")) {
            "dualdex.matrix.capture.configSha256 is required"
        }
        val output = Path.of(requireNotNull(System.getProperty("dualdex.matrix.capture.output")) {
            "dualdex.matrix.capture.output is required"
        }).toAbsolutePath().normalize()
        val working = Path.of(requireNotNull(System.getProperty("dualdex.matrix.capture.working")) {
            "dualdex.matrix.capture.working is required"
        }).toAbsolutePath().normalize()
        require(Files.isDirectory(output.parent, NOFOLLOW_LINKS) && output.parent.toRealPath() == output.parent) {
            "output parent must be an existing unaliased directory"
        }
        require(Files.notExists(output, NOFOLLOW_LINKS)) { "output already exists or is inaccessible" }

        val config = OfficialMatrixApiCaptureConfig.read(Path.of(configPath), configDigest)
        val result = OfficialMatrixApiBatchCapture.capture(
            cacheDirectory = Path.of(config.cacheDirectory),
            workingDirectory = working,
            controls = config.controls,
            databaseFactory = JdbcTestCatalogDatabaseFactory,
            binding = config.binding,
        )
        val bytes = (GsonBuilder().disableHtmlEscaping().serializeNulls().create().toJson(result) + "\n")
            .toByteArray(Charsets.UTF_8)
        require(bytes.size <= 128 * 1024 * 1024) { "API capture byte bound exceeded" }
        Files.newOutputStream(output, CREATE_NEW, WRITE).use { stream ->
            require(runCatching { stream.write(bytes) }.isSuccess) { "API capture write failed" }
        }
    }
}
