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
import java.util.zip.ZipException
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

    /** Preflights one ZIP through the core archive policy before exposing bounded ROM members. */
    fun zipRomEntries(name: String, path: Path): List<String> {
        requireZipSource(name, path)
        return Files.newInputStream(path).buffered().use { input ->
            listZipRomEntries(SourceSizeLimitInputStream(input, MAX_COMPRESSED_SOURCE_BYTES))
        }
    }

    /** Revalidates the complete ZIP and materializes only [entryName] after bounded extraction. */
    fun loadZipEntry(
        name: String,
        path: Path,
        entryName: String,
    ): LoadedRom {
        requireZipSource(name, path)
        return Files.newInputStream(path).buffered().use { input ->
            loadSelectedZipRom(
                name,
                entryName,
                SourceSizeLimitInputStream(input, MAX_COMPRESSED_SOURCE_BYTES),
            )
        }
    }

    fun inspect(path: Path): InspectedRomSource {
        require(Files.isRegularFile(path)) { "ROM source is not a readable file: $path" }
        val name = path.fileName.toString()
        val extension = sourceExtension(name)
        val maximumSourceBytes = if (extension in extensions) {
            maximumRomSourceBytes(extension)
        } else {
            MAX_COMPRESSED_SOURCE_BYTES
        }
        require(Files.size(path) <= maximumSourceBytes) {
            if (extension in extensions) {
                "ROM exceeds 32 MiB extracted limit"
            } else {
                "compressed ROM source exceeds 64 MiB limit"
            }
        }
        return when {
            extension in extensions -> Files.newInputStream(path).buffered().use { input ->
                inspectRom(name, input)
            }
            extension == "zip" -> Files.newInputStream(path).buffered().use { input ->
                inspectZip(name, SourceSizeLimitInputStream(input, MAX_COMPRESSED_SOURCE_BYTES))
            }
            extension == "7z" -> SevenZFile.builder()
                .setFile(path.toFile())
                .setMaxMemoryLimitKiB(SEVEN_Z_MEMORY_LIMIT_KIB)
                .get()
                .use { sevenZip -> inspectSevenZip(name, sevenZip) }
            else -> error("supported sources are .gb, .gbc, .gba, .zip, and .7z")
        }
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

    private fun loadZip(name: String, input: InputStream): LoadedRom =
        selectZipRom(name, input) { displayName, extension, member ->
            LoadedRom(displayName, loadRom(extension, member))
        }

    private fun inspectZip(name: String, input: InputStream): InspectedRomSource =
        selectZipRom(name, input) { displayName, _, member ->
            inspectRom(displayName, member)
        }

    private fun <T> selectZipRom(
        name: String,
        input: InputStream,
        readRom: (String, String, InputStream) -> T,
    ): T {
        var selected: T? = null
        visitZipRomEntries(input) { entryName, extension, member ->
            require(selected == null) { "archive contains multiple ROM entries" }
            selected = readRom("$name!$entryName", extension, member)
        }
        return requireNotNull(selected) { "archive contains no supported ROM entry" }
    }

    private fun listZipRomEntries(input: InputStream): List<String> = buildList {
        visitZipRomEntries(input) { entryName, _, _ ->
            require(entryName !in this) { "archive contains duplicate ROM entry $entryName" }
            add(entryName)
        }
    }

    private fun loadSelectedZipRom(
        name: String,
        entryName: String,
        input: InputStream,
    ): LoadedRom {
        var selected: LoadedRom? = null
        visitZipRomEntries(input) { candidate, extension, member ->
            if (candidate == entryName) {
                require(selected == null) { "archive contains duplicate ROM entry $entryName" }
                selected = LoadedRom(
                    "$name!$candidate",
                    loadRom(extension, member),
                )
            }
        }
        return requireNotNull(selected) { "archive entry is missing: $entryName" }
    }

    private fun visitZipRomEntries(
        input: InputStream,
        onRom: (String, String, InputStream) -> Unit,
    ) {
        val budget = ArchiveExtractionBudget()
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                budget.enterEntry()
                val extension = entry.name.substringAfterLast('.', "").lowercase()
                if (!entry.isDirectory && extension in extensions) {
                    val maximumBytes = maximumRomSourceBytes(extension)
                    require(entry.size < 0 || entry.size <= maximumBytes) {
                        "ROM exceeds 32 MiB extracted limit"
                    }
                    val member = budget.member(
                        zip,
                        maximumBytes,
                        "ROM exceeds 32 MiB extracted limit",
                    )
                    onRom(entry.name, extension, member)
                    member.drain()
                } else if (!entry.isDirectory) {
                    require(entry.size < 0 || entry.size <= MAX_NONSELECTED_DRAIN_BYTES) {
                        "archive non-ROM member exceeds 8 MiB drain limit"
                    }
                    budget.member(
                        zip,
                        MAX_NONSELECTED_DRAIN_BYTES,
                        "archive non-ROM member exceeds 8 MiB drain limit",
                    ).drain()
                }
                zip.closeEntry()
            }
        }
    }

    private fun loadSevenZip(name: String, sevenZip: SevenZFile): LoadedRom =
        selectSevenZipRom(name, sevenZip) { displayName, extension, member ->
            LoadedRom(displayName, loadRom(extension, member))
        }

    private fun inspectSevenZip(name: String, sevenZip: SevenZFile): InspectedRomSource =
        selectSevenZipRom(name, sevenZip) { displayName, _, member ->
            inspectRom(displayName, member)
        }

    private fun <T> selectSevenZipRom(
        name: String,
        sevenZip: SevenZFile,
        readRom: (String, String, InputStream) -> T,
    ): T {
        var selected: T? = null
        val budget = ArchiveExtractionBudget()
        while (true) {
            val entry = sevenZip.nextEntry ?: break
            budget.enterEntry()
            val entryName = entry.name ?: continue
            val extension = entryName.substringAfterLast('.', "").lowercase()
            if (!entry.isDirectory && extension in extensions) {
                require(selected == null) { "archive contains multiple ROM entries" }
                val maximumBytes = maximumRomSourceBytes(extension)
                require(entry.size < 0 || entry.size <= maximumBytes) {
                    "ROM exceeds 32 MiB extracted limit"
                }
                selected = sevenZip.getInputStream(entry).use { member ->
                    readRom(
                        "$name!$entryName",
                        extension,
                        budget.member(member, maximumBytes, "ROM exceeds 32 MiB extracted limit"),
                    )
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

    private fun requireZipSource(name: String, path: Path) {
        require(sourceExtension(name) == "zip") { "selected archive member loading requires a .zip source" }
        require(Files.isRegularFile(path)) { "ROM source is not a readable file: $path" }
        require(Files.size(path) <= MAX_COMPRESSED_SOURCE_BYTES) {
            "compressed ROM source exceeds 64 MiB limit"
        }
        val signature = Files.newInputStream(path).use { it.readNBytes(2) }
        if (!signature.contentEquals(byteArrayOf('P'.code.toByte(), 'K'.code.toByte()))) {
            throw ZipException("invalid ZIP header")
        }
    }

    private fun sourceExtension(name: String): String = name.substringAfterLast('.', "").lowercase()

    private const val MAX_ARCHIVE_ENTRIES = 1_024
    private const val MAX_ARCHIVE_EXTRACTED_BYTES = 64L * 1024 * 1024
    private const val MAX_NONSELECTED_DRAIN_BYTES = 8L * 1024 * 1024
    private const val SEVEN_Z_MEMORY_LIMIT_KIB = 64 * 1024
    private const val MAX_COMPRESSED_SOURCE_BYTES = 64L * 1024 * 1024
    private const val MAX_ROM_BYTES = RomImage.MAX_SIZE_BYTES
    private const val MAX_GBA_TRAILER_BYTES = 4 * 1024
    private const val MAX_GBA_SOURCE_BYTES = MAX_ROM_BYTES + MAX_GBA_TRAILER_BYTES
    private const val GBA_EXTENSION = "gba"
    private const val HEADER_BYTES = 0x150
    private const val STREAM_BUFFER_BYTES = 64 * 1024

    private class ArchiveExtractionBudget {
        private var entries = 0
        private var extractedBytes = 0L

        fun enterEntry() {
            entries++
            require(entries <= MAX_ARCHIVE_ENTRIES) {
                "archive entry count exceeds 1024 limit"
            }
        }

        fun member(
            input: InputStream,
            maximumMemberBytes: Long,
            memberFailure: String,
        ): InputStream = object : FilterInputStream(input) {
            private var memberBytes = 0L

            override fun read(): Int = super.read().also { value ->
                if (value >= 0) claim(1)
            }

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                super.read(buffer, offset, length).also { count ->
                    if (count > 0) claim(count.toLong())
                }

            override fun close() = Unit

            private fun claim(bytes: Long) {
                require(memberBytes <= maximumMemberBytes - bytes) {
                    memberFailure
                }
                require(extractedBytes <= MAX_ARCHIVE_EXTRACTED_BYTES - bytes) {
                    "archive extracted bytes exceed 64 MiB limit"
                }
                memberBytes += bytes
                extractedBytes += bytes
            }
        }
    }

    private fun InputStream.drain() {
        val buffer = ByteArray(STREAM_BUFFER_BYTES)
        while (read(buffer) >= 0) {
            // Sequential ZIP members must be consumed through the extraction budget.
        }
    }

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
