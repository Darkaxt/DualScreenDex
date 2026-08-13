package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.catalog.CatalogGen3RuntimeMemoryLayout
import com.enrpau.dualscreendex.parser.io.RomImage

/**
 * Recognizes the source-defined Gen III Main ABI from independent references to both the
 * structure base and its final aligned word. Absolute RAM addresses are evidence, never a profile.
 */
object Gen3RuntimeMemoryLayoutResolver {
    fun resolve(rom: RomImage): CatalogGen3RuntimeMemoryLayout? {
        val analysis = analyze(rom)
        val best = analysis.scores.values.maxWithOrNull(compareBy<ReferenceScore> { it.base }.thenBy { it.tail })
            ?: return null
        val mainBase = analysis.scores.filterValues { it == best }.keys.singleOrNull() ?: return null
        val battleField = resolveBattleField(rom, mainBase) ?: return null
        val liveParty = resolveLiveParty(analysis.references)
        val battleLayout = resolveBattleLayout(analysis.references)
        return CatalogGen3RuntimeMemoryLayout(
            mainAddress = mainBase,
            inBattleAddress = battleField.address,
            inBattleMask = battleField.mask,
            saveBlock1MapGroupOffset = SAVE_MAP_GROUP_OFFSET,
            saveBlock1MapNumberOffset = SAVE_MAP_NUMBER_OFFSET,
            multiUsePlayerCursorAddress = null,
            multiUsePlayerCursorEvidence = null,
            playerPartyCountAddress = liveParty?.countAddress,
            playerPartyAddress = liveParty?.partyAddress,
            battleMonsAddress = battleLayout?.battleMonsAddress,
        )
    }

    /**
     * The retail/expansion Gen III battle globals are emitted as one stable related layout:
     * battler count and positions precede gBattleMons, while the move and target cursors follow it.
     * Every address must be independently present in compiled literal pools. Selection is based on
     * the complete reference tuple and fails closed when two layouts have equal authority.
     */
    private fun resolveBattleLayout(references: Map<Long, Int>): LiveBattleLayout? {
        val candidates = references.keys.mapNotNull { battleMonsAddress ->
            if (battleMonsAddress !in EWRAM_START..EWRAM_END || battleMonsAddress and 3L != 0L) {
                return@mapNotNull null
            }
            if (battleMonsAddress + BATTLE_TARGET_CURSOR_DELTA + MAX_BATTLERS > EWRAM_END + 1) {
                return@mapNotNull null
            }
            val counts = listOf(
                references[battleMonsAddress] ?: return@mapNotNull null,
                references[battleMonsAddress - BATTLE_COUNT_DELTA] ?: return@mapNotNull null,
                references[battleMonsAddress - BATTLE_POSITIONS_DELTA] ?: return@mapNotNull null,
                references[battleMonsAddress + BATTLE_MOVE_CURSOR_DELTA] ?: return@mapNotNull null,
                references[battleMonsAddress + BATTLE_TARGET_CURSOR_DELTA] ?: return@mapNotNull null,
                references[battleMonsAddress + MAX_BATTLERS * BATTLE_MON_RECORD_BYTES]
                    ?: return@mapNotNull null,
            )
            LiveBattleLayout(battleMonsAddress, counts)
        }
        val best = candidates.maxWithOrNull(
            compareBy<LiveBattleLayout> { it.totalReferences }
                .thenBy { it.referenceCounts[0] }
                .thenBy { it.referenceCounts[1] }
                .thenBy { it.referenceCounts[2] }
                .thenBy { it.referenceCounts[3] }
                .thenBy { it.referenceCounts[4] }
                .thenBy { it.referenceCounts[5] },
        ) ?: return null
        return candidates.filter { it.score == best.score }.singleOrNull()
    }

    /**
     * EWRAM_DATA places the byte-sized party count immediately before the naturally aligned
     * Pokemon array. Both globals have many independent compiled consumers. We rank every
     * adjacent referenced pair and publish only one unique strongest authority; the addresses
     * themselves always come from the ROM literal pools.
     */
    private fun resolveLiveParty(references: Map<Long, Int>): LivePartyLayout? {
        val candidates = buildList {
            references.forEach { (partyAddress, partyReferences) ->
                if (partyAddress !in EWRAM_START..EWRAM_END || partyAddress and 3L != 0L) return@forEach
                if (partyAddress + LIVE_PARTY_BYTES > EWRAM_END + 1) return@forEach
                for (padding in 1L..MAX_COUNT_PADDING) {
                    val countAddress = partyAddress - padding
                    val countReferences = references[countAddress] ?: continue
                    if (partyReferences <= countReferences) continue
                    add(LivePartyLayout(countAddress, partyAddress, partyReferences, countReferences))
                }
            }
        }
        val best = candidates.maxWithOrNull(
            compareBy<LivePartyLayout> { it.partyReferences }.thenBy { it.countReferences },
        ) ?: return null
        return candidates.filter { it.score == best.score }.singleOrNull()
    }

