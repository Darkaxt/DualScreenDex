package com.enrpau.dualscreendex.parser.dataset.moves

import com.enrpau.dualscreendex.parser.catalog.CatalogParser
import com.enrpau.dualscreendex.parser.catalog.MoveRecord
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.RomCapability
import com.enrpau.dualscreendex.parser.model.TableRecordFormat
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** SHA-bound authority for selected-layout-only ordinary Gen III move details. */
class MoveDetailsLiveRomTest {
    @Test fun arcoirisRetainsTheSelectedRetailDetails() = assertResolvedParity(
        "DUALDEX_ARCOIRIS_ROM",
        "fe428c3a45747c9d1466506b5f6d9245e2faf7337660664b6ba3ee28a86ca4ab",
        0x1FB12C,
        355,
        TableRecordFormat.STANDARD,
        354,
        "f76c8f3d098046ea1b8e9052f6256b107a3e015376e93d5509953520d0bd2f2a",
    )

    @Test fun classicRetainsTheSelectedBattleEngineDetails() = assertResolvedParity(
        "DUALDEX_CLASSIC_ROM",
        "01c0177b2498e1842a1bf9ee2ddac145fb95275321bd3813dbf17341d63ad16c",
        0x3581F8,
        755,
        TableRecordFormat.BATTLE_ENGINE_MOVE_20,
        754,
        "8a02c920526513813969b4672cc8b175a9aac5f4e23a49aac9bc3e07e9e2a728",
    )

    @Test fun modernRetainsRetailMoveDetailsAndItsIndependentRulesets() {
        val parsed = parse(
            "DUALDEX_MODERN_EMERALD_ROM",
            "21a0306c4e5b5dc15ca70b74e713e3140612c1045aa298072993a6c5dd8d6895",
        )
        assertResolvedParity(
            parsed,
            0x8D6924,
            369,
            TableRecordFormat.STANDARD,
            368,
            "9ff9279332399e160a20866d18c309062cdcb306567ea65bccf1e23b7014ac19",
        )
        assertEquals(2, requireNotNull(parsed.catalog).learnsetRulesets.size)
    }

    @Test fun celiaPublishesItsCompleteWidenedRetailMoveDetailsWithoutTheAdjacentPointerTail() {
        val parsed = parse(
            "DUALDEX_CELIA_ROM",
            "81ac9b9d4e7bdd3bf06ed53954d784118a743372906c6c6fc62b3cbc19587148",
        )
        val layout = requireNotNull(parsed.layout)
        val selected = requireNotNull(layout.tables.moveData)
        assertEquals(0x73E7EC, selected.offset)
        assertEquals(1189, selected.count)
        assertEquals(16, selected.recordSize)
        assertEquals(TableRecordFormat.WIDENED_RETAIL_MOVE_16, selected.format)

        val catalog = requireNotNull(parsed.catalog)
        assertFalse(catalog.movesById.containsKey(0))
        val typed = requireNotNull(layout.resolvedDatasets.moveDetails)
        assertEquals(MoveDetailsAbi.WIDENED_RETAIL_16, typed.table.abi)
        assertEquals(1189L, typed.table.count)
        val capability = parsed.analysis.capabilities.single { it.capability == RomCapability.MOVE_DETAILS }
        assertEquals(CapabilityStatus.AVAILABLE, capability.status)
        assertTrue(capability.compatible)
        assertEquals(1188, catalog.movesById.size)
        assertFalse(catalog.movesById.containsKey(0))
        assertFalse(catalog.movesById.containsKey(1189))
        assertEquals((1..1188).toSet(), catalog.movesById.keys)
        assertTrue(catalog.movesById.values.all { it.name.value?.any(Char::isLetterOrDigit) == true })
        val pound = catalog.movesById.getValue(1)
        assertEquals(0, pound.typeId.value)
        assertEquals(com.enrpau.dualscreendex.parser.catalog.MoveCategory.PHYSICAL, pound.category.value)
        assertEquals(60, pound.power.value)
        assertEquals(100, pound.accuracy.value)
        assertEquals(35, pound.pp.value)
        assertEquals(0, pound.priority.value)
        assertEquals(0, pound.effectId.value)
        val customSpecial = catalog.movesById.getValue(327)
        assertEquals(33, customSpecial.typeId.value)
        assertEquals(com.enrpau.dualscreendex.parser.catalog.MoveCategory.SPECIAL, customSpecial.category.value)
        assertEquals(85, customSpecial.power.value)
        assertEquals(238, catalog.movesById.getValue(78).accuracy.value)
        assertEquals(30000, catalog.movesById.getValue(638).power.value)
        assertEquals(com.enrpau.dualscreendex.parser.catalog.MoveCategory.UNKNOWN, catalog.movesById.getValue(497).category.value)
        assertEquals(
            "3661acf5995ddc1ba16c0722e10decac17a1f31563e6db89aaa75a7758193105",
            moveSha256(catalog.movesById.values),
        )
        assertNoUnknownLearnsetMoves(catalog.movesById.keys, catalog.speciesById.values.flatMap {
            it.learnset.value.orEmpty().map { entry -> entry.moveId }
        })
        println("MOVE_DETAILS_CELIA typed=true moves=${catalog.movesById.size} adjacentTailRejected=true")
    }

