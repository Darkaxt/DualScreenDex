package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationException
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.dataset.abilities.CompiledSpeciesAbilitySlots
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomHeader
import org.junit.Assert.*
import org.junit.Test

class CompiledAbilityNativeFlowTest {
    @Test fun recursiveOpaqueExpressionsCannotCertifyAnUnboundedCallLoop() {
        val bytes = ByteArray(0x400)
        fun half(at: Int, value: Int) {
            bytes[at] = value.toByte(); bytes[at + 1] = (value ushr 8).toByte()
        }
        half(0x200, 0xF000); half(0x202, 0xF87E) // BL 0x300 with the preceding return as its next argument.
        half(0x204, 0xE7FC) // B 0x200.
        val session = RomAnalysisSession(RomImage(bytes), RomHeader(Platform.GBA, "SYNTHETIC"))
        val flow = CompiledAbilityNativeFlow(session.rom, CompiledAbilityProbeBudget(session))
        val proof = flow.analyze(flow.initial(0x200), 0x200..0x204)
        assertFalse(proof.complete)
        assertTrue(proof.returns.isEmpty())
    }

    @Test fun disjointFieldGeometryIsImmutableAndPartOfItsIdentity() {
        val fields = mutableListOf(24, 34)
        val slots = CompiledSpeciesAbilitySlots(0x200, 4, 40, 24, 2, 2, offsets = fields)
        fields[1] = 26
        assertEquals(listOf(24, 34), slots.offsets)
        assertNotEquals(CompiledSpeciesAbilitySlots(0x200, 4, 40, 24, 2, 2), slots)
        assertNotEquals(CompiledSpeciesAbilitySlots(0x200, 4, 40, 24, 2, 2).layoutIdentity, slots.layoutIdentity)
        assertThrows(UnsupportedOperationException::class.java) { (slots.offsets as MutableList<Int>).add(38) }
    }

    @Test fun cancellationPropagatesWithoutAnUnavailableOrSuccessResult() {
        val fixture = CompiledCachedAbilityFixture()
        val session = RomAnalysisSession(RomImage(fixture.bytes), RomHeader(Platform.GBA, "SYNTHETIC"),
            cancellation = ParserCancellationToken { throw ParserCancellationException() })
        assertThrows(ParserCancellationException::class.java) { CompiledAbilityTextResolver.resolve(session, fixture.core) }
    }
}
