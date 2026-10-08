package com.enrpau.dualscreendex.parser.sprite

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.catalog.RgbaSprite
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream

object PngEncoder {
    fun encode(
        sprite: RgbaSprite,
        cancellation: ParserCancellationToken = ParserCancellationToken.NONE,
    ): ByteArray {
        require(sprite.argb.size == sprite.width * sprite.height)
        cancellation.throwIfCancellationRequested()
        // Fill the filtered scanlines into one array: ByteArrayOutputStream.write(Int) is synchronized
        // and was called four times per pixel for hundreds of full-size map renders.
        val stride = sprite.width * 4 + 1
        val raw = ByteArray(Math.multiplyExact(sprite.height, stride))
        repeat(sprite.height) { y ->
            cancellation.throwIfCancellationRequested()
            var cursor = y * stride + 1
            repeat(sprite.width) { x ->
                val color = sprite.argb[y * sprite.width + x]
                raw[cursor++] = (color ushr 16).toByte()
                raw[cursor++] = (color ushr 8).toByte()
                raw[cursor++] = color.toByte()
                raw[cursor++] = (color ushr 24).toByte()
            }
        }
        cancellation.throwIfCancellationRequested()
        val compressed = ByteArrayOutputStream().also { output ->
            DeflaterOutputStream(output).use { it.write(raw) }
        }.toByteArray()
        cancellation.throwIfCancellationRequested()
        return ByteArrayOutputStream().also { png ->
            png.write(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))
            val header = ByteArrayOutputStream().also {
                writeInt(it, sprite.width)
                writeInt(it, sprite.height)
                it.write(byteArrayOf(8, 6, 0, 0, 0))
            }.toByteArray()
            writeChunk(png, "IHDR", header)
            writeChunk(png, "IDAT", compressed)
            writeChunk(png, "IEND", byteArrayOf())
        }.toByteArray().also {
            cancellation.throwIfCancellationRequested()
        }
    }

    private fun writeChunk(output: ByteArrayOutputStream, type: String, data: ByteArray) {
        writeInt(output, data.size)
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        output.write(typeBytes)
        output.write(data)
        val crc = CRC32()
        crc.update(typeBytes)
        crc.update(data)
        writeInt(output, crc.value.toInt())
    }

    private fun writeInt(output: ByteArrayOutputStream, value: Int) {
        output.write(value ushr 24 and 0xFF)
        output.write(value ushr 16 and 0xFF)
        output.write(value ushr 8 and 0xFF)
        output.write(value and 0xFF)
    }
}
