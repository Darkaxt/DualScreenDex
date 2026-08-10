package com.darkaxt.dualdex.save.gen2

import com.darkaxt.dualdex.save.OwnedIndividual
import com.darkaxt.dualdex.save.SaveCapability
import com.darkaxt.dualdex.save.SaveCapabilityEvidence
import com.darkaxt.dualdex.save.SaveCapabilityStatus
import com.darkaxt.dualdex.save.SaveParseContext
import com.darkaxt.dualdex.save.SaveParseResult
import com.darkaxt.dualdex.save.SaveSnapshot
import com.darkaxt.dualdex.save.SavedArea
import java.security.MessageDigest

object Gen2SaveReader {
    fun read(bytes: ByteArray, context: SaveParseContext): SaveParseResult {
        if (bytes.size != SAVE_SIZE) return unsupported("Gen II SaveRAM must be exactly 32 KiB")
        if (context.speciesById.isEmpty()) return unsupported("a parsed ROM species index is required")
        val attempts = LAYOUTS.flatMap { layout ->
            readCopies(bytes, layout).map { copy -> decode(bytes, copy, layout, context) }
        }
        val selected = attempts.maxWithOrNull(
            compareBy<Attempt> { it.party.evidence.status == SaveCapabilityStatus.AVAILABLE }
                .thenBy { it.party.records.size }
                .thenBy { it.boxes.validBoxes }
                .thenBy { it.copy.priority }
                .thenBy { it.layout.priority },
        ) ?: return unsupported("no checksum-valid Gen II primary or backup save block was found")
        val individuals = selected.party.records + selected.boxes.records
        val capabilities = linkedMapOf(
            SaveCapability.SAVE_SLOT to available(
                SaveCapability.SAVE_SLOT,
                1,
                "checksum-valid Gen II ${selected.copy.label} save block",
            ),
            SaveCapability.SEEN to available(SaveCapability.SEEN, selected.seen.size),
            SaveCapability.CAUGHT to available(SaveCapability.CAUGHT, selected.caught.size),
            SaveCapability.PARTY to selected.party.evidence,
            SaveCapability.BOXES to selected.boxes.evidence,
            SaveCapability.CURRENT_AREA to (
                selected.area?.let { available(SaveCapability.CURRENT_AREA, 1) }
                    ?: notFound(SaveCapability.CURRENT_AREA, "saved map group/number were invalid")
                ),
            SaveCapability.SPECIES to available(SaveCapability.SPECIES, individuals.size),
            SaveCapability.FORM to available(SaveCapability.FORM, individuals.count { it.formId != null }),
            SaveCapability.LEVEL to fieldEvidence(SaveCapability.LEVEL, individuals) { it.level != null },
            SaveCapability.EGG to available(SaveCapability.EGG, individuals.count { it.isEgg }),
            SaveCapability.IVS to fieldEvidence(SaveCapability.IVS, individuals) { it.dvs?.size == 5 },
            SaveCapability.CAPTURE_BALL to notApplicable(
                SaveCapability.CAPTURE_BALL,
                "Gen II does not store the original capture ball per Pokémon",
            ),
        )
        return SaveParseResult.Parsed(
            SaveSnapshot(
                romIdentity = context.romIdentity,
                saveIdentity = saveIdentity(context.romIdentity, selected.copy.gameData),
                saveGeneration = 2,
                saveCounter = selected.copy.checksum.toLong(),
                currentArea = selected.area,
                seenDexNumbers = selected.seen,
                caughtDexNumbers = selected.caught,
                party = selected.party.records,
                storedIndividuals = selected.boxes.records,
                capabilities = capabilities,
                schemaId = selected.layout.schemaId,
            ),
        )
    }

