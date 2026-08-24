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
