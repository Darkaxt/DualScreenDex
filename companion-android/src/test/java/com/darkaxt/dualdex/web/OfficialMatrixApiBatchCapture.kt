package com.darkaxt.dualdex.web

import com.darkaxt.dualdex.catalog.CatalogDatabaseFactory
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path

internal typealias MatrixApiCapture = (
    source: Path,
    workingDirectory: Path,
    expected: MatrixCacheControl,
    databaseFactory: CatalogDatabaseFactory,
    binding: MatrixG3Binding,
) -> JsonObject

/** Applies the single-cache production bootstrap capture to one explicit, SHA-keyed run partition. */
internal object OfficialMatrixApiBatchCapture {
    private val sha256 = Regex("[0-9a-f]{64}")
    private val gson = GsonBuilder().serializeNulls().create()

    fun capture(
        cacheDirectory: Path,
        workingDirectory: Path,
        controls: List<MatrixCacheControl>,
        databaseFactory: CatalogDatabaseFactory,
        binding: MatrixG3Binding,
        captureOne: MatrixApiCapture = OfficialMatrixApiCapture::capture,
    ): JsonObject {
        require(controls.size in 1..44) { "a run partition requires one to 44 controls" }
        require(controls.all { sha256.matches(it.romSha256) }) { "invalid control identity" }
        require(controls.map(MatrixCacheControl::romSha256).toSet().size == controls.size) {
            "duplicate control identity"
        }

        val caches = cacheDirectory.toAbsolutePath().normalize()
        val working = workingDirectory.toAbsolutePath().normalize()
        require(Files.isDirectory(caches, NOFOLLOW_LINKS) && caches.toRealPath() == caches) {
            "cache directory must be an existing unaliased directory"
        }
        require(Files.notExists(working, NOFOLLOW_LINKS)) {
            "working directory already exists or is inaccessible"
        }
        require(Files.isDirectory(working.parent, NOFOLLOW_LINKS) && working.parent.toRealPath() == working.parent) {
            "working parent must be an existing unaliased directory"
        }

        Files.createDirectory(working)
        val captured = JsonObject()
        controls.sortedBy(MatrixCacheControl::romSha256).forEach { expected ->
            captured.add(
                expected.romSha256,
                captureOne(
                    caches.resolve("${expected.romSha256}.sqlite"),
                    working.resolve(expected.romSha256),
                    expected,
                    databaseFactory,
                    binding,
                ),
            )
        }
        return JsonObject().apply {
            addProperty("schemaVersion", 1)
            add("binding", gson.toJsonTree(binding))
            add("controls", captured)
        }
    }
}
