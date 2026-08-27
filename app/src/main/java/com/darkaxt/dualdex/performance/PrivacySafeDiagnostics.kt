package com.darkaxt.dualdex.performance

import java.io.IOException

object PrivacySafeDiagnostics {
    fun message(category: String, outcome: String? = null, failure: Throwable? = null): String = buildString {
        append("category=")
        append(category.safeLabel())
        outcome?.let {
            append(" outcome=")
            append(it.safeLabel())
        }
        failure?.let {
            append(" failure=")
            append(it.coarseFailureClass())
        }
    }

    private fun String.safeLabel(): String = takeIf {
        length in 1..MAX_LABEL_LENGTH && all { character -> character in 'A'..'Z' || character == '_' || character in '0'..'9' }
    } ?: "UNKNOWN"

    private fun Throwable.coarseFailureClass(): String = when (this) {
        is OutOfMemoryError -> "RESOURCE_EXHAUSTED"
        is SecurityException -> "ACCESS_DENIED"
        is IOException -> "IO_FAILURE"
        is IllegalArgumentException,
        is IllegalStateException -> "INVALID_STATE"
        else -> "FAILURE"
    }

    private const val MAX_LABEL_LENGTH = 48
}
