package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.catalog.CatalogGen3BagAbi
import com.enrpau.dualscreendex.parser.catalog.CatalogGen3BagDataSource
import com.enrpau.dualscreendex.parser.catalog.CatalogGen3BagPocket
import com.enrpau.dualscreendex.parser.catalog.CatalogGen3BagPocketAbi
import com.enrpau.dualscreendex.parser.catalog.CatalogGen3EventFlagAbi
import com.enrpau.dualscreendex.parser.catalog.CatalogGen3BattleUiAbi
import com.enrpau.dualscreendex.parser.catalog.CatalogGen3BitFlag
import com.enrpau.dualscreendex.parser.catalog.CatalogGen3PartyAbi
import com.enrpau.dualscreendex.parser.catalog.CatalogGen3RuntimeMemoryLayout
import com.enrpau.dualscreendex.parser.catalog.CatalogGen3SaveRuntimeAbi
import com.enrpau.dualscreendex.parser.catalog.CatalogGen3TextEncoding
import com.enrpau.dualscreendex.parser.catalog.CatalogGen3TrainerCardAbi
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7Address
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7BranchRegister
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7DataOperation
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7DataProcessing
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7DecodeResult
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7Immediate
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7Instruction
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7MemoryTransfer
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7MemoryWidth
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7Register
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7RegisterOperand
import com.enrpau.dualscreendex.parser.analysis.thumb.ThumbDecoder
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.EngineFamily

/**
 * Adds independently proven player-facing runtime groups to the base Gen III memory layout.
 *
 * Pointer addresses come only from decoded Thumb literal consumers. A save ABI is admitted only
 * after one unique pointer global has independent byte/halfword consumers for the
 * player gender, trainer id, play-time hours and play-time minutes fields. The live party and
 * battle UI groups likewise require every member address to be present in compiled literal pools.
 */
object Gen3PlayerRuntimeLayoutResolver {
    fun attach(
        rom: RomImage,
        base: CatalogGen3RuntimeMemoryLayout,
        family: EngineFamily,
    ): CatalogGen3RuntimeMemoryLayout {
        val references = compiledRamReferences(rom)
        val directSave = if (family == EngineFamily.RUBY_SAPPHIRE) {
            resolveRubySapphireDirectSave(references)
        } else null
        val gfHeader = if (family == EngineFamily.FIRERED_LEAFGREEN) resolveGfRomHeader(rom) else null
        val expandedSave = gfHeader?.let { resolveExpandedSave(rom, it, references) }
        val saveBlock1Pointer = if (directSave == null) {
            Gen3SaveBlock1PointerResolver.resolve(rom) ?: expandedSave?.saveBlock1PointerAddress
        } else null
        val saveBlock2Pointer = if (directSave == null) {
            resolveSaveBlock2Pointer(rom, saveBlock1Pointer) ?: expandedSave?.saveBlock2PointerAddress
        } else null
        val saveGroup = if (directSave != null) {
            rubySapphireSaveRuntimeAbi()
        } else if (saveBlock1Pointer != null && saveBlock2Pointer != null) {
            when (family) {
                EngineFamily.EMERALD -> resolveEmeraldEncryptionKeyOffset(rom, saveBlock2Pointer)
                    ?.let(::emeraldSaveRuntimeAbi)
                EngineFamily.FIRERED_LEAFGREEN -> gfHeader?.let { fireRedSaveRuntimeAbi(it, expandedSave) }
                else -> null
            }
        } else null
        val party = resolveParty(base, references)
        val battle = resolveBattleLayout(references)
        return base.copy(
            battleMonsAddress = battle?.battleMonsAddress ?: base.battleMonsAddress,
            saveBlock1Address = saveGroup?.let { directSave?.saveBlock1Address },
            saveBlock2Address = saveGroup?.let { directSave?.saveBlock2Address },
            saveBlock1PointerAddress = saveGroup?.let { saveBlock1Pointer },
            saveBlock2PointerAddress = saveGroup?.let { saveBlock2Pointer },
            extendedSaveAddress = saveGroup?.extendedSaveDataSize?.takeIf { it > 0 }
                ?.let { expandedSave?.extendedSaveAddress },
            saveRuntimeAbi = saveGroup,
            partyAbi = party,
            battleUiAbi = battle?.battleUi,
        )
    }

