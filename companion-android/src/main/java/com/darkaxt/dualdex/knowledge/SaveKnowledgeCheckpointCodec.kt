package com.darkaxt.dualdex.knowledge

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.darkaxt.dualdex.progress.PlaythroughJournal
import com.darkaxt.dualdex.progress.PlaythroughJournalCodec
import com.enrpau.dualscreendex.companion.semantic.PlaythroughKey
import java.io.ByteArrayInputStream
import java.io.InputStreamReader

class SaveKnowledgeCheckpointCodec(
    private val gson: Gson = Gson(),
    private val ledgerCodec: KnowledgeLedgerJsonCodec = KnowledgeLedgerJsonCodec(gson),
    private val journalCodec: PlaythroughJournalCodec = PlaythroughJournalCodec(gson),
) {
    fun encode(checkpoint: SaveKnowledgeCheckpoint): ByteArray {
        val normalized = checkpoint.copy(key = checkpoint.key.normalizedOrNull() ?: error("invalid checkpoint key"))
        require(normalized.schema == SCHEMA) { "unsupported checkpoint schema" }
        require(normalized.capturedAtEpochMs >= 0) { "capture time must be nonnegative" }
        require(normalized.sourceId == null || normalized.sourceId.length <= MAXIMUM_SOURCE_ID_CHARACTERS) {
            "checkpoint source identity limit exceeded"
        }
        require(normalized.snapshotDigestSha256 == null || normalized.snapshotDigestSha256.matches(SHA256)) {
            "checkpoint snapshot digest is invalid"
        }
        require(normalized.snapshotVersionId == null || normalized.snapshotVersionId.matches(SNAPSHOT_VERSION_ID)) {
            "checkpoint snapshot version is invalid"
        }
        val ledgerJson = JsonParser.parseString(ledgerCodec.encode(normalized.ledger).toString(Charsets.UTF_8))
        val journalJson = normalized.journal?.let { journal ->
            require(journal.playthrough == PlaythroughKey(normalized.key.romSha256, normalized.key.saveIdentity)) {
                "journal identity must match checkpoint key"
            }
            JsonParser.parseString(journalCodec.encode(journal).toString(Charsets.UTF_8))
        }
        return gson.toJson(StoredCheckpoint.from(normalized, ledgerJson, journalJson))
            .toByteArray(Charsets.UTF_8)
            .also { bytes ->
                require(bytes.size <= MAXIMUM_ENCODED_BYTES) { "checkpoint byte limit exceeded" }
                CheckpointJsonBudget.validate(bytes)
            }
    }

    fun decodeExact(bytes: ByteArray, expectedKey: SaveCheckpointKey): SaveKnowledgeCheckpoint? {
        val expected = expectedKey.normalizedOrNull() ?: return null
        return decode(bytes)?.takeIf { it.key == expected }
    }

    fun decode(bytes: ByteArray): SaveKnowledgeCheckpoint? {
        if (bytes.size > MAXIMUM_ENCODED_BYTES) return null
        try {
            CheckpointJsonBudget.validate(bytes)
        } catch (_: Exception) {
            return null
        }
        val stored = try {
            gson.fromJson(bytes.toString(Charsets.UTF_8), StoredCheckpoint::class.java)
        } catch (_: Exception) {
            null
        } ?: return null
        if (stored.schema !in SUPPORTED_SCHEMAS || stored.capturedAtEpochMs < 0) return null
        if (stored.sourceId != null && stored.sourceId.length > MAXIMUM_SOURCE_ID_CHARACTERS) return null
        if (stored.snapshotDigestSha256 != null && !stored.snapshotDigestSha256.matches(SHA256)) return null
        if (stored.snapshotVersionId != null && !stored.snapshotVersionId.matches(SNAPSHOT_VERSION_ID)) return null
        val actual = stored.key.toKey().normalizedOrNull() ?: return null
        val ledgerElement = stored.ledger ?: return null
        val ledger = ledgerCodec.decode(gson.toJson(ledgerElement).toByteArray(Charsets.UTF_8)) ?: return null
        val journal = stored.journal?.let { element ->
            journalCodec.decodeExact(
                gson.toJson(element).toByteArray(Charsets.UTF_8),
                PlaythroughKey(actual.romSha256, actual.saveIdentity),
            ) ?: return null
        }
        return SaveKnowledgeCheckpoint(
            schema = SCHEMA,
            portable = stored.portable,
            key = actual,
            capturedAtEpochMs = stored.capturedAtEpochMs,
            ledger = ledger,
            journal = journal,
            sourceId = stored.sourceId,
            snapshotDigestSha256 = stored.snapshotDigestSha256,
            snapshotVersionId = stored.snapshotVersionId,
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
        val journal: JsonElement? = null,
        val sourceId: String? = null,
        val snapshotDigestSha256: String? = null,
        val snapshotVersionId: String? = null,
    ) {
        companion object {
            fun from(
                checkpoint: SaveKnowledgeCheckpoint,
                ledger: JsonElement,
                journal: JsonElement?,
            ) = StoredCheckpoint(
                schema = checkpoint.schema,
                portable = checkpoint.portable,
                key = StoredKey.from(checkpoint.key),
                capturedAtEpochMs = checkpoint.capturedAtEpochMs,
                ledger = ledger,
                journal = journal,
                sourceId = checkpoint.sourceId,
                snapshotDigestSha256 = checkpoint.snapshotDigestSha256,
                snapshotVersionId = checkpoint.snapshotVersionId,
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

    companion object {
        const val MAXIMUM_ENCODED_BYTES = 1024 * 1024
        private const val MAXIMUM_SOURCE_ID_CHARACTERS = 4_096
        private const val SCHEMA = 2
        private val SUPPORTED_SCHEMAS = setOf(1, SCHEMA)
        private val SHA256 = Regex("[0-9a-f]{64}")
        private val SNAPSHOT_VERSION_ID = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    }
}

private object CheckpointJsonBudget {
    private const val MAXIMUM_DEPTH = 32
    private const val MAXIMUM_NODES = 200_000
    private const val MAXIMUM_ARRAY_ELEMENTS = 65_536
    private const val MAXIMUM_OBJECT_MEMBERS = 4_096
    private const val MAXIMUM_TOKEN_CHARACTERS = 16_384

    fun validate(bytes: ByteArray) {
        InputStreamReader(ByteArrayInputStream(bytes), Charsets.UTF_8).use { input ->
            JsonReader(input).use { reader ->
                reader.isLenient = false
                Budget().readValue(reader, 0)
                require(reader.peek() == JsonToken.END_DOCUMENT) { "checkpoint has trailing JSON" }
            }
        }
    }

    private class Budget {
        private var nodes = 0

        fun readValue(reader: JsonReader, depth: Int) {
            require(depth <= MAXIMUM_DEPTH) { "checkpoint JSON depth limit exceeded" }
            require(++nodes <= MAXIMUM_NODES) { "checkpoint JSON node limit exceeded" }
            when (reader.peek()) {
                JsonToken.BEGIN_ARRAY -> readArray(reader, depth)
                JsonToken.BEGIN_OBJECT -> readObject(reader, depth)
                JsonToken.STRING, JsonToken.NUMBER ->
                    require(reader.nextString().length <= MAXIMUM_TOKEN_CHARACTERS) {
                        "checkpoint JSON token limit exceeded"
                    }
                JsonToken.BOOLEAN -> reader.nextBoolean()
                JsonToken.NULL -> reader.nextNull()
                else -> throw IllegalArgumentException("checkpoint JSON contains an unexpected token")
            }
        }

        private fun readArray(reader: JsonReader, depth: Int) {
            reader.beginArray()
            var elements = 0
            while (reader.hasNext()) {
                require(++elements <= MAXIMUM_ARRAY_ELEMENTS) { "checkpoint JSON array limit exceeded" }
                readValue(reader, depth + 1)
            }
            reader.endArray()
        }

        private fun readObject(reader: JsonReader, depth: Int) {
            reader.beginObject()
            var members = 0
            while (reader.hasNext()) {
                require(++members <= MAXIMUM_OBJECT_MEMBERS) { "checkpoint JSON object limit exceeded" }
                require(reader.nextName().length <= MAXIMUM_TOKEN_CHARACTERS) {
                    "checkpoint JSON member-name limit exceeded"
                }
                readValue(reader, depth + 1)
            }
            reader.endObject()
        }
    }
}
