package com.darkaxt.dualdex.live

import com.darkaxt.dualdex.save.SaveObservation
import com.darkaxt.dualdex.save.SaveSnapshot
import com.enrpau.dualscreendex.companion.api.SaveRamView
import com.enrpau.dualscreendex.companion.model.KnowledgeLedger

data class RecoveryProjection(
    val snapshot: SaveSnapshot,
    val saveRam: SaveRamView,
    val observation: SaveObservation? = null,
    val checkpointLedger: KnowledgeLedger? = null,
)

data class RecoveryApplication(
    val accepted: Boolean,
    val checkpointLedger: KnowledgeLedger? = null,
)

class PreparedRecovery internal constructor(
    val application: RecoveryApplication,
    internal val stateRevision: Long,
    internal val projection: RecoveryProjection,
    internal val samePlaythrough: Boolean,
    internal val unchanged: Boolean,
    internal val resetKnowledge: Boolean,
)
