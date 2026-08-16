package com.enrpau.dualscreendex.parser.io

import com.enrpau.dualscreendex.parser.detect.RomHeaderReader
import com.enrpau.dualscreendex.parser.model.Platform
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel
import java.io.BufferedInputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.channels.SeekableByteChannel
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.ZipInputStream

data class LoadedRom(val displayName: String, val rom: RomImage)

data class InspectedRomSource(
    val displayName: String,
    val platform: Platform,
    val crc32: String,
    val sha256: String,
)

object RomSourceLoader {
    private val extensions = setOf("gb", "gbc", "gba")

    fun load(path: Path): LoadedRom {
        require(Files.isRegularFile(path)) { "ROM source is not a readable file: $path" }
        if (path.fileName.toString().substringAfterLast('.', "").equals("7z", ignoreCase = true)) {
            return SevenZFile.builder()
                .setFile(path.toFile())
                .setMaxMemoryLimitKiB(SEVEN_Z_MEMORY_LIMIT_KIB)
                .get()
                .use { sevenZip -> loadSevenZip(path.fileName.toString(), sevenZip) }
        }
        return Files.newInputStream(path).buffered().use { input -> load(path.fileName.toString(), input) }
    }

    fun inspect(path: Path): InspectedRomSource {
        require(Files.isRegularFile(path)) { "ROM source is not a readable file: $path" }
        val name = path.fileName.toString()
        require(name.substringAfterLast('.', "").equals("7z", ignoreCase = true)) {
            "streaming inspection is currently required only for .7z sources"
        }
        return SevenZFile.builder()
            .setFile(path.toFile())
            .setMaxMemoryLimitKiB(SEVEN_Z_MEMORY_LIMIT_KIB)
            .get()
            .use { sevenZip -> inspectSevenZip(name, sevenZip) }
    }

    fun load(name: String, input: InputStream): LoadedRom {
        val extension = name.substringAfterLast('.', "").lowercase()
        if (extension in extensions) return LoadedRom(name, RomImage.from(input))
        return when (extension) {
            "zip" -> loadZip(name, input)
            "7z" -> SevenZFile.builder()
                .setSeekableByteChannel(SeekableInMemoryByteChannel(input.readBytes()))
                .setDefaultName(name)
                .setMaxMemoryLimitKiB(SEVEN_Z_MEMORY_LIMIT_KIB)
                .get()
                .use { sevenZip -> loadSevenZip(name, sevenZip) }
            else -> error("supported sources are .gb, .gbc, .gba, .zip, and .7z")
        }
    }

    /** Consumes [source] so raw uploads do not allocate a second full-ROM byte array. */
    fun load(name: String, source: ByteArray): LoadedRom {
        val extension = name.substringAfterLast('.', "").lowercase()
        if (extension in extensions) return LoadedRom(name, RomImage.consume(source))
        return when (extension) {
            "zip" -> loadZip(name, source.inputStream())
            "7z" -> SevenZFile.builder()
                .setSeekableByteChannel(SeekableInMemoryByteChannel(source))
                .setDefaultName(name)
                .setMaxMemoryLimitKiB(SEVEN_Z_MEMORY_LIMIT_KIB)
                .get()
                .use { sevenZip -> loadSevenZip(name, sevenZip) }
            else -> error("supported sources are .gb, .gbc, .gba, .zip, and .7z")
        }
    }

    fun load(name: String, channel: SeekableByteChannel): LoadedRom {
        require(name.substringAfterLast('.', "").equals("7z", ignoreCase = true)) {
            "seekable archive loading is supported only for .7z sources"
        }
        return SevenZFile.builder()
            .setSeekableByteChannel(channel)
            .setDefaultName(name)
            .setMaxMemoryLimitKiB(SEVEN_Z_MEMORY_LIMIT_KIB)
            .get()
            .use { sevenZip -> loadSevenZip(name, sevenZip) }
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

    private fun loadSevenZip(name: String, sevenZip: SevenZFile): LoadedRom {
        var selected: LoadedRom? = null
        while (true) {
            val entry = sevenZip.nextEntry ?: break
            val entryName = entry.name ?: continue
            val extension = entryName.substringAfterLast('.', "").lowercase()
            if (!entry.isDirectory && extension in extensions) {
                require(selected == null) { "archive contains multiple ROM entries" }
                selected = sevenZip.getInputStream(entry).use { member ->
                    LoadedRom("$name!$entryName", RomImage.from(member))
                }
            }
        }
        return requireNotNull(selected) { "archive contains no supported ROM entry" }
    }

    private fun inspectSevenZip(name: String, sevenZip: SevenZFile): InspectedRomSource {
        var selected: InspectedRomSource? = null
        while (true) {
            val entry = sevenZip.nextEntry ?: break
            val entryName = entry.name ?: continue
            val extension = entryName.substringAfterLast('.', "").lowercase()
            if (!entry.isDirectory && extension in extensions) {
                require(selected == null) { "archive contains multiple ROM entries" }
                selected = sevenZip.getInputStream(entry).use { member ->
                    inspectRom("$name!$entryName", member)
                }
            }
        }
        return requireNotNull(selected) { "archive contains no supported ROM entry" }
    }

    private fun inspectRom(displayName: String, input: InputStream): InspectedRomSource {
        val sha256 = MessageDigest.getInstance("SHA-256")
        val crc32 = CRC32()
        val headerBytes = ByteArray(HEADER_BYTES)
        val buffer = ByteArray(STREAM_BUFFER_BYTES)
        var headerLength = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (headerLength < headerBytes.size) {
                val copied = minOf(count, headerBytes.size - headerLength)
                buffer.copyInto(headerBytes, headerLength, 0, copied)
                headerLength += copied
            }
            sha256.update(buffer, 0, count)
            crc32.update(buffer, 0, count)
        }
        val platform = RomHeaderReader.read(RomImage(headerBytes.copyOf(headerLength))).platform
        require(platform != Platform.UNKNOWN) { "ROM header platform was not recognized" }
        return InspectedRomSource(
            displayName = displayName,
            platform = platform,
            crc32 = "%08X".format(crc32.value),
            sha256 = sha256.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) },
        )
    }

    private const val SEVEN_Z_MEMORY_LIMIT_KIB = 64 * 1024
    private const val HEADER_BYTES = 0x150
    private const val STREAM_BUFFER_BYTES = 64 * 1024
}
