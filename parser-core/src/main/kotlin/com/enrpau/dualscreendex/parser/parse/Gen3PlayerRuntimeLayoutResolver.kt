package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.catalog.CatalogGen3BagAbi
import com.enrpau.dualscreendex.parser.catalog.CatalogGen3BagPocket
import com.enrpau.dualscreendex.parser.catalog.CatalogGen3BagPocketAbi
import com.enrpau.dualscreendex.parser.catalog.CatalogGen3BattleUiAbi
import com.enrpau.dualscreendex.parser.catalog.CatalogGen3BitFlag
import com.enrpau.dualscreendex.parser.catalog.CatalogGen3PartyAbi
import com.enrpau.dualscreendex.parser.catalog.CatalogGen3RuntimeMemoryLayout
import com.enrpau.dualscreendex.parser.catalog.CatalogGen3SaveRuntimeAbi
import com.enrpau.dualscreendex.parser.catalog.CatalogGen3TextEncoding
import com.enrpau.dualscreendex.parser.catalog.CatalogGen3TrainerCardAbi
import com.enrpau.dualscreendex.parser.io.RomImage

/**
 * Adds independently proven player-facing runtime groups to the base Gen III memory layout.
 *
 * Pointer addresses come only from decoded Thumb literal consumers. The Emerald save ABI is
 * admitted only after one unique pointer global has independent byte/halfword consumers for the
 * player gender, trainer id, play-time hours and play-time minutes fields. The live party and
 * battle UI groups likewise require every member address to be present in compiled literal pools.
 */
object Gen3PlayerRuntimeLayoutResolver {
    fun attach(
        rom: RomImage,
        base: CatalogGen3RuntimeMemoryLayout,
    ): CatalogGen3RuntimeMemoryLayout {
        val references = compiledRamReferences(rom)
        val saveBlock1 = Gen3SaveBlock1PointerResolver.resolve(rom)
        val saveBlock2 = resolveSaveBlock2Pointer(rom)
        val saveGroup = if (saveBlock1 != null && saveBlock2 != null) emeraldSaveRuntimeAbi() else null
        val party = resolveParty(base, references)
        val battle = resolveBattleLayout(references)
        return base.copy(
            battleMonsAddress = battle?.battleMonsAddress ?: base.battleMonsAddress,
            saveBlock1PointerAddress = saveGroup?.let { saveBlock1 },
            saveBlock2PointerAddress = saveGroup?.let { saveBlock2 },
            saveRuntimeAbi = saveGroup,
            partyAbi = party,
            battleUiAbi = battle?.battleUi,
        )
    }

    private fun resolveSaveBlock2Pointer(rom: RomImage): Long? {
        val evidence = pointerFieldEvidence(rom)
        return evidence.filterValues { fields ->
            fields.totalPointerLoads >= MIN_SAVE_BLOCK_POINTER_LOADS &&
                fields.loadsAt(SAVE2_GENDER_OFFSET) >= MIN_SAVE_FIELD_LOADS &&
                fields.loadsAt(SAVE2_TRAINER_ID_OFFSET) >= MIN_SAVE_FIELD_LOADS &&
                fields.loadsAt(SAVE2_PLAY_HOURS_OFFSET) >= MIN_SAVE_FIELD_LOADS &&
                fields.loadsAt(SAVE2_PLAY_MINUTES_OFFSET) >= MIN_SAVE_FIELD_LOADS
        }.keys.singleOrNull()
    }

    private fun pointerFieldEvidence(rom: RomImage): Map<Long, PointerFields> {
        val output = linkedMapOf<Long, PointerFields>()
        var offset = 0
        while (offset <= rom.size - 2) {
            val literal = rom.u16le(offset)
            if (literal and 0xF800 != 0x4800) {
                offset += 2
                continue
            }
            val literalOffset = ((offset + 4) and -4) + (literal and 0xFF) * 4
            if (literalOffset > rom.size - 4) {
                offset += 2
                continue
            }
            val candidateGlobal = rom.u32le(literalOffset)
            if (candidateGlobal !in EWRAM_START..IWRAM_END) {
                offset += 2
                continue
            }
            val literalRegister = (literal ushr 8) and 7
            val pointerLoad = findPointerLoad(rom, offset + 2, literalRegister) ?: run {
                offset += 2
                continue
            }
            val fields = output.getOrPut(candidateGlobal, ::PointerFields)
            fields.totalPointerLoads++
            collectDirectFieldLoads(rom, pointerLoad.offset + 2, pointerLoad.register, fields)
            offset += 2
        }
        return output
    }

