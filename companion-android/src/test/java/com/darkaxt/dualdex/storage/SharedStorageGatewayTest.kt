package com.darkaxt.dualdex.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SharedStorageGatewayTest {
    @Test
    fun `does not expose roots while broad access is missing`() {
        val gateway = SharedStorageGateway(accessCheck = { false }, rootProvider = { listOf(File("ignored")) })

        assertFalse(gateway.isGranted())
        assertTrue(gateway.roots().isEmpty())
    }

    @Test
    fun `canonicalizes and deduplicates mounted roots when access is granted`() {
        val root = Files.createTempDirectory("dualdex-storage-root-").toFile()
        try {
            val nested = File(root, "folder/..").apply { mkdirs() }
            val gateway = SharedStorageGateway(
                accessCheck = { true },
                rootProvider = { listOf(root, nested, File(root, "missing")) },
            )

            assertTrue(gateway.isGranted())
            assertEquals(listOf(root.canonicalFile), gateway.roots())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `derives a mounted root from Android app-specific directories`() {
        val appDirectory = File("/storage/1234-5678/Android/data/com.darkaxt.dualdex/files")

        assertEquals(File("/storage/1234-5678"), SharedStorageGateway.mountedRootOf(appDirectory))
    }
}
