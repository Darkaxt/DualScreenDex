package com.enrpau.dualscreendex.parser.analysis

import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

class ParserCancellationException : CancellationException("parser work was cancelled")

fun interface ParserCancellationToken {
    fun throwIfCancellationRequested()

    companion object {
        val NONE = ParserCancellationToken {}
    }
}

class ParserCancellationSource {
    private val cancelled = AtomicBoolean()

    val token = ParserCancellationToken {
        if (cancelled.get() || Thread.currentThread().isInterrupted) {
            throw ParserCancellationException()
        }
    }

    val isCancellationRequested: Boolean
        get() = cancelled.get()

    fun cancel() {
        cancelled.set(true)
    }
}
