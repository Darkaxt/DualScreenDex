package com.darkaxt.dualdex.storage

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
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

    fun filesRecursively(parent: TreeDocument = root): Sequence<TreeDocument> = sequence {
        val queue = ArrayDeque<TreeDocument>().apply { add(parent) }
        val visited = mutableSetOf<String>()
        while (queue.isNotEmpty()) {
            val parent = queue.removeFirst()
            if (!visited.add(parent.documentId)) continue
            for (child in children(parent)) {
                if (child.mimeType == DocumentsContract.Document.MIME_TYPE_DIR) queue += child else yield(child)
            }
        }
    }

    fun children(parent: TreeDocument): List<TreeDocument> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parent.documentId)
        return resolver.query(uri, PROJECTION, null, null, null)?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0)
                    add(
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
        }.orEmpty()
    }

    fun read(document: TreeDocument): ByteArray = resolver.openInputStream(document.uri)?.use { it.readBytes() }
        ?: error("document provider did not open ${document.name} for reading")

    fun write(document: TreeDocument, bytes: ByteArray) {
        resolver.openFileDescriptor(document.uri, "rwt")?.use { descriptor ->
            FileOutputStream(descriptor.fileDescriptor).use { output ->
                output.write(bytes)
                output.flush()
                descriptor.fileDescriptor.sync()
            }
        } ?: error("document provider did not open ${document.name} for writing")
    }

    fun create(parent: TreeDocument, name: String, mimeType: String = "application/octet-stream"): TreeDocument {
        val uri = requireNotNull(DocumentsContract.createDocument(resolver, parent.uri, mimeType, name)) {
            "document provider did not create $name"
        }
        return readDocument(uri)
    }

    fun delete(document: TreeDocument) {
        check(DocumentsContract.deleteDocument(resolver, document.uri)) {
            "document provider did not delete ${document.name}"
        }
    }

    private fun readDocument(uri: Uri): TreeDocument = resolver.query(uri, PROJECTION, null, null, null)?.use { cursor ->
        require(cursor.moveToFirst()) { "document provider returned no metadata for $uri" }
        TreeDocument(
            uri,
            cursor.getString(0),
            cursor.getString(1),
            cursor.getString(2),
            cursor.getInt(3),
            cursor.getLong(4),
            cursor.getLong(5),
        )
    } ?: error("document provider did not return metadata for $uri")

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
