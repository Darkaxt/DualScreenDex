package com.darkaxt.dualdex.knowledge

import com.darkaxt.dualdex.save.SaveMonitorResult
import com.darkaxt.dualdex.save.SaveObservation
import com.darkaxt.dualdex.save.SaveObservationKind
import com.darkaxt.dualdex.save.SaveSnapshot
import com.darkaxt.dualdex.web.SaveKnowledgeApplication
import com.enrpau.dualscreendex.companion.api.SaveRamView
import com.enrpau.dualscreendex.companion.model.KnowledgeLedger

class SaveKnowledgeCheckpointCoordinator(
    private val checkpoints: KnowledgeCheckpointStore,
    private val applyRuntime: (SaveObservation, SaveSnapshot, SaveRamView, KnowledgeLedger?) -> SaveKnowledgeApplication,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun apply(result: SaveMonitorResult, saveView: SaveRamView): Boolean {
        val snapshot = result.snapshot ?: return false
        val observation = result.observation ?: return false
        val key = observation.key(snapshot)
        val checkpoint = if (
            observation.kind == SaveObservationKind.INITIAL || observation.kind == SaveObservationKind.SWITCHED
        ) {
            runCatching { checkpoints.readExact(observation.source, key) }.getOrNull()
        } else {
            null
        }
        val application = applyRuntime(observation, snapshot, saveView, checkpoint)
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
