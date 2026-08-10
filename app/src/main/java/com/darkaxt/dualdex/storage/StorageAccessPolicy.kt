package com.darkaxt.dualdex.storage

enum class StorageIndexAction {
    INDEX_DIRECT,
    USE_DIRECT,
    USE_SAF,
}

object StorageAccessPolicy {
    fun resolve(granted: Boolean, directIndexReady: Boolean): StorageIndexAction = when {
        !granted -> StorageIndexAction.USE_SAF
        directIndexReady -> StorageIndexAction.USE_DIRECT
        else -> StorageIndexAction.INDEX_DIRECT
    }
}
