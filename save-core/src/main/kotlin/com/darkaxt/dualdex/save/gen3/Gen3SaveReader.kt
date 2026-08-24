package com.darkaxt.dualdex.save.gen3

import com.darkaxt.dualdex.save.SaveCapability
import com.darkaxt.dualdex.save.SaveCapabilityEvidence
import com.darkaxt.dualdex.save.SaveCapabilityStatus
import com.darkaxt.dualdex.save.LevelUpRulesetDetectionFingerprint
import com.darkaxt.dualdex.save.SaveParseContext
import com.darkaxt.dualdex.save.SaveParseResult
import com.darkaxt.dualdex.save.SaveSnapshot
import com.darkaxt.dualdex.save.SavedArea
import java.security.MessageDigest

object Gen3SaveReader {
    fun read(bytes: ByteArray, context: SaveParseContext): SaveParseResult {
        if (bytes.size != SAVE_SIZE) return SaveParseResult.Unsupported(listOf("Gen III SaveRAM must be exactly 128 KiB"))
        if (context.speciesById.isEmpty()) return SaveParseResult.Unsupported(listOf("a parsed ROM species index is required"))
        val slots = readCompleteSlots(bytes, context.gen3SaveRuntimeAbi?.extendedSaveDataSize ?: 0)
        val newest = slots.reduceOrNull { current, candidate ->
            if (counterIsNewer(candidate.counter, current.counter)) candidate else current
        } ?: return SaveParseResult.Unsupported(listOf("no complete checksum-valid Gen III save slot was found"))

        val saveBlock2 = newest.sections.getValue(0)
        val saveBlock1 = concatenate(newest.sections, 1..4, newest.layout.sectionStride)
        val storage = concatenate(newest.sections, 5..13, newest.layout.sectionStride)
        val extendedSaveData = context.gen3SaveRuntimeAbi
            ?.extendedSaveDataSize
            ?.takeIf { it > 0 }
            ?.let { reconstructExtendedSave(bytes, newest, it) }
        val effectiveContext = context.gen3SaveRuntimeAbi?.let { abi ->
            context.copy(gen3TextEncoding = abi.textEncoding)
        } ?: context
        val currentArea = readArea(saveBlock1)
        val partyResult = readParty(saveBlock1, effectiveContext)
        val storageResult = readStorage(storage, newest.storageBoxCount, effectiveContext)
        val individuals = partyResult.records + storageResult.records
        val pokedexResult = Gen3PokedexCodec.decode(saveBlock2, context, partyResult.records)
        val pokedex = pokedexResult.value
            ?: return SaveParseResult.Unsupported(pokedexResult.reasons)
        val levelUpRuleset = detectLevelUpRuleset(saveBlock1, context)
        val levelUpRulesetFingerprint = levelUpRuleset.first?.takeIf { levelUpRuleset.second }?.let { id ->
            LevelUpRulesetDetectionFingerprint.create(context.levelUpRulesetSelectors, id)
        }
        val caught = pokedex.caughtDexNumbers
        val seen = pokedex.seenDexNumbers
        val playerState = context.gen3SaveRuntimeAbi?.let { abi ->
            Gen3PlayerStateCodec.decode(
                saveBlock1 = saveBlock1,
                saveBlock2 = saveBlock2,
                abi = abi,
                dexSeen = seen.size,
                dexCaught = caught.size,
                extendedSaveData = extendedSaveData,
            )
        }
        val bagSections = playerState?.bag.orEmpty()
        val availableBag = bagSections.values.mapNotNull { it.value }
        val capabilities = linkedMapOf(
            SaveCapability.SAVE_SLOT to available(SaveCapability.SAVE_SLOT, 14),
            SaveCapability.SEEN to available(SaveCapability.SEEN, seen.size),
            SaveCapability.CAUGHT to available(SaveCapability.CAUGHT, caught.size),
            SaveCapability.CURRENT_AREA to if (currentArea != null) available(SaveCapability.CURRENT_AREA, 1)
                else notFound(SaveCapability.CURRENT_AREA, "saved map group/number were invalid"),
            SaveCapability.PARTY to partyResult.evidence,
            SaveCapability.BOXES to storageResult.evidence,
            SaveCapability.SPECIES to available(SaveCapability.SPECIES, individuals.size),
            SaveCapability.FORM to formEvidence(context, individuals),
            SaveCapability.LEVEL to evidenceForField(SaveCapability.LEVEL, individuals, { it.level != null }),
            SaveCapability.EGG to available(SaveCapability.EGG, individuals.count { it.isEgg }),
            SaveCapability.IVS to evidenceForField(SaveCapability.IVS, individuals, { it.ivs?.size == 6 }),
            SaveCapability.CAPTURE_BALL to evidenceForField(SaveCapability.CAPTURE_BALL, individuals, { it.captureBallId != null }),
            SaveCapability.TRAINER to (
                playerState?.trainer?.let { section ->
                    if (section.value != null) available(SaveCapability.TRAINER, 1)
                    else notFound(SaveCapability.TRAINER, section.reasons.joinToString("; "))
                } ?: notFound(SaveCapability.TRAINER, "typed Gen III Trainer ABI was unavailable")
                ),
            SaveCapability.BAG to bagEvidence(bagSections),
        )
        return SaveParseResult.Parsed(
            SaveSnapshot(
                romIdentity = context.romIdentity,
                saveIdentity = saveIdentity(context.romIdentity, saveBlock2),
                saveGeneration = 3,
                saveCounter = newest.counter,
                currentArea = currentArea,
                seenDexNumbers = seen,
                caughtDexNumbers = caught,
                party = partyResult.records,
                storedIndividuals = storageResult.records,
                capabilities = capabilities,
                detectedLevelUpRulesetId = levelUpRuleset.first,
                levelUpRulesetDetectionResolved = levelUpRuleset.second && levelUpRulesetFingerprint != null,
                levelUpRulesetDetectionFingerprint = levelUpRulesetFingerprint,
                trainer = playerState?.trainer?.value,
                bag = availableBag,
                eventFlagIds = context.gen3SaveRuntimeAbi?.eventFlags?.let { abi ->
                    Gen3EventFlagSnapshot.decode(saveBlock1, abi)
                },
            ),
        )
    }

