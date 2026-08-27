package com.darkaxt.dualdex.setup

internal data class VerifiedSessionIdentity(
    val romSha256: String,
    val sourceId: String,
)

internal data class SessionWorkToken(
    val epoch: Long,
    val identity: VerifiedSessionIdentity,
)

internal class SessionEpochGate {
    private var epoch = 0L
    private var identity: VerifiedSessionIdentity? = null
    private var closed = false

    @Synchronized
    fun observe(next: VerifiedSessionIdentity?): SessionWorkToken? {
        if (closed) return null
        if (identity != next) {
            epoch++
            identity = next
        }
        return next?.let { SessionWorkToken(epoch, it) }
    }

    @Synchronized
    fun capture(expected: VerifiedSessionIdentity): SessionWorkToken? {
        if (closed || identity != expected) return null
        return SessionWorkToken(epoch, expected)
    }

    @Synchronized
    fun isCurrent(token: SessionWorkToken): Boolean =
        !closed && token.epoch == epoch && token.identity == identity

    @Synchronized
    fun commitIfCurrent(expectedEpoch: Long, commit: () -> Unit): Boolean {
        if (closed || identity == null || epoch != expectedEpoch) return false
        commit()
        return true
    }

    @Synchronized
    fun close() {
        if (closed) return
        closed = true
        identity = null
        epoch++
    }
}
