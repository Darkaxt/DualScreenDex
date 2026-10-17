package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomHeader
import com.enrpau.dualscreendex.parser.model.TableLayout
import java.io.File
import java.security.MessageDigest
import java.util.BitSet
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Optional private retained-byte ABI check, never an original-ROM qualification. */
class RetainedCachedAbilityContractTest {
    @Test fun joinsRetainedNativeCodeWithoutInventingMissingCodeOrProse() {
        val configured = System.getenv("DUALDEX_RETAINED_CACHED_ABILITY_CONTROL")
        assumeTrue("optional hash-pinned retained-only ABI control", configured != null)
        val raw = File(requireNotNull(configured)).readBytes()
        val digest = MessageDigest.getInstance("SHA-256").digest(raw).joinToString("") { "%02x".format(it) }
        assertEquals(System.getenv("DUALDEX_RETAINED_CACHED_ABILITY_CONTROL_SHA256"), digest)
        val rows = raw.toString(Charsets.UTF_8).lineSequence().filter(String::isNotBlank).map { it.split('\t') }.toList()
        val layout = rows.single { it[0] == "layout" }.drop(1).map(String::toInt)
        val fields = rows.single { it[0] == "fields" }.drop(1).map(String::toInt)
        val bytes = ByteArray(32 * 1024 * 1024) { 0xFF.toByte() }
        val occupied = BitSet(bytes.size)
        for (row in rows.filter { it[0] == "bytes" }) {
            val start = row[1].toInt()
            val hex = row[2]
            assertEquals(0, hex.length % 2)
            for (index in 0 until hex.length / 2) {
                val at = start + index
                val value = hex.substring(index * 2, index * 2 + 2).toInt(16).toByte()
                if (occupied[at]) assertEquals(bytes[at], value)
                occupied.set(at); bytes[at] = value
            }
        }
        // Published-role nomination is generated scaffolding if the capture omitted
        // those data cells. Retained code, literals, names and core rows stay exact.
        for ((cell, root) in listOf(0x1BC to layout[0], 0x1C0 to layout[3], 0x1C4 to layout[4])) {
            repeat(4) { index ->
                val value = ((0x08000000 + root) ushr (index * 8)).toByte()
                if (occupied[cell + index]) assertEquals(bytes[cell + index], value)
                bytes[cell + index] = value
            }
        }
        val session = RomAnalysisSession(RomImage(bytes), RomHeader(Platform.GBA, "RETAINED ABI CONTROL"))
        val roots = mapOf("selected-core" to layout[0], "names" to layout[3], "descriptions" to layout[4])
        for (row in rows.filter { it[0] == "sites" }) {
            val expected = row[2].split(',').map(String::toInt).sorted()
            val actual = requireNotNull(session.nominatedGbaReferenceSites(roots.getValue(row[1])))
            assertFalse(actual.siteBudgetExceeded)
            assertEquals("complete retained nomination for ${row[1]}", expected, actual.instructionSites.sorted())
            for (site in actual.instructionSites) {
                assertTrue(occupied[site] && occupied[site + 1])
                val op = session.rom.u16le(site)
                val cell = ((site + 4) and -4) + (op and 255) * 4
                assertTrue((cell until cell + 4).all(occupied::get))
            }
        }
        val result = CompiledAbilityTextResolver.resolve(session, TableLayout(layout[0], layout[1], layout[2]))
        val badWidths = requireNotNull(session.nominatedGbaReferenceSites(layout[3])).instructionSites.filter {
            (compiledFixedStrideConsumer(session.rom, it) ?: inlineTextStride(session.rom, it)) != layout[5]
        }
        assertTrue("$result; other name stride sites=$badWidths", result is CompiledAbilityTextResolution.Resolved)
        val binding = (result as CompiledAbilityTextResolution.Resolved).binding
        assertEquals(fields, binding.speciesSlots.offsets)
        assertEquals(layout[6].toLong(), binding.names.count)
        assertEquals(layout[5], binding.names.nameWidth)
        assertTrue(binding.staticDefaultOnly)
        assertTrue((layout[0] until layout[0] + layout[1] * layout[2]).all(occupied::get))
    }
}
