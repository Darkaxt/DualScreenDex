package com.enrpau.dualscreendex.parser.dataset.descriptions

import com.enrpau.dualscreendex.parser.catalog.CatalogParser
import com.enrpau.dualscreendex.parser.catalog.DescriptionRecord
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.detect.RomHeaderReader
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.RomCapability
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Real-ROM characterization for the ordinary Gen III typed-description cutover. */
class DescriptionLiveRomTest {
    @Test fun celiaPublishesItsCompiledPokedexEntryDescriptions() {
        val configured = System.getenv("DUALDEX_CELIA_ROM")
        assumeTrue("set DUALDEX_CELIA_ROM to run this live-ROM regression", !configured.isNullOrBlank())
        val path = Path.of(requireNotNull(configured))
        assumeTrue("live ROM does not exist: $path", Files.isRegularFile(path))
        val rom = RomImage(Files.readAllBytes(path))
        assertEquals(
            "81ac9b9d4e7bdd3bf06ed53954d784118a743372906c6c6fc62b3cbc19587148",
            rom.sha256,
        )

        val session = RomAnalysisSession(rom, RomHeaderReader.read(rom))
        val direct = DescriptionCodec().decode(
            session,
            DescriptionTableLayout(0xCA6B70, 386, 36, listOf(16)),
        ) as DescriptionTableOutcome.Decoded
        assertEquals(384, direct.rows.count { it is DescriptionRowOutcome.Decoded })
        assertEquals(1, direct.rows.count { it is DescriptionRowOutcome.StructuralEmpty })
        assertEquals(1, direct.rows.count { it is DescriptionRowOutcome.Malformed })
        val reference = requireNotNull(session.gbaReferenceIndex?.target(0xCA6B70))
        assertEquals(8, reference.count)
        val resolution = DescriptionResolver().resolve(session, 386)
        assertTrue(
            resolution.toString(),
            resolution is com.enrpau.dualscreendex.parser.resolution.DatasetResolution.Partial,
        )
        assertEquals(
            DescriptionTableLayout(0xCA6B70, 386, 36, listOf(16)),
            (resolution as com.enrpau.dualscreendex.parser.resolution.DatasetResolution.Partial).candidate.layout.table,
        )

        val parsed = CatalogParser.parse(rom)
        val layout = requireNotNull(parsed.layout)
        val catalogSpecies = requireNotNull(parsed.catalog).speciesById.values
        val selected = requireNotNull(layout.tables.descriptions)
        assertEquals(0xCA6B70, selected.offset)
        assertEquals(386, selected.count)
        assertEquals(36, selected.recordSize)
        assertEquals(listOf(16), selected.pointerOffsets)
        val capability = parsed.analysis.capabilities.single {
            it.capability == RomCapability.POKEDEX_DESCRIPTIONS
        }
        assertEquals(CapabilityStatus.PARTIAL, capability.status)
        assertEquals(382, capability.coveredRecords)
        assertEquals(384, capability.expectedRecords)
        assertEquals(2, capability.incompleteRecords)

        val typed = requireNotNull(layout.resolvedDatasets.descriptions).catalogDescriptions()
        val navigableSpecies = catalogSpecies.filter { (it.dexNumber.value ?: 0) > 0 }
        assertEquals(384, typed.size)
        assertEquals("UNKNOWN", typed.getValue(0).category)
        assertEquals("FIRST", typed.getValue(2).category)
        assertEquals(setOf(154, 197), (0 until 386).filterNot(typed::containsKey).toSet())
        assertEquals(411, navigableSpecies.size)
        assertEquals(411, navigableSpecies.maxOf { it.dexNumber.value ?: 0 })
        assertEquals(
            setOf(
                40, 251, 252, 253, 254, 255, 256, 257, 258, 259, 260, 261, 262, 263,
                264, 265, 266, 267, 268, 269, 270, 271, 272, 273, 274, 275, 276, 408,
            ),
            navigableSpecies
                .filter { it.description.value == null }
                .mapTo(linkedSetOf()) { it.id },
        )
        assertEquals(383, navigableSpecies.count { it.description.value != null })
        assertEquals(
            "d62c951fc4ddbf35f3a251ea9b26c4e1664c1ff2b957601db800d73ecbfedad7",
            descriptionSha256(typed),
        )
    }

    @Test fun aGrandDayOutThirtySixByteRowsHaveExactTypedPayloadParity() = assertCodecParity(
        "DUALDEX_A_GRAND_DAY_OUT_ROM",
        "2005275fc54ae63f3d1bc50c49980e87dcd9ecae5e4733d322bb2a2c99270916",
        expectedCount = 387,
        expectedPayloadSha256 = "dcfdf7b7300bc580c6446b4ec39a982f48379c11b3ec18a95bd7d809103956bd",
        expectedRecordSize = 36,
    )

