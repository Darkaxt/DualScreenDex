package com.darkaxt.dualdex.battle

import com.darkaxt.dualdex.save.BagPocket
import com.darkaxt.dualdex.save.BagPocketSnapshot
import com.darkaxt.dualdex.save.OwnedIndividual
import com.darkaxt.dualdex.save.SaveParseContext
import com.darkaxt.dualdex.save.TrainerSnapshot
import com.darkaxt.dualdex.save.gen3.Gen3PlayerStateCodec
import com.darkaxt.dualdex.save.gen3.Gen3PokemonCodec

enum class Gen3LiveSectionState { AVAILABLE, EMPTY, UNAVAILABLE }

data class Gen3LiveSection<T>(
    val state: Gen3LiveSectionState,
    val value: T? = null,
    val reasons: List<String> = emptyList(),
) {
    init {
        require((state == Gen3LiveSectionState.AVAILABLE) == (value != null))
        require((state == Gen3LiveSectionState.UNAVAILABLE) == reasons.isNotEmpty())
    }

    companion object {
        fun <T> available(value: T) = Gen3LiveSection(Gen3LiveSectionState.AVAILABLE, value)
        fun <T> empty() = Gen3LiveSection<T>(Gen3LiveSectionState.EMPTY)
        fun <T> unavailable(reason: String) =
            Gen3LiveSection<T>(Gen3LiveSectionState.UNAVAILABLE, reasons = listOf(reason))
    }
}

data class Gen3LiveReadWindow(val id: String, val address: Long, val byteCount: Int)

data class Gen3LivePointers(
    val saveBlock1Address: Long?,
    val saveBlock2Address: Long?,
)

data class Gen3LiveBattleState(val active: Boolean)

data class Gen3GameClock(val hours: Int, val minutes: Int) {
    init {
        require(hours in 0..23)
        require(minutes in 0..59)
    }
}

data class Gen3LiveBattleUiState(
    val targetBattler: Int?,
    val encounterKind: BattleEncounterKind,
)

data class Gen3LiveGameSnapshot(
    val romIdentity: String,
    val trainer: Gen3LiveSection<TrainerSnapshot>,
    val location: Gen3LiveSection<Int>,
    val party: Gen3LiveSection<List<OwnedIndividual>>,
    val bag: Map<BagPocket, Gen3LiveSection<BagPocketSnapshot>>,
    val battle: Gen3LiveSection<Gen3LiveBattleState>,
    val battleUi: Gen3LiveSection<Gen3LiveBattleUiState>,
    val clock: Gen3LiveSection<Gen3GameClock> = Gen3LiveSection.unavailable("live game clock was unavailable"),
)

object Gen3LiveGameState {
    const val SAVE_BLOCK1_ID = "live-save-block-1"
    const val SAVE_BLOCK2_ID = "live-save-block-2"
    const val PARTY_COUNT_ID = "live-party-count"
    const val PARTY_ID = "live-party"
    const val SAVE_BLOCK1_POINTER_ID = "live-save-block-1-pointer"
    const val SAVE_BLOCK2_POINTER_ID = "live-save-block-2-pointer"
    const val CLOCK_ID = "live-game-clock"

    fun pointerWindows(layout: Gen3RuntimeMemoryLayout): List<Gen3LiveReadWindow> {
        val saveBlock1Pointer = layout.saveBlock1PointerAddress ?: return emptyList()
        val saveBlock2Pointer = layout.saveBlock2PointerAddress ?: return emptyList()
        return listOf(
            Gen3LiveReadWindow(SAVE_BLOCK1_POINTER_ID, saveBlock1Pointer, POINTER_BYTES),
            Gen3LiveReadWindow(SAVE_BLOCK2_POINTER_ID, saveBlock2Pointer, POINTER_BYTES),
        )
    }

    fun decodePointers(
        regions: Map<String, ByteArray>,
        layout: Gen3RuntimeMemoryLayout,
    ): Gen3LivePointers = Gen3LivePointers(
        saveBlock1Address = decodePointer(regions[SAVE_BLOCK1_POINTER_ID], layout.saveBlock1Size),
        saveBlock2Address = decodePointer(regions[SAVE_BLOCK2_POINTER_ID], layout.saveBlock2Size),
    )

    fun dependentWindows(
        layout: Gen3RuntimeMemoryLayout,
        pointers: Gen3LivePointers,
    ): List<Gen3LiveReadWindow> = buildList {
        pointers.saveBlock1Address?.let { address ->
            add(Gen3LiveReadWindow(SAVE_BLOCK1_ID, address, requireNotNull(layout.saveBlock1Size)))
        }
        pointers.saveBlock2Address?.let { address ->
            add(Gen3LiveReadWindow(SAVE_BLOCK2_ID, address, requireNotNull(layout.saveBlock2Size)))
        }
        layout.playerPartyCountAddress?.let { address ->
            add(Gen3LiveReadWindow(PARTY_COUNT_ID, address, 1))
        }
        layout.playerPartyAddress?.let { address ->
            add(
                Gen3LiveReadWindow(
                    PARTY_ID,
                    address,
                    requireNotNull(layout.playerPartyCapacity) * requireNotNull(layout.playerPartyRecordSize),
                ),
            )
        }
        layout.liveClockAddress?.let { address ->
            add(Gen3LiveReadWindow(CLOCK_ID, address, CLOCK_BYTES))
        }
    }