    private fun decode(bytes: ByteArray, copy: SaveCopy, layout: Layout, context: SaveParseContext): Attempt {
        val party = decodeParty(copy.gameData, layout, context)
        val boxes = decodeBoxes(bytes, context)
        val caught = decodeFlags(copy.gameData, layout.caughtRelative, DEX_BYTES, context.maximumDexNumber)
        val seen = decodeFlags(copy.gameData, layout.seenRelative, DEX_BYTES, context.maximumDexNumber) + caught
        val group = copy.gameData[layout.mapGroupRelative].u8()
        val map = copy.gameData[layout.mapNumberRelative].u8()
        val area = if (group in 0..63 && map != 0xFF) SavedArea(group, map) else null
        return Attempt(layout, copy, party, boxes, caught, seen, area)
    }

    private fun readCopies(bytes: ByteArray, layout: Layout): List<SaveCopy> = buildList {
        if (validPrimary(bytes, layout)) {
            add(
                SaveCopy(
                    "primary",
                    bytes.copyOfRange(layout.gameStart, layout.gameEnd),
                    bytes.u16le(layout.checksumOffset),
                    priority = 1,
                ),
            )
        }
        when (layout.kind) {
            LayoutKind.CRYSTAL -> readCrystalBackup(bytes, layout)
            LayoutKind.GOLD_SILVER -> readGoldSilverBackup(bytes, layout)
        }?.let(::add)
    }

    private fun validPrimary(bytes: ByteArray, layout: Layout): Boolean =
        bytes[PRIMARY_CHECK_1].u8() == CHECK_VALUE_1 &&
            bytes[layout.check2Offset].u8() == CHECK_VALUE_2 &&
            Gen2Checksums.matches(bytes, layout.gameStart, layout.gameEnd, layout.checksumOffset)

    private fun readCrystalBackup(bytes: ByteArray, layout: Layout): SaveCopy? {
        if (bytes[CRYSTAL_BACKUP_CHECK_1].u8() != CHECK_VALUE_1 ||
            bytes[CRYSTAL_BACKUP_CHECK_2].u8() != CHECK_VALUE_2 ||
            !Gen2Checksums.matches(
                bytes,
                CRYSTAL_BACKUP_GAME_START,
                CRYSTAL_BACKUP_GAME_END,
                CRYSTAL_BACKUP_CHECKSUM,
            )
        ) return null
        val data = bytes.copyOfRange(CRYSTAL_BACKUP_GAME_START, CRYSTAL_BACKUP_GAME_END)
        if (data.size != layout.gameLength) return null
        return SaveCopy("backup", data, bytes.u16le(CRYSTAL_BACKUP_CHECKSUM), priority = 0)
    }

    private fun readGoldSilverBackup(bytes: ByteArray, layout: Layout): SaveCopy? {
        if (bytes[GOLD_BACKUP_CHECK_1].u8() != CHECK_VALUE_1 || bytes[GOLD_BACKUP_CHECK_2].u8() != CHECK_VALUE_2) {
            return null
        }
        val player1 = bytes.copyOfRange(GOLD_BACKUP_PLAYER_1, GOLD_BACKUP_PLAYER_1 + GOLD_PLAYER_1_SIZE)
        val player2 = bytes.copyOfRange(GOLD_BACKUP_PLAYER_2, GOLD_BACKUP_PLAYER_2 + GOLD_PLAYER_2_SIZE)
        val player3 = bytes.copyOfRange(GOLD_BACKUP_PLAYER_3, GOLD_BACKUP_PLAYER_3 + GOLD_PLAYER_3_SIZE)
        val map = bytes.copyOfRange(GOLD_BACKUP_MAP, GOLD_BACKUP_MAP + GOLD_MAP_SIZE)
        val pokemon = bytes.copyOfRange(GOLD_BACKUP_POKEMON, GOLD_BACKUP_POKEMON + GOLD_POKEMON_SIZE)
        val checksum = Gen2Checksums.byteSum16(listOf(pokemon, player3, player1, player2, map))
        if (checksum != bytes.u16le(GOLD_BACKUP_CHECKSUM)) return null
        val gameData = player1 + player2 + player3 + map + pokemon
        if (gameData.size != layout.gameLength) return null
        return SaveCopy("backup", gameData, checksum, priority = 0)
    }

