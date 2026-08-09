package com.enrpau.dualscreendex.server

import com.enrpau.dualscreendex.parser.io.RomImage
import java.io.BufferedInputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipInputStream

data class LoadedRom(val displayName: String, val rom: RomImage)

object RomSourceLoader {
    private val extensions = setOf("gb", "gbc", "gba")

    fun load(path: Path): LoadedRom {
        require(Files.isRegularFile(path)) { "ROM source is not a readable file: $path" }
        return Files.newInputStream(path).buffered().use { input -> load(path.fileName.toString(), input) }
    }

    fun load(name: String, input: InputStream): LoadedRom {
        val extension = name.substringAfterLast('.', "").lowercase()
        if (extension in extensions) return LoadedRom(name, RomImage.from(input))
        require(extension == "zip") { "supported sources are .gb, .gbc, .gba, and .zip" }
        return loadZip(name, input)
    }

    private fun loadZip(name: String, input: InputStream): LoadedRom {
        var selected: LoadedRom? = null
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val extension = entry.name.substringAfterLast('.', "").lowercase()
                if (!entry.isDirectory && extension in extensions) {
                    require(selected == null) { "archive contains multiple ROM entries" }
                    selected = LoadedRom("$name!${entry.name}", RomImage.from(zip))
                }
                zip.closeEntry()
            }
        }
        return requireNotNull(selected) { "archive contains no supported ROM entry" }
    }
}