    @Test fun celiaFailsClosedWhenARealActiveRecordViolatesItsAlignedTail() {
        val configured = System.getenv("DUALDEX_CELIA_ROM")
        assumeTrue("set DUALDEX_CELIA_ROM to run this live-ROM regression", !configured.isNullOrBlank())
        val path = Path.of(requireNotNull(configured))
        val bytes = Files.readAllBytes(path)
        assertEquals(
            "81ac9b9d4e7bdd3bf06ed53954d784118a743372906c6c6fc62b3cbc19587148",
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { byte ->
                "%02x".format(byte.toInt() and 0xFF)
            },
        )
        bytes[0x73E7EC + 16 + 14] = 1

        val parsed = CatalogParser.parse(RomImage(bytes))
        val layout = requireNotNull(parsed.layout)
        assertNull(layout.resolvedDatasets.moveDetails)
        val capability = parsed.analysis.capabilities.single { it.capability == RomCapability.MOVE_DETAILS }
        assertEquals(CapabilityStatus.NOT_FOUND, capability.status)
        assertFalse(capability.compatible)
        requireNotNull(parsed.catalog).movesById.values.forEach { move ->
            listOf(move.typeId, move.category, move.power, move.accuracy, move.pp, move.priority, move.effectId)
                .forEach { assertEquals(CapabilityStatus.NOT_FOUND, it.status) }
        }
    }

    @Test fun deltaKeepsItsNameOnlyCatalogWithoutASelectedDetailOutcome() {
        val parsed = parse(
            "DUALDEX_DELTA_EMERALD_ROM",
            "7f4aa1aa68b1df783c3a44b38984640227a5eec22debffbf18db3713de2616bc",
        )
        val layout = requireNotNull(parsed.layout)
        val catalog = requireNotNull(parsed.catalog)
        assertNull(layout.tables.moveData)
        assertNull(layout.resolvedDatasets.moveDetails)
        val capability = parsed.analysis.capabilities.single { it.capability == RomCapability.MOVE_DETAILS }
        assertEquals(CapabilityStatus.NOT_FOUND, capability.status)
        assertTrue(
            capability.reasons.toString(),
            capability.reasons.isNotEmpty() && capability.reasons.any {
                it.contains("plausible", ignoreCase = true) || it.contains("populated", ignoreCase = true)
            },
        )
        assertFalse(capability.reasons.any { it.contains("typed", ignoreCase = true) })
        assertEquals(719, catalog.movesById.size)
        assertFalse(catalog.movesById.containsKey(0))
        assertEquals("Tearful Look", catalog.movesById.getValue(715).name.value)
        catalog.movesById.values.forEach { move ->
            listOf(move.typeId, move.category, move.power, move.accuracy, move.pp, move.priority, move.effectId)
                .forEach { assertEquals(CapabilityStatus.NOT_FOUND, it.status) }
        }
        assertNoUnknownLearnsetMoves(catalog.movesById.keys, catalog.speciesById.values.flatMap {
            it.learnset.value.orEmpty().map { entry -> entry.moveId }
        })
        println("MOVE_DETAILS_DELTA moves=${catalog.movesById.size} typed=false")
    }

