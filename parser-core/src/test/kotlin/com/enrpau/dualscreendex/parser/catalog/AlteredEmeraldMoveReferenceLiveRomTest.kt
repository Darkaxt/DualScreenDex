package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** SHA-bound authority for a referenced Altered Emerald move whose ROM name row is blank. */
class AlteredEmeraldMoveReferenceLiveRomTest {
    @Test
    fun retainsReferencedBlankMoveIdentityWithoutInventingAName() {
        val configured = System.getenv("DUALDEX_ALTERED_EMERALD_ROM")
        assumeTrue("set DUALDEX_ALTERED_EMERALD_ROM to run this live-ROM regression", !configured.isNullOrBlank())
        val path = Path.of(requireNotNull(configured))
        assumeTrue("live ROM does not exist: $path", Files.isRegularFile(path))
        val rom = RomImage(Files.readAllBytes(path))
        assertEquals("8fe93d8245c96ea5aa49d61df2c74ee99a439b15cde7c0afa4f0b5a87aac34f0", rom.sha256)

        val parsed = CatalogParser.parse(rom)
        assertNotNull(parsed.layout)
        val catalog = requireNotNull(parsed.catalog)
        val text = catalog.defaultTextProjection()
        val move = catalog.movesById.getValue(729)

        assertEquals(CapabilityStatus.NOT_FOUND, move.name.status)
        assertNull(move.name.value)
        assertTrue(move.name.reasons.isNotEmpty())
        assertEquals(9, move.typeId.value)
        assertEquals(127, move.power.value)
        assertEquals(0, move.accuracy.value)
        assertEquals(8, move.pp.value)
        assertEquals(1, move.priority.value)
        assertEquals(24, move.effectId.value)
        assertEquals("Strikes with a blinding gust that lowers Sp.Atk.", text.moveDescription(move.id))

        assertEquals(761, catalog.movesById.size)
        assertEquals((1..761).toSet(), catalog.movesById.keys)
        assertFalse(catalog.movesById.containsKey(0))
        assertEquals(714, catalog.movesById.values.count { candidate ->
            candidate.typeId.status == CapabilityStatus.AVAILABLE &&
                candidate.power.status == CapabilityStatus.AVAILABLE &&
                candidate.accuracy.status == CapabilityStatus.AVAILABLE &&
                candidate.pp.status == CapabilityStatus.AVAILABLE
        })
        assertEquals(1, catalog.speciesById.getValue(252).learnset.value.orEmpty().count { it.moveId == 729 })
        assertTrue(catalog.speciesById.values.flatMap { species ->
            species.learnset.value.orEmpty()
        }.none { entry -> entry.moveId !in catalog.movesById })
    }
}