    private fun bagEvidence(
        sections: Map<com.darkaxt.dualdex.save.BagPocket, SaveSectionResult<com.darkaxt.dualdex.save.BagPocketSnapshot>>,
    ): SaveCapabilityEvidence {
        if (sections.isEmpty()) return notFound(SaveCapability.BAG, "typed Gen III Bag ABI was unavailable")
        val available = sections.values.mapNotNull { it.value }
        val records = available.sumOf { it.entries.size }
        val unavailable = sections.values.filter { it.value == null }
        return when {
            unavailable.isEmpty() -> available(SaveCapability.BAG, records)
            available.isEmpty() -> notFound(
                SaveCapability.BAG,
                unavailable.flatMap { it.reasons }.distinct().joinToString("; "),
            )
            else -> SaveCapabilityEvidence(
                capability = SaveCapability.BAG,
                status = SaveCapabilityStatus.PARTIAL,
                records = records,
                reasons = unavailable.flatMap { it.reasons }.distinct(),
            )
        }
    }

    private fun detectLevelUpRuleset(
        saveBlock1: ByteArray,
        context: SaveParseContext,
    ): Pair<String?, Boolean> {
        val selectors = context.levelUpRulesetSelectors
        if (selectors.isEmpty() || selectors.map { it.rulesetId }.distinct().size != selectors.size) {
            return null to false
        }
        val valid = selectors.all { selector ->
            selector.rulesetId.isNotBlank() &&
                selector.saveBlock1ByteOffset in saveBlock1.indices &&
                selector.mask in 1..0x80 && selector.mask and (selector.mask - 1) == 0 &&
                selector.expectedValue in 0..0xFF && selector.expectedValue and selector.mask == selector.expectedValue
        }
        if (!valid) return null to false
        val matches = selectors.filter { selector ->
            val value = saveBlock1[selector.saveBlock1ByteOffset].toInt() and 0xFF
            value and selector.mask == selector.expectedValue
        }
        return if (matches.size == 1) matches.single().rulesetId to true else null to false
    }