    private fun decodeParty(gameData: ByteArray, layout: Layout, context: SaveParseContext): DecodeResult {
        val offset = layout.partyRelative
        val count = gameData[offset].u8()
        if (count !in 0..PARTY_CAPACITY) {
            return DecodeResult(emptyList(), notFound(SaveCapability.PARTY, "party count was outside 0..6"))
        }
        val records = mutableListOf<OwnedIndividual>()
        repeat(count) { index ->
            val marker = gameData[offset + 1 + index].u8()
            val recordOffset = offset + PARTY_MONS_RELATIVE + index * PARTY_MON_SIZE
            decodeMon(gameData, recordOffset, marker, "party-$index", context)?.let(records::add)
        }
        return DecodeResult(records, collectionEvidence(SaveCapability.PARTY, count, records.size, "party"))
    }

    private fun decodeBoxes(bytes: ByteArray, context: SaveParseContext): BoxDecodeResult {
        val records = mutableListOf<OwnedIndividual>()
        var validBoxes = 0
        repeat(BOX_COUNT) { boxIndex ->
            val offset = boxOffset(boxIndex)
            val decoded = decodeBox(bytes, offset, boxIndex, context)
            if (decoded != null) {
                validBoxes++
                records += decoded
            } else if (bytes.isAll(0xFF, offset, offset + BOX_SIZE)) {
                // Fresh Gen II saves retain erased PC banks until they are used.
                validBoxes++
            }
        }
        val evidence = when {
            validBoxes == BOX_COUNT -> available(SaveCapability.BOXES, records.size)
            validBoxes > 0 -> SaveCapabilityEvidence(
                SaveCapability.BOXES,
                SaveCapabilityStatus.PARTIAL,
                records.size,
                listOf("${BOX_COUNT - validBoxes} Gen II boxes failed structural validation"),
            )
            else -> notFound(SaveCapability.BOXES, "no Gen II PC box passed structural validation")
        }
        return BoxDecodeResult(records, evidence, validBoxes)
    }

    private fun decodeBox(
        bytes: ByteArray,
        offset: Int,
        boxIndex: Int,
        context: SaveParseContext,
    ): List<OwnedIndividual>? {
        val count = bytes[offset].u8()
        if (count !in 0..BOX_CAPACITY) return null
        if (count == 0 && bytes[offset + 1].u8() !in setOf(0, 0xFF)) return null
        if (count > 0 && bytes[offset + 1 + count].u8() != 0xFF) return null
        return (0 until count).mapNotNull { index ->
            decodeMon(
                bytes,
                offset + BOX_MONS_RELATIVE + index * BOX_MON_SIZE,
                bytes[offset + 1 + index].u8(),
                "box-${boxIndex + 1}-$index",
                context,
            )
        }.takeIf { it.size == count }
    }

    private fun decodeMon(
        bytes: ByteArray,
        offset: Int,
        speciesMarker: Int,
        stableLocation: String,
        context: SaveParseContext,
    ): OwnedIndividual? {
        if (offset + BOX_MON_SIZE > bytes.size) return null
        val species = bytes[offset].u8()
        val isEgg = speciesMarker == EGG_MARKER
        if (species !in context.speciesById || (!isEgg && speciesMarker != species)) return null
        val dvs = decodeDvs(bytes[offset + DVS_RELATIVE].u8(), bytes[offset + DVS_RELATIVE + 1].u8())
        val level = bytes[offset + LEVEL_RELATIVE].u8().takeIf { it in 1..100 }
        return OwnedIndividual(
            stableLocation = stableLocation,
            speciesId = species,
            formId = if (context.speciesById[species]?.dexNumber == UNOWN_DEX) unownForm(dvs) else null,
            level = level,
            isEgg = isEgg,
            dvs = dvs,
        )
    }