    /**
     * Finds a live byte by decoding complete Thumb read/modify/write operations. A field is
     * authoritative only when the ROM contains both a one-bit set and a matching clear for the
     * same WRAM address. Literal addresses, index arithmetic and the mask all come from ROM code.
     */
    private fun resolveBattleField(rom: RomImage, mainBase: Long): BitField? {
        val mutations = linkedSetOf<BitMutation>()
        var offset = 0
        while (offset <= rom.size - 2) {
            val raw = rom.u16le(offset)
            if (raw and 0xF800 == 0x4800) {
                val literalOffset = ((offset + 4) and -4) + (raw and 0xFF) * 4
                if (literalOffset <= rom.size - 4 && rom.u32le(literalOffset) in IWRAM_START..IWRAM_END) {
                    var start = maxOf(0, offset - LOOK_BEHIND_BYTES) and -2
                    while (start <= offset) {
                        traceLinearMutations(rom, start, mutations)
                        start += 2
                    }
                }
            }
            offset += 2
        }
        val fields = mutations
            .filter { it.address in mainBase until mainBase + MAIN_STRUCT_SIZE }
            .groupBy { BitField(it.address, it.mask) }
            .filterValues { evidence -> evidence.any { it.set } && evidence.any { !it.set } }
        val highestEvidence = fields.maxOfOrNull { (_, evidence) -> evidence.map { it.site }.distinct().size }
            ?: return null
        return fields.filterValues { evidence -> evidence.map { it.site }.distinct().size == highestEvidence }
            .keys.singleOrNull()
    }

    private fun traceLinearMutations(rom: RomImage, start: Int, output: MutableSet<BitMutation>) {
        val registers = arrayOfNulls<Value>(8)
        var offset = start
        val end = minOf(rom.size, start + TRACE_BYTES)
        while (offset <= end - 2) {
            val raw = rom.u16le(offset)
            when {
                raw and 0xF800 == 0x4800 -> {
                    val literalOffset = ((offset + 4) and -4) + (raw and 0xFF) * 4
                    registers[(raw ushr 8) and 7] = if (literalOffset <= rom.size - 4) {
                        Value.Constant(rom.u32le(literalOffset))
                    } else null
                }
                raw and 0xF800 == 0x2000 ->
                    registers[(raw ushr 8) and 7] = Value.Constant((raw and 0xFF).toLong())
                raw and 0xFE00 == 0x5C00 -> {
                    val destination = raw and 7
                    val base = registers[(raw ushr 3) and 7] as? Value.Constant
                    val index = registers[(raw ushr 6) and 7] as? Value.Constant
                    registers[destination] = if (base != null && index != null) {
                        Value.ByteAt(base.value + index.value)
                    } else null
                }
                raw and 0xF800 == 0x7800 -> {
                    val destination = raw and 7
                    val base = registers[(raw ushr 3) and 7] as? Value.Constant
                    registers[destination] = base?.let { Value.ByteAt(it.value + ((raw ushr 6) and 0x1F)) }
                }
                raw and 0xFC00 == 0x4000 -> {
                    val operation = (raw ushr 6) and 0xF
                    val destination = raw and 7
                    val source = registers[(raw ushr 3) and 7] as? Value.Constant
                    val current = registers[destination] as? Value.ByteAt
                    registers[destination] = if (
                        operation in setOf(OR_OPERATION, BIT_CLEAR_OPERATION) &&
                        current != null && source != null && source.value in 1..0xFF &&
                        source.value.countOneBits() == 1
                    ) {
                        Value.ModifiedByte(current.address, source.value.toInt(), operation == OR_OPERATION)
                    } else if (operation in NON_MUTATING_ALU_OPERATIONS) {
                        registers[destination]
                    } else null
                }
                raw and 0xFE00 == 0x5400 -> {
                    val source = registers[raw and 7] as? Value.ModifiedByte
                    val base = registers[(raw ushr 3) and 7] as? Value.Constant
                    val index = registers[(raw ushr 6) and 7] as? Value.Constant
                    if (source != null && base != null && index != null && source.address == base.value + index.value) {
                        output += BitMutation(source.address, source.mask, source.set, offset)
                    }
                }
                raw and 0xF800 == 0x7000 -> {
                    val source = registers[raw and 7] as? Value.ModifiedByte
                    val base = registers[(raw ushr 3) and 7] as? Value.Constant
                    val address = base?.value?.plus((raw ushr 6) and 0x1F)
                    if (source != null && address == source.address) {
                        output += BitMutation(source.address, source.mask, source.set, offset)
                    }
                }
                raw and 0xF000 == 0xD000 || raw and 0xF800 == 0xE000 || raw and 0xFF87 == 0x4700 -> return
            }
            offset += 2
        }
    }

