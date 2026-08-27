package com.darkaxt.dualdex.storage

internal object StoredSafIndexEligibility {
    fun isEligible(storedTreeUri: String?, readablePersistedTreeUris: Set<String>): Boolean =
        storedTreeUri != null && storedTreeUri in readablePersistedTreeUris
}
