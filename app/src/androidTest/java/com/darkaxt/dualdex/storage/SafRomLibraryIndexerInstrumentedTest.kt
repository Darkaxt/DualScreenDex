package com.darkaxt.dualdex.storage

import android.content.ContentResolver
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

class SafRomLibraryIndexerInstrumentedTest {
    @Test
    fun indexesUncachedGbaAndZipThroughTheRealAndroidProviderPath() = withProvider { provider, resolver ->
        for (root in listOf("root", "games")) {
            val tree = DocumentsContract.buildTreeDocumentUri(AUTHORITY, root)
            val result = AndroidRomLibraryIndexer(resolver).index(tree)
            assertTrue(result.warnings.isEmpty())
            assertEquals(2, result.entries.size)
            assertEquals(1, result.entries.map { it.sha256 }.distinct().size)
            assertEquals(1, result.entries.count { it.archiveEntry != null })
            assertTrue(result.entries.all { it.sourceId.startsWith("content://$AUTHORITY/") })
        }
        assertEquals(4, provider.opens.get())
    }

    @Test
    fun repeatedScanReusesUnchangedSourceMetadataWithoutOpeningItAgain() = withProvider { provider, resolver ->
        val tree = DocumentsContract.buildTreeDocumentUri(AUTHORITY, "games")
        val indexer = AndroidRomLibraryIndexer(resolver)
        val first = indexer.index(tree)
        val second = indexer.index(tree, first.entries)
        assertEquals(2, first.entries.size)
        assertEquals(first.entries, second.entries)
        assertEquals(2, provider.opens.get())
    }

    @Test
    fun oversizedDirectoryFailsBeforeAnyRomCallbackRuns() = withProvider { provider, resolver ->
        val tree = DocumentsContract.buildTreeDocumentUri(AUTHORITY, "games")
        var visits = 0
        val result = runCatching {
            DocumentTreeAccess(resolver, tree).visitFilesRecursively(
                operation = StorageTraversalOperation(StorageTraversalQuota(maximumFiles = 1)),
            ) { _, _ -> visits++ }
        }
        assertTrue(result.exceptionOrNull() is StorageTraversalLimitExceeded)
        assertEquals(0, visits)
        assertEquals(0, provider.opens.get())
    }

    private fun withProvider(test: (GeneratedRomProvider, ContentResolver) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val provider = GeneratedRomProvider()
        provider.attachInfo(context, ProviderInfo().apply {
            authority = AUTHORITY
            exported = true
            grantUriPermissions = true
            readPermission = android.Manifest.permission.MANAGE_DOCUMENTS
            writePermission = android.Manifest.permission.MANAGE_DOCUMENTS
        })
        try {
            test(provider, ContentResolver.wrap(provider))
        } finally {
            provider.shutdown()
        }
    }

    private class GeneratedRomProvider : DocumentsProvider() {
        val opens = AtomicInteger()
        private lateinit var folder: File
        private lateinit var files: Map<String, File>

        override fun onCreate(): Boolean {
            folder = Files.createTempDirectory(requireNotNull(context).cacheDir.toPath(), "saf-index-test-").toFile()
            val bytes = ByteArray(0xc0).also { image ->
                byteArrayOf(0x24, 0xff.toByte(), 0xae.toByte(), 0x51, 0x69, 0x9a.toByte(),
                    0xa2.toByte(), 0x21, 0x3d, 0x84.toByte(), 0x82.toByte(), 0x0a).copyInto(image, 4)
                "INDEX TEST".toByteArray().copyInto(image, 0xa0)
                "BPRS".toByteArray().copyInto(image, 0xac)
            }
            val archive = ByteArrayOutputStream().also { output ->
                ZipOutputStream(output).use { zip ->
                    zip.putNextEntry(ZipEntry("Generated.gba"))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }.toByteArray()
            files = mapOf(
                "game.gba" to File(folder, "game.gba").apply { writeBytes(bytes) },
                "game.zip" to File(folder, "game.zip").apply { writeBytes(archive) },
            )
            return true
        }

        override fun queryRoots(projection: Array<out String>?): Cursor = MatrixCursor(
            projection ?: arrayOf(DocumentsContract.Root.COLUMN_ROOT_ID),
        )

        override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor =
            MatrixCursor(projection ?: COLUMNS).apply { add(documentId) }

        override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor =
            MatrixCursor(projection ?: COLUMNS).apply {
                when (parentDocumentId) {
                    "root" -> add("games")
                    "games" -> files.keys.forEach { add(it) }
                    else -> error("Unknown generated directory")
                }
            }

        override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean =
            when (parentDocumentId) {
                "root" -> documentId == "games" || documentId in files
                "games" -> documentId in files
                else -> false
            }

        private fun MatrixCursor.add(id: String) {
            require(id == "root" || id == "games" || id in files)
            val file = files[id]
            val row = newRow()
            row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, id)
            row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, id)
            row.add(DocumentsContract.Document.COLUMN_MIME_TYPE,
                if (file == null) DocumentsContract.Document.MIME_TYPE_DIR else "application/octet-stream")
            row.add(DocumentsContract.Document.COLUMN_FLAGS, 0)
            row.add(DocumentsContract.Document.COLUMN_SIZE, file?.length() ?: 0L)
            row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, 1L)
        }

        override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
            require(mode == "r")
            signal?.throwIfCanceled()
            opens.incrementAndGet()
            return ParcelFileDescriptor.open(files.getValue(documentId), ParcelFileDescriptor.MODE_READ_ONLY)
        }

        override fun shutdown() {
            if (::folder.isInitialized) check(folder.deleteRecursively()) { "Generated provider fixture cleanup failed" }
        }
    }

    private companion object {
        const val AUTHORITY = "com.darkaxt.dualdex.generated-index-test"
        val COLUMNS = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
    }
}