    private fun readCompleteSlots(bytes: ByteArray, expectedExtendedSaveSize: Int): List<Slot> {
        val sectors = (0 until DATA_SECTORS).mapNotNull { physical ->
            val offset = physical * Gen3Checksums.SECTOR_SIZE
            val id = bytes.u16le(offset + ID_OFFSET)
            val checksum = bytes.u16le(offset + CHECKSUM_OFFSET)
            val signature = bytes.u32le(offset + SIGNATURE_OFFSET)
            val counter = bytes.u32le(offset + COUNTER_OFFSET)
            if (signature != Gen3Checksums.SECTOR_SIGNATURE || id !in 0 until SECTIONS_PER_SLOT) return@mapNotNull null
            Sector(id, counter, checksum, bytes.copyOfRange(offset, offset + Gen3Checksums.SECTOR_DATA_SIZE))
        }
        return sectors.groupBy { it.counter }.mapNotNull { (counter, group) ->
            if (group.size != SECTIONS_PER_SLOT || group.map { it.id }.toSet().size != SECTIONS_PER_SLOT) return@mapNotNull null
            val layout = inferSaveSlotLayout(group, expectedExtendedSaveSize > 0) ?: return@mapNotNull null
            val terminal = group.single { it.id == STORAGE_END_SECTION_ID }
            Slot(
                counter,
                layout,
                inferStorageBoxCount(layout, terminal),
                group.associate { it.id to it.data },
            )
        }
    }

    private fun reconstructExtendedSave(bytes: ByteArray, slot: Slot, expectedSize: Int): ByteArray? {
        if (slot.layout !== CFRU_LAYOUT_MARKER) return null
        val parasite = buildList<Byte>() {
            CFRU_PARASITE_SECTIONS.forEach { sectionId ->
                val data = slot.sections.getValue(sectionId)
                val start = slot.layout.sizeFor(sectionId)
                addAll(data.copyOfRange(start, slot.layout.sectionStride).asIterable())
            }
        }.toByteArray()
        val sector30 = readSpecialSector(bytes, 30) ?: return null
        val sector31 = readSpecialSector(bytes, 31) ?: return null
        val result = parasite + sector30 + sector31
        return result.takeIf { it.size == expectedSize }
    }

    private fun readSpecialSector(bytes: ByteArray, physicalSector: Int): ByteArray? {
        val offset = physicalSector * Gen3Checksums.SECTOR_SIZE
        if (offset < 0 || offset + Gen3Checksums.SECTOR_SIZE > bytes.size) return null
        if (bytes.u32le(offset + SIGNATURE_OFFSET) != Gen3Checksums.SECTOR_SIGNATURE) return null
        val data = bytes.copyOfRange(offset, offset + CFRU_SECTION_STRIDE)
        val storedChecksum = bytes.u16le(offset + ID_OFFSET)
        return data.takeIf { Gen3Checksums.sector(it, size = it.size) == storedChecksum }
    }

    private fun inferSaveSlotLayout(sectors: List<Sector>, expectCfru: Boolean): SaveSlotLayout? {
        val cfru = CFRU_LAYOUT_MARKER
        val cfruValid = sectors.all { sector -> sector.checksumMatchesExactly(cfru.sizeFor(sector.id)) }
        val cfruDiscriminated = sectors.any { sector ->
            !sector.checksumMatchesExactly(LEGACY_CHUNK_SIZE) &&
                !sector.checksumMatchesExactly(FULL_CHUNK_SIZE)
        }
        if (cfruValid && (expectCfru || cfruDiscriminated)) return cfru

        val chunkSize = inferChunkSize(sectors) ?: return null
        return SaveSlotLayout(chunkSize, List(SECTIONS_PER_SLOT) { chunkSize })
    }

