package com.enrpau.dualscreendex.parser.io

import com.enrpau.dualscreendex.parser.detect.RomHeaderReader
import com.enrpau.dualscreendex.parser.model.Platform
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel
import java.io.BufferedInputStream
import java.io.FilterInputStream
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
        return load(path.fileName.toString(), path)
    }

    fun load(name: String, path: Path): LoadedRom {
        require(Files.isRegularFile(path)) { "ROM source is not a readable file: $path" }
        val extension = sourceExtension(name)
        val maximumSourceBytes = if (extension in extensions) {
            maximumRomSourceBytes(extension)
        } else {
            MAX_COMPRESSED_SOURCE_BYTES
        }
        require(Files.size(path) <= maximumSourceBytes) {
            if (extension in extensions) "ROM exceeds 32 MiB extracted limit" else "compressed ROM source exceeds 64 MiB limit"
        }
        if (extension == "7z") {
            return SevenZFile.builder()
                .setFile(path.toFile())
                .setMaxMemoryLimitKiB(SEVEN_Z_MEMORY_LIMIT_KIB)
                .get()
                .use { sevenZip -> loadSevenZip(name, sevenZip) }
        }
        return Files.newInputStream(path).buffered().use { input -> load(name, input) }
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
        val extension = sourceExtension(name)
        if (extension in extensions) return LoadedRom(name, loadRom(extension, input))
        return when (extension) {
            "zip" -> loadZip(name, SourceSizeLimitInputStream(input, MAX_COMPRESSED_SOURCE_BYTES))
            "7z" -> SevenZFile.builder()
                .setSeekableByteChannel(SeekableInMemoryByteChannel(readCompressedSource(input)))
                .setDefaultName(name)
                .setMaxMemoryLimitKiB(SEVEN_Z_MEMORY_LIMIT_KIB)
                .get()
                .use { sevenZip -> loadSevenZip(name, sevenZip) }
            else -> error("supported sources are .gb, .gbc, .gba, .zip, and .7z")
        }
    }

    /** Consumes [source] so raw uploads do not allocate a second full-ROM byte array. */
    fun load(name: String, source: ByteArray): LoadedRom {
        val extension = sourceExtension(name)
        if (extension in extensions) {
            require(source.size.toLong() <= maximumRomSourceBytes(extension)) {
                "ROM exceeds 32 MiB extracted limit"
            }
            val rom = if (source.size <= MAX_ROM_BYTES) {
                RomImage.consume(source)
            } else {
                RomImage.consume(source.copyOf(MAX_ROM_BYTES))
            }
            requireAddressableRom(extension, source.size.toLong(), rom)
            return LoadedRom(name, rom)
        }
        require(source.size.toLong() <= MAX_COMPRESSED_SOURCE_BYTES) { "compressed ROM source exceeds 64 MiB limit" }
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
        require(channel.size() <= MAX_COMPRESSED_SOURCE_BYTES) { "compressed ROM source exceeds 64 MiB limit" }
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
                    require(entry.size < 0 || entry.size <= maximumRomSourceBytes(extension)) {
                        "ROM exceeds 32 MiB extracted limit"
                    }
                    selected = LoadedRom("$name!${entry.name}", loadRom(extension, zip))
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
                require(entry.size <= maximumRomSourceBytes(extension)) { "ROM exceeds 32 MiB extracted limit" }
                selected = sevenZip.getInputStream(entry).use { member ->
                    LoadedRom("$name!$entryName", loadRom(extension, member))
                }
            }
        }
        return requireNotNull(selected) { "archive contains no supported ROM entry" }
    }

    private fun loadRom(extension: String, input: InputStream): RomImage {
        if (extension != GBA_EXTENSION) return RomImage.from(input)
        val accumulator = AddressableGbaAccumulator()
        val buffer = ByteArray(STREAM_BUFFER_BYTES)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            accumulator.write(buffer, count)
        }
        val rom = RomImage.consume(accumulator.take())
        requireAddressableRom(extension, accumulator.sourceBytes, rom)
        return rom
    }

    private fun requireAddressableRom(extension: String, sourceBytes: Long, rom: RomImage) {
        requireAddressableRom(extension, sourceBytes, RomHeaderReader.read(rom).platform)
    }

    private fun requireAddressableRom(extension: String, sourceBytes: Long, platform: Platform) {
        if (sourceBytes <= MAX_ROM_BYTES) return
        require(extension == GBA_EXTENSION && platform == Platform.GBA) {
            "ROM exceeds 32 MiB extracted limit; an unaddressable trailer requires a structurally recognized GBA header"
        }
    }

    private fun maximumRomSourceBytes(extension: String): Long =
        if (extension == GBA_EXTENSION) MAX_GBA_SOURCE_BYTES.toLong() else MAX_ROM_BYTES.toLong()

    private fun inspectSevenZip(name: String, sevenZip: SevenZFile): InspectedRomSource {
        var selected: InspectedRomSource? = null
        while (true) {
            val entry = sevenZip.nextEntry ?: break
            val entryName = entry.name ?: continue
            val extension = entryName.substringAfterLast('.', "").lowercase()
            if (!entry.isDirectory && extension in extensions) {
                require(selected == null) { "archive contains multiple ROM entries" }
                require(entry.size <= maximumRomSourceBytes(extension)) { "ROM exceeds 32 MiB extracted limit" }
                selected = sevenZip.getInputStream(entry).use { member ->
                    inspectRom("$name!$entryName", member)
                }
            }
        }
        return requireNotNull(selected) { "archive contains no supported ROM entry" }
    }

    private fun inspectRom(displayName: String, input: InputStream): InspectedRomSource {
        val extension = sourceExtension(displayName)
        val maximumSourceBytes = maximumRomSourceBytes(extension)
        val sha256 = MessageDigest.getInstance("SHA-256")
        val crc32 = CRC32()
        val headerBytes = ByteArray(HEADER_BYTES)
        val buffer = ByteArray(STREAM_BUFFER_BYTES)
        var headerLength = 0
        var hashedBytes = 0
        var sourceBytes = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            sourceBytes += count
            require(sourceBytes <= maximumSourceBytes) { "ROM exceeds 32 MiB extracted limit" }
            if (headerLength < headerBytes.size) {
                val copied = minOf(count, headerBytes.size - headerLength)
                buffer.copyInto(headerBytes, headerLength, 0, copied)
                headerLength += copied
            }
            val retained = minOf(count, MAX_ROM_BYTES - hashedBytes)
            if (retained > 0) {
                sha256.update(buffer, 0, retained)
                crc32.update(buffer, 0, retained)
                hashedBytes += retained
            }
        }
        val platform = RomHeaderReader.read(RomImage(headerBytes.copyOf(headerLength))).platform
        require(platform != Platform.UNKNOWN) { "ROM header platform was not recognized" }
        requireAddressableRom(extension, sourceBytes, platform)
        return InspectedRomSource(
            displayName = displayName,
            platform = platform,
            crc32 = "%08X".format(crc32.value),
            sha256 = sha256.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) },
        )
    }

    private fun readCompressedSource(input: InputStream): ByteArray {
        val bounded = SourceSizeLimitInputStream(input, MAX_COMPRESSED_SOURCE_BYTES)
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(STREAM_BUFFER_BYTES)
        while (true) {
            val count = bounded.read(buffer)
            if (count < 0) break
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun sourceExtension(name: String): String = name.substringAfterLast('.', "").lowercase()

    private const val SEVEN_Z_MEMORY_LIMIT_KIB = 64 * 1024
    private const val MAX_COMPRESSED_SOURCE_BYTES = 64L * 1024 * 1024
    private const val MAX_ROM_BYTES = RomImage.MAX_SIZE_BYTES
    private const val MAX_GBA_TRAILER_BYTES = 4 * 1024
    private const val MAX_GBA_SOURCE_BYTES = MAX_ROM_BYTES + MAX_GBA_TRAILER_BYTES
    private const val GBA_EXTENSION = "gba"
    private const val HEADER_BYTES = 0x150
    private const val STREAM_BUFFER_BYTES = 64 * 1024

    private class AddressableGbaAccumulator {
        private var bytes = ByteArray(minOf(STREAM_BUFFER_BYTES, MAX_ROM_BYTES))
        private var retainedBytes = 0
        var sourceBytes = 0L
            private set

        fun write(source: ByteArray, count: Int) {
            require(count in 0..source.size)
            sourceBytes += count
            require(sourceBytes <= MAX_GBA_SOURCE_BYTES.toLong()) { "ROM exceeds 32 MiB extracted limit" }
            val retained = minOf(count, MAX_ROM_BYTES - retainedBytes)
            if (retained <= 0) return
            ensureCapacity(retainedBytes + retained)
            source.copyInto(bytes, retainedBytes, 0, retained)
            retainedBytes += retained
        }

        fun take(): ByteArray = if (retainedBytes == bytes.size) bytes else bytes.copyOf(retainedBytes)

        private fun ensureCapacity(required: Int) {
            if (required <= bytes.size) return
            var capacity = bytes.size.coerceAtLeast(1)
            while (capacity < required) capacity = minOf(MAX_ROM_BYTES, capacity * 2)
            bytes = bytes.copyOf(capacity)
        }
    }
}

private class SourceSizeLimitInputStream(
    source: InputStream,
    private val maximumBytes: Long,
) : FilterInputStream(source) {
    private var consumed = 0L

    override fun read(): Int {
        val value = super.read()
        if (value >= 0) record(1)
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val count = super.read(buffer, offset, length)
        if (count > 0) record(count.toLong())
        return count
    }

    private fun record(count: Long) {
        consumed += count
        require(consumed <= maximumBytes) { "compressed ROM source exceeds 64 MiB limit" }
    }
}
