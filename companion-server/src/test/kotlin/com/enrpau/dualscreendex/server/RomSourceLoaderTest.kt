package com.enrpau.dualscreendex.server

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class RomSourceLoaderTest {
    @Test
    fun streamsSingleRomFromZipWithoutExtraction() {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("notes.txt"))
            zip.write("ignore".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("game.gba"))
            zip.write(byteArrayOf(1, 2, 3, 4))
            zip.closeEntry()
        }

        val loaded = RomSourceLoader.load("sample.zip", ByteArrayInputStream(output.toByteArray()))

        assertEquals("sample.zip!game.gba", loaded.displayName)
        assertEquals(4, loaded.rom.size)
    }
}
