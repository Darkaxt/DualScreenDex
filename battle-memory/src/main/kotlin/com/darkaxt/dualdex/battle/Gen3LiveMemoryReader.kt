package com.darkaxt.dualdex.battle

import com.darkaxt.dualdex.save.OwnedIndividual
import com.darkaxt.dualdex.save.SaveParseContext
import com.darkaxt.dualdex.save.gen3.Gen3EventFlagSnapshot
import com.darkaxt.dualdex.save.gen3.Gen3PokemonCodec

data class Gen3LiveReadWindow(val id: String, val address: Long, val byteCount: Int)

data class Gen3LivePointers(
    val saveBlock1Address: Long?,
    val saveBlock2Address: Long?,
)

data class Gen3LiveMemoryValues(
    val location: LiveValue<Int>,
    val party: LiveValue<List<OwnedIndividual>>,
    val clock: LiveValue<LiveClockState>,
    val eventFlags: LiveValue<Set<Int>>,
)

/** Stateless bounded-memory helper. UnifiedGameStateDecoder is the only production state owner. */
object Gen3LiveMemoryReader {
    const val SAVE_BLOCK1_ID = "live-save-block-1"
    const val SAVE_BLOCK2_ID = "live-save-block-2"
    const val PARTY_COUNT_ID = "live-party-count"
    const val PARTY_ID = "live-party"
    const val SAVE_BLOCK1_POINTER_ID = "live-save-block-1-pointer"
    const val SAVE_BLOCK2_POINTER_ID = "live-save-block-2-pointer"
    const val EXTENDED_SAVE_ID = "live-extended-save"
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
        layout.extendedSaveAddress?.let { address ->
            add(Gen3LiveReadWindow(EXTENDED_SAVE_ID, address, requireNotNull(layout.extendedSaveSize)))
        }
        addAll(independentWindows(layout))
    }

    fun independentWindows(layout: Gen3RuntimeMemoryLayout): List<Gen3LiveReadWindow> = buildList {
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
        regions: Map<String, ByteArray>,
        layout: Gen3RuntimeMemoryLayout,
        saveContext: SaveParseContext?,
    ): Gen3LiveMemoryValues {
        val saveBlock1 = regions[SAVE_BLOCK1_ID]
        val saveAbi = saveContext?.gen3SaveRuntimeAbi
        val location = if (saveBlock1 != null) {
            val group = saveBlock1.getOrNull(layout.saveBlock1MapGroupOffset)?.toInt()?.and(0xFF)
            val map = saveBlock1.getOrNull(layout.saveBlock1MapNumberOffset)?.toInt()?.and(0xFF)
            if (group != null && map != null) LiveValue.Available((group shl 8) or map)
            else unavailable(LiveUnavailableCode.INVALID_VALUE, "SaveBlock1 location bytes were incomplete")
        } else {
            unavailable(LiveUnavailableCode.MISSING_REGION, "SaveBlock1 pointer was unavailable")
        }
        return Gen3LiveMemoryValues(
            location = location,
            party = decodeParty(regions[PARTY_COUNT_ID], regions[PARTY_ID], layout, saveContext),
            clock = decodeClock(regions[CLOCK_ID]),
            eventFlags = decodeEventFlags(saveBlock1, saveAbi),
        )
    }

    private fun decodeEventFlags(
        saveBlock1: ByteArray?,
        saveAbi: com.darkaxt.dualdex.save.gen3.Gen3SaveRuntimeAbi?,
    ): LiveValue<Set<Int>> {
        val flagAbi = saveAbi?.eventFlags
            ?: return unavailable(LiveUnavailableCode.UNSUPPORTED_LAYOUT, "typed event flag ABI was unavailable")
        val decoded = saveBlock1?.let { Gen3EventFlagSnapshot.decode(it, flagAbi) }
            ?: return unavailable(LiveUnavailableCode.MISSING_REGION, "live event flag bytes were unavailable")
        return LiveValue.Available(decoded)
    }

    private fun decodeClock(bytes: ByteArray?): LiveValue<LiveClockState> {
        if (bytes?.size != CLOCK_BYTES) {
            return unavailable(LiveUnavailableCode.MISSING_REGION, "live game clock bytes were unavailable")
        }
        val hours = bytes[CLOCK_HOUR_OFFSET].toInt() and 0xFF
        val minutes = bytes[CLOCK_MINUTE_OFFSET].toInt() and 0xFF
        val seconds = bytes[CLOCK_SECOND_OFFSET].toInt() and 0xFF
        return if (hours in 0..23 && minutes in 0..59 && seconds in 0..59) {
            LiveValue.Available(LiveClockState(hours, minutes, seconds))
        } else {
            unavailable(LiveUnavailableCode.INVALID_VALUE, "live game clock fields were invalid")
        }
    }

    private fun decodeParty(
        countBytes: ByteArray?,
        partyBytes: ByteArray?,
        layout: Gen3RuntimeMemoryLayout,
        context: SaveParseContext?,
    ): LiveValue<List<OwnedIndividual>> {
        val count = countBytes?.singleOrNull()?.toInt()?.and(0xFF)
            ?: return unavailable(LiveUnavailableCode.MISSING_REGION, "party count was unavailable")
        val capacity = layout.playerPartyCapacity
            ?: return unavailable(LiveUnavailableCode.UNSUPPORTED_LAYOUT, "party ABI was unavailable")
        val recordSize = layout.playerPartyRecordSize
            ?: return unavailable(LiveUnavailableCode.UNSUPPORTED_LAYOUT, "party ABI was unavailable")
        if (count !in 0..capacity) {
            return unavailable(LiveUnavailableCode.INVALID_VALUE, "party count was outside the declared capacity")
        }
        if (count == 0) return LiveValue.Available(emptyList())
        if (context == null || partyBytes?.size != capacity * recordSize) {
            return unavailable(LiveUnavailableCode.MISSING_REGION, "party bytes or parser context were unavailable")
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
        return if (decoded.size == count) LiveValue.Available(decoded)
        else unavailable(LiveUnavailableCode.INVALID_VALUE, "one or more party records failed validation")
    }

    private fun decodePointer(bytes: ByteArray?, byteCount: Int?): Long? {
        if (bytes?.size != POINTER_BYTES || byteCount == null) return null
        val address = bytes.foldIndexed(0L) { index, result, byte ->
            result or ((byte.toLong() and 0xFF) shl (index * 8))
        }
        return address.takeIf { it >= EWRAM_START && it + byteCount <= EWRAM_END_EXCLUSIVE }
    }

    private fun <T> unavailable(code: LiveUnavailableCode, detail: String): LiveValue<T> =
        LiveValue.Unavailable(LiveUnavailableReason(code, detail))

    private const val POINTER_BYTES = 4
    private const val PARTY_LEVEL_OFFSET = 4
    private const val CLOCK_BYTES = 5
    private const val CLOCK_HOUR_OFFSET = 2
    private const val CLOCK_MINUTE_OFFSET = 3
    private const val CLOCK_SECOND_OFFSET = 4
    private const val EWRAM_START = 0x02000000L
    private const val EWRAM_END_EXCLUSIVE = 0x02040000L
}
