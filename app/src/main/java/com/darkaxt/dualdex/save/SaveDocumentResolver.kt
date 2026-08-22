package com.darkaxt.dualdex.save

import com.darkaxt.dualdex.retroarch.RomIndexEntry

interface AtomicSiblingTarget {
    fun read(name: String): ByteArray?
    fun replace(name: String, bytes: ByteArray)
}

data class SaveDocumentSource(
    val id: String,
    val displayPath: String,
    val name: String,
    val size: Long,
    val lastModifiedEpochMs: Long,
    val read: () -> ByteArray,
    val atomicSiblingTarget: AtomicSiblingTarget? = null,
)

object SaveDocumentResolver {
    private val extensions = setOf("srm", "sav")

    fun matching(entry: RomIndexEntry, documents: List<SaveDocumentSource>): List<SaveDocumentSource> {
        val expected = entry.gameBasename.trim().lowercase()
        return documents.filter { candidate ->
            candidate.name.substringAfterLast('.', "").lowercase() in extensions &&
                candidate.name.substringBeforeLast('.', candidate.name).trim().lowercase() == expected
        }.distinctBy { it.id }.sortedWith(compareBy({ it.displayPath.lowercase() }, { it.id }))
    }
}