    private fun findPointerLoad(rom: RomImage, start: Int, literalRegister: Int): PointerLoad? {
        var offset = start
        while (offset <= minOf(start + POINTER_LOAD_LOOKAHEAD_BYTES, rom.size - 2)) {
            val instruction = rom.u16le(offset)
            if (
                instruction and 0xF800 == 0x6800 &&
                (instruction ushr 6) and 0x1F == 0 &&
                (instruction ushr 3) and 7 == literalRegister
            ) {
                return PointerLoad(offset, instruction and 7)
            }
            if (isControlFlow(instruction)) return null
            offset += 2
        }
        return null
    }

    private fun collectDirectFieldLoads(
        rom: RomImage,
        start: Int,
        pointerRegister: Int,
        output: PointerFields,
    ) {
        var offset = start
        val end = minOf(start + DIRECT_FIELD_TRACE_BYTES, rom.size)
        while (offset <= end - 2) {
            val instruction = rom.u16le(offset)
            val baseRegister = (instruction ushr 3) and 7
            if (baseRegister == pointerRegister) {
                val byteOffset = when {
                    instruction and 0xF800 == 0x7800 -> (instruction ushr 6) and 0x1F
                    instruction and 0xF800 == 0x8800 -> ((instruction ushr 6) and 0x1F) * 2
                    instruction and 0xF800 == 0x6800 -> ((instruction ushr 6) and 0x1F) * 4
                    else -> null
                }
                byteOffset?.let(output::record)
            }
            if (isControlFlow(instruction)) return
            offset += 2
        }
    }

    private fun isControlFlow(instruction: Int): Boolean =
        instruction and 0xF000 == 0xD000 ||
            instruction and 0xF800 == 0xE000 ||
            instruction and 0xF800 == 0xF000 ||
            instruction and 0xFF87 == 0x4700 ||
            instruction and 0xFF00 == 0xBD00

    private fun compiledRamReferences(rom: RomImage): Map<Long, Int> {
        val references = linkedMapOf<Long, Int>()
        var offset = 0
        while (offset <= rom.size - 4) {
            val value = rom.u32le(offset)
            if (value in EWRAM_START..EWRAM_END || value in IWRAM_START..IWRAM_END) {
                references[value] = references.getOrDefault(value, 0) + 1
            }
            offset += 4
        }
        return references
    }

    private fun resolveParty(
        base: CatalogGen3RuntimeMemoryLayout,
        references: Map<Long, Int>,
    ): CatalogGen3PartyAbi? {
        val count = base.playerPartyCountAddress ?: return null
        val party = base.playerPartyAddress ?: return null
        if ((references[count] ?: 0) < MIN_PARTY_COUNT_REFERENCES) return null
        if ((references[party] ?: 0) < MIN_PARTY_REFERENCES) return null
        return CatalogGen3PartyAbi(count, party, PARTY_CAPACITY, PARTY_RECORD_SIZE)
    }

    private fun resolveBattleLayout(references: Map<Long, Int>): ResolvedBattleLayout? {
        val candidates = references.keys.mapNotNull { battleMons ->
            if (battleMons !in EWRAM_START..EWRAM_END || battleMons and 3L != 0L) return@mapNotNull null
            val active = battleMons - ACTIVE_BATTLER_DELTA
            val count = battleMons - BATTLER_COUNT_DELTA
            val end = battleMons + BATTLE_MON_WINDOW_BYTES
            val action = battleMons + ACTION_CURSOR_DELTA
            val move = battleMons + MOVE_CURSOR_DELTA
            val target = battleMons + TARGET_BATTLER_DELTA
            val required = mapOf(
                battleMons to MIN_BATTLE_MON_REFERENCES,
                end to MIN_BATTLE_END_REFERENCES,
                active to MIN_ACTIVE_BATTLER_REFERENCES,
                count to MIN_BATTLER_COUNT_REFERENCES,
                action to MIN_CURSOR_REFERENCES,
                move to MIN_CURSOR_REFERENCES,
                target to MIN_TARGET_REFERENCES,
            )
            if (required.any { (address, minimum) -> (references[address] ?: 0) < minimum }) return@mapNotNull null
            ResolvedBattleLayout(
                battleMons,
                CatalogGen3BattleUiAbi(active, action, move, target),
            )
        }
        return candidates.singleOrNull()
    }

