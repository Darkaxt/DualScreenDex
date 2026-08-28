package com.darkaxt.dualdex.storage

import android.net.Uri
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicLong

enum class SafProviderRetryDisposition {
    Retryable,
    ResetRequired,
}

class SafProviderOperationTimeout(
    val disposition: SafProviderRetryDisposition,
) : IllegalStateException(
    if (disposition == SafProviderRetryDisposition.Retryable) {
        "SAF provider operation timed out; retry is available"
    } else {
        "SAF provider timed out and needs reset or app restart before retry"
    },
)

class SafProviderOperationUnavailable(
    val disposition: SafProviderRetryDisposition = SafProviderRetryDisposition.ResetRequired,
) : IllegalStateException(
    "SAF provider needs reset or app restart after repeated timed-out operations",
)

class SafProviderFailure(message: String) : IllegalStateException(message)

enum class SafProviderOperationKind {
    READ_ONLY,
    MUTATION,
}

object SafProviderResults {
    fun <T> requireValue(value: T?, message: String): T = value ?: throw SafProviderFailure(message)
}

object SafProviderOperations {
    val shared = SafProviderOperationRegistry()
}

class SafProviderOperationRegistry(
    private val maximumAuthorities: Int = DEFAULT_MAXIMUM_AUTHORITIES,
    private val supervisorFactory: () -> SafProviderOperationSupervisor = { SafProviderOperationSupervisor() },
) : AutoCloseable {
    private val lock = Any()
    private val supervisors = mutableMapOf<String, SafProviderOperationSupervisor>()

    init {
        require(maximumAuthorities > 0) { "SAF provider authority limit must be positive" }
    }

    fun forUri(uri: Uri): SafProviderOperationSupervisor = forAuthority(uri.authority.orEmpty())

    fun forAuthority(authority: String): SafProviderOperationSupervisor = synchronized(lock) {
        val key = authority.ifBlank { NO_AUTHORITY }
        supervisors[key] ?: run {
            if (supervisors.size >= maximumAuthorities) throw SafProviderOperationUnavailable()
            supervisorFactory().also { supervisors[key] = it }
        }
    }

    override fun close() {
        val closing = synchronized(lock) {
            supervisors.values.toList().also { supervisors.clear() }
        }
        closing.forEach(SafProviderOperationSupervisor::close)
    }

    private companion object {
        const val DEFAULT_MAXIMUM_AUTHORITIES = 4
        const val NO_AUTHORITY = "<no-authority>"
    }
}

