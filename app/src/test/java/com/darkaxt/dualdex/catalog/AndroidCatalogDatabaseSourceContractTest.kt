package com.darkaxt.dualdex.catalog

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidCatalogDatabaseSourceContractTest {
    @Test
    fun androidTransactionPublishesSuccessAndEndInsideCancellationFence() {
        val adapter = File(
            "src/main/java/com/darkaxt/dualdex/catalog/" +
                "AndroidCatalogDatabase.kt",
        ).readText()
        val publication = adapter
            .substringAfter("cancellation.publish {")
            .substringBefore("\n            }\n            result")

        assertTrue(publication.contains("database.setTransactionSuccessful()"))
        assertTrue(publication.contains("database.endTransaction()"))
    }

    @Test
    fun androidAdapterStreamsBoundedBlobsOutsideCursorWindows() {
        val adapter = File("src/main/java/com/darkaxt/dualdex/catalog/AndroidCatalogDatabase.kt").readText()
        val catalogReader = File(
            "../catalog-store/src/main/kotlin/com/darkaxt/dualdex/catalog/CatalogReader.kt",
        ).readText()
        val snapshotStore = File(
            "../catalog-store/src/main/kotlin/com/darkaxt/dualdex/catalog/SaveSnapshotStore.kt",
        ).readText()

        assertTrue(adapter.contains("simpleQueryForBlobFileDescriptor"))
        assertTrue(adapter.contains("ParcelFileDescriptor.AutoCloseInputStream"))
        assertTrue(adapter.contains("readBoundedBytes"))
        assertFalse(adapter.contains("cursor::getBlob"))
        assertFalse(catalogReader.contains("length(payload) AS payload_length, payload"))
        assertFalse(snapshotStore.contains("payload_bytes, payload_json"))
    }
}