    private fun inferChunkSize(sectors: List<Sector>): Int? {
        val valid = CHUNK_SIZE_CANDIDATES.filter { chunkSize ->
            sectors.all { sector -> sector.checksumMatches(chunkSize) }
        }
        if (valid.isEmpty()) return null
        if (FULL_CHUNK_SIZE in valid && sectors.any { sector ->
                sector.id !in TERMINAL_SECTION_IDS &&
                    Gen3Checksums.sector(sector.data, size = LEGACY_CHUNK_SIZE) != sector.checksum &&
                    Gen3Checksums.sector(sector.data, size = FULL_CHUNK_SIZE) == sector.checksum
            }
        ) return FULL_CHUNK_SIZE
        return valid.minOrNull()
    }

    private fun Sector.checksumMatches(chunkSize: Int): Boolean {
        if (checksumMatchesExactly(chunkSize)) return true
        if (id !in TERMINAL_SECTION_IDS) return false
        return matchingTerminalPrefix(chunkSize) != null
    }

    private fun Sector.checksumMatchesExactly(size: Int): Boolean =
        size in 4..data.size && size % 4 == 0 && Gen3Checksums.sector(data, size = size) == checksum

    private fun Sector.matchingTerminalPrefix(chunkSize: Int): Int? =
        (MIN_TERMINAL_DATA_SIZE..chunkSize step 4).firstOrNull { size ->
            Gen3Checksums.sector(data, size = size) == checksum &&
                hasZeroRun(data, size, chunkSize)
        }

    private fun inferStorageBoxCount(layout: SaveSlotLayout, terminal: Sector): Int {
        val terminalSize = if (layout === CFRU_LAYOUT_MARKER) {
            layout.sizeFor(STORAGE_END_SECTION_ID)
        } else {
            terminal.matchingTerminalPrefix(layout.sectionStride) ?: layout.sectionStride
        }
        val approximateStorageSize = STORAGE_FULL_SECTIONS * layout.sectionStride + terminalSize
        return ((approximateStorageSize - STORAGE_RECORDS_OFFSET) / BYTES_PER_BOX)
            .coerceIn(MIN_STORAGE_BOXES, MAX_STORAGE_BOXES)
    }

    private fun hasZeroRun(data: ByteArray, start: Int, limit: Int): Boolean {
        val end = (start + TERMINAL_ZERO_RUN).coerceAtMost(limit)
        return end - start == TERMINAL_ZERO_RUN && (start until end).all { data[it].toInt() == 0 }
    }

    private fun counterIsNewer(candidate: Long, current: Long): Boolean {
        val delta = (candidate - current) and 0xFFFF_FFFFL
        return delta in 1..0x7FFF_FFFFL
    }

    private fun readArea(saveBlock1: ByteArray): SavedArea? {
        val group = saveBlock1[LOCATION_OFFSET].toInt() and 0xFF
        val number = saveBlock1[LOCATION_OFFSET + 1].toInt() and 0xFF
        return if (group != 0xFF && number != 0xFF) SavedArea(group, number) else null
    }

    private fun readParty(saveBlock1: ByteArray, context: SaveParseContext): DecodeResult {
        val attempt = PARTY_LAYOUTS.mapNotNull { layout -> readPartyLayout(saveBlock1, context, layout) }
            .maxWithOrNull(
                compareBy<PartyAttempt> { it.count > 0 }
                    .thenBy { it.records.size == it.count }
                    .thenBy { it.records.size }
                    .thenBy { it.count },
            )
            ?: return DecodeResult(
                emptyList(),
                notFound(SaveCapability.PARTY, "no compatible Gen III party layout exposed a count in 0..6"),
            )
        val count = attempt.count
        val records = attempt.records
        val evidence = if (records.size == count) available(SaveCapability.PARTY, records.size)
        else SaveCapabilityEvidence(
            SaveCapability.PARTY,
            SaveCapabilityStatus.PARTIAL,
            records.size,
            listOf("${count - records.size} occupied ${attempt.layout.label} party records failed Pokémon checksum or species validation"),
        )
        return DecodeResult(records, evidence)
    }

