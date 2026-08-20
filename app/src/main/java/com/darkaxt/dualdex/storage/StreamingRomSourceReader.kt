package com.darkaxt.dualdex.storage

import com.darkaxt.dualdex.retroarch.RomPlatform
import com.enrpau.dualscreendex.parser.detect.RomHeaderReader
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.io.RomSourceLoader
import com.enrpau.dualscreendex.parser.model.Platform
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.ZipInputStream

internal data class StreamingRomSourceIdentity(
    val displayName: String,
    val archiveEntry: String?,
    val platform: RomPlatform,
    val crc32: String,
    val sha256: String,
)

/** Reads only a bounded header window while streaming hashes across the complete source. */
internal object StreamingRomSourceReader {
    fun read(source: File): StreamingRomSourceIdentity {
        require(source.isFile) { "ROM source is not a readable file: $source" }
        return when (source.extension.lowercase()) {
            in ROM_EXTENSIONS -> source.inputStream().buffered().use { input ->
                readRom(source.name, archiveEntry = null, input)
            }
            "zip" -> readZip(source)
            "7z" -> readSevenZip(source)
            else -> error("supported sources are .gb, .gbc, .gba, .zip, and .7z")
        }
    }

    private fun readSevenZip(source: File): StreamingRomSourceIdentity {
        val inspected = RomSourceLoader.inspect(source.toPath())
        val entryName = inspected.displayName.substringAfter('!', "")
        require(entryName.isNotBlank()) { "archive contains no supported ROM entry" }
        val platform = when (inspected.platform) {
            Platform.GB -> RomPlatform.GB
            Platform.GBC -> RomPlatform.GBC
            Platform.GBA -> RomPlatform.GBA
            Platform.UNKNOWN -> error("ROM header platform was not recognized")
        }
        return StreamingRomSourceIdentity(
            displayName = inspected.displayName,
            archiveEntry = entryName,
            platform = platform,
            crc32 = inspected.crc32,
            sha256 = inspected.sha256,
        )
    }

    private fun readZip(source: File): StreamingRomSourceIdentity {
        var selected: StreamingRomSourceIdentity? = null
        ZipInputStream(BufferedInputStream(source.inputStream())).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val extension = entry.name.substringAfterLast('.', "").lowercase()
                if (!entry.isDirectory && extension in ROM_EXTENSIONS) {
                    require(selected == null) { "archive contains multiple ROM entries" }
                    selected = readRom("${source.name}!${entry.name}", entry.name, zip)
                }
                zip.closeEntry()
            }
        }
        return requireNotNull(selected) { "archive contains no supported ROM entry" }
    }

    private fun readRom(
        displayName: String,
        archiveEntry: String?,
        input: InputStream,
    ): StreamingRomSourceIdentity {
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
        val header = RomHeaderReader.read(RomImage(headerBytes.copyOf(headerLength)))
        val platform = when (header.platform) {
            Platform.GB -> RomPlatform.GB
            Platform.GBC -> RomPlatform.GBC
            Platform.GBA -> RomPlatform.GBA
            Platform.UNKNOWN -> error("ROM header platform was not recognized")
        }
        return StreamingRomSourceIdentity(
            displayName = displayName,
            archiveEntry = archiveEntry,
            platform = platform,
            crc32 = "%08X".format(crc32.value),
            sha256 = sha256.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) },
        )
    }

    private const val HEADER_BYTES = 0x150
    private const val STREAM_BUFFER_BYTES = 64 * 1024
    private val ROM_EXTENSIONS = setOf("gb", "gbc", "gba")
}
