package com.darkaxt.dualdex.knowledge

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser

class SaveKnowledgeCheckpointCodec(
    private val gson: Gson = Gson(),
    private val ledgerCodec: KnowledgeLedgerJsonCodec = KnowledgeLedgerJsonCodec(gson),
) {
    fun encode(checkpoint: SaveKnowledgeCheckpoint): ByteArray {
        val normalized = checkpoint.copy(key = checkpoint.key.normalizedOrNull() ?: error("invalid checkpoint key"))
        require(normalized.schema == SCHEMA) { "unsupported checkpoint schema" }
        require(normalized.capturedAtEpochMs >= 0) { "capture time must be nonnegative" }
        val ledgerJson = JsonParser.parseString(ledgerCodec.encode(normalized.ledger).toString(Charsets.UTF_8))
        return gson.toJson(StoredCheckpoint.from(normalized, ledgerJson)).toByteArray(Charsets.UTF_8)
    }

    fun decodeExact(bytes: ByteArray, expectedKey: SaveCheckpointKey): SaveKnowledgeCheckpoint? {
        val expected = expectedKey.normalizedOrNull() ?: return null
        val stored = runCatching {
            gson.fromJson(bytes.toString(Charsets.UTF_8), StoredCheckpoint::class.java)
        }.getOrNull() ?: return null
        if (stored.schema != SCHEMA || stored.capturedAtEpochMs < 0) return null
        val actual = stored.key.toKey().normalizedOrNull() ?: return null
        if (actual != expected) return null
        val ledgerElement = stored.ledger ?: return null
        val ledger = ledgerCodec.decode(gson.toJson(ledgerElement).toByteArray(Charsets.UTF_8)) ?: return null
        return SaveKnowledgeCheckpoint(
            schema = stored.schema,
            portable = stored.portable,
            key = actual,
            capturedAtEpochMs = stored.capturedAtEpochMs,
            ledger = ledger,
        )
    }

    private fun SaveCheckpointKey.normalizedOrNull(): SaveCheckpointKey? {
        val rom = romSha256.lowercase()
        val save = saveIdentity.lowercase()
        val file = saveFileSha256.lowercase()
        if (!rom.matches(SHA256) || !save.matches(SHA256) || !file.matches(SHA256)) return null
        if (saveSize < 0 || saveLastModifiedEpochMs < 0) return null
        return copy(romSha256 = rom, saveIdentity = save, saveFileSha256 = file)
    }

    private data class StoredCheckpoint(
        val schema: Int = 0,
        val portable: Boolean = false,
        val key: StoredKey = StoredKey(),
        val capturedAtEpochMs: Long = -1,
        val ledger: JsonElement? = null,
    ) {
        companion object {
            fun from(checkpoint: SaveKnowledgeCheckpoint, ledger: JsonElement) = StoredCheckpoint(
                schema = checkpoint.schema,
                portable = checkpoint.portable,
                key = StoredKey.from(checkpoint.key),
                capturedAtEpochMs = checkpoint.capturedAtEpochMs,
                ledger = ledger,
            )
        }
    }

    private data class StoredKey(
        val romSha256: String = "",
        val saveIdentity: String = "",
        val saveFileSha256: String = "",
        val saveSize: Long = -1,
        val saveLastModifiedEpochMs: Long = -1,
    ) {
        fun toKey() = SaveCheckpointKey(
            romSha256 = romSha256,
            saveIdentity = saveIdentity,
            saveFileSha256 = saveFileSha256,
            saveSize = saveSize,
            saveLastModifiedEpochMs = saveLastModifiedEpochMs,
        )

        companion object {
            fun from(key: SaveCheckpointKey) = StoredKey(
                romSha256 = key.romSha256,
                saveIdentity = key.saveIdentity,
                saveFileSha256 = key.saveFileSha256,
                saveSize = key.saveSize,
                saveLastModifiedEpochMs = key.saveLastModifiedEpochMs,
            )
        }
    }

    private companion object {
        const val SCHEMA = 1
        val SHA256 = Regex("[0-9a-f]{64}")
    }
}
