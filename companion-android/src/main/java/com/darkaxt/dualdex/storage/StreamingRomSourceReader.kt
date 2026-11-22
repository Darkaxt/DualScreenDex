package com.darkaxt.dualdex.storage

import com.darkaxt.dualdex.retroarch.RomPlatform
import com.enrpau.dualscreendex.parser.io.RomSourceLoader
import com.enrpau.dualscreendex.parser.model.Platform
import java.io.File

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
        val inspected = RomSourceLoader.inspect(source.toPath())
        val platform = when (inspected.platform) {
            Platform.GB -> RomPlatform.GB
            Platform.GBC -> RomPlatform.GBC
            Platform.GBA -> RomPlatform.GBA
            Platform.UNKNOWN -> error("ROM header platform was not recognized")
        }
        return StreamingRomSourceIdentity(
            displayName = inspected.displayName,
            archiveEntry = inspected.displayName.substringAfter('!', "").ifBlank { null },
            platform = platform,
            crc32 = inspected.crc32,
            sha256 = inspected.sha256,
        )
    }
}