    private fun emeraldSaveRuntimeAbi() = CatalogGen3SaveRuntimeAbi(
        saveBlock1Size = 0x3D88,
        saveBlock2Size = 0x0F2C,
        textEncoding = CatalogGen3TextEncoding.ENGLISH,
        trainer = CatalogGen3TrainerCardAbi(
            playerNameOffset = 0x00,
            playerNameLength = 8,
            genderOffset = SAVE2_GENDER_OFFSET,
            trainerIdOffset = SAVE2_TRAINER_ID_OFFSET,
            playTimeHoursOffset = SAVE2_PLAY_HOURS_OFFSET,
            playTimeMinutesOffset = SAVE2_PLAY_MINUTES_OFFSET,
            encryptionKeyOffset = 0xAC,
            moneyOffset = 0x490,
            maximumMoney = 999_999,
            badgeFlags = listOf(
                CatalogGen3BitFlag(0x137C, 0x80),
                CatalogGen3BitFlag(0x137D, 0x01),
                CatalogGen3BitFlag(0x137D, 0x02),
                CatalogGen3BitFlag(0x137D, 0x04),
                CatalogGen3BitFlag(0x137D, 0x08),
                CatalogGen3BitFlag(0x137D, 0x10),
                CatalogGen3BitFlag(0x137D, 0x20),
                CatalogGen3BitFlag(0x137D, 0x40),
            ),
        ),
        bag = CatalogGen3BagAbi(
            listOf(
                CatalogGen3BagPocketAbi(CatalogGen3BagPocket.ITEMS, 0x560, 30),
                CatalogGen3BagPocketAbi(CatalogGen3BagPocket.KEY_ITEMS, 0x5D8, 30),
                CatalogGen3BagPocketAbi(CatalogGen3BagPocket.BALLS, 0x650, 16),
                CatalogGen3BagPocketAbi(CatalogGen3BagPocket.TM_HM, 0x690, 64),
                CatalogGen3BagPocketAbi(CatalogGen3BagPocket.BERRIES, 0x790, 46),
            ),
        ),
    )

    private const val EWRAM_START = 0x02000000L
    private const val EWRAM_END = 0x0203FFFFL
    private const val IWRAM_START = 0x03000000L
    private const val IWRAM_END = 0x03007FFFL
    private const val POINTER_LOAD_LOOKAHEAD_BYTES = 12
    private const val DIRECT_FIELD_TRACE_BYTES = 40
    private const val MIN_SAVE_BLOCK_POINTER_LOADS = 20
    private const val MIN_SAVE_FIELD_LOADS = 4
    private const val SAVE2_GENDER_OFFSET = 0x08
    private const val SAVE2_TRAINER_ID_OFFSET = 0x0A
    private const val SAVE2_PLAY_HOURS_OFFSET = 0x0E
    private const val SAVE2_PLAY_MINUTES_OFFSET = 0x10
    private const val PARTY_CAPACITY = 6
    private const val PARTY_RECORD_SIZE = 100
    private const val MIN_PARTY_COUNT_REFERENCES = 4
    private const val MIN_PARTY_REFERENCES = 16
    private const val ACTIVE_BATTLER_DELTA = 0x20
    private const val BATTLER_COUNT_DELTA = 0x1C
    private const val BATTLE_MON_WINDOW_BYTES = 4 * 0x58
    private const val TARGET_BATTLER_DELTA = 0x188
    private const val ACTION_CURSOR_DELTA = 0x428
    private const val MOVE_CURSOR_DELTA = 0x42C
    private const val MIN_ACTIVE_BATTLER_REFERENCES = 16
    private const val MIN_BATTLER_COUNT_REFERENCES = 4
    private const val MIN_BATTLE_MON_REFERENCES = 16
    private const val MIN_BATTLE_END_REFERENCES = 1
    private const val MIN_TARGET_REFERENCES = 8
    private const val MIN_CURSOR_REFERENCES = 4

    private data class PointerLoad(val offset: Int, val register: Int)
    private data class ResolvedBattleLayout(
        val battleMonsAddress: Long,
        val battleUi: CatalogGen3BattleUiAbi,
    )
    private class PointerFields {
        var totalPointerLoads: Int = 0
        private val offsets = linkedMapOf<Int, Int>()

        fun record(offset: Int) {
            offsets[offset] = offsets.getOrDefault(offset, 0) + 1
        }

        fun loadsAt(offset: Int): Int = offsets[offset] ?: 0
    }
}