    private fun decodeDvs(first: Int, second: Int): List<Int> {
        val attack = first ushr 4
        val defense = first and 0x0F
        val speed = second ushr 4
        val special = second and 0x0F
        val hp = ((attack and 1) shl 3) or ((defense and 1) shl 2) or ((speed and 1) shl 1) or (special and 1)
        return listOf(hp, attack, defense, speed, special)
    }

    private fun unownForm(dvs: List<Int>): Int {
        val attack = dvs[1]
        val defense = dvs[2]
        val speed = dvs[3]
        val special = dvs[4]
        return (((attack and 6) shl 5) or ((defense and 6) shl 3) or ((speed and 6) shl 1) or
            ((special and 6) ushr 1)) / 10
    }

    private fun decodeFlags(bytes: ByteArray, offset: Int, byteCount: Int, maximumDex: Int): Set<Int> = buildSet {
        for (dex in 1..minOf(maximumDex, byteCount * 8)) {
            val index = dex - 1
            if (bytes[offset + index / 8].u8() and (1 shl (index % 8)) != 0) add(dex)
        }
    }

    private fun boxOffset(index: Int): Int =
        (if (index < BOXES_PER_BANK) FIRST_BOX_BANK else SECOND_BOX_BANK) + (index % BOXES_PER_BANK) * BOX_SIZE

