package com.darkaxt.dualdex.save.gen1

import com.darkaxt.dualdex.save.OwnedIndividual
import com.darkaxt.dualdex.save.SaveCapability
import com.darkaxt.dualdex.save.SaveCapabilityEvidence
import com.darkaxt.dualdex.save.SaveCapabilityStatus
import com.darkaxt.dualdex.save.SaveParseContext
import com.darkaxt.dualdex.save.SaveParseResult
import com.darkaxt.dualdex.save.SaveSnapshot
import com.darkaxt.dualdex.save.SavedArea
import java.security.MessageDigest

object Gen1SaveReader {
    fun read(bytes: ByteArray, context: SaveParseContext): SaveParseResult {
        if (bytes.size != SAVE_SIZE) return unsupported("Gen I SaveRAM must be exactly 32 KiB")
        if (context.speciesById.isEmpty()) return unsupported("a parsed ROM species index is required")
        if (!Gen1Checksums.matches(bytes, MAIN_START, MAIN_END, MAIN_CHECKSUM)) {
            return unsupported("the Gen I main save checksum did not validate")
        }

        val party = decodeParty(bytes, context)
        val boxes = decodeBoxes(bytes, context)
        val individuals = party.records + boxes.records
        val caught = decodeFlags(bytes, OWNED_OFFSET, DEX_BYTES, context.maximumDexNumber)
        val seen = decodeFlags(bytes, SEEN_OFFSET, DEX_BYTES, context.maximumDexNumber) + caught
        val map = bytes[MAP_OFFSET].u8().takeUnless { it == 0xFF }?.let { SavedArea(0, it) }
        val checksum = bytes[MAIN_CHECKSUM].u8()
        val capabilities = linkedMapOf(
            SaveCapability.SAVE_SLOT to available(
                SaveCapability.SAVE_SLOT,
                1,
                "checksum-valid Gen I main save block",
            ),
            SaveCapability.SEEN to available(SaveCapability.SEEN, seen.size),
            SaveCapability.CAUGHT to available(SaveCapability.CAUGHT, caught.size),
            SaveCapability.PARTY to party.evidence,
            SaveCapability.BOXES to boxes.evidence,
            SaveCapability.CURRENT_AREA to (
                map?.let { available(SaveCapability.CURRENT_AREA, 1) }
                    ?: notFound(SaveCapability.CURRENT_AREA, "saved map id was 0xff")
                ),
            SaveCapability.SPECIES to available(SaveCapability.SPECIES, individuals.size),
            SaveCapability.FORM to notApplicable(SaveCapability.FORM, "Gen I has no saved form identity"),
            SaveCapability.LEVEL to fieldEvidence(SaveCapability.LEVEL, individuals) { it.level != null },
            SaveCapability.EGG to notApplicable(SaveCapability.EGG, "Gen I has no eggs"),
            SaveCapability.IVS to fieldEvidence(SaveCapability.IVS, individuals) { it.dvs?.size == 5 },
            SaveCapability.CAPTURE_BALL to notApplicable(
                SaveCapability.CAPTURE_BALL,
                "Gen I does not store the original capture ball per Pokémon",
            ),
        )
        return SaveParseResult.Parsed(
            SaveSnapshot(
                romIdentity = context.romIdentity,
                saveIdentity = saveIdentity(context.romIdentity, bytes),
                saveGeneration = 1,
                saveCounter = checksum.toLong(),
                currentArea = map,
                seenDexNumbers = seen,
                caughtDexNumbers = caught,
                party = party.records,
                storedIndividuals = boxes.records,
                capabilities = capabilities,
                schemaId = "gen1-v1",
                eventFlagIds = decodeZeroBasedFlags(bytes, HIDDEN_ITEM_FLAGS_OFFSET, HIDDEN_ITEM_FLAGS_BYTES),
            ),
        )
    }