    /**
     * Retail Ruby/Sapphire owns the save blocks as adjacent EWRAM objects rather than pointer-backed
     * allocations. Resolve that mode from the complete source-defined field tuple: identity/time
     * fields in SaveBlock2 and location/party/money/bag/flags fields in SaveBlock1. Absolute
     * addresses are admitted only when the ROM has one unique tuple with the source-defined
     * SaveBlock2-to-SaveBlock1 allocation relationship.
     */
    private fun resolveRubySapphireDirectSave(
        references: Map<Long, Int>,
    ): DirectSaveResolution? {
        val saveBlock2Candidates = references.keys.filter { base ->
            base in EWRAM_START..EWRAM_END - RUBY_SAPPHIRE_SAVE_BLOCK2_SIZE + 1 &&
                (references[base] ?: 0) >= MIN_DIRECT_SAVE2_BASE_REFERENCES &&
                (references[base + SAVE2_TRAINER_ID_OFFSET] ?: 0) >= MIN_DIRECT_SAVE2_TRAINER_ID_REFERENCES &&
                (references[base + RUBY_SAPPHIRE_LOCAL_TIME_OFFSET] ?: 0) >= MIN_DIRECT_SAVE2_TIME_REFERENCES &&
                (references[base + RUBY_SAPPHIRE_BERRY_TIME_OFFSET] ?: 0) >= MIN_DIRECT_SAVE2_BERRY_TIME_REFERENCES
        }
        val saveBlock1Candidates = references.keys.filterTo(linkedSetOf()) { base ->
            base in EWRAM_START..EWRAM_END - RUBY_SAPPHIRE_SAVE_BLOCK1_SIZE + 1 &&
                (references[base] ?: 0) >= MIN_DIRECT_SAVE1_BASE_REFERENCES &&
                (references[base + SAVE1_LOCATION_OFFSET] ?: 0) >= MIN_DIRECT_SAVE1_LOCATION_REFERENCES &&
                (references[base + RUBY_SAPPHIRE_PARTY_OFFSET] ?: 0) >= MIN_DIRECT_SAVE1_PARTY_REFERENCES &&
                (references[base + RUBY_SAPPHIRE_MONEY_OFFSET] ?: 0) >= MIN_DIRECT_SAVE1_MONEY_REFERENCES &&
                (references[base + RUBY_SAPPHIRE_ITEMS_OFFSET] ?: 0) >= MIN_DIRECT_SAVE1_BAG_REFERENCES &&
                (references[base + RUBY_SAPPHIRE_FLAGS_OFFSET] ?: 0) >= MIN_DIRECT_SAVE1_FLAGS_REFERENCES
        }
        return saveBlock2Candidates.mapNotNull { saveBlock2 ->
            val saveBlock1 = saveBlock2 + RUBY_SAPPHIRE_SAVE_BLOCK2_SIZE
            saveBlock1.takeIf(saveBlock1Candidates::contains)?.let {
                DirectSaveResolution(saveBlock1Address = it, saveBlock2Address = saveBlock2)
            }
        }.singleOrNull()
    }

