package com.darkaxt.dualdex.knowledge

import com.enrpau.dualscreendex.companion.model.Effectiveness
import com.enrpau.dualscreendex.companion.model.KnowledgeLedger
import com.enrpau.dualscreendex.companion.model.MatchupKey
import com.enrpau.dualscreendex.companion.model.MoveObservation
import com.enrpau.dualscreendex.companion.model.OwnedPokemon
import com.google.gson.Gson
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

interface KnowledgeRepository {
    fun read(romIdentity: String, saveIdentity: String): KnowledgeLedger?
    fun write(romIdentity: String, saveIdentity: String, ledger: KnowledgeLedger)
}

class FileKnowledgeRepository(
    private val root: File,
    private val gson: Gson = Gson(),
) : KnowledgeRepository {
    override fun read(romIdentity: String, saveIdentity: String): KnowledgeLedger? {
        val identity = normalize(romIdentity, "ROM")
        val save = normalize(saveIdentity, "save")
        val document = root.resolve("$identity.$save.json")
        if (!document.isFile) return null
        val stored = runCatching { gson.fromJson(document.readText(), StoredLedger::class.java) }.getOrNull()
            ?: return null
        if (
            stored.schema != SCHEMA ||
            !stored.romIdentity.equals(identity, ignoreCase = true) ||
            !stored.saveIdentity.equals(save, ignoreCase = true)
        ) return null
        return stored.toLedger()
    }

    override fun write(romIdentity: String, saveIdentity: String, ledger: KnowledgeLedger) {
        val identity = normalize(romIdentity, "ROM")
        val save = normalize(saveIdentity, "save")
        check(root.isDirectory || root.mkdirs()) { "knowledge directory could not be created" }
        val destination = root.resolve("$identity.$save.json")
        val temporary = root.resolve("$identity.$save.tmp")
        temporary.writeText(gson.toJson(StoredLedger.from(identity, save, ledger)))
        runCatching {
            Files.move(
                temporary.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        }.getOrElse {
            Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun normalize(identity: String, kind: String): String {
        val normalized = identity.lowercase()
        require(normalized.matches(Regex("[0-9a-f]{64}"))) { "$kind identity must be a SHA-256 hash" }
        return normalized
    }

    private data class StoredLedger(
        val schema: Int = 0,
        val romIdentity: String = "",
        val saveIdentity: String = "",
        val seenSpecies: List<Int> = emptyList(),
        val caughtSpecies: List<Int> = emptyList(),
        val owned: List<OwnedPokemon> = emptyList(),
        val teamSpecies: List<Int> = emptyList(),
        val currentAreaBaseId: Int? = null,
        val visitedAreaBaseIds: List<Int> = emptyList(),
        val seenSpeciesByArea: List<StoredAreaSpecies> = emptyList(),
        val observedMoves: List<StoredSpeciesMoves> = emptyList(),
        val discoveredMatchups: List<StoredMatchup> = emptyList(),
        val knownMoves: List<Int> = emptyList(),
        val matchupEvidenceVersion: Int = 0,
    ) {
        fun toLedger() = KnowledgeLedger(
            seenSpecies = seenSpecies.toSet(),
            caughtSpecies = caughtSpecies.toSet(),
            owned = owned,
            teamSpecies = teamSpecies.toSet(),
            currentAreaBaseId = currentAreaBaseId,
            visitedAreaBaseIds = visitedAreaBaseIds.filter { it >= 0 }.toSet() +
                seenSpeciesByArea.mapNotNull { it.areaBaseId.takeIf { id -> id >= 0 } } +
                listOfNotNull(currentAreaBaseId),
            seenSpeciesByArea = seenSpeciesByArea
                .filter { it.areaBaseId >= 0 }
                .associate { area -> area.areaBaseId to area.speciesIds.filter { it > 0 }.toSet() },
            observedMoves = observedMoves.associate { species ->
                species.speciesId to species.moves
                    .filter { it.moveId > 0 && it.frequency > 0 }
                    .sortedWith(compareByDescending<MoveObservation> { it.frequency }.thenBy { it.moveId })
            },
            discoveredMatchups = if (matchupEvidenceVersion >= KnowledgeLedger.CURRENT_MATCHUP_EVIDENCE_VERSION) {
                discoveredMatchups.mapNotNull { matchup ->
                    runCatching {
                        MatchupKey(matchup.speciesId, matchup.moveId) to Effectiveness.valueOf(matchup.effectiveness)
                    }.getOrNull()
                }.toMap()
            } else {
                emptyMap()
            },
            knownMoves = knownMoves.toSet(),
            matchupEvidenceVersion = KnowledgeLedger.CURRENT_MATCHUP_EVIDENCE_VERSION,
        )

        companion object {
            fun from(identity: String, saveIdentity: String, ledger: KnowledgeLedger) = StoredLedger(
                schema = SCHEMA,
                romIdentity = identity,
                saveIdentity = saveIdentity,
                seenSpecies = ledger.seenSpecies.sorted(),
                caughtSpecies = ledger.caughtSpecies.sorted(),
                owned = ledger.owned,
                teamSpecies = ledger.teamSpecies.sorted(),
                currentAreaBaseId = ledger.currentAreaBaseId,
                visitedAreaBaseIds = ledger.visitedAreaBaseIds.sorted(),
                seenSpeciesByArea = ledger.seenSpeciesByArea.entries
                    .sortedBy { it.key }
                    .map { StoredAreaSpecies(it.key, it.value.sorted()) },
                observedMoves = ledger.observedMoves.entries.sortedBy { it.key }.map { entry ->
                    StoredSpeciesMoves(entry.key, entry.value)
                },
                discoveredMatchups = ledger.discoveredMatchups.entries
                    .sortedWith(compareBy({ it.key.speciesId }, { it.key.moveId }))
                    .map { StoredMatchup(it.key.speciesId, it.key.moveId, it.value.name) },
                knownMoves = ledger.knownMoves.sorted(),
                matchupEvidenceVersion = ledger.matchupEvidenceVersion,
            )
        }
    }

    private data class StoredSpeciesMoves(
        val speciesId: Int = 0,
        val moves: List<MoveObservation> = emptyList(),
    )

    private data class StoredAreaSpecies(
        val areaBaseId: Int = 0,
        val speciesIds: List<Int> = emptyList(),
    )

    private data class StoredMatchup(
        val speciesId: Int = 0,
        val moveId: Int = 0,
        val effectiveness: String = "",
    )

    private companion object {
        const val SCHEMA = 4
    }
}
