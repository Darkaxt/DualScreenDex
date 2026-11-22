package com.darkaxt.dualdex.storage

data class StorageSetupStatus(
    val storageGrant: String,
    val romGrant: String,
)

object StorageSetupStatusPolicy {
    fun available(
        allFilesGranted: Boolean,
        directIndexReady: Boolean,
        safIndexGranted: Boolean,
    ): StorageSetupStatus = when {
        !allFilesGranted -> StorageSetupStatus(
            storageGrant = "MISSING",
            romGrant = if (safIndexGranted) "GRANTED" else "MISSING",
        )
        directIndexReady || safIndexGranted -> StorageSetupStatus("GRANTED", "GRANTED")
        else -> indexing(allFilesGranted = true)
    }

    fun indexing(allFilesGranted: Boolean): StorageSetupStatus = StorageSetupStatus(
        storageGrant = if (allFilesGranted) "GRANTED" else "MISSING",
        romGrant = if (allFilesGranted) "INDEXING" else "MISSING",
    )

    fun failed(
        allFilesGranted: Boolean,
        retainedDirectIndex: Boolean,
        safIndexGranted: Boolean,
    ): StorageSetupStatus = when {
        !allFilesGranted -> available(
            allFilesGranted = false,
            directIndexReady = false,
            safIndexGranted = safIndexGranted,
        )
        retainedDirectIndex || safIndexGranted -> StorageSetupStatus("GRANTED", "GRANTED")
        else -> StorageSetupStatus("GRANTED", "FAILED")
    }
}
