package com.darkaxt.dualdex.catalog

import com.darkaxt.dualdex.save.SaveCapability
import com.darkaxt.dualdex.save.SaveSnapshot
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.io.StringReader
import java.nio.channels.OverlappingFileLockException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.Locale

data class StoredSaveSnapshot(
    val snapshot: SaveSnapshot,
    val sourceLastModifiedEpochMs: Long,
    val refreshedAtEpochMs: Long,
)

data class SaveSnapshotCorruption(
    val reason: String,
)

interface SaveSnapshotRepository {
    fun write(snapshot: SaveSnapshot, sourceLastModifiedEpochMs: Long, refreshedAtEpochMs: Long)
    fun read(romSha256: String): StoredSaveSnapshot?
}

class SaveSnapshotStore(
    private val catalogDirectory: File,
    private val databaseFactory: CatalogDatabaseFactory,
    private val gson: Gson = Gson(),
    private val onCorruptSnapshot: (SaveSnapshotCorruption) -> Unit = {},
    private val decodeSnapshot: (String) -> SaveSnapshot? = { payload -> gson.fromJson(payload, SaveSnapshot::class.java) },
) : SaveSnapshotRepository {
    private val snapshotDirectory = File(catalogDirectory, SNAPSHOT_DIRECTORY)

    init {
        require(catalogDirectory.exists() || catalogDirectory.mkdirs()) {
            "catalog cache directory could not be created: $catalogDirectory"
        }
        require(catalogDirectory.isDirectory) { "catalog cache path is not a directory: $catalogDirectory" }
        require(snapshotDirectory.exists() || snapshotDirectory.mkdirs()) {
            "recovery snapshot directory could not be created: $snapshotDirectory"
        }
        require(snapshotDirectory.isDirectory) { "recovery snapshot path is not a directory: $snapshotDirectory" }
        migrateLegacySnapshots()
    }

    override fun write(snapshot: SaveSnapshot, sourceLastModifiedEpochMs: Long, refreshedAtEpochMs: Long) {
        SnapshotPayloadPolicy.validateSnapshot(snapshot)
        val payloadJson = gson.toJson(snapshot)
        SnapshotPayloadPolicy.validateEncodedPayload(payloadJson)
        writeRecord(
            SnapshotRecord(
                romSha256 = snapshot.romIdentity.lowercase(),
                saveIdentity = snapshot.saveIdentity,
                saveSchemaId = snapshot.schemaId,
                payloadJson = payloadJson,
                sourceLastModifiedEpochMs = sourceLastModifiedEpochMs,
                refreshedAtEpochMs = refreshedAtEpochMs,
            ),
        )
    }

    override fun read(romSha256: String): StoredSaveSnapshot? {
        requireHash(romSha256)
        val normalizedSha = romSha256.lowercase()
        var file = fileFor(normalizedSha)
        if (!file.isFile) {
            migrateLegacySnapshot(legacyFileFor(normalizedSha), normalizedSha)
            file = fileFor(normalizedSha)
        }
        if (!file.isFile) return null
        val stored = CanonicalDatabaseWriteCoordinator.write(file) {
            readCoordinated(file, normalizedSha)
        }
        if (stored != null || !legacyFileFor(normalizedSha).isFile) return stored
        migrateLegacySnapshot(legacyFileFor(normalizedSha), normalizedSha)
        file = fileFor(normalizedSha)
        if (!file.isFile) return null
        return CanonicalDatabaseWriteCoordinator.write(file) {
            readCoordinated(file, normalizedSha)
        }
    }

    private fun readCoordinated(file: File, normalizedSha: String): StoredSaveSnapshot? =
        databaseFactory.open(file).use { database ->
            SaveSnapshotMigration.prepare(database)
            val record = try {
                readRecord(database)
            } catch (failure: CorruptSnapshotPayloadException) {
                quarantine(database, failure.identity)
                reportCorruption(failure)
                return@use null
            } ?: return@use null
            try {
                decode(normalizedSha, record)
            } catch (failure: CorruptSnapshotPayloadException) {
                quarantine(database, failure.identity)
                reportCorruption(failure)
                null
            }
        }

    private fun readRecord(database: CatalogDatabase): SnapshotRecord? {
        val identity = database.query(
            """
            SELECT rom_sha256, save_identity, save_schema_id,
                   length(CAST(payload_json AS BLOB)) AS payload_bytes,
                   source_last_modified_epoch_ms, refreshed_at_epoch_ms
            FROM save_snapshot WHERE id = 1
            """.trimIndent(),
        ) { row ->
            SnapshotRowIdentity(
                romSha256 = requireNotNull(row.string("rom_sha256")),
                saveIdentity = requireNotNull(row.string("save_identity")),
                saveSchemaId = requireNotNull(row.string("save_schema_id")),
                sourceLastModifiedEpochMs = requireNotNull(row.long("source_last_modified_epoch_ms")),
                refreshedAtEpochMs = requireNotNull(row.long("refreshed_at_epoch_ms")),
                payloadBytes = requireNotNull(row.long("payload_bytes")),
                payloadJson = null,
            )
        }.singleOrNull() ?: return null
        if (identity.payloadBytes !in 1..SnapshotPayloadPolicy.maximumJsonBytes.toLong()) {
            throw CorruptSnapshotPayloadException(
                identity,
                IllegalArgumentException("SaveRAM snapshot JSON byte limit exceeded"),
            )
        }
        val payload = try {
            database.readBlob(
                """
                SELECT CAST(payload_json AS BLOB) AS payload
                FROM save_snapshot
                WHERE id = 1
                  AND rom_sha256 = ?
                  AND save_identity = ?
                  AND save_schema_id = ?
                  AND source_last_modified_epoch_ms = ?
                  AND refreshed_at_epoch_ms = ?
                  AND length(CAST(payload_json AS BLOB)) = ?
                """.trimIndent(),
                listOf(
                    identity.romSha256,
                    identity.saveIdentity,
                    identity.saveSchemaId,
                    identity.sourceLastModifiedEpochMs,
                    identity.refreshedAtEpochMs,
                    identity.payloadBytes,
                ),
                SnapshotPayloadPolicy.maximumJsonBytes,
            )
        } catch (failure: IllegalArgumentException) {
            if (failure.message != DATABASE_BLOB_LIMIT_EXCEEDED) throw failure
            throw CorruptSnapshotPayloadException(identity, failure)
        } ?: throw IOException("SaveRAM snapshot JSON changed during bounded retrieval")
        if (payload.size.toLong() != identity.payloadBytes) {
            throw IOException("SaveRAM snapshot JSON length changed during retrieval")
        }
        val payloadJson = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(payload))
                .toString()
        } catch (failure: java.nio.charset.CharacterCodingException) {
            throw CorruptSnapshotPayloadException(identity, failure)
        }
        return SnapshotRecord(
            romSha256 = identity.romSha256,
            saveIdentity = identity.saveIdentity,
            saveSchemaId = identity.saveSchemaId,
            payloadJson = payloadJson,
            sourceLastModifiedEpochMs = identity.sourceLastModifiedEpochMs,
            refreshedAtEpochMs = identity.refreshedAtEpochMs,
        )
    }

    private fun decode(
        requestedSha: String,
        record: SnapshotRecord,
    ): StoredSaveSnapshot {
        val identity = record.identity()
        return try {
            require(record.romSha256.equals(requestedSha, ignoreCase = true)) {
                "SaveRAM snapshot belongs to another ROM"
            }
            SnapshotPayloadPolicy.validateEncodedPayload(record.payloadJson)
            val snapshot = requireNotNull(
                decodeSnapshot(record.payloadJson),
            ) { "SaveRAM snapshot payload is null" }
            SnapshotPayloadPolicy.validateSnapshot(snapshot)
            require(snapshot.romIdentity.equals(requestedSha, ignoreCase = true)) {
                "SaveRAM snapshot payload belongs to another ROM"
            }
            require(snapshot.saveIdentity == record.saveIdentity) {
                "SaveRAM snapshot identity metadata does not match its payload"
            }
            require(snapshot.schemaId == record.saveSchemaId) {
                "SaveRAM snapshot schema metadata does not match its payload"
            }
            StoredSaveSnapshot(
                snapshot = snapshot,
                sourceLastModifiedEpochMs = record.sourceLastModifiedEpochMs,
                refreshedAtEpochMs = record.refreshedAtEpochMs,
            )
        } catch (failure: Exception) {
            throw CorruptSnapshotPayloadException(identity, failure)
        }
    }

    private fun quarantine(database: CatalogDatabase, identity: SnapshotRowIdentity) {
        val payloadPredicate = if (identity.payloadJson == null) {
            ""
        } else {
            " AND payload_json = ?"
        }
        val arguments = mutableListOf<Any?>(
            identity.romSha256,
            identity.saveIdentity,
            identity.saveSchemaId,
            identity.sourceLastModifiedEpochMs,
            identity.refreshedAtEpochMs,
            identity.payloadBytes,
        ).apply {
            identity.payloadJson?.let(::add)
        }
        database.transaction {
            database.execute(
                """
                DELETE FROM save_snapshot
                WHERE id = 1
                  AND rom_sha256 = ?
                  AND save_identity = ?
                  AND save_schema_id = ?
                  AND source_last_modified_epoch_ms = ?
                  AND refreshed_at_epoch_ms = ?
                  AND length(CAST(payload_json AS BLOB)) = ?$payloadPredicate
                """.trimIndent(),
                arguments,
            )
        }
    }

    private fun reportCorruption(failure: CorruptSnapshotPayloadException) {
        val reason = failure.cause?.javaClass?.simpleName
            ?.take(MAX_DIAGNOSTIC_REASON_LENGTH)
            .orEmpty()
        runCatching {
            onCorruptSnapshot(SaveSnapshotCorruption(reason = reason))
        }
    }

    private fun migrateLegacySnapshots() {
        catalogDirectory.listFiles { file -> file.isFile && LEGACY_CATALOG_FILE.matches(file.name) }.orEmpty()
            .forEach { file -> migrateLegacySnapshot(file, file.nameWithoutExtension.lowercase()) }
    }

    private fun migrateLegacySnapshot(legacyFile: File, romSha256: String) {
        if (!legacyFile.isFile) return
        val destination = fileFor(romSha256)
        CanonicalDatabaseWriteCoordinator.write(destination) {
            when (
                withSnapshotFileLock(destination) {
                    migrateLegacySnapshotLocked(legacyFile, destination, romSha256)
                }
            ) {
                is FileLockResult.Acquired -> Unit
                FileLockResult.Unavailable -> Unit
            }
        }
    }

    private fun migrateLegacySnapshotLocked(legacyFile: File, destination: File, romSha256: String) {
        val initialDestination = when (val probe = probeDestination(destination, romSha256)) {
            is DestinationProbe.Valid -> return
            is DestinationProbe.Invalid -> probe
            DestinationProbe.Unavailable -> return
        }
        val record = readValidLegacyRecord(legacyFile, romSha256) ?: return
        publishMigratedRecord(destination, record.copy(romSha256 = romSha256), initialDestination)
    }

    private fun readValidLegacyRecord(legacyFile: File, romSha256: String): SnapshotRecord? = try {
        databaseFactory.open(legacyFile).use { database ->
            val hasSnapshot = database.query(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'save_snapshot'",
            ) { row -> row.string("name") }.isNotEmpty()
            if (!hasSnapshot) return@use null
            val record = readRecord(database) ?: return@use null
            require(record.romSha256.equals(romSha256, ignoreCase = true)) {
                "SaveRAM snapshot belongs to another ROM"
            }
            decode(romSha256, record)
            record
        }
    } catch (_: Exception) {
        null
    }

    private fun probeDestination(file: File, romSha256: String): DestinationProbe {
        val initialVersion = fileVersion(file) ?: return DestinationProbe.Unavailable
        if (!initialVersion.exists) return DestinationProbe.Invalid(initialVersion)
        return try {
            databaseFactory.open(file).use { database ->
                prepareDestinationProbe(database)
                val record = readRecord(database)
                    ?: return DestinationProbe.Invalid(fileVersion(file) ?: return DestinationProbe.Unavailable)
                val stored = decode(romSha256, record)
                DestinationProbe.Valid(
                    SnapshotVersion(
                        saveIdentity = stored.snapshot.saveIdentity,
                        saveSchemaId = stored.snapshot.schemaId,
                        sourceLastModifiedEpochMs = stored.sourceLastModifiedEpochMs,
                        refreshedAtEpochMs = stored.refreshedAtEpochMs,
                    ),
                )
            }
        } catch (failure: Exception) {
            if (!failure.isConfidentDestinationInvalid()) {
                DestinationProbe.Unavailable
            } else {
                fileVersion(file)?.let(DestinationProbe::Invalid) ?: DestinationProbe.Unavailable
            }
        }
    }

    private fun prepareDestinationProbe(database: CatalogDatabase) {
        val version = database.query("PRAGMA user_version") { row -> row.long("user_version")?.toInt() ?: 0 }
            .singleOrNull() ?: 0
        require(version == 0 || version == SaveSnapshotSchema.version) {
            "unsupported recovery snapshot schema: $version"
        }
        if (version == 0) SaveSnapshotMigration.prepare(database)
    }

    private fun publishMigratedRecord(
        destination: File,
        record: SnapshotRecord,
        initialDestination: DestinationProbe.Invalid,
    ) {
        val temporary = File.createTempFile(".${destination.name}.migration-", ".tmp", snapshotDirectory)
        try {
            writeRecordToFile(temporary, record)
            check(probeDestination(temporary, record.romSha256) is DestinationProbe.Valid) {
                "temporary recovery snapshot database did not reopen"
            }
            val finalDestination = probeDestination(destination, record.romSha256)
            if (finalDestination !is DestinationProbe.Invalid || finalDestination.fileVersion != initialDestination.fileVersion) {
                return
            }
            if (destinationSidecars(destination).any(File::exists)) return
            try {
                Files.move(
                    temporary.toPath(),
                    destination.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    temporary.toPath(),
                    destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
        } finally {
            deleteTemporaryDatabaseFiles(temporary)
        }
    }

    private fun writeRecord(record: SnapshotRecord) {
        requireHash(record.romSha256)
        val file = fileFor(record.romSha256)
        CanonicalDatabaseWriteCoordinator.write(file) {
            when (withSnapshotFileLock(file) { writeRecordToFile(file, record) }) {
                is FileLockResult.Acquired -> Unit
                FileLockResult.Unavailable -> throw IllegalStateException("recovery snapshot database is temporarily unavailable")
            }
        }
    }

    private fun writeRecordToFile(file: File, record: SnapshotRecord) {
        databaseFactory.open(file).use { database ->
            SaveSnapshotMigration.prepare(database)
            database.transaction {
                database.execute(
                    """
                    INSERT OR REPLACE INTO save_snapshot (
                        id, rom_sha256, save_identity, save_schema_id, payload_json,
                        source_last_modified_epoch_ms, refreshed_at_epoch_ms
                    ) VALUES (1, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                    listOf(
                        record.romSha256.lowercase(),
                        record.saveIdentity,
                        record.saveSchemaId,
                        record.payloadJson,
                        record.sourceLastModifiedEpochMs,
                        record.refreshedAtEpochMs,
                    ),
                )
            }
        }
    }

    private fun destinationSidecars(file: File): List<File> =
        listOf("-wal", "-shm", "-journal").map { suffix -> File(file.path + suffix) }

    private fun deleteTemporaryDatabaseFiles(file: File) {
        destinationSidecars(file).forEach { sidecar -> Files.deleteIfExists(sidecar.toPath()) }
        Files.deleteIfExists(file.toPath())
    }

    private fun <T> withSnapshotFileLock(databaseFile: File, operation: () -> T): FileLockResult<T> {
        val lockFile = File(databaseFile.parentFile, "${databaseFile.name}.migration.lock")
        repeat(FILE_LOCK_RETRY_DELAYS_MS.size + 1) { attempt ->
            try {
                RandomAccessFile(lockFile, "rw").channel.use { channel ->
                    val lock = try {
                        channel.tryLock()
                    } catch (_: OverlappingFileLockException) {
                        null
                    }
                    if (lock != null) {
                        lock.use {
                            return FileLockResult.Acquired(operation())
                        }
                    }
                }
            } catch (failure: IOException) {
                if (attempt == FILE_LOCK_RETRY_DELAYS_MS.size) return FileLockResult.Unavailable
            }
            if (attempt < FILE_LOCK_RETRY_DELAYS_MS.size) {
                try {
                    Thread.sleep(FILE_LOCK_RETRY_DELAYS_MS[attempt])
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return FileLockResult.Unavailable
                }
            }
        }
        return FileLockResult.Unavailable
    }

    private fun fileVersion(file: File): FileVersion? = try {
        if (!Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            FileVersion(exists = false, size = 0L, lastModifiedEpochMs = 0L, fileKey = null)
        } else {
            val attributes = Files.readAttributes(
                file.toPath(),
                BasicFileAttributes::class.java,
                LinkOption.NOFOLLOW_LINKS,
            )
            FileVersion(
                exists = true,
                size = attributes.size(),
                lastModifiedEpochMs = attributes.lastModifiedTime().toMillis(),
                fileKey = attributes.fileKey()?.toString(),
            )
        }
    } catch (_: IOException) {
        null
    }

    private fun Exception.isConfidentDestinationInvalid(): Boolean {
        var current: Throwable? = this
        repeat(MAX_FAILURE_CAUSE_DEPTH) {
            val failure = current ?: return false
            if (failure is CorruptSnapshotPayloadException || failure is IllegalArgumentException) return true
            val message = failure.message.orEmpty().lowercase(Locale.ROOT)
            if (
                "not a database" in message ||
                "sqlite_notadb" in message ||
                "database disk image is malformed" in message ||
                "malformed database" in message
            ) {
                return true
            }
            if (
                failure is IOException ||
                "database is locked" in message ||
                "sqlite_busy" in message ||
                "disk i/o" in message ||
                "unable to open" in message ||
                "permission denied" in message ||
                "read-only" in message
            ) {
                return false
            }
            current = failure.cause
        }
        return false
    }

    private fun fileFor(sha256: String) = File(snapshotDirectory, "${sha256.lowercase()}.sqlite")

    private fun legacyFileFor(sha256: String) = File(catalogDirectory, "${sha256.lowercase()}.sqlite")

    private fun requireHash(sha256: String) {
        require(sha256.matches(Regex("[0-9a-fA-F]{64}"))) { "catalog SHA-256 is invalid" }
    }

    private data class SnapshotRecord(
        val romSha256: String,
        val saveIdentity: String,
        val saveSchemaId: String,
        val payloadJson: String,
        val sourceLastModifiedEpochMs: Long,
        val refreshedAtEpochMs: Long,
    ) {
        fun identity() = SnapshotRowIdentity(
            romSha256 = romSha256,
            saveIdentity = saveIdentity,
            saveSchemaId = saveSchemaId,
            sourceLastModifiedEpochMs = sourceLastModifiedEpochMs,
            refreshedAtEpochMs = refreshedAtEpochMs,
            payloadBytes = payloadJson.toByteArray(Charsets.UTF_8).size.toLong(),
            payloadJson = payloadJson,
        )
    }

    private data class SnapshotRowIdentity(
        val romSha256: String,
        val saveIdentity: String,
        val saveSchemaId: String,
        val sourceLastModifiedEpochMs: Long,
        val refreshedAtEpochMs: Long,
        val payloadBytes: Long,
        val payloadJson: String?,
    )

    private data class SnapshotVersion(
        val saveIdentity: String,
        val saveSchemaId: String,
        val sourceLastModifiedEpochMs: Long,
        val refreshedAtEpochMs: Long,
    )

    private data class FileVersion(
        val exists: Boolean,
        val size: Long,
        val lastModifiedEpochMs: Long,
        val fileKey: String?,
    )

    private sealed interface DestinationProbe {
        data class Valid(val snapshotVersion: SnapshotVersion) : DestinationProbe
        data class Invalid(val fileVersion: FileVersion) : DestinationProbe
        data object Unavailable : DestinationProbe
    }

    private sealed interface FileLockResult<out T> {
        data class Acquired<T>(val value: T) : FileLockResult<T>
        data object Unavailable : FileLockResult<Nothing>
    }

    private class CorruptSnapshotPayloadException(
        val identity: SnapshotRowIdentity,
        cause: Exception,
    ) : Exception(cause)

    private companion object {
        const val SNAPSHOT_DIRECTORY = "save-snapshots"
        const val MAX_DIAGNOSTIC_REASON_LENGTH = 64
        const val MAX_FAILURE_CAUSE_DEPTH = 8
        const val DATABASE_BLOB_LIMIT_EXCEEDED = "database blob limit exceeded"
        val FILE_LOCK_RETRY_DELAYS_MS = longArrayOf(10, 25, 50)
        val LEGACY_CATALOG_FILE = Regex("[0-9a-fA-F]{64}\\.sqlite")
    }
}

private object SnapshotPayloadPolicy {
    const val maximumJsonBytes = 4 * 1024 * 1024
    private const val MAXIMUM_JSON_DEPTH = 32
    private const val MAXIMUM_JSON_NODES = 300_000
    private const val MAXIMUM_OBJECT_MEMBERS = 256
    private const val MAXIMUM_GENERIC_ARRAY_ELEMENTS = 4_096
    private const val MAXIMUM_DEX_ENTRIES = 65_536
    private const val MAXIMUM_PARTY_MEMBERS = 6
    private const val MAXIMUM_STORED_INDIVIDUALS = 4_096
    private const val MAXIMUM_BAG_POCKETS = 5
    private const val MAXIMUM_BAG_ENTRIES = 65_536
    private const val MAXIMUM_INDIVIDUAL_VALUES = 6
    private const val MAXIMUM_MOVE_VALUES = 4

    fun validateEncodedPayload(payloadJson: String) {
        require(payloadJson.toByteArray(Charsets.UTF_8).size <= maximumJsonBytes) {
            "SaveRAM snapshot JSON byte limit exceeded"
        }
        try {
            JsonReader(StringReader(payloadJson)).use { reader ->
                reader.isLenient = false
                JsonBudget().readValue(reader, "$", 0)
                require(reader.peek() == JsonToken.END_DOCUMENT) { "SaveRAM snapshot JSON has trailing content" }
            }
        } catch (failure: IOException) {
            throw JsonSyntaxException(failure)
        }
    }

    fun validateSnapshot(snapshot: SaveSnapshot) {
        require(snapshot.seenDexNumbers.size <= MAXIMUM_DEX_ENTRIES) { "SaveRAM seen collection limit exceeded" }
        require(snapshot.caughtDexNumbers.size <= MAXIMUM_DEX_ENTRIES) { "SaveRAM caught collection limit exceeded" }
        require(snapshot.party.size <= MAXIMUM_PARTY_MEMBERS) { "SaveRAM party collection limit exceeded" }
        require(snapshot.storedIndividuals.size <= MAXIMUM_STORED_INDIVIDUALS) {
            "SaveRAM stored-individual collection limit exceeded"
        }
        require(snapshot.capabilities.size <= SaveCapability.entries.size) { "SaveRAM capability collection limit exceeded" }
        require(snapshot.bag.size <= MAXIMUM_BAG_POCKETS) { "SaveRAM bag-pocket collection limit exceeded" }
        require(snapshot.bag.sumOf { it.entries.size } <= MAXIMUM_BAG_ENTRIES) { "SaveRAM bag-entry collection limit exceeded" }
        require(snapshot.eventFlagIds.orEmpty().size <= MAXIMUM_DEX_ENTRIES) { "SaveRAM event-flag collection limit exceeded" }
        snapshot.party.asSequence().plus(snapshot.storedIndividuals).forEach { individual ->
            require(individual.ivs.orEmpty().size <= MAXIMUM_INDIVIDUAL_VALUES) { "SaveRAM IV collection limit exceeded" }
            require(individual.dvs.orEmpty().size <= MAXIMUM_INDIVIDUAL_VALUES) { "SaveRAM DV collection limit exceeded" }
            individual.details?.let { details ->
                require(details.stats.size <= MAXIMUM_INDIVIDUAL_VALUES) { "SaveRAM stat collection limit exceeded" }
                require(details.moveIds.size <= MAXIMUM_MOVE_VALUES) { "SaveRAM move collection limit exceeded" }
                require(details.movePp.size <= MAXIMUM_MOVE_VALUES) { "SaveRAM move-PP collection limit exceeded" }
                require(details.movePpBonuses.size <= MAXIMUM_MOVE_VALUES) { "SaveRAM PP-bonus collection limit exceeded" }
            }
        }
    }

    private class JsonBudget {
        private var nodes = 0
        private var bagEntries = 0

        fun readValue(reader: JsonReader, path: String, depth: Int) {
            require(depth <= MAXIMUM_JSON_DEPTH) { "SaveRAM snapshot JSON depth limit exceeded" }
            require(++nodes <= MAXIMUM_JSON_NODES) { "SaveRAM snapshot JSON node limit exceeded" }
            when (reader.peek()) {
                JsonToken.BEGIN_ARRAY -> readArray(reader, path, depth)
                JsonToken.BEGIN_OBJECT -> readObject(reader, path, depth)
                JsonToken.STRING, JsonToken.NUMBER -> reader.nextString()
                JsonToken.BOOLEAN -> reader.nextBoolean()
                JsonToken.NULL -> reader.nextNull()
                else -> throw IllegalArgumentException("SaveRAM snapshot JSON contains an unexpected token")
            }
        }

        private fun readArray(reader: JsonReader, path: String, depth: Int) {
            reader.beginArray()
            var elements = 0
            val limit = arrayLimit(path)
            while (reader.hasNext()) {
                require(++elements <= limit) { "SaveRAM snapshot semantic collection limit exceeded: $path" }
                if (path == "$.bag[].entries") {
                    require(++bagEntries <= MAXIMUM_BAG_ENTRIES) {
                        "SaveRAM snapshot aggregate bag-entry limit exceeded"
                    }
                }
                readValue(reader, "$path[]", depth + 1)
            }
            reader.endArray()
        }

        private fun readObject(reader: JsonReader, path: String, depth: Int) {
            reader.beginObject()
            var members = 0
            val limit = if (path == "$.capabilities") SaveCapability.entries.size else MAXIMUM_OBJECT_MEMBERS
            while (reader.hasNext()) {
                require(++members <= limit) { "SaveRAM snapshot object member limit exceeded: $path" }
                val name = reader.nextName()
                readValue(reader, "$path.$name", depth + 1)
            }
            reader.endObject()
        }

        private fun arrayLimit(path: String): Int = when (path) {
            "$.seenDexNumbers", "$.caughtDexNumbers", "$.eventFlagIds" -> MAXIMUM_DEX_ENTRIES
            "$.party" -> MAXIMUM_PARTY_MEMBERS
            "$.storedIndividuals" -> MAXIMUM_STORED_INDIVIDUALS
            "$.bag" -> MAXIMUM_BAG_POCKETS
            "$.bag[].entries" -> MAXIMUM_BAG_ENTRIES
            else -> when {
                path.endsWith(".ivs") || path.endsWith(".dvs") || path.endsWith(".stats") ->
                    MAXIMUM_INDIVIDUAL_VALUES
                path.endsWith(".moveIds") || path.endsWith(".movePp") || path.endsWith(".movePpBonuses") ->
                    MAXIMUM_MOVE_VALUES
                else -> MAXIMUM_GENERIC_ARRAY_ELEMENTS
            }
        }
    }
}
