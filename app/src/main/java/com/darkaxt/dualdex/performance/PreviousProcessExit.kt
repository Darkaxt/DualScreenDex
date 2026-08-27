package com.darkaxt.dualdex.performance

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.SharedPreferences

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
)

fun interface PreviousProcessExitSource {
    fun latest(): PreviousProcessExitSnapshot?
}

interface PreviousProcessExitMarker {
    fun read(): String?
    fun write(value: String)
}

fun interface PreviousProcessExitSink {
    fun append(event: PreviousProcessExitEvent)
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
        val markerValue = "${event.category}:${event.timestampBucket}:${event.memoryBucket}"
        if (runCatching(marker::read).getOrNull() == markerValue) return null
        return runCatching {
            sink.append(event)
            marker.write(markerValue)
            event
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
) : PreviousProcessExitMarker {
    override fun read(): String? = preferences.getString(KEY, null)

    override fun write(value: String) {
        preferences.edit().putString(KEY, value).apply()
    }

    private companion object {
        const val KEY = "previous_process_exit_marker"
    }
}

private const val PREVIOUS_PROCESS_EXIT_SCHEMA_VERSION = 1