    private fun readPartyLayout(
        saveBlock1: ByteArray,
        context: SaveParseContext,
        layout: PartyLayout,
    ): PartyAttempt? {
        if (layout.countOffset !in saveBlock1.indices) return null
        val count = saveBlock1[layout.countOffset].toInt() and 0xFF
        if (count !in 0..6) return null
        val records = (0 until count).mapNotNull { index ->
            val offset = layout.partyOffset + index * Gen3PokemonCodec.PARTY_RECORD_SIZE
            if (offset + Gen3PokemonCodec.PARTY_RECORD_SIZE > saveBlock1.size) return@mapNotNull null
            Gen3PokemonCodec.decode(
                saveBlock1,
                offset,
                "party-$index",
                context,
                partyLevel = saveBlock1[offset + Gen3PokemonCodec.BOX_RECORD_SIZE + 4].toInt() and 0xFF,
            )
        }
        return PartyAttempt(layout, count, records)
    }

    private fun readStorage(storage: ByteArray, boxCount: Int, context: SaveParseContext): DecodeResult {
        val records = mutableListOf<com.darkaxt.dualdex.save.OwnedIndividual>()
        var occupiedCandidates = 0
        val capacity = boxCount * POKEMON_PER_BOX
        repeat(capacity) { index ->
            val offset = STORAGE_RECORDS_OFFSET + index * Gen3PokemonCodec.BOX_RECORD_SIZE
            if (offset + Gen3PokemonCodec.BOX_RECORD_SIZE > storage.size) return@repeat
            val headerFlags = storage[offset + 19].toInt() and 0xFF
            if (headerFlags and 0x02 != 0) {
                occupiedCandidates++
                Gen3PokemonCodec.decode(storage, offset, "box-$index", context)?.let(records::add)
            }
        }
        val evidence = when {
            occupiedCandidates == records.size -> available(SaveCapability.BOXES, records.size)
            records.isNotEmpty() -> SaveCapabilityEvidence(
                SaveCapability.BOXES,
                SaveCapabilityStatus.PARTIAL,
                records.size,
                listOf("${occupiedCandidates - records.size} storage-shaped records failed checksum or species validation"),
            )
            storage.any { it.toInt() != 0 } -> notFound(
                SaveCapability.BOXES,
                "storage data was present but no standard Gen III BoxPokemon record validated",
            )
            else -> available(SaveCapability.BOXES, 0)
        }
        return DecodeResult(records, evidence)
    }

    private fun concatenate(sections: Map<Int, ByteArray>, range: IntRange, chunkSize: Int): ByteArray =
        ByteArray(range.count() * chunkSize).also { joined ->
            range.forEachIndexed { index, section ->
                sections.getValue(section).copyInto(joined, index * chunkSize, 0, chunkSize)
            }
        }

