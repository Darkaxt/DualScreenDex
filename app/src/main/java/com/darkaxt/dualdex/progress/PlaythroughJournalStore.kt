package com.darkaxt.dualdex.progress

import com.darkaxt.dualdex.knowledge.CheckpointStorage
import com.darkaxt.dualdex.knowledge.KnowledgeCheckpointStore
import com.darkaxt.dualdex.knowledge.SaveCheckpointKey
import com.darkaxt.dualdex.knowledge.SaveKnowledgeCheckpoint
import com.darkaxt.dualdex.save.SaveDocumentSource

/**
 * Journal access through the same exact-identity, atomically replaced sidecar
 * used by the knowledge checkpoint. It deliberately does not create a second
 * source of persistence truth.
 */
class PlaythroughJournalStore(private val checkpoints: KnowledgeCheckpointStore) {
    fun readExact(source: SaveDocumentSource, key: SaveCheckpointKey): PlaythroughJournal? =
        checkpoints.readCheckpointExact(source, key)?.journal

    fun write(
        source: SaveDocumentSource,
        checkpoint: SaveKnowledgeCheckpoint,
        journal: PlaythroughJournal,
    ): CheckpointStorage = checkpoints.write(source, checkpoint.copy(journal = journal))
}
