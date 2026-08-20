package com.darkaxt.dualdex.retroarch

enum class RomPlatform { GB, GBC, GBA }

data class RomIndexEntry(
    val sourceId: String,
    val sourceName: String,
    val archiveEntry: String?,
    val platform: RomPlatform,
    val gameBasename: String,
    val crc32: String,
    val sha256: String,
)

sealed interface SessionResolution {
    data object NoContent : SessionResolution
    data class Resolved(val entry: RomIndexEntry) : SessionResolution
    data class Ambiguous(val entries: List<RomIndexEntry>) : SessionResolution
    data class NotFound(val reason: String) : SessionResolution
}

object RomSessionResolver {
    fun resolve(status: RetroArchStatus, entries: List<RomIndexEntry>): SessionResolution = when (status) {
        RetroArchStatus.Contentless -> SessionResolution.NoContent
        is RetroArchStatus.Malformed -> SessionResolution.NotFound(status.reason)
        is RetroArchStatus.Running -> resolveRunning(status, entries)
    }

    fun verifySha(entry: RomIndexEntry, actualSha256: String): Boolean =
        entry.sha256.matches(Regex("[0-9a-fA-F]{64}")) && entry.sha256.equals(actualSha256, ignoreCase = true)

    private fun resolveRunning(status: RetroArchStatus.Running, entries: List<RomIndexEntry>): SessionResolution {
        val platforms = compatiblePlatforms(status.systemId)
        if (platforms.isEmpty()) return SessionResolution.NotFound("unsupported RetroArch system: ${status.systemId}")
        val compatible = entries.filter { it.platform in platforms }
        val matches = if (status.crc32 != null) {
            compatible.filter { it.crc32.equals(status.crc32, ignoreCase = true) }
        } else {
            val basename = normalizedBasename(status.gameBasename)
            compatible.filter { entry -> basename in entry.normalizedBasenames() }
        }
        return when (matches.size) {
            0 -> SessionResolution.NotFound(
                if (status.crc32 != null) "no granted ROM has CRC32 ${status.crc32}"
                else "no granted ROM matches ${status.gameBasename}",
            )
            1 -> SessionResolution.Resolved(matches.single())
            else -> {
                val sorted = matches.sortedBy { it.sourceId }
                val hashes = sorted.map { it.sha256.lowercase() }.distinct()
                if (hashes.size == 1 && hashes.single().matches(SHA_256)) {
                    SessionResolution.Resolved(sorted.first())
                } else {
                    SessionResolution.Ambiguous(sorted)
                }
            }
        }
    }

    private fun compatiblePlatforms(systemId: String): Set<RomPlatform> {
        val value = systemId.lowercase().replace('_', ' ').replace('-', ' ')
        return when {
            "game boy advance" in value || value == "gba" -> setOf(RomPlatform.GBA)
            "game boy" in value || value == "gb" || value == "gbc" -> setOf(RomPlatform.GB, RomPlatform.GBC)
            else -> emptySet()
        }
    }

    private fun normalizedBasename(value: String): String = value
        .removeKnownContentExtension()
        .lowercase()
        .filter(Char::isLetterOrDigit)

    private fun String.removeKnownContentExtension(): String {
        val extension = substringAfterLast('.', "").lowercase()
        return if (extension in CONTENT_EXTENSIONS) dropLast(extension.length + 1) else this
    }

    private fun RomIndexEntry.normalizedBasenames(): Set<String> = buildSet {
        add(normalizedBasename(gameBasename))
        if (archiveEntry != null) {
            val containerName = sourceName
                .substringBefore('!')
                .substringAfterLast('/')
                .substringAfterLast('\\')
            add(normalizedBasename(containerName))
        }
    }

    private val SHA_256 = Regex("[0-9a-f]{64}")
    private val CONTENT_EXTENSIONS = setOf("gb", "gbc", "gba", "zip", "7z")
}
