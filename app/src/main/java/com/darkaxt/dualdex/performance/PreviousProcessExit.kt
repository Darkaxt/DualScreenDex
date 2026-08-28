package com.darkaxt.dualdex.performance

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.SharedPreferences
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

enum class PreviousProcessExitCategory {
    CRASH,
    ANR,
    LOW_MEMORY,
    SYSTEM,
    USER,
    OTHER,
}

data class PreviousProcessExitSnapshot(
    val category: PreviousProcessExitCategory,
    val timestampEpochMillis: Long,
    val pssKilobytes: Long,
    val rssKilobytes: Long,
    val description: String? = null,
    val trace: String? = null,
)

data class PreviousProcessExitEvent(
    val schemaVersion: Int = PREVIOUS_PROCESS_EXIT_SCHEMA_VERSION,
    val category: PreviousProcessExitCategory,
    val timestampBucket: Long,
    val memoryBucket: String,
    val dedupeId: String = "",
)

fun interface PreviousProcessExitSource {
    fun latest(): PreviousProcessExitSnapshot?
}

data class PreviousProcessExitPending(
    val sourceMarker: String,
    val id: String,
)

class PreviousProcessExitPendingStore(
    private val file: File,
    private val publish: (File, ByteArray) -> Boolean = ::publishAtomically,
) {
    @Synchronized
    fun read(): PreviousProcessExitPending? = runCatching {
        if (!file.isFile) return@runCatching null
        val fields = file.readText(Charsets.UTF_8).split('\n')
        require(fields.size == 4 && fields[0] == FORMAT_VERSION && fields[3].isEmpty()) {
            "pending previous-process-exit record is malformed"
        }
        require(fields[1].isNotBlank() && fields[2].isNotBlank()) {
            "pending previous-process-exit record is incomplete"
        }
        PreviousProcessExitPending(fields[1], fields[2])
    }.getOrNull()

    @Synchronized
    fun write(value: PreviousProcessExitPending): Boolean = runCatching {
        publish(
            file,
            listOf(FORMAT_VERSION, value.sourceMarker, value.id)
                .joinToString("\n", postfix = "\n")
                .toByteArray(Charsets.UTF_8),
        )
    }.getOrDefault(false)

    @Synchronized
    fun clear(): Boolean = !file.exists() || file.delete()

    private companion object {
        const val FORMAT_VERSION = "dualdex-previous-exit-pending-v1"

        fun publishAtomically(target: File, text: ByteArray): Boolean {
            val parent = target.parentFile ?: return false
            if (!parent.isDirectory && !parent.mkdirs()) return false
            val pending = File(parent, ".${target.name}.${UUID.randomUUID()}.tmp")
            return try {
                FileOutputStream(pending).use { output ->
                    output.write(text)
                    output.flush()
                    output.fd.sync()
                }
                try {
                    Files.move(
                        pending.toPath(),
                        target.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(pending.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
                true
            } catch (_: Throwable) {
                false
            } finally {
                if (pending.exists()) pending.delete()
            }
        }
    }
}

interface PreviousProcessExitMarker {
    fun read(): String?
    fun readPending(): PreviousProcessExitPending?
    fun writePending(value: PreviousProcessExitPending): Boolean
    fun write(value: String): Boolean
}

fun interface PreviousProcessExitSink {
    fun append(event: PreviousProcessExitEvent): Boolean
}

class PreviousProcessExitRecorder(
    private val source: PreviousProcessExitSource,
    private val marker: PreviousProcessExitMarker,
    private val sink: PreviousProcessExitSink,
) {
    fun recordLatest(): PreviousProcessExitEvent? {
        val snapshot = runCatching(source::latest).getOrNull() ?: return null
        val event = PreviousProcessExitEvent(
            category = snapshot.category,
            timestampBucket = snapshot.timestampEpochMillis.coerceAtLeast(0L) / TIMESTAMP_BUCKET_MILLIS,
            memoryBucket = memoryBucket(maxOf(snapshot.pssKilobytes, snapshot.rssKilobytes)),
        )
        val markerValue = "${snapshot.timestampEpochMillis.coerceAtLeast(0L)}:${event.category}:${event.memoryBucket}"
        if (runCatching(marker::read).getOrNull() == markerValue) return null
        return runCatching {
            val pending = marker.readPending()
                ?.takeIf { it.sourceMarker == markerValue }
                ?: PreviousProcessExitPending(markerValue, UUID.randomUUID().toString())
                    .also { value -> if (!marker.writePending(value)) return@runCatching null }
            val identifiedEvent = event.copy(dedupeId = pending.id)
            if (!sink.append(identifiedEvent) || !marker.write(markerValue)) null else identifiedEvent
        }.getOrNull()
    }

    private fun memoryBucket(valueKilobytes: Long): String = when (valueKilobytes.coerceAtLeast(0L)) {
        in 0 until 64L * 1_024 -> "BELOW_64_MIB"
        in 64L * 1_024 until 128L * 1_024 -> "64_TO_127_MIB"
        in 128L * 1_024 until 256L * 1_024 -> "128_TO_255_MIB"
        else -> "256_MIB_OR_MORE"
    }

    private companion object {
        const val TIMESTAMP_BUCKET_MILLIS = 6L * 60 * 60 * 1_000
    }
}

class AndroidPreviousProcessExitSource(context: Context) : PreviousProcessExitSource {
    private val activityManager = context.getSystemService(ActivityManager::class.java)
    private val packageName = context.packageName

    override fun latest(): PreviousProcessExitSnapshot? = activityManager
        .getHistoricalProcessExitReasons(packageName, 0, 1)
        .firstOrNull()
        ?.let { info ->
            PreviousProcessExitSnapshot(
                category = info.reason.toExitCategory(),
                timestampEpochMillis = info.timestamp,
                pssKilobytes = info.pss,
                rssKilobytes = info.rss,
            )
        }

    private fun Int.toExitCategory(): PreviousProcessExitCategory = when (this) {
        ApplicationExitInfo.REASON_CRASH,
        ApplicationExitInfo.REASON_CRASH_NATIVE -> PreviousProcessExitCategory.CRASH
        ApplicationExitInfo.REASON_ANR -> PreviousProcessExitCategory.ANR
        ApplicationExitInfo.REASON_LOW_MEMORY,
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> PreviousProcessExitCategory.LOW_MEMORY
        ApplicationExitInfo.REASON_USER_REQUESTED,
        ApplicationExitInfo.REASON_USER_STOPPED -> PreviousProcessExitCategory.USER
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE,
        ApplicationExitInfo.REASON_DEPENDENCY_DIED,
        ApplicationExitInfo.REASON_PERMISSION_CHANGE,
        ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE,
        ApplicationExitInfo.REASON_PACKAGE_UPDATED -> PreviousProcessExitCategory.SYSTEM
        else -> PreviousProcessExitCategory.OTHER
    }
}

class SharedPreferencesPreviousProcessExitMarker(
    private val preferences: SharedPreferences,
    private val pendingStore: PreviousProcessExitPendingStore,
) : PreviousProcessExitMarker {
    override fun read(): String? = preferences.getString(KEY, null)

    override fun readPending(): PreviousProcessExitPending? = pendingStore.read()

    override fun writePending(value: PreviousProcessExitPending): Boolean = pendingStore.write(value)

    override fun write(value: String): Boolean = preferences.edit()
        .putString(KEY, value)
        .commit()
        .also { committed -> if (committed) pendingStore.clear() }

    private companion object {
        const val KEY = "previous_process_exit_marker"
    }
}

private const val PREVIOUS_PROCESS_EXIT_SCHEMA_VERSION = 1
