package com.darkaxt.dualdex.progress

import com.enrpau.dualscreendex.companion.semantic.PlaythroughKey
import com.google.gson.Gson

class PlaythroughJournalCodec(private val gson: Gson = Gson()) {
    fun encode(journal: PlaythroughJournal): ByteArray {
        val normalized = journal.sanitizedAndCompacted()
        require(normalized.schema == PlaythroughJournal.SCHEMA)
        require(validKey(normalized.playthrough))
        return gson.toJson(normalized).toByteArray(Charsets.UTF_8)
    }

    fun decodeExact(bytes: ByteArray, expected: PlaythroughKey): PlaythroughJournal? {
        if (!validKey(expected)) return null
        val decoded = try {
            gson.fromJson(bytes.toString(Charsets.UTF_8), PlaythroughJournal::class.java)
                .sanitizedAndCompacted()
        } catch (_: Exception) {
            null
        } ?: return null
        if (decoded.schema != PlaythroughJournal.SCHEMA || decoded.playthrough != expected) return null
        return decoded
    }

    private fun validKey(key: PlaythroughKey) =
        key.romSha256.matches(SHA256) && key.saveIdentity.matches(SHA256)

    private companion object {
        val SHA256 = Regex("[0-9a-f]{64}")
    }
}