class SafProviderOperationSupervisor(
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    private val monotonicNanos: () -> Long = System::nanoTime,
    private val maximumRetiredExecutors: Int = DEFAULT_MAXIMUM_RETIRED_EXECUTORS,
) : AutoCloseable {
    private val lock = Any()
    private var activeExecutor: ExecutorService? = newExecutor()
    private val retiredExecutors = mutableListOf<ExecutorService>()
    private var strandedExecutor: ExecutorService? = null
    private var mutationRecoveryExecutor: ExecutorService? = null
    private var readProbeInFlight = false
    private var recoveredAfterProbe = false

    init {
        require(timeoutMillis > 0L) { "SAF provider timeout must be positive" }
        require(maximumRetiredExecutors > 0) { "SAF provider retired executor limit must be positive" }
    }

    fun <T> await(
        kind: SafProviderOperationKind = SafProviderOperationKind.READ_ONLY,
        onTimeout: () -> Unit = {},
        operation: () -> T,
    ): T {
        val admission = acquire(kind)
        val future = admission.executor.submit(Callable(operation))
        val deadline = monotonicNanos() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        try {
            val remaining = deadline - monotonicNanos()
            if (remaining <= 0L) throw TimeoutException()
            val result = future.get(remaining, TimeUnit.NANOSECONDS)
            finishProbe(admission, recovered = true)
            return result
        } catch (_: TimeoutException) {
            onTimeout()
            future.cancel(true)
            throw SafProviderOperationTimeout(timeout(admission))
        } catch (failure: ExecutionException) {
            finishProbe(admission, recovered = false)
            throw (failure.cause ?: failure)
        } catch (failure: InterruptedException) {
            finishProbe(admission, recovered = false)
            Thread.currentThread().interrupt()
            throw IllegalStateException("interrupted while waiting for SAF provider", failure)
        }
    }

    override fun close() {
        val executors = synchronized(lock) {
            buildList {
                activeExecutor?.let(::add)
                strandedExecutor?.let(::add)
                mutationRecoveryExecutor?.let(::add)
                addAll(retiredExecutors)
                activeExecutor = null
                strandedExecutor = null
                mutationRecoveryExecutor = null
                retiredExecutors.clear()
            }
        }
        executors.distinct().forEach(ExecutorService::shutdownNow)
    }

    private fun acquire(kind: SafProviderOperationKind): Admission = synchronized(lock) {
        pruneTerminatedExecutors()
        if (kind == SafProviderOperationKind.MUTATION && mutationRecoveryExecutor != null) {
            throw SafProviderOperationUnavailable()
        }
        val executor = activeExecutor ?: throw SafProviderOperationUnavailable()
        if (retiredExecutors.size < maximumRetiredExecutors || recoveredAfterProbe) {
            return@synchronized Admission(executor, kind, isHalfOpenProbe = false)
        }
        if (kind != SafProviderOperationKind.READ_ONLY || readProbeInFlight) throw SafProviderOperationUnavailable()
        readProbeInFlight = true
        Admission(executor, kind, isHalfOpenProbe = true)
    }

    private fun finishProbe(admission: Admission, recovered: Boolean) = synchronized(lock) {
        if (admission.isHalfOpenProbe) {
            readProbeInFlight = false
            if (recovered) recoveredAfterProbe = true
        }
    }

    private fun timeout(admission: Admission): SafProviderRetryDisposition {
        val disposition = synchronized(lock) {
            if (admission.isHalfOpenProbe) readProbeInFlight = false
            if (admission.kind == SafProviderOperationKind.MUTATION) {
                mutationRecoveryExecutor = admission.executor
            }
            if (retiredExecutors.size < maximumRetiredExecutors) {
                if (activeExecutor === admission.executor) activeExecutor = newExecutor()
                retiredExecutors += admission.executor
                if (admission.kind == SafProviderOperationKind.MUTATION) {
                    SafProviderRetryDisposition.ResetRequired
                } else {
                    SafProviderRetryDisposition.Retryable
                }
            } else {
                if (activeExecutor === admission.executor) activeExecutor = null
                strandedExecutor = admission.executor
                recoveredAfterProbe = false
                SafProviderRetryDisposition.ResetRequired
            }
        }
        admission.executor.shutdownNow()
        return disposition
    }

    private fun pruneTerminatedExecutors() {
        retiredExecutors.removeAll(ExecutorService::isTerminated)
        if (strandedExecutor?.isTerminated == true) {
            strandedExecutor = null
            if (activeExecutor == null) activeExecutor = newExecutor()
        }
        if (mutationRecoveryExecutor?.isTerminated == true) mutationRecoveryExecutor = null
    }

    private fun newExecutor(): ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "dualdex-saf-provider").apply { isDaemon = true }
    }

    private data class Admission(
        val executor: ExecutorService,
        val kind: SafProviderOperationKind,
        val isHalfOpenProbe: Boolean,
    )

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 5_000L
        const val DEFAULT_MAXIMUM_RETIRED_EXECUTORS = 2
    }
}

class SafOperationToken internal constructor(
    val generation: Long,
)

class SafOperationGenerations {
    private val current = AtomicLong(0L)

    @Synchronized
    fun begin(): SafOperationToken = SafOperationToken(current.incrementAndGet())

    @Synchronized
    fun isCurrent(token: SafOperationToken): Boolean = current.get() == token.generation

    @Synchronized
    fun commitIfCurrent(token: SafOperationToken, commit: () -> Unit): Boolean {
        if (!isCurrent(token)) return false
        commit()
        return true
    }
}
