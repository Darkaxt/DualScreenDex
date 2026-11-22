package com.darkaxt.dualdex.setup

import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask

internal enum class SessionIdentityEvidence {
    RETROARCH_CRC,
    BASENAME_DISCOVERY,
}

internal data class VerifiedSessionIdentity(
    val romSha256: String,
    val sourceId: String,
    val evidence: SessionIdentityEvidence = SessionIdentityEvidence.RETROARCH_CRC,
)

internal data class SessionWorkToken(
    val epoch: Long,
    val identity: VerifiedSessionIdentity,
)

internal class SessionActivationAuthority {
    @Volatile private var verifiedToken: SessionWorkToken? = null

    fun isVerified(token: SessionWorkToken): Boolean = verifiedToken == token

    fun markVerified(token: SessionWorkToken) {
        verifiedToken = token
    }
}

internal class SessionActivationCoordinator(
    private val sessions: SessionEpochGate,
    private val activations: GuideActivationGate = GuideActivationGate(),
    private val authority: SessionActivationAuthority = SessionActivationAuthority(),
) {
    fun isVerified(token: SessionWorkToken): Boolean =
        sessions.isCurrent(token) && authority.isVerified(token)

    fun requiresSourceVerification(
        token: SessionWorkToken,
        activeCatalogSha256: String?,
        expectedSha256: String,
    ): Boolean = !isVerified(token) || !activeCatalogSha256.equals(expectedSha256, ignoreCase = true)

    fun isLoading(sourceId: String, token: SessionWorkToken): Boolean =
        activations.isLoading(sourceId, token)

    fun isFailed(sourceId: String, token: SessionWorkToken): Boolean =
        activations.isFailed(sourceId, token)

    fun begin(token: SessionWorkToken, sourceId: String, publish: () -> Unit): Boolean {
        var began = false
        val committed = sessions.commitIfCurrent(token) {
            began = activations.tryBegin(sourceId, token)
            if (began) publish()
        }
        return committed && began
    }

    fun finish(token: SessionWorkToken, sourceId: String, publish: () -> Unit): Boolean {
        var finished = false
        val committed = sessions.commitIfCurrent(token) {
            if (activations.isLoading(sourceId, token)) {
                activations.finishSuccess(sourceId, token)
                authority.markVerified(token)
                publish()
                finished = true
            }
        }
        return committed && finished
    }

    fun fail(token: SessionWorkToken, sourceId: String, publish: () -> Unit): Boolean {
        var failed = false
        val committed = sessions.commitIfCurrent(token) {
            if (activations.isLoading(sourceId, token)) {
                activations.finishFailure(sourceId, token)
                publish()
                failed = true
            }
        }
        return committed && failed
    }
}

internal class SessionEpochGate {
    @Volatile private var ownerThread: Thread? = null
    private val owner = Executors.newSingleThreadExecutor { runnable ->
        Thread(
            {
                ownerThread = Thread.currentThread()
                runnable.run()
            },
            "dualdex-session-owner",
        ).apply { isDaemon = true }
    }
    @Volatile private var state = State()

    fun observe(next: VerifiedSessionIdentity?): SessionWorkToken? = onOwner {
        val current = state
        if (current.closed) return@onOwner null
        val updated = if (current.identity != next) {
            current.copy(epoch = current.epoch + 1, identity = next)
        } else {
            current
        }
        state = updated
        next?.let { SessionWorkToken(updated.epoch, it) }
    }

    fun capture(expected: VerifiedSessionIdentity): SessionWorkToken? {
        val current = state
        if (current.closed || current.identity != expected) return null
        return SessionWorkToken(current.epoch, expected)
    }

    fun isCurrent(token: SessionWorkToken): Boolean = state.isCurrent(token)

    fun commitIfCurrent(token: SessionWorkToken, commit: () -> Unit): Boolean = onOwner {
        if (!state.isCurrent(token)) return@onOwner false
        commit()
        true
    }

    fun commitIfCurrent(expectedEpoch: Long, commit: () -> Unit): Boolean = onOwner {
        val current = state
        if (current.closed || current.identity == null || current.epoch != expectedEpoch) return@onOwner false
        commit()
        true
    }

    fun close() {
        if (state.closed) return
        onOwner {
            val current = state
            if (!current.closed) {
                state = current.copy(
                    epoch = current.epoch + 1,
                    identity = null,
                    closed = true,
                )
            }
        }
    }

    private fun <T> onOwner(operation: () -> T): T {
        if (Thread.currentThread() === ownerThread) return operation()
        val task = FutureTask(Callable(operation))
        owner.execute(task)
        return try {
            task.get()
        } catch (failure: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IllegalStateException("session-owner operation was interrupted", failure)
        } catch (failure: ExecutionException) {
            when (val cause = failure.cause ?: failure) {
                is Error -> throw cause
                is RuntimeException -> throw cause
                else -> throw IllegalStateException("session-owner operation failed", cause)
            }
        }
    }

    private data class State(
        val epoch: Long = 0,
        val identity: VerifiedSessionIdentity? = null,
        val closed: Boolean = false,
    ) {
        fun isCurrent(token: SessionWorkToken): Boolean =
            !closed && token.epoch == epoch && token.identity == identity
    }
}