    private fun saveIdentity(romIdentity: String, gameData: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(romIdentity.toByteArray(Charsets.UTF_8))
        digest.update(gameData, 0, PLAYER_ID_SIZE)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun collectionEvidence(capability: SaveCapability, expected: Int, actual: Int, label: String) = when {
        expected == actual -> available(capability, actual)
        actual > 0 -> SaveCapabilityEvidence(
            capability,
            SaveCapabilityStatus.PARTIAL,
            actual,
            listOf("${expected - actual} occupied $label records failed species validation"),
        )
        else -> notFound(capability, "occupied $label records failed species validation")
    }

    private fun fieldEvidence(capability: SaveCapability, records: List<OwnedIndividual>, test: (OwnedIndividual) -> Boolean): SaveCapabilityEvidence {
        val count = records.count(test)
        return when {
            records.isEmpty() || count == records.size -> available(capability, count)
            count > 0 -> SaveCapabilityEvidence(
                capability,
                SaveCapabilityStatus.PARTIAL,
                count,
                listOf("${records.size - count} owned records did not expose this field"),
            )
            else -> notFound(capability, "no owned record exposed this field")
        }
    }

    private fun available(capability: SaveCapability, records: Int, reason: String? = null) = SaveCapabilityEvidence(
        capability,
        SaveCapabilityStatus.AVAILABLE,
        records,
        reason?.let(::listOf).orEmpty(),
    )

    private fun notFound(capability: SaveCapability, reason: String) =
        SaveCapabilityEvidence(capability, SaveCapabilityStatus.NOT_FOUND, reasons = listOf(reason))

    private fun notApplicable(capability: SaveCapability, reason: String) =
        SaveCapabilityEvidence(capability, SaveCapabilityStatus.NOT_APPLICABLE, reasons = listOf(reason))

    private fun unsupported(reason: String) = SaveParseResult.Unsupported(listOf(reason))
    private fun Byte.u8() = toInt() and 0xFF
    private fun ByteArray.isAll(value: Int, start: Int, endExclusive: Int): Boolean =
        (start until endExclusive).all { this[it].u8() == value }

    private data class SaveCopy(
        val label: String,
        val gameData: ByteArray,
        val checksum: Int,
        val priority: Int,
    )
    private data class DecodeResult(val records: List<OwnedIndividual>, val evidence: SaveCapabilityEvidence)
    private data class BoxDecodeResult(
        val records: List<OwnedIndividual>,
        val evidence: SaveCapabilityEvidence,
        val validBoxes: Int,
    )
    private data class Attempt(
        val layout: Layout,
        val copy: SaveCopy,
        val party: DecodeResult,
        val boxes: BoxDecodeResult,
        val caught: Set<Int>,
        val seen: Set<Int>,
        val area: SavedArea?,
    )
    private enum class LayoutKind { GOLD_SILVER, CRYSTAL }
    private data class Layout(
        val kind: LayoutKind,
        val schemaId: String,
        val gameStart: Int,
        val gameEnd: Int,
        val checksumOffset: Int,
        val check2Offset: Int,
        val mapGroupRelative: Int,
        val mapNumberRelative: Int,
        val partyRelative: Int,
        val caughtRelative: Int,
        val seenRelative: Int,
        val priority: Int,
    ) {
        val gameLength get() = gameEnd - gameStart
    }

    private const val SAVE_SIZE = 0x8000
    private const val CHECK_VALUE_1 = 99
    private const val CHECK_VALUE_2 = 127
    private const val PRIMARY_CHECK_1 = 0x2008
    private const val DEX_BYTES = 32
    private const val PLAYER_ID_SIZE = 2
    private const val PARTY_CAPACITY = 6
    private const val BOX_CAPACITY = 20
    private const val BOX_COUNT = 14
    private const val BOXES_PER_BANK = 7
    private const val FIRST_BOX_BANK = 0x4000
    private const val SECOND_BOX_BANK = 0x6000
    private const val BOX_SIZE = 0x450
    private const val PARTY_MONS_RELATIVE = 8
    private const val PARTY_MON_SIZE = 48
    private const val BOX_MONS_RELATIVE = 22
    private const val BOX_MON_SIZE = 32
    private const val DVS_RELATIVE = 21
    private const val LEVEL_RELATIVE = 31
    private const val EGG_MARKER = 0xFD
    private const val UNOWN_DEX = 201

    private const val CRYSTAL_BACKUP_CHECK_1 = 0x1208
    private const val CRYSTAL_BACKUP_GAME_START = 0x1209
    private const val CRYSTAL_BACKUP_GAME_END = 0x1D83
    private const val CRYSTAL_BACKUP_CHECKSUM = 0x1F0D
    private const val CRYSTAL_BACKUP_CHECK_2 = 0x1F0F

    private const val GOLD_BACKUP_PLAYER_3 = 0x0C6B
    private const val GOLD_BACKUP_POKEMON = 0x10E8
    private const val GOLD_BACKUP_PLAYER_1 = 0x15C7
    private const val GOLD_BACKUP_PLAYER_2 = 0x3D96
    private const val GOLD_BACKUP_CHECK_1 = 0x7E38
    private const val GOLD_BACKUP_MAP = 0x7E39
    private const val GOLD_BACKUP_CHECKSUM = 0x7E6D
    private const val GOLD_BACKUP_CHECK_2 = 0x7E6F
    private const val GOLD_PLAYER_1_SIZE = 0x226
    private const val GOLD_PLAYER_2_SIZE = 0x1AA
    private const val GOLD_PLAYER_3_SIZE = 0x47D
    private const val GOLD_MAP_SIZE = 0x34
    private const val GOLD_POKEMON_SIZE = 0x4DF

    private val LAYOUTS = listOf(
        Layout(
            kind = LayoutKind.GOLD_SILVER,
            schemaId = "gen2-gold-silver-v1",
            gameStart = 0x2009,
            gameEnd = 0x2D69,
            checksumOffset = 0x2D69,
            check2Offset = 0x2D6B,
            mapGroupRelative = 0x85F,
            mapNumberRelative = 0x860,
            partyRelative = 0x881,
            caughtRelative = 0xA43,
            seenRelative = 0xA63,
            priority = 0,
        ),
        Layout(
            kind = LayoutKind.CRYSTAL,
            schemaId = "gen2-crystal-v1",
            gameStart = 0x2009,
            gameEnd = 0x2B83,
            checksumOffset = 0x2D0D,
            check2Offset = 0x2D0F,
            mapGroupRelative = 0x83A,
            mapNumberRelative = 0x83B,
            partyRelative = 0x85C,
            caughtRelative = 0xA1E,
            seenRelative = 0xA3E,
            priority = 1,
        ),
    )
}