    private fun saveIdentity(romIdentity: String, saveBlock2: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(romIdentity.toByteArray(Charsets.UTF_8))
        digest.update(saveBlock2, TRAINER_ID_OFFSET, TRAINER_ID_SIZE)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun evidenceForField(
        capability: SaveCapability,
        records: List<com.darkaxt.dualdex.save.OwnedIndividual>,
        predicate: (com.darkaxt.dualdex.save.OwnedIndividual) -> Boolean,
    ): SaveCapabilityEvidence {
        val available = records.count(predicate)
        return when {
            records.isEmpty() || available == records.size -> available(capability, available)
            available > 0 -> SaveCapabilityEvidence(
                capability,
                SaveCapabilityStatus.PARTIAL,
                available,
                listOf("${records.size - available} owned records did not expose this field"),
            )
            else -> notFound(capability, "no owned record exposed this field")
        }
    }

    private fun formEvidence(
        context: SaveParseContext,
        records: List<com.darkaxt.dualdex.save.OwnedIndividual>,
    ): SaveCapabilityEvidence {
        val supportsSavedForm = context.speciesById.values.any { it.formId > 0 || it.dexNumber == 201 }
        return if (supportsSavedForm) {
            available(SaveCapability.FORM, records.count { it.formId != null })
        } else {
            SaveCapabilityEvidence(
                SaveCapability.FORM,
                SaveCapabilityStatus.NOT_APPLICABLE,
                reasons = listOf("this ROM catalog has no individual form identity stored in standard Gen III Pokémon records"),
            )
        }
    }

    private fun available(capability: SaveCapability, records: Int) =
        SaveCapabilityEvidence(capability, SaveCapabilityStatus.AVAILABLE, records)

    private fun notFound(capability: SaveCapability, reason: String) =
        SaveCapabilityEvidence(capability, SaveCapabilityStatus.NOT_FOUND, reasons = listOf(reason))

    private data class Sector(val id: Int, val counter: Long, val checksum: Int, val data: ByteArray)
    private data class Slot(
        val counter: Long,
        val layout: SaveSlotLayout,
        val storageBoxCount: Int,
        val sections: Map<Int, ByteArray>,
    )
    private data class SaveSlotLayout(
        val sectionStride: Int,
        val sectionSizes: List<Int>,
    ) {
        fun sizeFor(sectionId: Int): Int = sectionSizes[sectionId]
    }
    private data class DecodeResult(
        val records: List<com.darkaxt.dualdex.save.OwnedIndividual>,
        val evidence: SaveCapabilityEvidence,
    )
    private data class PartyLayout(val label: String, val countOffset: Int, val partyOffset: Int)
    private data class PartyAttempt(
        val layout: PartyLayout,
        val count: Int,
        val records: List<com.darkaxt.dualdex.save.OwnedIndividual>,
    )

    private const val SAVE_SIZE = 128 * 1024
    private const val DATA_SECTORS = 28
    private const val SECTIONS_PER_SLOT = 14
    private const val ID_OFFSET = 0xFF4
    private const val CHECKSUM_OFFSET = 0xFF6
    private const val SIGNATURE_OFFSET = 0xFF8
    private const val COUNTER_OFFSET = 0xFFC
    private const val TRAINER_ID_OFFSET = 0x0A
    private const val TRAINER_ID_SIZE = 4
    private const val LOCATION_OFFSET = 0x04
    private const val STORAGE_RECORDS_OFFSET = 0x04
    private const val LEGACY_CHUNK_SIZE = 3968
    private const val FULL_CHUNK_SIZE = Gen3Checksums.SECTOR_DATA_SIZE
    private const val CFRU_SECTION_STRIDE = 0xFF0
    private const val MIN_TERMINAL_DATA_SIZE = 512
    private const val TERMINAL_ZERO_RUN = 128
    private const val STORAGE_END_SECTION_ID = 13
    private const val STORAGE_FULL_SECTIONS = 8
    private const val BYTES_PER_BOX = 30 * Gen3PokemonCodec.BOX_RECORD_SIZE
    private const val POKEMON_PER_BOX = 30
    private const val MIN_STORAGE_BOXES = 14
    private const val MAX_STORAGE_BOXES = 15
    private val CHUNK_SIZE_CANDIDATES = listOf(LEGACY_CHUNK_SIZE, FULL_CHUNK_SIZE)
    private val CFRU_SECTION_SIZES = listOf(
        0xF24,
        0xFF0, 0xFF0, 0xFF0, 0xD98,
        0xFF0, 0xFF0, 0xFF0, 0xFF0, 0xFF0, 0xFF0, 0xFF0, 0xFF0, 0x450,
    )
    private val CFRU_LAYOUT_MARKER = SaveSlotLayout(CFRU_SECTION_STRIDE, CFRU_SECTION_SIZES)
    private val CFRU_PARASITE_SECTIONS = listOf(0, 4, 13)
    private val TERMINAL_SECTION_IDS = setOf(0, 4, 13)
    private val PARTY_LAYOUTS = listOf(
        PartyLayout("Hoenn", countOffset = 0x234, partyOffset = 0x238),
        PartyLayout("Kanto", countOffset = 0x34, partyOffset = 0x38),
    )
}