    @Test fun allInThirtyTwoByteRowsHaveExactTypedPayloadParity() = assertCodecParity(
        "DUALDEX_ALL_IN_ROM",
        "baf1bad15fd25fa8103d53021991bdadb64c142f8108efd29c14cd01ba069905",
        expectedCount = 387,
        expectedPayloadSha256 = "3c8049c4004788ba9f6172deb060bf2a2a159b2e19d36b4ed7fca794a5a698ee",
        expectedRecordSize = 32,
    )

    @Test fun alteredEmeraldUnreferencedStructuralRowsHaveExactTypedPayloadParity() = assertCodecParity(
        "DUALDEX_ALTERED_EMERALD_ROM",
        "8fe93d8245c96ea5aa49d61df2c74ee99a439b15cde7c0afa4f0b5a87aac34f0",
        expectedCount = 387,
        expectedPayloadSha256 = "4924b9c9a6fe8ffe9b604116ccae21c320f30f15c539cdc0aeebd98cf4b70c10",
    )

    @Test fun cloudWhiteTwoPartialRowsHaveExactTypedPayloadParity() = assertCodecParity(
        "DUALDEX_CLOUD_WHITE_2_ROM",
        "6d9075a559c289eee4f336c925b46fdba55f34c6baa0576626d4a3b71513d879",
        expectedCount = 944,
        expectedPayloadSha256 = "9a798dd96ca13a2acb9ce059491566fe0d145984f5344a9a63105b107b7634ab",
    )

    @Test fun cloverRowsHaveExactTypedPayloadParity() = assertCodecParity(
        "DUALDEX_CLOVER_ROM",
        "42f99abd548934d77999ac3eb563fb9bc70a34701d37a262b21b882a43a8bdd9",
        expectedCount = 387,
        expectedPayloadSha256 = "54c6967047c53b1fa9ed0accad4c5aa1ab651b25be61773e953688ba7e8cc2ef",
    )

    private fun assertCodecParity(
        environmentVariable: String,
        expectedSha256: String,
        expectedCount: Int,
        expectedPayloadSha256: String,
        expectedRecordSize: Int? = null,
    ) {
        val configured = System.getenv(environmentVariable)
        assumeTrue("set $environmentVariable to run this live-ROM regression", !configured.isNullOrBlank())
        val path = Path.of(requireNotNull(configured))
        assumeTrue("live ROM does not exist: $path", Files.isRegularFile(path))
        val rom = RomImage(Files.readAllBytes(path))
        assertEquals(expectedSha256, rom.sha256)
        val parsed = CatalogParser.parse(rom)
        val layout = requireNotNull(parsed.layout)
        val catalog = requireNotNull(parsed.catalog)
        assertEquals(3, layout.generation)
        assertNull("expansion descriptions remain on their characterized path", layout.pokeemeraldExpansion)
        val selected = requireNotNull(layout.tables.descriptions)
        expectedRecordSize?.let { assertEquals(it, selected.recordSize) }
        val pointerOffsets = selected.pointerOffsets.ifEmpty {
            if (selected.recordSize >= 36) listOf(16, 20) else listOf(16)
        }
        val propagated = requireNotNull(layout.resolvedDatasets.descriptions)
        val typed = propagated.catalogDescriptions()

        assertEquals(expectedCount, typed.size)
        assertEquals(expectedPayloadSha256, descriptionSha256(typed))
        catalog.speciesById.values.forEach { species ->
            val expected = species.dexNumber.value?.let(typed::get)
            assertEquals(expected?.text, species.description.value)
            assertEquals(expected?.height, species.height.value)
            assertEquals(expected?.weight, species.weight.value)
        }
        assertTrue(typed.isNotEmpty())
        println(
            "DESCRIPTION_CODEC_PARITY $environmentVariable count=${typed.size} " +
                "sha256=${descriptionSha256(typed)} root=0x${selected.offset.toString(16)} " +
                "record=${selected.recordSize} pointers=${pointerOffsets.joinToString(",")}",
        )
    }

    private fun descriptionSha256(values: Map<Int, DescriptionRecord>): String {
        val bytes = values.toSortedMap().entries.joinToString("\u001e") { (id, value) ->
            "$id\u001f${value.text}\u001f${value.height}\u001f${value.weight}\u001f${value.category}"
        }.toByteArray()
        return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { byte ->
            "%02x".format(byte.toInt() and 0xFF)
        }
    }
}