    private fun resolveSaveBlock2Pointer(rom: RomImage, saveBlock1PointerAddress: Long?): Long? {
        val evidence = pointerFieldEvidence(rom)
        val independentlyResolved = evidence.filterValues { fields ->
            fields.totalPointerLoads >= MIN_SAVE_BLOCK_POINTER_LOADS &&
                fields.loadsAt(SAVE2_GENDER_OFFSET) >= MIN_SAVE_FIELD_LOADS &&
                fields.loadsAt(SAVE2_TRAINER_ID_OFFSET) >= MIN_SAVE_FIELD_LOADS &&
                fields.loadsAt(SAVE2_PLAY_HOURS_OFFSET) >= MIN_SAVE_FIELD_LOADS &&
                fields.loadsAt(SAVE2_PLAY_MINUTES_OFFSET) >= MIN_SAVE_FIELD_LOADS
        }.keys.singleOrNull()
        if (independentlyResolved != null) return independentlyResolved

        // IWRAM common allocation emits the two save pointers as adjacent aligned globals.
        // Admit that structural relationship only when SaveBlock1 was independently resolved and
        // the adjacent pointer itself has compiled consumers for every trainer identity/time field.
        val adjacent = saveBlock1PointerAddress?.plus(4) ?: return null
        val fields = evidence[adjacent] ?: return null
        return adjacent.takeIf {
            fields.totalPointerLoads >= MIN_SAVE_BLOCK_POINTER_LOADS &&
                fields.loadsAt(SAVE2_GENDER_OFFSET) >= MIN_ADJACENT_SAVE_FIELD_LOADS &&
                fields.loadsAt(SAVE2_TRAINER_ID_OFFSET) >= MIN_ADJACENT_SAVE_FIELD_LOADS &&
                fields.loadsAt(SAVE2_PLAY_HOURS_OFFSET) >= MIN_ADJACENT_SAVE_FIELD_LOADS &&
                fields.loadsAt(SAVE2_PLAY_MINUTES_OFFSET) >= MIN_ADJACENT_SAVE_FIELD_LOADS
        }
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

    /**
     * Resolves the SaveBlock2 encryption-key member from the compiled GetMoney leaf rather than
     * inheriting the retail Emerald struct layout. Emerald-derived projects can insert members in
     * SaveBlock2 while retaining the rest of the save ABI, as Modern Emerald does.
     *
     * The admitted flow is deliberately narrow: load the resolved SaveBlock2 pointer global,
     * dereference it, materialize one member offset, load the caller-supplied encrypted word, load
     * the key through that member offset, XOR the two values and return. Multiple distinct offsets
     * fail closed.
     */
    private fun resolveEmeraldEncryptionKeyOffset(
        rom: RomImage,
        saveBlock2PointerAddress: Long,
    ): Int? {
        val candidates = linkedSetOf<Int>()
        var offset = 0
        while (offset <= rom.size - EMERALD_GET_MONEY_BYTES) {
            if (rom.u16le(offset) and THUMB_PC_RELATIVE_LOAD_MASK != THUMB_PC_RELATIVE_LOAD) {
                offset += 2
                continue
            }
            val literalLoad = decodedThumb(rom, offset) as? Arm7MemoryTransfer
            val literalAddress = literalLoad?.address as? Arm7Address.PcRelative
            if (
                literalLoad == null || !literalLoad.load ||
                literalLoad.width != Arm7MemoryWidth.WORD || literalAddress == null ||
                literalAddress.resolvedAddress !in 0L..rom.size.toLong() - 4L ||
                rom.u32le(literalAddress.resolvedAddress.toInt()) != saveBlock2PointerAddress
            ) {
                offset += 2
                continue
            }

            val pointerLoad = decodedThumb(rom, offset + 2) as? Arm7MemoryTransfer
            val pointerAddress = pointerLoad?.address as? Arm7Address.RegisterOffset
            val keyOffsetMove = decodedThumb(rom, offset + 4) as? Arm7DataProcessing
            val encryptedLoad = decodedThumb(rom, offset + 6) as? Arm7MemoryTransfer
            val encryptedAddress = encryptedLoad?.address as? Arm7Address.RegisterOffset
            val keyLoad = decodedThumb(rom, offset + 8) as? Arm7MemoryTransfer
            val keyAddress = keyLoad?.address as? Arm7Address.RegisterOffset
            val xor = decodedThumb(rom, offset + 10) as? Arm7DataProcessing
            val returnInstruction = decodedThumb(rom, offset + 12) as? Arm7BranchRegister
            val keyOffset = (keyOffsetMove?.second as? Arm7Immediate)?.value?.toInt()
            val xorFirst = xor?.first as? Arm7RegisterOperand
            val xorSecond = xor?.second as? Arm7RegisterOperand
            val keyOffsetFirst = keyOffsetMove?.first as? Arm7RegisterOperand
            val indexedKeyLoad =
                keyOffsetMove?.operation == Arm7DataOperation.MOVE &&
                    keyAddress != null && keyAddress.base == pointerLoad?.valueRegister &&
                    keyAddress.index == keyOffsetMove.destination && keyAddress.immediate == 0
            val advancedPointerKeyLoad =
                keyOffsetMove?.operation == Arm7DataOperation.ADD &&
                    keyOffsetMove.destination == pointerLoad?.valueRegister &&
                    keyOffsetFirst?.register == pointerLoad.valueRegister &&
                    keyAddress != null && keyAddress.base == pointerLoad.valueRegister &&
                    keyAddress.index == null && keyAddress.immediate == 0

            if (
                pointerLoad?.load == true && pointerLoad.width == Arm7MemoryWidth.WORD &&
                pointerAddress?.base == literalLoad.valueRegister && pointerAddress.index == null &&
                pointerAddress.immediate == 0 &&
                keyOffset != null && keyOffset in 4 until EMERALD_SAVE_BLOCK2_SIZE && keyOffset and 3 == 0 &&
                encryptedLoad?.load == true && encryptedLoad.width == Arm7MemoryWidth.WORD &&
                encryptedLoad.valueRegister == Arm7Register.R0 &&
                encryptedAddress?.base == Arm7Register.R0 && encryptedAddress.index == null &&
                encryptedAddress.immediate == 0 &&
                keyLoad?.load == true && keyLoad.width == Arm7MemoryWidth.WORD &&
                (indexedKeyLoad || advancedPointerKeyLoad) &&
                xor?.operation == Arm7DataOperation.EXCLUSIVE_OR &&
                xor.destination == Arm7Register.R0 && xorFirst?.register == Arm7Register.R0 &&
                xorSecond?.register == keyLoad.valueRegister &&
                returnInstruction?.targetRegister == Arm7Register.LR && !returnInstruction.link
            ) {
                candidates += keyOffset
            }
            offset += 2
        }
        return candidates.singleOrNull()
    }

    private fun decodedThumb(rom: RomImage, offset: Int): Arm7Instruction? =
        (ThumbDecoder.decode(rom, offset) as? Arm7DecodeResult.Decoded)?.instruction

    private fun emeraldSaveRuntimeAbi(encryptionKeyOffset: Int) = CatalogGen3SaveRuntimeAbi(
        saveBlock1Size = 0x3D88,
        saveBlock2Size = EMERALD_SAVE_BLOCK2_SIZE,
        textEncoding = CatalogGen3TextEncoding.ENGLISH,
        trainer = CatalogGen3TrainerCardAbi(
            playerNameOffset = 0x00,
            playerNameLength = 8,
            genderOffset = SAVE2_GENDER_OFFSET,
            trainerIdOffset = SAVE2_TRAINER_ID_OFFSET,
            playTimeHoursOffset = SAVE2_PLAY_HOURS_OFFSET,
            playTimeMinutesOffset = SAVE2_PLAY_MINUTES_OFFSET,
            encryptionKeyOffset = encryptionKeyOffset,
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
        eventFlags = CatalogGen3EventFlagAbi(0x1270, 0x12C),
    )

    private fun rubySapphireSaveRuntimeAbi() = CatalogGen3SaveRuntimeAbi(
        saveBlock1Size = RUBY_SAPPHIRE_SAVE_BLOCK1_SIZE,
        saveBlock2Size = RUBY_SAPPHIRE_SAVE_BLOCK2_SIZE,
        textEncoding = CatalogGen3TextEncoding.ENGLISH,
        trainer = CatalogGen3TrainerCardAbi(
            playerNameOffset = 0x00,
            playerNameLength = 8,
            genderOffset = SAVE2_GENDER_OFFSET,
            trainerIdOffset = SAVE2_TRAINER_ID_OFFSET,
            playTimeHoursOffset = SAVE2_PLAY_HOURS_OFFSET,
            playTimeMinutesOffset = SAVE2_PLAY_MINUTES_OFFSET,
            encryptionKeyOffset = null,
            moneyOffset = RUBY_SAPPHIRE_MONEY_OFFSET,
            maximumMoney = MAXIMUM_MONEY,
            badgeFlags = listOf(
                CatalogGen3BitFlag(0x1320, 0x80),
                CatalogGen3BitFlag(0x1321, 0x01),
                CatalogGen3BitFlag(0x1321, 0x02),
                CatalogGen3BitFlag(0x1321, 0x04),
                CatalogGen3BitFlag(0x1321, 0x08),
                CatalogGen3BitFlag(0x1321, 0x10),
                CatalogGen3BitFlag(0x1321, 0x20),
                CatalogGen3BitFlag(0x1321, 0x40),
            ),
        ),
        bag = CatalogGen3BagAbi(
            listOf(
                CatalogGen3BagPocketAbi(CatalogGen3BagPocket.ITEMS, RUBY_SAPPHIRE_ITEMS_OFFSET, 20),
                CatalogGen3BagPocketAbi(CatalogGen3BagPocket.KEY_ITEMS, 0x5B0, 20),
                CatalogGen3BagPocketAbi(CatalogGen3BagPocket.BALLS, 0x600, 16),
                CatalogGen3BagPocketAbi(CatalogGen3BagPocket.TM_HM, 0x640, 64),
                CatalogGen3BagPocketAbi(CatalogGen3BagPocket.BERRIES, 0x740, 46),
            ),
        ),
        eventFlags = CatalogGen3EventFlagAbi(RUBY_SAPPHIRE_FLAGS_OFFSET, 0x120),
    )

    /**
     * The source-defined `GFRomHeader` publishes the save-block sizes, field offsets and bag
     * capacities used by FRLG-derived binaries. It is recognized by its internal pointer/range
     * relationships, never by its linked address, ROM identity or game-name contents.
     */
    private fun fireRedSaveRuntimeAbi(
        header: GfRomHeader,
        expandedSave: ExpandedSaveResolution?,
    ): CatalogGen3SaveRuntimeAbi? {
        val moneyOffset = header.partyOffset + PARTY_CAPACITY * PARTY_RECORD_SIZE
        val firstBagOffset = header.pcItemsOffset + header.pcItemsCount * ITEM_SLOT_SIZE
        var pocketOffset = firstBagOffset
        val standardPockets = CatalogGen3BagPocket.entries.mapIndexed { index, pocket ->
            val capacity = header.bagCounts[index]
            CatalogGen3BagPocketAbi(pocket, pocketOffset, capacity).also {
                pocketOffset += capacity * ITEM_SLOT_SIZE
            }
        }
        if (pocketOffset != header.seen1Offset) return null
        val pockets = expandedSave?.pockets ?: standardPockets
        val firstBadgeId = header.gameClearFlag - BADGE_TO_GAME_CLEAR_DELTA
        if (firstBadgeId < 0) return null
        val badges = (0 until BADGE_COUNT).map { index ->
            val flagId = firstBadgeId + index
            CatalogGen3BitFlag(header.flagsOffset + flagId / 8, 1 shl (flagId % 8))
        }
        return runCatching {
            CatalogGen3SaveRuntimeAbi(
                saveBlock1Size = header.saveBlock1Size,
                saveBlock2Size = header.saveBlock2Size,
                extendedSaveDataSize = if (expandedSave == null) 0 else CFRU_EXTENDED_SAVE_SIZE,
                textEncoding = CatalogGen3TextEncoding.ENGLISH,
                trainer = CatalogGen3TrainerCardAbi(
                    playerNameOffset = header.playerNameOffset,
                    playerNameLength = header.playerNameLength + 1,
                    genderOffset = header.playerGenderOffset,
                    trainerIdOffset = header.trainerIdOffset,
                    playTimeHoursOffset = SAVE2_PLAY_HOURS_OFFSET,
                    playTimeMinutesOffset = SAVE2_PLAY_MINUTES_OFFSET,
                    encryptionKeyOffset = header.saveBlock2Size - ENCRYPTION_KEY_SIZE,
                    moneyOffset = moneyOffset,
                    maximumMoney = MAXIMUM_MONEY,
                    badgeFlags = badges,
                ),
                bag = CatalogGen3BagAbi(pockets),
                eventFlags = CatalogGen3EventFlagAbi(
                    byteOffset = header.flagsOffset,
                    byteCount = header.varsOffset - header.flagsOffset,
                ),
            )
        }.getOrNull()
    }

    private fun resolveExpandedSave(
        rom: RomImage,
        header: GfRomHeader,
        references: Map<Long, Int>,
    ): ExpandedSaveResolution? {
        val directory = resolveCfruSaveDirectory(rom, header) ?: return null
        val pointerGlobals = resolveSavePointerGlobals(rom, directory) ?: return null
        val bag = expandedBagArrangements(rom).singleOrNull() ?: return null
        val firstBagAddress = bag.pointers.first()
        val lastBagEnd = bag.pointers.last() + bag.counts.last().toLong() * ITEM_SLOT_SIZE
        val extendedCandidates = references.keys.filter { candidate ->
            candidate in EWRAM_START..EWRAM_END &&
                candidate <= firstBagAddress && lastBagEnd <= candidate + CFRU_EXTENDED_SAVE_SIZE &&
                references.containsKey(candidate + CFRU_PARASITE_FRAGMENT_1_SIZE) &&
                references.containsKey(candidate + CFRU_PARASITE_FRAGMENT_1_SIZE + CFRU_PARASITE_FRAGMENT_2_SIZE) &&
                references.containsKey(candidate + CFRU_PARASITE_SIZE) &&
                references.containsKey(candidate + CFRU_PARASITE_SIZE + CFRU_SECTION_STRIDE)
        }
        val extendedBase = extendedCandidates.singleOrNull() ?: return null
        val pockets = CatalogGen3BagPocket.entries.mapIndexed { index, pocket ->
            CatalogGen3BagPocketAbi(
                pocket = pocket,
                byteOffset = (bag.pointers[index] - extendedBase).toInt(),
                capacity = bag.counts[index],
                dataSource = CatalogGen3BagDataSource.EXTENDED_SAVE,
            )
        }
        return runCatching {
            ExpandedSaveResolution(
                saveBlock1PointerAddress = pointerGlobals.first,
                saveBlock2PointerAddress = pointerGlobals.second,
                extendedSaveAddress = extendedBase,
                pockets = pockets,
            )
        }.getOrNull()
    }

    private fun resolveCfruSaveDirectory(rom: RomImage, header: GfRomHeader): SaveDirectory? {
        if (header.saveBlock2Size != CFRU_SECTION_SIZES.first() ||
            header.saveBlock1Size != CFRU_SECTION_SIZES.slice(1..4).sum()
        ) return null
        val candidates = buildList {
            var offset = 0
            while (offset <= rom.size - CFRU_DIRECTORY_BYTES) {
                val addresses = LongArray(CFRU_SECTION_COUNT)
                var valid = true
                repeat(CFRU_SECTION_COUNT) { index ->
                    addresses[index] = rom.u32le(offset + index * SAVE_CHUNK_ENTRY_BYTES)
                    val size = rom.u32le(offset + index * SAVE_CHUNK_ENTRY_BYTES + 4).toInt()
                    if (addresses[index] !in EWRAM_START..EWRAM_END || size != CFRU_SECTION_SIZES[index]) {
                        valid = false
                    }
                }
                if (valid &&
                    (1..3).all { addresses[it + 1] == addresses[it] + CFRU_SECTION_STRIDE } &&
                    (5..12).all { addresses[it + 1] == addresses[it] + CFRU_SECTION_STRIDE }
                ) {
                    add(SaveDirectory(addresses[0], addresses[1], addresses[5]))
                }
                offset += 4
            }
        }
        return candidates.distinct().singleOrNull()
    }

    private fun resolveSavePointerGlobals(rom: RomImage, directory: SaveDirectory): Pair<Long, Long>? {
        val assignments = decodedRamAssignments(rom)
        val candidates = assignments.groupBy(RamAssignment::blockStart).mapNotNull { (_, block) ->
            val saveBlock1Globals = block.filter { it.value == directory.saveBlock1Address }
                .map(RamAssignment::global).distinct()
            val saveBlock2Globals = block.filter { it.value == directory.saveBlock2Address }
                .map(RamAssignment::global).distinct()
            if (saveBlock1Globals.size == 1 && saveBlock2Globals.size == 1 &&
                saveBlock1Globals.single() != saveBlock2Globals.single()
            ) saveBlock1Globals.single() to saveBlock2Globals.single() else null
        }
        return candidates.distinct().singleOrNull()
    }

    private fun decodedRamAssignments(rom: RomImage): List<RamAssignment> {
        val registers = arrayOfNulls<Long>(8)
        val assignments = mutableListOf<RamAssignment>()
        var blockStart = 0
        var offset = 0
        while (offset <= rom.size - 2) {
            val instruction = rom.u16le(offset)
            when {
                instruction and 0xF800 == 0x4800 -> {
                    val destination = (instruction ushr 8) and 7
                    val literalOffset = ((offset + 4) and -4) + (instruction and 0xFF) * 4
                    registers[destination] = if (literalOffset <= rom.size - 4) rom.u32le(literalOffset) else null
                }
                instruction and 0xF800 == 0x2000 -> {
                    registers[(instruction ushr 8) and 7] = (instruction and 0xFF).toLong()
                }
                instruction and 0xFC00 == 0x1800 -> {
                    val destination = instruction and 7
                    val left = registers[(instruction ushr 3) and 7]
                    val immediate = instruction and 0x0400 != 0
                    val right = if (immediate) ((instruction ushr 6) and 7).toLong()
                    else registers[(instruction ushr 6) and 7]
                    val subtract = instruction and 0x0200 != 0
                    registers[destination] = if (left != null && right != null) {
                        if (subtract) left - right else left + right
                    } else null
                }
                instruction and 0xF800 == 0x3000 -> {
                    val register = (instruction ushr 8) and 7
                    registers[register] = registers[register]?.plus(instruction and 0xFF)
                }
                instruction and 0xF800 == 0x3800 -> {
                    val register = (instruction ushr 8) and 7
                    registers[register] = registers[register]?.minus(instruction and 0xFF)
                }
                instruction and 0xF800 == 0x6000 -> {
                    val value = registers[instruction and 7]
                    val base = registers[(instruction ushr 3) and 7]
                    val address = base?.plus(((instruction ushr 6) and 0x1F) * 4L)
                    if (value in EWRAM_START..EWRAM_END && address in IWRAM_START..IWRAM_END) {
                        assignments += RamAssignment(blockStart, requireNotNull(address), requireNotNull(value))
                    }
                }
                instruction and 0xF800 == 0x6800 ||
                    instruction and 0xF800 == 0x7800 ||
                    instruction and 0xF800 == 0x8800 -> {
                    registers[instruction and 7] = null
                }
                instruction and 0xF800 == 0xF000 -> {
                    repeat(4) { registers[it] = null }
                }
                isBlockTerminator(instruction) -> {
                    registers.fill(null)
                    blockStart = offset + 2
                }
            }
            offset += 2
        }
        return assignments
    }

    private fun isBlockTerminator(instruction: Int): Boolean =
        instruction and 0xF000 == 0xD000 ||
            instruction and 0xF800 == 0xE000 ||
            instruction and 0xFF87 == 0x4700 ||
            instruction and 0xFF00 == 0xBD00

    private fun expandedBagArrangements(rom: RomImage): List<ExpandedBagArrangement> = buildList {
        var offset = 0
        while (offset <= rom.size - EXPANDED_BAG_DESCRIPTOR_BYTES) {
            val pointers = LongArray(5)
            val counts = IntArray(5)
            var valid = true
            repeat(5) { index ->
                pointers[index] = rom.u32le(offset + index * 8)
                counts[index] = rom.u32le(offset + index * 8 + 4).toInt()
                if (pointers[index] !in EWRAM_START..EWRAM_END || counts[index] !in 1..MAX_EXPANDED_BAG_CAPACITY) {
                    valid = false
                }
            }
            if (valid && (0 until 4).all { pointers[it + 1] == pointers[it] + counts[it] * ITEM_SLOT_SIZE }) {
                add(ExpandedBagArrangement(pointers.toList(), counts.toList()))
            }
            offset += 4
        }
    }

    private fun resolveGfRomHeader(rom: RomImage): GfRomHeader? {
        val candidates = mutableListOf<GfRomHeader>()
        var offset = 0
        while (offset <= rom.size - GF_ROM_HEADER_SIZE) {
            val version = rom.u32le(offset)
            val language = rom.u32le(offset + 4)
            if (version in 1L..MAX_GAME_VERSION && language in 1L..MAX_GAME_LANGUAGE &&
                validInlineLabel(rom, offset + 8, GF_GAME_NAME_BYTES)
            ) {
                decodeGfRomHeader(rom, offset)?.let(candidates::add)
            }
            offset += 4
        }
        return candidates.singleOrNull()
    }

    private fun decodeGfRomHeader(rom: RomImage, offset: Int): GfRomHeader? {
        if (GF_REQUIRED_POINTER_OFFSETS.any { rom.gbaPointer(offset + it) == null }) return null
        val saveBlock2Size = rom.u32le(offset + 0x88).toInt()
        val saveBlock1Size = rom.u32le(offset + 0x8C).toInt()
        val partyCountOffset = rom.u32le(offset + 0x90).toInt()
        val partyOffset = rom.u32le(offset + 0x94).toInt()
        val trainerIdOffset = rom.u32le(offset + 0x9C).toInt()
        val playerNameOffset = rom.u32le(offset + 0xA0).toInt()
        val playerGenderOffset = rom.u32le(offset + 0xA4).toInt()
        val flagsOffset = rom.u32le(offset + 0x50).toInt()
        val varsOffset = rom.u32le(offset + 0x54).toInt()
        val seen1Offset = rom.u32le(offset + 0x5C).toInt()
        val gameClearFlag = rom.u32le(offset + 0xDC).toInt()
        val playerNameLength = rom.u8(offset + 0x74)
        val bagCounts = (0 until 5).map { rom.u8(offset + 0xE4 + it) }
        val pcItemsCount = rom.u8(offset + 0xE9)
        val pcItemsOffset = rom.u32le(offset + 0xEC).toInt()
        val basicRangesValid = saveBlock2Size in MIN_SAVE_BLOCK2_SIZE..MAX_SAVE_BLOCK2_SIZE &&
            saveBlock1Size in MIN_SAVE_BLOCK1_SIZE..MAX_SAVE_BLOCK1_SIZE &&
            playerNameLength in 1..MAX_PLAYER_NAME_LENGTH &&
            playerNameOffset == 0 && playerGenderOffset == playerNameLength + 1 &&
            trainerIdOffset == playerGenderOffset + 2 &&
            partyCountOffset >= 0 && partyOffset in partyCountOffset + 1..partyCountOffset + MAX_PARTY_ALIGNMENT &&
            partyOffset + PARTY_CAPACITY * PARTY_RECORD_SIZE <= saveBlock1Size &&
            flagsOffset in 0 until saveBlock1Size && varsOffset in (flagsOffset + 1)..saveBlock1Size &&
            seen1Offset in 0 until saveBlock1Size &&
            bagCounts.all { it > 0 } && pcItemsCount > 0 && pcItemsOffset in 0 until saveBlock1Size
        if (!basicRangesValid) return null
        return GfRomHeader(
            saveBlock1Size = saveBlock1Size,
            saveBlock2Size = saveBlock2Size,
            partyOffset = partyOffset,
            trainerIdOffset = trainerIdOffset,
            playerNameOffset = playerNameOffset,
            playerNameLength = playerNameLength,
            playerGenderOffset = playerGenderOffset,
            flagsOffset = flagsOffset,
            varsOffset = varsOffset,
            seen1Offset = seen1Offset,
            gameClearFlag = gameClearFlag,
            bagCounts = bagCounts,
            pcItemsCount = pcItemsCount,
            pcItemsOffset = pcItemsOffset,
        )
    }

    private fun validInlineLabel(rom: RomImage, offset: Int, length: Int): Boolean {
        var terminator = -1
        repeat(length) { index ->
            val value = rom.u8(offset + index)
            if (terminator < 0 && value == 0) terminator = index
            else if (terminator < 0 && value !in ASCII_PRINTABLE_START..ASCII_PRINTABLE_END) return false
            else if (terminator >= 0 && value != 0) return false
        }
        return terminator >= MIN_GF_GAME_NAME_LENGTH
    }

    private const val EWRAM_START = 0x02000000L
    private const val EWRAM_END = 0x0203FFFFL
    private const val IWRAM_START = 0x03000000L
    private const val IWRAM_END = 0x03007FFFL
    private const val POINTER_LOAD_LOOKAHEAD_BYTES = 12
    private const val DIRECT_FIELD_TRACE_BYTES = 40
    private const val MIN_SAVE_BLOCK_POINTER_LOADS = 20
    private const val MIN_SAVE_FIELD_LOADS = 4
    private const val MIN_ADJACENT_SAVE_FIELD_LOADS = 1
    private const val SAVE2_GENDER_OFFSET = 0x08
    private const val SAVE2_TRAINER_ID_OFFSET = 0x0A
    private const val SAVE2_PLAY_HOURS_OFFSET = 0x0E
    private const val SAVE2_PLAY_MINUTES_OFFSET = 0x10
    private const val EMERALD_SAVE_BLOCK2_SIZE = 0x0F2C
    private const val EMERALD_GET_MONEY_BYTES = 14
    private const val THUMB_PC_RELATIVE_LOAD_MASK = 0xF800
    private const val THUMB_PC_RELATIVE_LOAD = 0x4800
    private const val SAVE1_LOCATION_OFFSET = 0x04
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
    private const val GF_ROM_HEADER_SIZE = 0x104
    private const val GF_GAME_NAME_BYTES = 32
    private const val MIN_GF_GAME_NAME_LENGTH = 8
    private const val MAX_GAME_VERSION = 32L
    private const val MAX_GAME_LANGUAGE = 16L
    private const val ASCII_PRINTABLE_START = 0x20
    private const val ASCII_PRINTABLE_END = 0x7E
    private const val MIN_SAVE_BLOCK2_SIZE = 0x100
    private const val MAX_SAVE_BLOCK2_SIZE = 0xFF4
    private const val MIN_SAVE_BLOCK1_SIZE = 0x1000
    private const val MAX_SAVE_BLOCK1_SIZE = 4 * 0xFF4
    private const val MAX_PLAYER_NAME_LENGTH = 15
    private const val MAX_PARTY_ALIGNMENT = 8
    private const val ITEM_SLOT_SIZE = 4
    private const val ENCRYPTION_KEY_SIZE = 4
    private const val BADGE_COUNT = 8
    private const val BADGE_TO_GAME_CLEAR_DELTA = 12
    private const val MAXIMUM_MONEY = 999_999L
    private const val RUBY_SAPPHIRE_SAVE_BLOCK1_SIZE = 0x3AC0
    private const val RUBY_SAPPHIRE_SAVE_BLOCK2_SIZE = 0x0890
    private const val RUBY_SAPPHIRE_LOCAL_TIME_OFFSET = 0x98
    private const val RUBY_SAPPHIRE_BERRY_TIME_OFFSET = 0xA0
    private const val RUBY_SAPPHIRE_PARTY_OFFSET = 0x238
    private const val RUBY_SAPPHIRE_MONEY_OFFSET = 0x490
    private const val RUBY_SAPPHIRE_ITEMS_OFFSET = 0x560
    private const val RUBY_SAPPHIRE_FLAGS_OFFSET = 0x1220
    private const val MIN_DIRECT_SAVE2_BASE_REFERENCES = 100
    private const val MIN_DIRECT_SAVE2_TRAINER_ID_REFERENCES = 4
    private const val MIN_DIRECT_SAVE2_TIME_REFERENCES = 2
    private const val MIN_DIRECT_SAVE2_BERRY_TIME_REFERENCES = 1
    private const val MIN_DIRECT_SAVE1_BASE_REFERENCES = 200
    private const val MIN_DIRECT_SAVE1_LOCATION_REFERENCES = 3
    private const val MIN_DIRECT_SAVE1_PARTY_REFERENCES = 2
    private const val MIN_DIRECT_SAVE1_MONEY_REFERENCES = 8
    private const val MIN_DIRECT_SAVE1_BAG_REFERENCES = 3
    private const val MIN_DIRECT_SAVE1_FLAGS_REFERENCES = 3
    private const val EXPANDED_BAG_DESCRIPTOR_BYTES = 40
    private const val MAX_EXPANDED_BAG_CAPACITY = 1024
    private const val SAVE_CHUNK_ENTRY_BYTES = 8
    private const val CFRU_SECTION_COUNT = 14
    private const val CFRU_DIRECTORY_BYTES = CFRU_SECTION_COUNT * SAVE_CHUNK_ENTRY_BYTES
    private const val CFRU_SECTION_STRIDE = 0xFF0
    private const val CFRU_PARASITE_FRAGMENT_1_SIZE = 0xCCL
    private const val CFRU_PARASITE_FRAGMENT_2_SIZE = 0x258L
    private const val CFRU_PARASITE_SIZE = 0xEC4L
    private const val CFRU_EXTENDED_SAVE_SIZE = 0x2EA4
    private val CFRU_SECTION_SIZES = listOf(
        0xF24,
        0xFF0, 0xFF0, 0xFF0, 0xD98,
        0xFF0, 0xFF0, 0xFF0, 0xFF0, 0xFF0, 0xFF0, 0xFF0, 0xFF0, 0x450,
    )
    private val GF_REQUIRED_POINTER_OFFSETS = listOf(
        0x28, 0x2C, 0x30, 0x34, 0x38, 0x3C, 0x40, 0x44, 0x48, 0x4C,
        0xBC, 0xC0, 0xC4, 0xC8, 0xCC, 0xD0, 0xD4,
    )

    private data class PointerLoad(val offset: Int, val register: Int)
    private data class ResolvedBattleLayout(
        val battleMonsAddress: Long,
        val battleUi: CatalogGen3BattleUiAbi,
    )
    private data class GfRomHeader(
        val saveBlock1Size: Int,
        val saveBlock2Size: Int,
        val partyOffset: Int,
        val trainerIdOffset: Int,
        val playerNameOffset: Int,
        val playerNameLength: Int,
        val playerGenderOffset: Int,
        val flagsOffset: Int,
        val varsOffset: Int,
        val seen1Offset: Int,
        val gameClearFlag: Int,
        val bagCounts: List<Int>,
        val pcItemsCount: Int,
        val pcItemsOffset: Int,
    )
    private data class SaveDirectory(
        val saveBlock2Address: Long,
        val saveBlock1Address: Long,
        val storageAddress: Long,
    )
    private data class RamAssignment(val blockStart: Int, val global: Long, val value: Long)
    private data class ExpandedBagArrangement(val pointers: List<Long>, val counts: List<Int>)
    private data class ExpandedSaveResolution(
        val saveBlock1PointerAddress: Long,
        val saveBlock2PointerAddress: Long,
        val extendedSaveAddress: Long,
        val pockets: List<CatalogGen3BagPocketAbi>,
    )
    private data class DirectSaveResolution(
        val saveBlock1Address: Long,
        val saveBlock2Address: Long,
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
