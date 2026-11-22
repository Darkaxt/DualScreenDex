package com.darkaxt.dualdex.storage

import java.io.File
import java.io.InputStream
import java.net.URI

class RomSourceInput(
    private val contentOpener: (String) -> InputStream?,
) {
    fun open(sourceId: String): InputStream {
        val uri = URI(sourceId)
        return when (uri.scheme?.lowercase()) {
            "file" -> File(uri).inputStream()
            "content" -> contentOpener(sourceId)
                ?: error("document provider did not open ROM source for reading")
            else -> error("unsupported ROM source URI: $sourceId")
        }
    }
}
