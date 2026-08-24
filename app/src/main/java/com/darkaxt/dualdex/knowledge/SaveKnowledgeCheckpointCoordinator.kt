package com.darkaxt.dualdex.knowledge

import com.darkaxt.dualdex.save.SaveMonitorResult
import com.darkaxt.dualdex.save.SaveObservationKind
import com.darkaxt.dualdex.live.RecoveryApplication
import com.darkaxt.dualdex.live.RecoveryProjection
import com.enrpau.dualscreendex.companion.api.SaveRamView

class SaveKnowledgeCheckpointCoordinator(
    private val checkpoints: KnowledgeCheckpointStore,
    private val applyRecovery: (RecoveryProjection) -> RecoveryApplication,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun apply(result: SaveMonitorResult, saveView: SaveRamView): Boolean {
        val snapshot = result.snapshot ?: result.retained?.snapshot ?: return false
        val observation = result.observation ?: return false
        val key = observation.key(snapshot)
        val checkpoint = if (
            observation.kind == SaveObservationKind.INITIAL || observation.kind == SaveObservationKind.SWITCHED
        ) {
            runCatching { checkpoints.readExact(observation.source, key) }.getOrNull()
        } else {
            null
        }
        val application = applyRecovery(
            RecoveryProjection(
                snapshot = snapshot,
                saveRam = saveView,
                observation = observation,
                checkpointLedger = checkpoint,
            ),
        )
        if (observation.kind == SaveObservationKind.CHANGED && application.checkpointLedger != null) {
            runCatching {
                checkpoints.write(
                    observation.source,
                    SaveKnowledgeCheckpoint(
                        portable = observation.source.atomicSiblingTarget != null,
                        key = key,
                        capturedAtEpochMs = clock(),
                        ledger = application.checkpointLedger,
                    ),
                )
            }
        }
        return application.accepted
    }
}
