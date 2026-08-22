package com.darkaxt.dualdex.knowledge

import com.enrpau.dualscreendex.companion.model.KnowledgeLedger

data class SaveFileFingerprint(
    val sha256: String,
    val size: Long,
    val lastModifiedEpochMs: Long,
)

data class SaveCheckpointKey(
    val romSha256: String,
    val saveIdentity: String,
    val saveFileSha256: String,
    val saveSize: Long,
    val saveLastModifiedEpochMs: Long,
)

data class SaveKnowledgeCheckpoint(
    val schema: Int = 1,
    val portable: Boolean,
    val key: SaveCheckpointKey,
    val capturedAtEpochMs: Long,
    val ledger: KnowledgeLedger,
)
