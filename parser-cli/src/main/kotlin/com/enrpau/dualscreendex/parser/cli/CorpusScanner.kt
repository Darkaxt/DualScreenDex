package com.enrpau.dualscreendex.parser.cli

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.io.RomSourceLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.zip.ZipFile

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
            ZipFile(inputPath.toFile()).use { zip ->
                val entry = zip.getEntry(archiveEntry)
                    ?.takeUnless { it.isDirectory }
                    ?: throw IllegalArgumentException("archive entry is missing: $archiveEntry")
                zip.getInputStream(entry).use { RomSourceLoader.load(displayName, it).rom }
            }
        }
    }
}

class CorpusScanner(
    private val includeAllRomNames: Boolean = false,
) {
    fun scan(roots: List<Path>): List<CorpusInput> = roots
        .flatMap { root -> scan(root) }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.source + "!" + (it.archiveEntry ?: "") })

    fun scan(root: Path): List<CorpusInput> {
        if (!Files.exists(root)) {
            return listOf(error(root.fileName?.toString() ?: root.toString(), root.toString(), "root does not exist"))
        }
        val files = Files.walk(root).use { stream ->
            stream.filter(Files::isRegularFile)
                .filter { supportedOuterExtension(it) }
                .sorted(compareBy<Path> { normalizedRelative(root, it).lowercase(Locale.ROOT) })
                .toList()
        }
        return files.flatMap { file -> scanFile(root, file) }
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
            ZipFile(file.toFile()).use { zip ->
                zip.entries().asSequence()
                    .filterNot { it.isDirectory }
                    .filter { supportedRomExtension(it.name) }
                    .filter { includeAllRomNames || outerMatches || isPokemonName(Path.of(it.name).fileName.toString()) }
                    .filterNot { isExcludedNonMainlineName(Path.of(it.name).fileName.toString()) }
                    .sortedBy { it.name.lowercase(Locale.ROOT) }
                    .map { entry ->
                        val displayName = "${file.fileName}!${entry.name}"
                        CorpusInput(displayName, source, archiveEntry = entry.name, path = file)
                    }
                    .toList()
            }
        } catch (failure: Exception) {
            if (outerMatches) listOf(error(file.fileName.toString(), source, readableMessage(failure))) else emptyList()
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
