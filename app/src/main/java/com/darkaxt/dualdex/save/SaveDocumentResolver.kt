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

    fun matching(
        entry: RomIndexEntry,
        documents: List<SaveDocumentSource>,
        activeGameBasename: String? = null,
    ): List<SaveDocumentSource> {
        val supported = documents.filter { candidate ->
            candidate.name.substringAfterLast('.', "").lowercase() in extensions &&
                candidate.name.substringBeforeLast('.', candidate.name).isNotBlank()
        }
        val rankedBasenames = listOfNotNull(
            activeGameBasename?.trim()?.takeIf(String::isNotEmpty),
            entry.sourceName
                .substringBefore('!')
                .substringAfterLast('/')
                .substringAfterLast('\\')
                .let { source -> source.substringBeforeLast('.', source) }
                .trim()
                .takeIf(String::isNotEmpty),
            entry.gameBasename.trim().takeIf(String::isNotEmpty),
        ).distinctBy { it.lowercase() }
        val matches = rankedBasenames.firstNotNullOfOrNull { basename ->
            matchingBasename(supported, basename).takeIf(List<SaveDocumentSource>::isNotEmpty)
        }.orEmpty()
        return matches
            .distinctBy { it.id }
            .sortedWith(compareBy({ it.displayPath.lowercase() }, { it.id }))
    }

    private fun matchingBasename(
        documents: List<SaveDocumentSource>,
        basename: String,
    ): List<SaveDocumentSource> {
        val expected = basename.trim().lowercase()
        return documents.filter { candidate ->
            candidate.name.substringBeforeLast('.', candidate.name).trim().lowercase() == expected
        }
    }
}
