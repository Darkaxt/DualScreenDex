package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationException
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.language.resolvedLanguageManifest
import com.enrpau.dualscreendex.parser.language.textUnavailableLanguageManifests
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.GbaCompiledReferenceIndex
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.ProfileTables
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class AbilityDescriptionMaterializerTest {
    @Test
    fun unknownAndAmbiguousLanguageDisableAbilityDescriptions() {
        textUnavailableLanguageManifests.forEach { manifest ->
            assertNull(
                AbilityDescriptionMaterializer.materialize(
                    RomImage(ByteArray(0x100)),
                    layout(namesOffset = 0).copy(languageManifest = manifest),
                ),
            )
        }
    }

    @Test
    fun decodesAValidatedPointerTableAdjacentToAbilityNames() {
        val bytes = ByteArray(0x1000) { 0xFF.toByte() }
        val namesOffset = 0x100
        val descriptionsOffset = 0x134
        listOf("NO SPECIAL ABILITY", "HELPS REPEL WILD POKEMON", "SUMMONS RAIN IN BATTLE", "BOOSTS SPEED EACH TURN")
            .forEachIndexed { id, description ->
                val textOffset = 0x400 + id * 0x40
                putGbaPointer(bytes, descriptionsOffset + id * 4, textOffset)
                encodeGbaText(bytes, textOffset, description)
            }
        putThumbLiteralReference(bytes, 0x200, 0x280, descriptionsOffset)

        val result = AbilityDescriptionMaterializer.materialize(
            RomImage(bytes),
            layout(namesOffset, referenceCounts = mapOf(descriptionsOffset to 1)),
        )

        assertEquals(descriptionsOffset, result?.sourceOffset)
        assertEquals("HELPS REPEL WILD POKEMON", result?.descriptions?.get(1))
        assertEquals("BOOSTS SPEED EACH TURN", result?.descriptions?.get(3))
    }

    @Test
    fun decodesAValidatedPointerTableOutsideTheNamesSearchRadius() {
        val bytes = ByteArray(0x24000) { 0xFF.toByte() }
        val descriptionsOffset = 0x22000
        listOf("NO SPECIAL ABILITY", "HELPS REPEL WILD POKEMON", "SUMMONS RAIN IN BATTLE", "BOOSTS SPEED EACH TURN")
            .forEachIndexed { id, description ->
                val textOffset = 0x23000 + id * 0x40
                putGbaPointer(bytes, descriptionsOffset + id * 4, textOffset)
                encodeGbaText(bytes, textOffset, description)
            }
        putThumbLiteralReference(bytes, 0x200, 0x280, descriptionsOffset)

        val result = AbilityDescriptionMaterializer.materialize(
            RomImage(bytes),
            layout(0x100, referenceCounts = mapOf(descriptionsOffset to 1)),
        )

        assertEquals(descriptionsOffset, result?.sourceOffset)
        assertEquals("SUMMONS RAIN IN BATTLE", result?.descriptions?.get(2))
    }

    @Test
    fun preservesValidTailAfterAnIntentionalShortPlaceholderDescription() {
        val bytes = ByteArray(0x2000) { 0xFF.toByte() }
        val descriptionsOffset = 0x400
        val descriptions = listOf(
            "NO SPECIAL ABILITY",
            "FIRST EFFECT DESCRIPTION",
            "SECOND EFFECT DESCRIPTION",
            "-",
            "FOURTH EFFECT DESCRIPTION",
            "FIFTH EFFECT DESCRIPTION",
        )
        descriptions.forEachIndexed { id, description ->
            val textOffset = 0x1000 + id * 0x40
            putGbaPointer(bytes, descriptionsOffset + id * 4, textOffset)
            encodeGbaText(bytes, textOffset, description)
        }
        putThumbLiteralReference(bytes, 0x200, 0x280, descriptionsOffset)

        val result = AbilityDescriptionMaterializer.materialize(
            RomImage(bytes),
            layout(
                namesOffset = 0x100,
                count = descriptions.size,
                referenceCounts = mapOf(descriptionsOffset to 1),
            ),
        )

        assertEquals(descriptionsOffset, result?.sourceOffset)
        assertEquals(null, result?.descriptions?.get(3))
        assertEquals("FOURTH EFFECT DESCRIPTION", result?.descriptions?.get(4))
        assertEquals("FIFTH EFFECT DESCRIPTION", result?.descriptions?.get(5))
    }

    @Test
    fun rejectsAPointerTableWithUndecodableDescriptions() {
        val bytes = ByteArray(0x1000)
        val descriptionsOffset = 0x134
        repeat(4) { id ->
            val textOffset = 0x400 + id * 0x40
            putGbaPointer(bytes, descriptionsOffset + id * 4, textOffset)
        }

        assertNull(
            AbilityDescriptionMaterializer.materialize(
                RomImage(bytes),
                layout(0x100, referenceCounts = mapOf(descriptionsOffset to 1)),
            ),
        )
    }

    @Test
    fun acceptsASeventyFivePercentPublishedTableInsteadOfADenseMoveDescriptionDecoy() {
        val count = 21
        val bytes = ByteArray(0x8000) { 0xFF.toByte() }
        val namesOffset = 0x400
        val publishedRoot = 0x1000
        val decoyRoot = 0x1800
        repeat(count) { id ->
            encodeGbaText(bytes, namesOffset + id * 13, if (id == 0) "-------" else "ABILITY", width = 13)
        }

        putGbaPointer(bytes, publishedRoot, 0x3000)
        encodeGbaText(bytes, 0x3000, "NO SPECIAL ABILITY")
        repeat(15) { index ->
            val id = index + 1
            val textOffset = 0x3100 + index * 0x30
            putGbaPointer(bytes, publishedRoot + id * 4, textOffset)
            encodeGbaText(bytes, textOffset, "ABILITY EFFECT DESCRIPTION")
        }
        putGbaPointer(bytes, publishedRoot + 17 * 4, 0x4F00)
        encodeGbaText(bytes, 0x4F00, "UNRELATED ADJACENT TEXT")
        repeat(count) { id ->
            val textOffset = 0x5000 + id * 0x30
            putGbaPointer(bytes, decoyRoot + id * 4, textOffset)
            encodeGbaText(bytes, textOffset, "MOVE DAMAGE DESCRIPTION")
        }
        repeat(3) { index ->
            putThumbLiteralReference(bytes, 0x200 + index * 8, 0x280 + index * 4, publishedRoot)
            putThumbLiteralReference(bytes, 0x300 + index * 8, 0x380 + index * 4, decoyRoot)
        }
        val publishedRoots = listOf(0x2200, 0x2300, publishedRoot, 0x2400, 0x2500, 0x2600, 0x2700)
        publishedRoots.forEachIndexed { index, root -> putGbaPointer(bytes, 0x1BC + index * 4, root) }

        val result = AbilityDescriptionMaterializer.materialize(
            RomImage(bytes),
            layout(namesOffset, count),
        )

        assertEquals(publishedRoot, result?.sourceOffset)
        assertEquals(15, result?.descriptions?.size)
        assertEquals("ABILITY EFFECT DESCRIPTION", result?.descriptions?.get(1))
        assertEquals(null, result?.descriptions?.get(17))
    }

    @Test
    fun rejectsAnUnpublishedUnreferencedDenseMoveDescriptionTable() {
        val bytes = ByteArray(0x2000) { 0xFF.toByte() }
        val decoyRoot = 0x800
        listOf("NO SPECIAL ABILITY", "MOVE DAMAGE DESCRIPTION", "MOVE DAMAGE DESCRIPTION", "MOVE DAMAGE DESCRIPTION")
            .forEachIndexed { id, description ->
                val textOffset = 0x1000 + id * 0x40
                putGbaPointer(bytes, decoyRoot + id * 4, textOffset)
                encodeGbaText(bytes, textOffset, description)
            }

        assertNull(AbilityDescriptionMaterializer.materialize(RomImage(bytes), layout(0x100)))
    }

    @Test
    fun doesNotDiscoverCompiledCandidatesWhenLayoutEvidenceIsAbsent() {
        val bytes = ByteArray(0x1000) { 0xFF.toByte() }
        val descriptionsOffset = 0x134
        listOf("NO SPECIAL ABILITY", "FIRST EFFECT DESCRIPTION", "SECOND EFFECT DESCRIPTION", "THIRD EFFECT DESCRIPTION")
            .forEachIndexed { id, description ->
                val textOffset = 0x400 + id * 0x40
                putGbaPointer(bytes, descriptionsOffset + id * 4, textOffset)
                encodeGbaText(bytes, textOffset, description)
            }
        putThumbLiteralReference(bytes, 0x200, 0x280, descriptionsOffset)

        assertNull(AbilityDescriptionMaterializer.materialize(RomImage(bytes), layout(0x100)))
    }

    @Test
    fun prefersThePublishedAndCompiledRootOverAnAdjacentDuplicatePointer() {
        val bytes = ByteArray(0x2000) { 0xFF.toByte() }
        val namesOffset = 0x100
        val adjacentCandidate = 0x134
        val describedRoot = adjacentCandidate + 4
        encodeGbaText(bytes, namesOffset, "-------", width = 13)
        encodeGbaText(bytes, namesOffset + 13, "FIRST", width = 13)
        encodeGbaText(bytes, namesOffset + 26, "SECOND", width = 13)
        encodeGbaText(bytes, namesOffset + 39, "THIRD", width = 13)

        val descriptions = listOf(
            "NO SPECIAL ABILITY",
            "FIRST EFFECT DESCRIPTION",
            "SECOND EFFECT DESCRIPTION",
            "THIRD EFFECT DESCRIPTION",
        )
        descriptions.forEachIndexed { id, description ->
            val textOffset = 0x800 + id * 0x40
            putGbaPointer(bytes, describedRoot + id * 4, textOffset)
            encodeGbaText(bytes, textOffset, description)
        }
        putGbaPointer(bytes, adjacentCandidate, 0x800)

        val publishedRoots = listOf(0x300, 0x400, describedRoot, 0x600, 0x700, 0x900, 0xA00)
        publishedRoots.forEachIndexed { index, root -> putGbaPointer(bytes, 0x1BC + index * 4, root) }
        putGbaPointer(bytes, 0x280, describedRoot) // Simulated aligned Thumb literal-pool reference.

        val result = AbilityDescriptionMaterializer.materialize(
            RomImage(bytes),
            layout(namesOffset, referenceCounts = mapOf(describedRoot to 1)),
        )

        assertEquals(describedRoot, result?.sourceOffset)
        assertEquals("FIRST EFFECT DESCRIPTION", result?.descriptions?.get(1))
        assertEquals("SECOND EFFECT DESCRIPTION", result?.descriptions?.get(2))
    }

    @Test
    fun skipsAOneSlotShiftThatDuplicatesTheSentinelDescription() {
        val bytes = ByteArray(0x1000) { 0xFF.toByte() }
        val namesOffset = 0x100
        val shiftedRoot = 0x134
        encodeGbaText(bytes, namesOffset, "-------", width = 13)
        encodeGbaText(bytes, namesOffset + 13, "FIRST", width = 13)
        encodeGbaText(bytes, namesOffset + 26, "SECOND", width = 13)
        encodeGbaText(bytes, namesOffset + 39, "THIRD", width = 13)
        val targets = listOf(0x600, 0x600, 0x680, 0x700, 0x780)
        targets.forEachIndexed { index, target -> putGbaPointer(bytes, shiftedRoot + index * 4, target) }
        encodeGbaText(bytes, 0x600, "NO SPECIAL ABILITY")
        encodeGbaText(bytes, 0x680, "FIRST EFFECT DESCRIPTION")
        encodeGbaText(bytes, 0x700, "SECOND EFFECT DESCRIPTION")
        encodeGbaText(bytes, 0x780, "THIRD EFFECT DESCRIPTION")
        putThumbLiteralReference(bytes, 0x200, 0x280, shiftedRoot + 4)

        val result = AbilityDescriptionMaterializer.materialize(
            RomImage(bytes),
            layout(namesOffset, referenceCounts = mapOf(shiftedRoot + 4 to 1)),
        )

        assertEquals(shiftedRoot + 4, result?.sourceOffset)
        assertEquals("FIRST EFFECT DESCRIPTION", result?.descriptions?.get(1))
        assertEquals("SECOND EFFECT DESCRIPTION", result?.descriptions?.get(2))
    }

    @Test
    fun weakReferencedPointerRootsDoNotExhaustTheCandidateBudgetBeforeACompleteTable() {
        val bytes = ByteArray(0x20000) { 0xFF.toByte() }
        val references = linkedMapOf<Int, Int>()
        repeat(513) { index ->
            val weakRoot = 0x1000 + index * 0x20
            putGbaPointer(bytes, weakRoot, 0x8000)
            references[weakRoot] = 1
        }

        val descriptionsOffset = 0x18000
        listOf(
            "NO SPECIAL ABILITY",
            "FIRST EFFECT DESCRIPTION",
            "SECOND EFFECT DESCRIPTION",
            "THIRD EFFECT DESCRIPTION",
        ).forEachIndexed { id, description ->
            val textOffset = 0x19000 + id * 0x40
            putGbaPointer(bytes, descriptionsOffset + id * 4, textOffset)
            encodeGbaText(bytes, textOffset, description)
        }
        references[descriptionsOffset] = 1

        val result = AbilityDescriptionMaterializer.materialize(
            RomImage(bytes),
            layout(0x100, referenceCounts = references),
        )

        assertEquals(descriptionsOffset, result?.sourceOffset)
        assertEquals("THIRD EFFECT DESCRIPTION", result?.descriptions?.get(3))
    }

    @Test
    fun cancellationInterruptsCandidateAndRecordDecoding() {
        val bytes = ByteArray(0x1000) { 0xFF.toByte() }
        val descriptionsOffset = 0x134
        listOf(
            "NO SPECIAL ABILITY",
            "HELPS REPEL WILD POKEMON",
            "SUMMONS RAIN IN BATTLE",
            "BOOSTS SPEED EACH TURN",
        ).forEachIndexed { id, description ->
            val textOffset = 0x400 + id * 0x40
            putGbaPointer(bytes, descriptionsOffset + id * 4, textOffset)
            encodeGbaText(bytes, textOffset, description)
        }
        val cancellation = CancelAfterChecks(successfulChecks = 5)

        assertThrows(ParserCancellationException::class.java) {
            AbilityDescriptionMaterializer.materialize(
                RomImage(bytes),
                layout(0x100, referenceCounts = mapOf(descriptionsOffset to 1)),
                cancellation,
            )
        }
        assertEquals(6, cancellation.checks)
    }

    private class CancelAfterChecks(
        private val successfulChecks: Int,
    ) : ParserCancellationToken {
        var checks: Int = 0
            private set

        override fun throwIfCancellationRequested() {
            checks++
            if (checks > successfulChecks) throw ParserCancellationException()
        }
    }

    private fun layout(
        namesOffset: Int,
        count: Int = 4,
        referenceCounts: Map<Int, Int>? = null,
    ) = ResolvedRomLayout(
        family = EngineFamily.EMERALD,
        generation = 3,
        platform = Platform.GBA,
        speciesCount = count,
        moveCount = count,
        tables = ProfileTables(abilities = TableLayout(namesOffset, count, 13)),
        compiledGbaReferences = referenceCounts?.let(::GbaCompiledReferenceIndex),
        languageManifest = resolvedLanguageManifest(PokemonTextCodec.gbaEnglish),
    )

    private fun encodeGbaText(target: ByteArray, offset: Int, value: String, width: Int? = null) {
        if (width != null) {
            repeat(width) { target[offset + it] = 0 }
        }
        value.forEachIndexed { index, char ->
            target[offset + index] = when (char) {
                ' ' -> 0
                '-' -> 0xAE.toByte()
                '.' -> 0xAD.toByte()
                in 'A'..'Z' -> (0xBB + char.code - 'A'.code).toByte()
                else -> error("unsupported fixture character")
            }
        }
        if (width == null || value.length < width) {
            target[offset + value.length] = 0xFF.toByte()
        }
    }

    private fun putGbaPointer(target: ByteArray, offset: Int, targetOffset: Int) {
        val value = 0x08000000 + targetOffset
        repeat(4) { index -> target[offset + index] = (value ushr (index * 8)).toByte() }
    }

    private fun putThumbLiteralReference(target: ByteArray, instructionOffset: Int, literalOffset: Int, targetOffset: Int) {
        val alignedPc = (instructionOffset + 4) and 3.inv()
        require(literalOffset >= alignedPc && (literalOffset - alignedPc) % 4 == 0)
        val immediate = (literalOffset - alignedPc) / 4
        require(immediate in 0..255)
        val instruction = 0x4800 or immediate
        target[instructionOffset] = instruction.toByte()
        target[instructionOffset + 1] = (instruction ushr 8).toByte()
        putGbaPointer(target, literalOffset, targetOffset)
    }
}
