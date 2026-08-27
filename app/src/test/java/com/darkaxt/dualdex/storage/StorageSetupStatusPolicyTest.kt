package com.darkaxt.dualdex.storage

import org.junit.Assert.assertEquals
import org.junit.Test

class StorageSetupStatusPolicyTest {
    @Test
    fun `permission remains granted while direct indexing runs`() {
        assertEquals(
            StorageSetupStatus(storageGrant = "GRANTED", romGrant = "INDEXING"),
            StorageSetupStatusPolicy.indexing(allFilesGranted = true),
        )
    }

    @Test
    fun `direct index failure never rewrites a granted permission`() {
        assertEquals(
            StorageSetupStatus(storageGrant = "GRANTED", romGrant = "FAILED"),
            StorageSetupStatusPolicy.failed(
                allFilesGranted = true,
                retainedDirectIndex = false,
                safIndexGranted = false,
            ),
        )
        assertEquals(
            StorageSetupStatus(storageGrant = "GRANTED", romGrant = "GRANTED"),
            StorageSetupStatusPolicy.failed(
                allFilesGranted = true,
                retainedDirectIndex = true,
                safIndexGranted = false,
            ),
        )
    }

    @Test
    fun `SAF fallback remains available without all files access`() {
        assertEquals(
            StorageSetupStatus(storageGrant = "MISSING", romGrant = "GRANTED"),
            StorageSetupStatusPolicy.available(
                allFilesGranted = false,
                directIndexReady = false,
                safIndexGranted = true,
            ),
        )
    }
}
