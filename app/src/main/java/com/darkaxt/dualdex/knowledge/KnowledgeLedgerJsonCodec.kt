package com.darkaxt.dualdex.knowledge

import com.enrpau.dualscreendex.companion.model.Effectiveness
import com.enrpau.dualscreendex.companion.model.KnowledgeLedger
import com.enrpau.dualscreendex.companion.model.LocalMapPoiPreferences
import com.enrpau.dualscreendex.companion.model.MatchupKey
import com.enrpau.dualscreendex.companion.model.MoveObservation
import com.enrpau.dualscreendex.companion.model.OwnedPokemon
import com.google.gson.Gson

class KnowledgeLedgerJsonCodec(
    private val gson: Gson = Gson(),
) {
    fun encode(ledger: KnowledgeLedger): ByteArray = encodeDocument("", "", ledger)

    fun decode(bytes: ByteArray): KnowledgeLedger? = decodeDocument(bytes)?.ledger

    internal fun encodeDocument(
        romIdentity: String,
        saveIdentity: String,
        ledger: KnowledgeLedger,
    ): ByteArray = gson.toJson(StoredLedger.from(romIdentity, saveIdentity, ledger))
        .toByteArray(Charsets.UTF_8)

    internal fun decodeDocument(bytes: ByteArray): DecodedLedgerDocument? {
        val stored = try {
            gson.fromJson(bytes.toString(Charsets.UTF_8), StoredLedger::class.java)
        } catch (_: Exception) {
            null
        } ?: return null
        return stored
            .takeIf { it.schema in SUPPORTED_SCHEMAS }
            ?.let { DecodedLedgerDocument(it.romIdentity, it.saveIdentity, it.toLedger()) }
    }

    internal data class DecodedLedgerDocument(
        val romIdentity: String,
        val saveIdentity: String,
        val ledger: KnowledgeLedger,
    )

    private data class StoredLedger(
        val schema: Int = 0,
        val romIdentity: String = "",
        val saveIdentity: String = "",
        val seenSpecies: List<Int> = emptyList(),
        val caughtSpecies: List<Int> = emptyList(),
        val owned: List<OwnedPokemon> = emptyList(),
        val teamSpecies: List<Int> = emptyList(),
        val trainerCardUnlocked: Boolean = false,
        val currentAreaBaseId: Int? = null,
        val visitedAreaBaseIds: List<Int> = emptyList(),
        val seenSpeciesByArea: List<StoredAreaSpecies> = emptyList(),
        val observedMoves: List<StoredSpeciesMoves> = emptyList(),
        val discoveredMatchups: List<StoredMatchup> = emptyList(),
        val knownMoves: List<Int> = emptyList(),
        val proximityRevealedPoiKeys: List<String> = emptyList(),
        val identifiedPoiKeys: List<String> = emptyList(),
        val enteredPoiKeys: List<String> = emptyList(),
        val collectedPoiKeys: List<String> = emptyList(),
        val localMapPoiPreferences: LocalMapPoiPreferences = LocalMapPoiPreferences(),
        val matchupEvidenceVersion: Int = 0,
    ) {
        fun toLedger() = KnowledgeLedger(
            seenSpecies = seenSpecies.toSet(),
            caughtSpecies = caughtSpecies.toSet(),
            owned = owned,
            teamSpecies = teamSpecies.toSet(),
            trainerCardUnlocked = trainerCardUnlocked,
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
            proximityRevealedPoiKeys = proximityRevealedPoiKeys.filter(String::isNotBlank).toSet(),
            identifiedPoiKeys = identifiedPoiKeys.filter(String::isNotBlank).toSet(),
            enteredPoiKeys = enteredPoiKeys.filter(String::isNotBlank).toSet(),
            collectedPoiKeys = collectedPoiKeys.filter(String::isNotBlank).toSet(),
            localMapPoiPreferences = sanitizePreferences(localMapPoiPreferences),
            matchupEvidenceVersion = KnowledgeLedger.CURRENT_MATCHUP_EVIDENCE_VERSION,
        )

        companion object {
            fun from(identity: String, saveIdentity: String, ledger: KnowledgeLedger) = StoredLedger(
                schema = CURRENT_SCHEMA,
                romIdentity = identity,
                saveIdentity = saveIdentity,
                seenSpecies = ledger.seenSpecies.sorted(),
                caughtSpecies = ledger.caughtSpecies.sorted(),
                owned = ledger.owned,
                teamSpecies = ledger.teamSpecies.sorted(),
                trainerCardUnlocked = ledger.trainerCardUnlocked,
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
                proximityRevealedPoiKeys = ledger.proximityRevealedPoiKeys.sorted(),
                identifiedPoiKeys = ledger.identifiedPoiKeys.sorted(),
                enteredPoiKeys = ledger.enteredPoiKeys.sorted(),
                collectedPoiKeys = ledger.collectedPoiKeys.sorted(),
                localMapPoiPreferences = sanitizePreferences(ledger.localMapPoiPreferences),
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
        val SUPPORTED_SCHEMAS = setOf(4, 5, 6, 7)
        const val CURRENT_SCHEMA = 7

        fun sanitizePreferences(preferences: LocalMapPoiPreferences): LocalMapPoiPreferences {
            val icon = preferences.iconZoomThresholdPercent.coerceIn(0, 100)
            return preferences.copy(
                iconZoomThresholdPercent = icon,
                labelZoomThresholdPercent = preferences.labelZoomThresholdPercent.coerceIn(icon, 100),
            )
        }
    }
}