    private fun decodeParty(bytes: ByteArray, context: SaveParseContext): DecodeResult {
        val count = bytes[PARTY_OFFSET].u8()
        if (count !in 0..PARTY_CAPACITY) {
            return DecodeResult(emptyList(), notFound(SaveCapability.PARTY, "party count was outside 0..6"))
        }
        val records = mutableListOf<OwnedIndividual>()
        repeat(count) { index ->
            val listedSpecies = bytes[PARTY_OFFSET + 1 + index].u8()
            val recordOffset = PARTY_MONS_OFFSET + index * PARTY_MON_SIZE
            decodeMon(bytes, recordOffset, listedSpecies, party = true, "party-$index", context)?.let(records::add)
        }
        return DecodeResult(records, collectionEvidence(SaveCapability.PARTY, count, records.size, "party"))
    }

    private fun decodeBoxes(bytes: ByteArray, context: SaveParseContext): DecodeResult {
        val currentBox = bytes[CURRENT_BOX_OFFSET].u8() and 0x7F
        val records = mutableListOf<OwnedIndividual>()
        var validBoxes = 0
        repeat(BOX_COUNT) { boxIndex ->
            val canonical = boxOffset(boxIndex)
            val bankStart = if (boxIndex < BOXES_PER_BANK) FIRST_BOX_BANK else SECOND_BOX_BANK
            val bankChecksum = bankStart + BOXES_PER_BANK * BOX_SIZE
            val validCanonical = Gen1Checksums.matches(bytes, bankStart, bankChecksum, bankChecksum) ||
                Gen1Checksums.matches(
                    bytes,
                    canonical,
                    canonical + BOX_SIZE,
                    bankChecksum + 1 + boxIndex % BOXES_PER_BANK,
                )
            val preferredOffset = when {
                boxIndex == currentBox && validBoxHeader(bytes, CURRENT_BOX_DATA_OFFSET, context) -> CURRENT_BOX_DATA_OFFSET
                validCanonical -> canonical
                else -> null
            }
            if (preferredOffset != null) {
                val decoded = decodeBox(bytes, preferredOffset, boxIndex, context)
                if (decoded != null) {
                    validBoxes++
                    records += decoded
                }
            } else if (bytes.isAll(0xFF, canonical, canonical + BOX_SIZE)) {
                // A newly created Gen I save leaves unopened PC box banks erased.
                // The checksum-valid main block plus the current-box copy makes
                // those erased records safely interpretable as empty boxes.
                validBoxes++
            }
        }
        val evidence = when {
            validBoxes == BOX_COUNT -> available(SaveCapability.BOXES, records.size)
            validBoxes > 0 -> SaveCapabilityEvidence(
                SaveCapability.BOXES,
                SaveCapabilityStatus.PARTIAL,
                records.size,
                listOf("${BOX_COUNT - validBoxes} Gen I boxes failed checksum or structural validation"),
            )
            else -> notFound(SaveCapability.BOXES, "no Gen I PC box passed checksum and structural validation")
        }
        return DecodeResult(records, evidence)
    }

    private fun validBoxHeader(bytes: ByteArray, offset: Int, context: SaveParseContext): Boolean {
        val count = bytes[offset].u8()
        if (count !in 0..BOX_CAPACITY) return false
        if (count == 0) return bytes[offset + 1].u8() in setOf(0, 0xFF)
        if (bytes[offset + 1 + count].u8() != 0xFF) return false
        return (0 until count).all { bytes[offset + 1 + it].u8() in context.speciesById }
    }

    private fun decodeBox(
        bytes: ByteArray,
        offset: Int,
        boxIndex: Int,
        context: SaveParseContext,
    ): List<OwnedIndividual>? {
        if (!validBoxHeader(bytes, offset, context)) return null
        val count = bytes[offset].u8()
        return (0 until count).mapNotNull { index ->
            val listedSpecies = bytes[offset + 1 + index].u8()
            decodeMon(
                bytes,
                offset + BOX_MONS_RELATIVE + index * BOX_MON_SIZE,
                listedSpecies,
                party = false,
                stableLocation = "box-${boxIndex + 1}-$index",
                context = context,
            )
        }.takeIf { it.size == count }
    }

