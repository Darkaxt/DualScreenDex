package com.darkaxt.dualdex.save

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import com.darkaxt.dualdex.retroarch.RomIndexEntry
import com.darkaxt.dualdex.storage.DocumentTreeAccess

class AndroidSaveDocumentResolver(
    private val resolver: ContentResolver,
) {
    fun discover(
        entry: RomIndexEntry,
        configTreeUri: Uri?,
        romTreeUri: Uri?,
        activeGameBasename: String? = null,
    ): List<SaveDocumentSource> {
        val documents = buildList {
            if (configTreeUri != null) addAll(discoverConfigTree(configTreeUri))
            if (romTreeUri != null && romTreeUri != configTreeUri) addAll(discoverRomTree(romTreeUri))
        }
        return SaveDocumentResolver.matching(entry, documents, activeGameBasename)
    }

    fun refresh(sources: List<SaveDocumentSource>): List<SaveDocumentSource> = sources.mapNotNull { source ->
        val uri = Uri.parse(source.id)
        resolver.query(uri, METADATA_PROJECTION, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            source.copy(
                name = cursor.getString(0),
                size = cursor.getLong(1),
                lastModifiedEpochMs = cursor.getLong(2),
                read = { readerFor(uri) },
            )
        }
    }

    private fun discoverConfigTree(treeUri: Uri): List<SaveDocumentSource> {
        val access = DocumentTreeAccess(resolver, treeUri)
        val rootChildren = access.children(access.root)
        return buildList {
            rootChildren.filter { it.name.equals("saves", ignoreCase = true) && it.mimeType == DocumentsContract.Document.MIME_TYPE_DIR }
                .forEach { saveRoot -> access.filesRecursively(saveRoot).forEach { add(it.toSource()) } }
            rootChildren.filter { it.mimeType != DocumentsContract.Document.MIME_TYPE_DIR }.forEach { add(it.toSource()) }
        }
    }

    private fun discoverRomTree(treeUri: Uri): List<SaveDocumentSource> =
        DocumentTreeAccess(resolver, treeUri).filesRecursively().map { it.toSource() }.toList()

    private fun com.darkaxt.dualdex.storage.TreeDocument.toSource() = SaveDocumentSource(
        id = uri.toString(),
        displayPath = documentId.substringAfter(':', documentId),
        name = name,
        size = size,
        lastModifiedEpochMs = lastModifiedEpochMs,
        read = { readerFor(uri) },
    )

    private fun readerFor(uri: Uri): ByteArray = resolver.openInputStream(uri)?.use { it.readBytes() }
        ?: error("document provider did not open SaveRAM for reading")

    private companion object {
        val METADATA_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
    }
}
