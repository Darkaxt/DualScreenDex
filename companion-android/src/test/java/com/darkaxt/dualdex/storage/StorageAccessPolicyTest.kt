package com.darkaxt.dualdex.storage

import org.junit.Assert.assertEquals
import org.junit.Test

class StorageAccessPolicyTest {
    @Test
    fun `missing or revoked access falls back to SAF`() {
        assertEquals(StorageIndexAction.USE_SAF, StorageAccessPolicy.resolve(granted = false, directIndexReady = false))
        assertEquals(StorageIndexAction.USE_SAF, StorageAccessPolicy.resolve(granted = false, directIndexReady = true))
    }

    @Test
    fun `granted access indexes once and then reuses the direct cache`() {
        assertEquals(StorageIndexAction.INDEX_DIRECT, StorageAccessPolicy.resolve(granted = true, directIndexReady = false))
        assertEquals(StorageIndexAction.USE_DIRECT, StorageAccessPolicy.resolve(granted = true, directIndexReady = true))
    }
}
