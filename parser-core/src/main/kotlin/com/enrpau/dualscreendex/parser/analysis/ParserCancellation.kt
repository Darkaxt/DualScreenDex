package com.enrpau.dualscreendex.parser.analysis

import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class ParserCancellationException : CancellationException("parser work was cancelled")

fun interface ParserCancellationToken {
    fun throwIfCancellationRequested()

    fun <T> publish(block: () -> T): T {
        throwIfCancellationRequested()
        return block()
    }

    companion object {
        val NONE = ParserCancellationToken {}
    }
}

class ParserCancellationSource {
    private val cancelled = AtomicBoolean()
    private val publicationFence = ReentrantReadWriteLock()

    val token = object : ParserCancellationToken {
        override fun throwIfCancellationRequested() {
            if (cancelled.get() || Thread.currentThread().isInterrupted) {
                throw ParserCancellationException()
            }
        }

        override fun <T> publish(block: () -> T): T = publicationFence.read {
            throwIfCancellationRequested()
            block()
        }
    }

    val isCancellationRequested: Boolean
        get() = cancelled.get()

    fun cancel() {
        publicationFence.write {
            cancelled.set(true)
        }
    }
}
