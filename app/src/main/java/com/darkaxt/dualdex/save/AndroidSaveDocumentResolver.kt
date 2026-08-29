package com.darkaxt.dualdex.save

import android.content.ContentResolver
import android.net.Uri
import android.os.CancellationSignal
import android.provider.DocumentsContract
import com.darkaxt.dualdex.retroarch.RomIndexEntry
import com.darkaxt.dualdex.storage.DocumentTreeAccess
import com.darkaxt.dualdex.storage.BoundedStorageReader
import com.darkaxt.dualdex.storage.SafBoundedRead
import com.darkaxt.dualdex.storage.SafProviderOperations
import com.darkaxt.dualdex.storage.SafProviderResults
import com.darkaxt.dualdex.storage.StorageTraversalOperation
import com.darkaxt.dualdex.storage.TreeDocument
import java.io.FileInputStream

class AndroidSaveDocumentResolver(
    private val resolver: ContentResolver,
) {
    fun discover(
        entry: RomIndexEntry,
        configTreeUri: Uri?,
        romTreeUri: Uri?,
        activeGameBasename: String? = null,
    ): List<SaveDocumentSource> {
        val traversal = StorageTraversalOperation()
        val documents = mutableListOf<SaveDocumentSource>()
        if (configTreeUri != null) discoverConfigTree(configTreeUri, traversal, documents)
        if (romTreeUri != null && romTreeUri != configTreeUri) discoverRomTree(romTreeUri, traversal, documents)
        return SaveDocumentResolver.matching(entry, documents, activeGameBasename)
    }

    fun refresh(sources: List<SaveDocumentSource>): List<SaveDocumentSource> = sources.mapNotNull { source ->
        val uri = Uri.parse(source.id)
        providerOperation(uri) { cancellation ->
            SafProviderResults.requireValue(
                resolver.query(uri, METADATA_PROJECTION, null, null, null, cancellation),
                "SAF provider did not return SaveRAM metadata",
            ).use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                source.copy(
                    name = cursor.getString(0),
                    size = cursor.getLong(1),
                    lastModifiedEpochMs = cursor.getLong(2),
                    open = { openStream(uri) },
                )
            }
        }
    }

    private fun discoverConfigTree(
        treeUri: Uri,
        traversal: StorageTraversalOperation,
        documents: MutableList<SaveDocumentSource>,
    ) {
        val access = DocumentTreeAccess(resolver, treeUri)
        val rootChildren = access.children(access.root, traversal)
        rootChildren
            .filter { it.name.equals("saves", ignoreCase = true) && it.mimeType == DocumentsContract.Document.MIME_TYPE_DIR }
            .forEach { saveRoot ->
                access.visitFilesRecursively(saveRoot, traversal) { document, operation ->
                    retainSaveCandidate(document, operation, documents)
                }
            }
        rootChildren
            .filter { it.mimeType != DocumentsContract.Document.MIME_TYPE_DIR }
            .forEach { document -> retainSaveCandidate(document, traversal, documents) }
    }

    private fun discoverRomTree(
        treeUri: Uri,
        traversal: StorageTraversalOperation,
        documents: MutableList<SaveDocumentSource>,
    ) {
        DocumentTreeAccess(resolver, treeUri).visitFilesRecursively(operation = traversal) { document, operation ->
            retainSaveCandidate(document, operation, documents)
        }
    }

    private fun retainSaveCandidate(
        document: TreeDocument,
        traversal: StorageTraversalOperation,
        documents: MutableList<SaveDocumentSource>,
    ) {
        if (document.name.substringAfterLast('.', "").lowercase() !in SAVE_EXTENSIONS) return
        traversal.budget.retainResult()
        documents += document.toSource()
    }

    private fun com.darkaxt.dualdex.storage.TreeDocument.toSource() = SaveDocumentSource(
        id = uri.toString(),
        displayPath = documentId.substringAfter(':', documentId),
        name = name,
        size = size,
        lastModifiedEpochMs = lastModifiedEpochMs,
        open = { openStream(uri) },
    )

    private fun openStream(uri: Uri): java.io.InputStream {
        val cancellation = CancellationSignal()
        return SafBoundedRead.read(
            supervisor = SafProviderOperations.shared.forUri(uri),
            maximumBytes = MAX_SUPPORTED_SAVE_BYTES,
            onTimeout = cancellation::cancel,
        ) {
            SafProviderResults.requireValue(
                resolver.openFileDescriptor(uri, "r", cancellation),
                "SAF provider did not open SaveRAM for reading",
            ).use { descriptor ->
                FileInputStream(descriptor.fileDescriptor).use { input ->
                    BoundedStorageReader.read(
                        input = input,
                        maximumBytes = MAX_SUPPORTED_SAVE_BYTES,
                    )
                }
            }
        }.inputStream()
    }

    private fun <T> providerOperation(uri: Uri, operation: (CancellationSignal) -> T): T {
        val cancellation = CancellationSignal()
        return SafProviderOperations.shared.forUri(uri).await(
            onTimeout = cancellation::cancel,
        ) {
            operation(cancellation)
        }
    }

    private companion object {
        val SAVE_EXTENSIONS = setOf("srm", "sav")
        val METADATA_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
    }
}
