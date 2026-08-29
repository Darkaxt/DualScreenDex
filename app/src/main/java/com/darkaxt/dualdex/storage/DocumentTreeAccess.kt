package com.darkaxt.dualdex.storage

import android.content.ContentResolver
import android.net.Uri
import android.os.CancellationSignal
import android.provider.DocumentsContract
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.ArrayDeque

data class TreeDocument(
    val uri: Uri,
    val documentId: String,
    val name: String,
    val mimeType: String,
    val flags: Int,
    val size: Long,
    val lastModifiedEpochMs: Long,
)

data class LocatedTreeDocument(
    val parent: TreeDocument,
    val document: TreeDocument,
)

class DocumentTreeAccess(
    private val resolver: ContentResolver,
    private val treeUri: Uri,
) {
    private val provider by lazy { SafProviderOperations.shared.forUri(treeUri) }

    val root: TreeDocument by lazy {
        val id = DocumentsContract.getTreeDocumentId(treeUri)
        readDocument(DocumentsContract.buildDocumentUriUsingTree(treeUri, id))
    }

    fun findFilesNamed(name: String): List<LocatedTreeDocument> {
        val matches = mutableListOf<LocatedTreeDocument>()
        val queue = ArrayDeque<TreeDocument>().apply { add(root) }
        val visited = mutableSetOf<String>()
        while (queue.isNotEmpty()) {
            val parent = queue.removeFirst()
            if (!visited.add(parent.documentId)) continue
            children(parent).forEach { child ->
                if (child.mimeType == DocumentsContract.Document.MIME_TYPE_DIR) queue += child
                else if (child.name == name) matches += LocatedTreeDocument(parent, child)
            }
        }
        return matches
    }

    fun filesRecursively(parent: TreeDocument = root): Sequence<TreeDocument> {
        val files = mutableListOf<TreeDocument>()
        visitFilesRecursively(parent) { document, operation ->
            operation.budget.retainResult()
            files += document
        }
        return files.asSequence()
    }

    fun visitFilesRecursively(
        parent: TreeDocument = root,
        operation: StorageTraversalOperation = StorageTraversalOperation(),
        visitor: (TreeDocument, StorageTraversalOperation) -> Unit,
    ) {
        val queue = ArrayDeque<TreeDocument>()
        operation.budget.enqueueNode()
        queue.add(parent)
        while (queue.isNotEmpty()) {
            val directory = queue.removeFirst()
            if (!operation.claimDirectory(directory.uri.toString())) continue
            operation.budget.visitDirectory()
            forEachChild(directory) { child ->
                if (child.mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                    operation.budget.enqueueNode()
                    queue.addLast(child)
                } else {
                    operation.budget.visitFile()
                    visitor(child, operation)
                }
            }
        }
    }

    fun children(
        parent: TreeDocument,
        operation: StorageTraversalOperation = StorageTraversalOperation(),
    ): List<TreeDocument> = buildList {
        forEachChild(parent) { child ->
            if (child.mimeType == DocumentsContract.Document.MIME_TYPE_DIR) operation.budget.enqueueNode()
            else operation.budget.visitFile()
            add(child)
        }
    }

    fun read(
        document: TreeDocument,
        maximumBytes: Int = ConfigDocumentReadPolicy.MAXIMUM_BYTES,
    ): ByteArray = providerOperation { cancellation ->
        SafProviderResults.requireValue(
            resolver.openFileDescriptor(document.uri, "r", cancellation),
            "SAF provider did not open a document for reading",
        ).use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).use { input ->
                BoundedStorageReader.read(input, maximumBytes, document.size.takeIf { it >= 0 })
            }
        }
    }

    fun write(document: TreeDocument, bytes: ByteArray) {
        providerOperation(SafProviderOperationKind.MUTATION) { cancellation ->
            SafProviderResults.requireValue(
                resolver.openFileDescriptor(document.uri, "rwt", cancellation),
                "SAF provider did not open a document for writing",
            ).use { descriptor ->
                FileOutputStream(descriptor.fileDescriptor).use { output ->
                    output.write(bytes)
                    output.flush()
                    descriptor.fileDescriptor.sync()
                }
            }
        }
    }

    fun create(parent: TreeDocument, name: String, mimeType: String = "application/octet-stream"): TreeDocument {
        val uri = providerOperation(SafProviderOperationKind.MUTATION) {
            SafProviderResults.requireValue(
                DocumentsContract.createDocument(resolver, parent.uri, mimeType, name),
                "SAF provider did not create a document",
            )
        }
        return readDocument(uri)
    }

    fun delete(document: TreeDocument) {
        providerOperation(SafProviderOperationKind.MUTATION) {
            check(DocumentsContract.deleteDocument(resolver, document.uri)) {
                "SAF provider did not delete a document"
            }
        }
    }

    private fun forEachChild(parent: TreeDocument, visitor: (TreeDocument) -> Unit) {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parent.documentId)
        providerOperation { cancellation ->
            SafProviderResults.requireValue(
                resolver.query(uri, PROJECTION, null, null, null, cancellation),
                "SAF provider did not return child documents",
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0)
                    visitor(
                        TreeDocument(
                            DocumentsContract.buildDocumentUriUsingTree(treeUri, id),
                            id,
                            cursor.getString(1),
                            cursor.getString(2),
                            cursor.getInt(3),
                            cursor.getLong(4),
                            cursor.getLong(5),
                        ),
                    )
                }
            }
        }
    }

    private fun readDocument(uri: Uri): TreeDocument = providerOperation { cancellation ->
        SafProviderResults.requireValue(
            resolver.query(uri, PROJECTION, null, null, null, cancellation),
            "SAF provider did not return document metadata",
        ).use { cursor ->
            require(cursor.moveToFirst()) { "SAF provider returned no document metadata" }
            TreeDocument(
                uri,
                cursor.getString(0),
                cursor.getString(1),
                cursor.getString(2),
                cursor.getInt(3),
                cursor.getLong(4),
                cursor.getLong(5),
            )
        }
    }

    private fun <T> providerOperation(
        kind: SafProviderOperationKind = SafProviderOperationKind.READ_ONLY,
        operation: (CancellationSignal) -> T,
    ): T {
        val cancellation = CancellationSignal()
        return provider.await(
            kind = kind,
            onTimeout = cancellation::cancel,
        ) {
            operation(cancellation)
        }
    }

    private companion object {
        val PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
    }
}