    private fun assertResolvedParity(
        environmentVariable: String,
        expectedSha256: String,
        root: Int,
        count: Int,
        format: TableRecordFormat,
        catalogCount: Int,
        semanticSha256: String,
    ) = assertResolvedParity(parse(environmentVariable, expectedSha256), root, count, format, catalogCount, semanticSha256)

    private fun assertResolvedParity(
        parsed: com.enrpau.dualscreendex.parser.catalog.CatalogParseResult,
        root: Int,
        count: Int,
        format: TableRecordFormat,
        catalogCount: Int,
        semanticSha256: String,
    ) {
        val layout = requireNotNull(parsed.layout)
        val selected = requireNotNull(layout.tables.moveData)
        assertEquals(root, selected.offset)
        assertEquals(count, selected.count)
        assertEquals(format, selected.format)
        val typed = requireNotNull(layout.resolvedDatasets.moveDetails)
        assertEquals(root.toLong(), typed.table.offset)
        assertEquals(count.toLong(), typed.table.count)
        assertEquals(format, typed.table.abi.tableRecordFormat)
        val capability = parsed.analysis.capabilities.single { it.capability == RomCapability.MOVE_DETAILS }
        assertEquals(CapabilityStatus.AVAILABLE, capability.status)
        assertTrue(capability.compatible)

        val catalog = requireNotNull(parsed.catalog)
        assertFalse(catalog.movesById.containsKey(0))
        assertEquals(catalogCount, catalog.movesById.size)
        assertEquals((1..catalogCount).toSet(), catalog.movesById.keys)
        assertEquals(typed.catalogDetails().filterKeys { it > 0 }, catalog.movesById.mapValues { (_, move) ->
            CatalogMoveDetails(
                requireNotNull(move.typeId.value),
                requireNotNull(move.category.value),
                requireNotNull(move.power.value),
                requireNotNull(move.accuracy.value),
                requireNotNull(move.pp.value),
                requireNotNull(move.priority.value),
                requireNotNull(move.effectId.value),
            )
        })
        assertEquals(semanticSha256, moveSha256(catalog.movesById.values))
        assertNoUnknownLearnsetMoves(catalog.movesById.keys, catalog.speciesById.values.flatMap {
            it.learnset.value.orEmpty().map { entry -> entry.moveId }
        })
        println("MOVE_DETAILS_PARITY root=0x${root.toString(16)} count=$catalogCount sha256=$semanticSha256 abi=${typed.table.abi}")
    }

    private fun parse(environmentVariable: String, expectedSha256: String): com.enrpau.dualscreendex.parser.catalog.CatalogParseResult {
        val configured = System.getenv(environmentVariable)
        assumeTrue("set $environmentVariable to run this live-ROM regression", !configured.isNullOrBlank())
        val path = Path.of(requireNotNull(configured))
        assumeTrue("live ROM does not exist: $path", Files.isRegularFile(path))
        val rom = RomImage(Files.readAllBytes(path))
        assertEquals(expectedSha256, rom.sha256)
        val parsed = CatalogParser.parse(rom)
        assertNotNull(parsed.layout)
        assertNotNull(parsed.catalog)
        return parsed
    }

    private fun moveSha256(moves: Collection<MoveRecord>): String {
        val payload = moves.sortedBy(MoveRecord::id).joinToString("\n") { move ->
            listOf(
                move.id,
                move.typeId.value,
                move.category.value,
                move.power.value,
                move.accuracy.value,
                move.pp.value,
                move.priority.value,
                move.effectId.value,
            ).joinToString("|")
        }
        return MessageDigest.getInstance("SHA-256").digest(payload.toByteArray()).joinToString("") { byte ->
            "%02x".format(byte.toInt() and 0xFF)
        }
    }

    private fun assertNoUnknownLearnsetMoves(moveIds: Set<Int>, learnedMoveIds: Collection<Int>) {
        assertTrue(learnedMoveIds.none { it !in moveIds })
    }
}
