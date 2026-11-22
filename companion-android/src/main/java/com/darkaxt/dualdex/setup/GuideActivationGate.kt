package com.darkaxt.dualdex.setup

/**
 * Owns the complete activation lifecycle for one ROM source at a time.
 *
 * A failed source remains blocked until the user explicitly retries it or the
 * ROM index is refreshed. Other sources remain eligible immediately.
 */
internal class GuideActivationGate {
    private var loadingSource: String? = null
    private var loadingToken: SessionWorkToken? = null
    private var failedSource: String? = null
    private var failedToken: SessionWorkToken? = null

    @Synchronized
    fun tryBegin(sourceId: String): Boolean {
        if (failedSource == sourceId || loadingSource != null) return false
        loadingSource = sourceId
        loadingToken = null
        return true
    }

    @Synchronized
    fun tryBegin(sourceId: String, token: SessionWorkToken): Boolean {
        if (failedSource == sourceId && failedToken == token) return false
        if (loadingSource != null && loadingToken == token) return false
        loadingSource = sourceId
        loadingToken = token
        return true
    }

    @Synchronized
    fun finishSuccess(sourceId: String) {
        if (loadingSource != sourceId) return
        clearSuccess()
    }

    @Synchronized
    fun finishSuccess(sourceId: String, token: SessionWorkToken) {
        if (loadingSource != sourceId || loadingToken != token) return
        clearSuccess()
    }

    @Synchronized
    fun finishFailure(sourceId: String) {
        failedSource = sourceId
        failedToken = null
        if (loadingSource == sourceId) clearLoading()
    }

    @Synchronized
    fun finishFailure(sourceId: String, token: SessionWorkToken) {
        if (loadingSource != sourceId || loadingToken != token) return
        failedSource = sourceId
        failedToken = token
        clearLoading()
    }

    @Synchronized
    fun cancel(sourceId: String) {
        if (loadingSource == sourceId) clearLoading()
    }

    @Synchronized
    fun cancel(sourceId: String, token: SessionWorkToken) {
        if (loadingSource == sourceId && loadingToken == token) clearLoading()
    }

    @Synchronized
    fun retry(sourceId: String): Boolean {
        if (failedSource != sourceId) return false
        failedSource = null
        failedToken = null
        return true
    }

    @Synchronized
    fun clearFailure() {
        failedSource = null
        failedToken = null
    }

    @Synchronized
    fun isLoading(sourceId: String): Boolean = loadingSource == sourceId

    @Synchronized
    fun isLoading(sourceId: String, token: SessionWorkToken): Boolean =
        loadingSource == sourceId && loadingToken == token

    @Synchronized
    fun isFailed(sourceId: String): Boolean = failedSource == sourceId

    @Synchronized
    fun isFailed(sourceId: String, token: SessionWorkToken): Boolean =
        failedSource == sourceId && failedToken == token

    private fun clearSuccess() {
        failedSource = null
        failedToken = null
        clearLoading()
    }

    private fun clearLoading() {
        loadingSource = null
        loadingToken = null
    }
}
