package com.darkaxt.dualdex.save

import com.darkaxt.dualdex.save.gen1.Gen1SaveReader
import com.darkaxt.dualdex.save.gen2.Gen2SaveReader
import com.darkaxt.dualdex.save.gen3.Gen3SaveReader

object SaveParser {
    fun parse(bytes: ByteArray, context: SaveParseContext): SaveParseResult {
        val attempts = when (bytes.size) {
            32 * 1024 -> listOf(Gen2SaveReader.read(bytes, context), Gen1SaveReader.read(bytes, context))
            128 * 1024 -> listOf(Gen3SaveReader.read(bytes, context))
            else -> emptyList()
        }
        return attempts.firstOrNull { it is SaveParseResult.Parsed }
            ?: SaveParseResult.Unsupported(
                attempts.flatMap { (it as? SaveParseResult.Unsupported)?.reasons.orEmpty() }
                    .ifEmpty { listOf("SaveRAM size is not supported") },
            )
    }
}
