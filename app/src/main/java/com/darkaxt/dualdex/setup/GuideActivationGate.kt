package com.darkaxt.dualdex.setup

/**
 * Owns the complete activation lifecycle for one ROM source at a time.
 *
 * A failed source remains blocked until the user explicitly retries it or the
 * ROM index is refreshed. Other sources remain eligible immediately.
 */
internal class GuideActivationGate {
    private var loadingSource: String? = null
    private var failedSource: String? = null

    @Synchronized
    fun tryBegin(sourceId: String): Boolean {
        if (failedSource == sourceId || loadingSource != null) return false
        loadingSource = sourceId
        return true
    }

    @Synchronized
    fun finishSuccess(sourceId: String) {
        if (loadingSource != sourceId) return
        failedSource = null
        loadingSource = null
    }

    @Synchronized
    fun finishFailure(sourceId: String) {
        failedSource = sourceId
        if (loadingSource == sourceId) loadingSource = null
    }

    @Synchronized
    fun retry(sourceId: String): Boolean {
        if (failedSource != sourceId) return false
        failedSource = null
        return true
    }

    @Synchronized
    fun clearFailure() {
        failedSource = null
    }

    @Synchronized
    fun isLoading(sourceId: String): Boolean = loadingSource == sourceId

    @Synchronized
    fun isFailed(sourceId: String): Boolean = failedSource == sourceId
}