    private fun analyze(rom: RomImage): ReferenceAnalysis {
        val references = linkedMapOf<Long, Int>()
        var offset = 0
        while (offset <= rom.size - 4) {
            val value = rom.u32le(offset)
            if (value in EWRAM_START..EWRAM_END || value in IWRAM_START..IWRAM_END) {
                references[value] = references.getOrDefault(value, 0) + 1
            }
            offset += 4
        }
        val scores = references.filter { (base, count) ->
            base in IWRAM_START..IWRAM_END &&
                count >= MIN_MAIN_BASE_REFERENCES &&
                references.getOrDefault(base + MAIN_TAIL_WORD_OFFSET, 0) >= MIN_MAIN_TAIL_REFERENCES &&
                base + MAIN_STRUCT_SIZE <= IWRAM_END + 1
        }.mapValues { (base, count) -> ReferenceScore(count, references.getValue(base + MAIN_TAIL_WORD_OFFSET)) }
        return ReferenceAnalysis(references, scores)
    }

    private const val IWRAM_START = 0x03000000L
    private const val IWRAM_END = 0x03007FFFL
    private const val EWRAM_START = 0x02000000L
    private const val EWRAM_END = 0x0203FFFFL
    private const val MAIN_STRUCT_SIZE = 0x43C
    private const val MAIN_TAIL_WORD_OFFSET = 0x438
    private const val SAVE_MAP_GROUP_OFFSET = 4
    private const val SAVE_MAP_NUMBER_OFFSET = 5
    private const val MIN_MAIN_BASE_REFERENCES = 32
    private const val MIN_MAIN_TAIL_REFERENCES = 3
    private const val LIVE_PARTY_BYTES = 6 * 100L
    private const val MAX_COUNT_PADDING = 3L
    private const val BATTLE_COUNT_DELTA = 0x1CL
    private const val BATTLE_POSITIONS_DELTA = 0x10L
    private const val BATTLE_MOVE_CURSOR_DELTA = 0x438L
    private const val BATTLE_TARGET_CURSOR_DELTA = 0x43CL
    private const val MAX_BATTLERS = 4L
    private const val BATTLE_MON_RECORD_BYTES = 0x58L
    private const val LOOK_BEHIND_BYTES = 16
    private const val TRACE_BYTES = 64
    private const val OR_OPERATION = 12
    private const val BIT_CLEAR_OPERATION = 14
    private val NON_MUTATING_ALU_OPERATIONS = setOf(8, 10)

    private data class ReferenceScore(val base: Int, val tail: Int)
    private data class ReferenceAnalysis(
        val references: Map<Long, Int>,
        val scores: Map<Long, ReferenceScore>,
    )

    private data class LivePartyLayout(
        val countAddress: Long,
        val partyAddress: Long,
        val partyReferences: Int,
        val countReferences: Int,
    ) {
        val score: Pair<Int, Int> get() = partyReferences to countReferences
    }

    private data class LiveBattleLayout(
        val battleMonsAddress: Long,
        val referenceCounts: List<Int>,
    ) {
        val totalReferences: Int get() = referenceCounts.sum()
        val score: List<Int> get() = listOf(totalReferences) + referenceCounts
    }

    private sealed interface Value {
        data class Constant(val value: Long) : Value
        data class ByteAt(val address: Long) : Value
        data class ModifiedByte(val address: Long, val mask: Int, val set: Boolean) : Value
    }

    private data class BitField(val address: Long, val mask: Int)
    private data class BitMutation(val address: Long, val mask: Int, val set: Boolean, val site: Int)
}