    private fun decodeMon(
        bytes: ByteArray,
        offset: Int,
        listedSpecies: Int,
        party: Boolean,
        stableLocation: String,
        context: SaveParseContext,
    ): OwnedIndividual? {
        if (listedSpecies !in context.speciesById || offset + (if (party) PARTY_MON_SIZE else BOX_MON_SIZE) > bytes.size) {
            return null
        }
        val species = bytes[offset].u8()
        if (species != listedSpecies) return null
        val level = bytes[offset + if (party) PARTY_LEVEL_RELATIVE else BOX_LEVEL_RELATIVE].u8()
            .takeIf { it in 1..100 }
        val dvs = decodeDvs(bytes[offset + DVS_RELATIVE].u8(), bytes[offset + DVS_RELATIVE + 1].u8())
        return OwnedIndividual(
            stableLocation = stableLocation,
            speciesId = species,
            level = level,
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

    private fun decodeFlags(bytes: ByteArray, offset: Int, byteCount: Int, maximumDex: Int): Set<Int> = buildSet {
        for (dex in 1..minOf(maximumDex, byteCount * 8)) {
            val index = dex - 1
            if (bytes[offset + index / 8].u8() and (1 shl (index % 8)) != 0) add(dex)
        }
    }

    private fun decodeZeroBasedFlags(bytes: ByteArray, offset: Int, byteCount: Int): Set<Int> = buildSet {
        repeat(byteCount) { byteIndex ->
            val value = bytes[offset + byteIndex].u8()
            repeat(Byte.SIZE_BITS) { bitIndex ->
                if (value and (1 shl bitIndex) != 0) add(byteIndex * Byte.SIZE_BITS + bitIndex)
            }
        }
    }

    private fun boxOffset(index: Int): Int =
        (if (index < BOXES_PER_BANK) FIRST_BOX_BANK else SECOND_BOX_BANK) + (index % BOXES_PER_BANK) * BOX_SIZE

    private fun saveIdentity(romIdentity: String, bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(romIdentity.toByteArray(Charsets.UTF_8))
        digest.update(bytes, PLAYER_NAME_OFFSET, PLAYER_NAME_SIZE)
        digest.update(bytes, PLAYER_ID_OFFSET, PLAYER_ID_SIZE)
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

    private data class DecodeResult(val records: List<OwnedIndividual>, val evidence: SaveCapabilityEvidence)

    private const val SAVE_SIZE = 0x8000
    private const val MAIN_START = 0x2598
    private const val MAIN_END = 0x3523
    private const val MAIN_CHECKSUM = 0x3523
    private const val PLAYER_NAME_OFFSET = 0x2598
    private const val PLAYER_NAME_SIZE = 11
    private const val PLAYER_ID_OFFSET = 0x2605
    private const val PLAYER_ID_SIZE = 2
    private const val OWNED_OFFSET = 0x25A3
    private const val SEEN_OFFSET = 0x25B6
    private const val DEX_BYTES = 19
    private const val MAP_OFFSET = 0x260A
    private const val CURRENT_BOX_OFFSET = 0x284C
    private const val HIDDEN_ITEM_FLAGS_OFFSET = 0x2B30
    private const val HIDDEN_ITEM_FLAGS_BYTES = 14
    private const val PARTY_OFFSET = 0x2F2C
    private const val PARTY_MONS_OFFSET = 0x2F34
    private const val CURRENT_BOX_DATA_OFFSET = 0x30C0
    private const val FIRST_BOX_BANK = 0x4000
    private const val SECOND_BOX_BANK = 0x6000
    private const val BOX_SIZE = 0x462
    private const val BOXES_PER_BANK = 6
    private const val BOX_COUNT = 12
    private const val PARTY_CAPACITY = 6
    private const val BOX_CAPACITY = 20
    private const val PARTY_MON_SIZE = 44
    private const val BOX_MON_SIZE = 33
    private const val BOX_MONS_RELATIVE = 22
    private const val BOX_LEVEL_RELATIVE = 3
    private const val PARTY_LEVEL_RELATIVE = 33
    private const val DVS_RELATIVE = 27
}
