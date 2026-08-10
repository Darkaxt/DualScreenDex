package com.darkaxt.dualdex.setup

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class FileRetroArchConfigStoreTest {
    private val roots = mutableListOf<File>()

    @After
    fun cleanUp() {
        roots.forEach(File::deleteRecursively)
    }

    @Test
    fun `preserves exact config and recovery bytes and reads effective save settings`() {
        val config = File(temporaryRoot(), "RetroArch/retroarch.cfg").apply {
            requireNotNull(parentFile).mkdirs()
            writeBytes(
                (
                    "savefile_directory = \"/storage/emulated/0/RetroArch/saves\"\r\n" +
                        "autosave_interval = \"10\"\r\n" +
                        "sort_savefiles_enable = \"true\"\r\n"
                    ).toByteArray(),
            )
        }
        val store = FileRetroArchConfigStore(config)
        val original = store.readConfig()
        val replacement = byteArrayOf(0, 1, 2, 3, 13, 10)

        assertEquals("/storage/emulated/0/RetroArch/saves", store.readSaveSettings().savefileDirectory)
        assertEquals("VERIFIED", store.readSaveSettings().autosaveStatus)

        store.writeRecovery(original)
        store.writeConfig(replacement)

        assertArrayEquals(replacement, store.readConfig())
        assertArrayEquals(original, store.readRecovery())

        store.deleteRecovery()
        assertNull(store.readRecovery())
    }

    @Test
    fun `locates only a public RetroArch config at the root of mounted storage`() {
        val root = temporaryRoot()
        val publicConfig = File(root, "RetroArch/retroarch.cfg").apply {
            requireNotNull(parentFile).mkdirs()
            writeText("public")
        }
        File(root, "Games/retroarch.cfg").apply {
            requireNotNull(parentFile).mkdirs()
            writeText("unrelated")
        }

        assertEquals(publicConfig.canonicalFile, FileRetroArchConfigStore.findPublic(listOf(root)))
        assertTrue(publicConfig.isFile)
        assertFalse(File(root, "RetroArch/retroarch.cfg.dualdex-recovery").exists())
    }

    private fun temporaryRoot(): File = Files.createTempDirectory("dualdex-config-").toFile().also(roots::add)
}
