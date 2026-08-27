package com.darkaxt.dualdex.save.cli

import java.nio.file.Files
import java.nio.file.Path

internal object SaveCliPathPolicy {
    fun validate(options: SaveCliOptions) {
        val inputs = buildList {
            options.pairs.forEachIndexed { index, pair ->
                add(Target("pair[$index] ROM", pair.rom))
                add(Target("pair[$index] SaveRAM", pair.save))
            }
            options.probes.forEachIndexed { index, path -> add(Target("probe[$index]", path)) }
        }
        val outputs = listOf(
            Target("JSON output", options.json),
            Target("Markdown output", options.markdown),
        )

        outputs.forEachIndexed { index, output ->
            (inputs + outputs.take(index)).forEach { other ->
                require(!collides(output.path, other.path)) {
                    "${output.label} collides with ${other.label}"
                }
            }
        }
    }

    private fun collides(first: Path, second: Path): Boolean {
        val firstResolved = resolveExistingAncestry(first)
        val secondResolved = resolveExistingAncestry(second)
        if (firstResolved == secondResolved) return true
        if (firstResolved.startsWith(secondResolved) || secondResolved.startsWith(firstResolved)) return true
        if (!Files.exists(first) || !Files.exists(second)) return false
        return try {
            Files.isSameFile(first, second)
        } catch (failure: Exception) {
            throw IllegalArgumentException("path identity could not be verified", failure)
        }
    }

    private fun resolveExistingAncestry(path: Path): Path {
        val absolute = path.toAbsolutePath().normalize()
        var existing: Path? = absolute
        val missing = mutableListOf<String>()
        while (existing != null && !Files.exists(existing)) {
            existing.fileName?.toString()?.let(missing::add)
            existing = existing.parent
        }
        var resolved = existing?.toRealPath() ?: absolute.root ?: absolute
        missing.asReversed().forEach { name -> resolved = resolved.resolve(name) }
        return resolved.normalize()
    }

    private data class Target(val label: String, val path: Path)
}