    fun decode(
        romIdentity: String,
        regions: Map<String, ByteArray>,
        layout: Gen3RuntimeMemoryLayout,
        saveContext: SaveParseContext?,
        savedTrainer: TrainerSnapshot?,
        battleActive: Boolean?,
        targetBattler: Int?,
        encounterKind: BattleEncounterKind,
    ): Gen3LiveGameSnapshot {
        val saveBlock1 = regions[SAVE_BLOCK1_ID]
        val saveBlock2 = regions[SAVE_BLOCK2_ID]
        val saveAbi = saveContext?.gen3SaveRuntimeAbi
        val playerState = if (
            saveBlock1 != null && saveBlock2 != null && saveAbi != null && savedTrainer != null
        ) {
            Gen3PlayerStateCodec.decode(
                saveBlock1,
                saveBlock2,
                saveAbi,
                savedTrainer.dexSeen,
                savedTrainer.dexCaught,
            )
        } else {
            null
        }
        val location = if (saveBlock1 != null) {
            val group = saveBlock1.getOrNull(layout.saveBlock1MapGroupOffset)?.toInt()?.and(0xFF)
            val map = saveBlock1.getOrNull(layout.saveBlock1MapNumberOffset)?.toInt()?.and(0xFF)
            if (group != null && map != null) Gen3LiveSection.available((group shl 8) or map)
            else Gen3LiveSection.unavailable("SaveBlock1 location bytes were incomplete")
        } else {
            Gen3LiveSection.unavailable("SaveBlock1 pointer was unavailable")
        }
        val party = decodeParty(
            regions[PARTY_COUNT_ID],
            regions[PARTY_ID],
            layout,
            saveContext,
        )
        return Gen3LiveGameSnapshot(
            romIdentity = romIdentity,
            trainer = playerState?.trainer?.value?.let(Gen3LiveSection.Companion::available)
                ?: Gen3LiveSection.unavailable(
                    if (savedTrainer == null) "saved Pokédex counts were unavailable"
                    else "live Trainer Card fields were unavailable",
                ),
            location = location,
            party = party,
            bag = BagPocket.entries.associateWith { pocket ->
                playerState?.bag?.get(pocket)?.value?.let(Gen3LiveSection.Companion::available)
                    ?: Gen3LiveSection.unavailable("live $pocket pocket was unavailable")
            },
            battle = battleActive?.let { Gen3LiveSection.available(Gen3LiveBattleState(it)) }
                ?: Gen3LiveSection.unavailable("battle lifecycle byte was unavailable"),
            battleUi = battleActive?.let {
                Gen3LiveSection.available(Gen3LiveBattleUiState(targetBattler, encounterKind))
            } ?: Gen3LiveSection.unavailable("battle UI lifecycle was unavailable"),
            clock = decodeClock(regions[CLOCK_ID]),
        )
    }

    private fun decodeClock(bytes: ByteArray?): Gen3LiveSection<Gen3GameClock> {
        if (bytes?.size != CLOCK_BYTES) return Gen3LiveSection.unavailable("live game clock bytes were unavailable")
        val hours = bytes[CLOCK_HOUR_OFFSET].toInt() and 0xFF
        val minutes = bytes[CLOCK_MINUTE_OFFSET].toInt() and 0xFF
        val seconds = bytes[CLOCK_SECOND_OFFSET].toInt() and 0xFF
        return if (hours in 0..23 && minutes in 0..59 && seconds in 0..59) {
            Gen3LiveSection.available(Gen3GameClock(hours, minutes))
        } else {
            Gen3LiveSection.unavailable("live game clock fields were invalid")
        }
    }

    private fun decodeParty(
        countBytes: ByteArray?,
        partyBytes: ByteArray?,
        layout: Gen3RuntimeMemoryLayout,
        context: SaveParseContext?,
    ): Gen3LiveSection<List<OwnedIndividual>> {
        val count = countBytes?.singleOrNull()?.toInt()?.and(0xFF)
            ?: return Gen3LiveSection.unavailable("party count was unavailable")
        val capacity = layout.playerPartyCapacity
            ?: return Gen3LiveSection.unavailable("party ABI was unavailable")
        val recordSize = layout.playerPartyRecordSize
            ?: return Gen3LiveSection.unavailable("party ABI was unavailable")
        if (count !in 0..capacity) return Gen3LiveSection.unavailable("party count was outside the declared capacity")
        if (count == 0) return Gen3LiveSection.available(emptyList())
        if (context == null || partyBytes?.size != capacity * recordSize) {
            return Gen3LiveSection.unavailable("party bytes or parser context were unavailable")
        }
        val decoded = (0 until count).mapNotNull { index ->
            val offset = index * recordSize
            Gen3PokemonCodec.decode(
                partyBytes,
                offset,
                "party-$index",
                context,
                partyBytes.getOrNull(offset + Gen3PokemonCodec.BOX_RECORD_SIZE + PARTY_LEVEL_OFFSET)
                    ?.toInt()
                    ?.and(0xFF),
            )
        }
        return if (decoded.size == count) Gen3LiveSection.available(decoded)
        else Gen3LiveSection.unavailable("one or more party records failed validation")
    }

    private fun decodePointer(bytes: ByteArray?, byteCount: Int?): Long? {
        if (bytes?.size != POINTER_BYTES || byteCount == null) return null
        val address = bytes.foldIndexed(0L) { index, result, byte ->
            result or ((byte.toLong() and 0xFF) shl (index * 8))
        }
        return address.takeIf { it >= EWRAM_START && it + byteCount <= EWRAM_END_EXCLUSIVE }
    }

    private const val POINTER_BYTES = 4
    private const val PARTY_LEVEL_OFFSET = 4
    private const val CLOCK_BYTES = 5
    private const val CLOCK_HOUR_OFFSET = 2
    private const val CLOCK_MINUTE_OFFSET = 3
    private const val CLOCK_SECOND_OFFSET = 4
    private const val EWRAM_START = 0x02000000L
    private const val EWRAM_END_EXCLUSIVE = 0x02040000L
}
