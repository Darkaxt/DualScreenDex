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
    fun read(romIdentity: String): KnowledgeLedger?
    fun write(romIdentity: String, ledger: KnowledgeLedger)
}

class FileKnowledgeRepository(
    private val root: File,
    private val gson: Gson = Gson(),
) : KnowledgeRepository {
    override fun read(romIdentity: String): KnowledgeLedger? {
        val identity = normalize(romIdentity)
        val document = root.resolve("$identity.json")
        if (!document.isFile) return null
        val stored = runCatching { gson.fromJson(document.readText(), StoredLedger::class.java) }.getOrNull()
            ?: return null
        if (stored.schema != SCHEMA || !stored.romIdentity.equals(identity, ignoreCase = true)) return null
        return stored.toLedger()
    }

    override fun write(romIdentity: String, ledger: KnowledgeLedger) {
        val identity = normalize(romIdentity)
        check(root.isDirectory || root.mkdirs()) { "knowledge directory could not be created" }
        val destination = root.resolve("$identity.json")
        val temporary = root.resolve("$identity.tmp")
        temporary.writeText(gson.toJson(StoredLedger.from(identity, ledger)))
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

    private fun normalize(identity: String): String {
        val normalized = identity.lowercase()
        require(normalized.matches(Regex("[0-9a-f]{64}"))) { "ROM identity must be a SHA-256 hash" }
        return normalized
    }

    private data class StoredLedger(
        val schema: Int = 0,
        val romIdentity: String = "",
        val seenSpecies: List<Int> = emptyList(),
        val caughtSpecies: List<Int> = emptyList(),
        val owned: List<OwnedPokemon> = emptyList(),
        val teamSpecies: List<Int> = emptyList(),
        val currentAreaBaseId: Int? = null,
        val observedMoves: List<StoredSpeciesMoves> = emptyList(),
        val discoveredMatchups: List<StoredMatchup> = emptyList(),
        val knownMoves: List<Int> = emptyList(),
    ) {
        fun toLedger() = KnowledgeLedger(
            seenSpecies = seenSpecies.toSet(),
            caughtSpecies = caughtSpecies.toSet(),
            owned = owned,
            teamSpecies = teamSpecies.toSet(),
            currentAreaBaseId = currentAreaBaseId,
            observedMoves = observedMoves.associate { species ->
                species.speciesId to species.moves
                    .filter { it.moveId > 0 && it.frequency > 0 }
                    .sortedWith(compareByDescending<MoveObservation> { it.frequency }.thenBy { it.moveId })
            },
            discoveredMatchups = discoveredMatchups.mapNotNull { matchup ->
                runCatching {
                    MatchupKey(matchup.speciesId, matchup.moveId) to Effectiveness.valueOf(matchup.effectiveness)
                }.getOrNull()
            }.toMap(),
            knownMoves = knownMoves.toSet(),
        )

        companion object {
            fun from(identity: String, ledger: KnowledgeLedger) = StoredLedger(
                schema = SCHEMA,
                romIdentity = identity,
                seenSpecies = ledger.seenSpecies.sorted(),
                caughtSpecies = ledger.caughtSpecies.sorted(),
                owned = ledger.owned,
                teamSpecies = ledger.teamSpecies.sorted(),
                currentAreaBaseId = ledger.currentAreaBaseId,
                observedMoves = ledger.observedMoves.entries.sortedBy { it.key }.map { entry ->
                    StoredSpeciesMoves(entry.key, entry.value)
                },
                discoveredMatchups = ledger.discoveredMatchups.entries
                    .sortedWith(compareBy({ it.key.speciesId }, { it.key.moveId }))
                    .map { StoredMatchup(it.key.speciesId, it.key.moveId, it.value.name) },
                knownMoves = ledger.knownMoves.sorted(),
            )
        }
    }

    private data class StoredSpeciesMoves(
        val speciesId: Int = 0,
        val moves: List<MoveObservation> = emptyList(),
    )

    private data class StoredMatchup(
        val speciesId: Int = 0,
        val moveId: Int = 0,
        val effectiveness: String = "",
    )

    private companion object {
        const val SCHEMA = 1
    }
}
