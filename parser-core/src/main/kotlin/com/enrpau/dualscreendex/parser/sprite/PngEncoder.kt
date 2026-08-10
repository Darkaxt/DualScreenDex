package com.enrpau.dualscreendex.parser.sprite

import com.enrpau.dualscreendex.parser.catalog.RgbaSprite
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream

object PngEncoder {
    fun encode(sprite: RgbaSprite): ByteArray {
        require(sprite.argb.size == sprite.width * sprite.height)
        val raw = ByteArrayOutputStream()
        repeat(sprite.height) { y ->
            raw.write(0)
            repeat(sprite.width) { x ->
                val color = sprite.argb[y * sprite.width + x]
                raw.write(color ushr 16 and 0xFF)
                raw.write(color ushr 8 and 0xFF)
                raw.write(color and 0xFF)
                raw.write(color ushr 24 and 0xFF)
            }
        }
        val compressed = ByteArrayOutputStream().also { output ->
            DeflaterOutputStream(output).use { it.write(raw.toByteArray()) }
        }.toByteArray()
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
        }.toByteArray()
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
