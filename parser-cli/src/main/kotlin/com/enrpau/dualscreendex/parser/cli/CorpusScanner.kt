package com.enrpau.dualscreendex.parser.cli

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.io.RomSourceLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

data class CorpusInput(
    val displayName: String,
    val source: String,
    val archiveEntry: String? = null,
    internal val path: Path? = null,
    val error: String? = null,
) {
    internal fun loadRom(): RomImage {
        val inputPath = requireNotNull(path) { "input has no ROM path" }
        return if (archiveEntry == null) {
            RomSourceLoader.load(displayName, inputPath).rom
        } else {
            RomSourceLoader.loadZipEntry(
                inputPath.fileName.toString(),
                inputPath,
                archiveEntry,
            ).rom
        }
    }
}

class CorpusScanner(
    private val includeAllRomNames: Boolean = false,
) {
    fun scan(roots: List<Path>): Sequence<CorpusInput> = roots.asSequence()
        .flatMap(::scan)

    fun scan(root: Path): Sequence<CorpusInput> = sequence {
        if (!Files.exists(root)) {
            yield(error(root.fileName?.toString() ?: root.toString(), root.toString(), "root does not exist"))
            return@sequence
        }
        Files.walk(root).use { stream ->
            val files = stream.iterator()
            while (files.hasNext()) {
                val file = files.next()
                if (!Files.isRegularFile(file) || !supportedOuterExtension(file)) continue
                for (input in scanFile(root, file)) yield(input)
            }
        }
    }

    private fun scanFile(root: Path, file: Path): List<CorpusInput> {
        val source = normalizedRelative(root, file)
        return if (extension(file) == "zip") scanZip(file, source) else scanDirect(file, source)
    }

    private fun scanDirect(file: Path, source: String): List<CorpusInput> {
        if ((!includeAllRomNames && !isPokemonName(file.fileName.toString())) ||
            isExcludedNonMainlineName(file.fileName.toString())
        ) return emptyList()
        return listOf(CorpusInput(file.fileName.toString(), source, path = file))
    }

    private fun scanZip(file: Path, source: String): List<CorpusInput> {
        if (isExcludedNonMainlineName(file.fileName.toString())) return emptyList()
        val outerMatches = isPokemonName(file.fileName.toString())
        return try {
            RomSourceLoader.zipRomEntries(file.fileName.toString(), file)
                .asSequence()
                .filter { supportedRomExtension(it) }
                .filter { entryName ->
                    val fileName = Path.of(entryName).fileName.toString()
                    (includeAllRomNames || outerMatches || isPokemonName(fileName)) &&
                        !isExcludedNonMainlineName(fileName)
                }
                .sortedBy { it.lowercase(Locale.ROOT) }
                .map { entryName ->
                    val displayName = "${file.fileName}!$entryName"
                    CorpusInput(displayName, source, archiveEntry = entryName, path = file)
                }
                .toList()
        } catch (failure: Exception) {
            if (outerMatches || includeAllRomNames) {
                listOf(error(file.fileName.toString(), source, readableMessage(failure)))
            } else {
                emptyList()
            }
        }
    }

    private fun supportedOuterExtension(path: Path): Boolean = extension(path) in ROM_EXTENSIONS || extension(path) == "zip"

    private fun supportedRomExtension(name: String): Boolean = name.substringAfterLast('.', "").lowercase(Locale.ROOT) in ROM_EXTENSIONS

    private fun extension(path: Path): String = path.fileName.toString().substringAfterLast('.', "").lowercase(Locale.ROOT)

    private fun normalizedRelative(root: Path, file: Path): String = try {
        root.toAbsolutePath().normalize().relativize(file.toAbsolutePath().normalize()).toString().replace('\\', '/')
    } catch (_: IllegalArgumentException) {
        file.fileName.toString()
    }

    private fun isPokemonName(name: String): Boolean {
        val normalized = name.lowercase(Locale.ROOT)
        return "pokemon" in normalized || "pokémon" in normalized
    }

    private fun isExcludedNonMainlineName(name: String): Boolean {
        val normalized = name.lowercase(Locale.ROOT)
        return EXCLUDED_NON_MAINLINE_TITLES.any { it in normalized }
    }

    private fun error(displayName: String, source: String, message: String, archiveEntry: String? = null) = CorpusInput(
        displayName = displayName,
        source = source,
        archiveEntry = archiveEntry,
        error = message,
    )

    private fun readableMessage(failure: Exception): String =
        "${failure.javaClass.simpleName}: ${failure.message ?: "unreadable input"}"

    private companion object {
        val ROM_EXTENSIONS = setOf("gb", "gbc", "gba")
        val EXCLUDED_NON_MAINLINE_TITLES = setOf(
            "mystery dungeon",
            "pinball",
            "puzzle challenge",
            "trading card game",
        )
    }
}
