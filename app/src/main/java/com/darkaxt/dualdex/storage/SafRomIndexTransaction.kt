package com.darkaxt.dualdex.storage

import com.darkaxt.dualdex.retroarch.RomIndexEntry

sealed interface SafRomIndexCommitResult {
    data object Committed : SafRomIndexCommitResult
    data object Failed : SafRomIndexCommitResult
}

class SafRomIndexTransaction(
    private val write: (List<RomIndexEntry>) -> Unit,
) {
    fun commit(entries: List<RomIndexEntry>): SafRomIndexCommitResult = try {
        write(entries)
        SafRomIndexCommitResult.Committed
    } catch (_: Throwable) {
        SafRomIndexCommitResult.Failed
    }
}
