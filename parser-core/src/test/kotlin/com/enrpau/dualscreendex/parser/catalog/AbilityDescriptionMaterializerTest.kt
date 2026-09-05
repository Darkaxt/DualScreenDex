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
    fun shortCompiledInlineNativeProseKeepsExactTokenAndPaddingSafety() {
        val short = byteArrayOf(27, 41, 31, 21, 2, 0xFF.toByte()) // ひるまない
        val descriptor = com.enrpau.dualscreendex.parser.parse.CompiledInlineAbilityText(0x100, 19)
        listOf(com.enrpau.dualscreendex.parser.text.JapanesePokemonTextCodecs.gen3RubySapphire, com.enrpau.dualscreendex.parser.text.JapanesePokemonTextCodecs.gen3Later).forEach { codec ->
            val bytes = ByteArray(0x200)
            short.copyInto(bytes, 0x100)
            fun decode(record: ByteArray): Map<Int, String>? {
                bytes.fill(0, 0x113, 0x126)
                record.copyInto(bytes, 0x113)
                return descriptor.decode(RomImage(bytes), 2, codec, ParserCancellationToken.NONE)
            }
            assertEquals("ひるまない", decode(short)?.get(1))
            assertNull(decode(byteArrayOf(0xFF.toByte()))) // empty
            assertNull(decode(ByteArray(19) { 1 })) // unterminated, despite valid native glyphs
            assertNull(decode(byteArrayOf(0xFA.toByte(), 0xFF.toByte()))) // controls are not prose
            assertNull(decode(short.copyOf(5) + byteArrayOf(0xFD.toByte(), 0xFF.toByte()))) // FF is a control argument
            assertNull(decode(byteArrayOf(0xFC.toByte(), 0x7F) + short)) // invalid extended control
            assertNull(decode(short + byteArrayOf(1))) // post-terminator contamination
            assertNull(decode(byteArrayOf(0xBB.toByte(), 0xBC.toByte(), 0xBD.toByte(), 0xBE.toByte(), 0xBF.toByte(), 0xFF.toByte()))) // wrong script
        }
    }

    @Test
    fun decodesOnlyConsumerProvenInlineDescriptionsBoundedByTheNameExtent() {
        val bytes = ByteArray(0x1000)
        val names = 0x400
        val count = 11
        val root = names + count * 8
        repeat(count) { id ->
            if (id == 0) bytes.fill(0xAE.toByte(), names, names + 7)
            else byteArrayOf(1, 2).copyInto(bytes, names + id * 8)
            bytes[names + id * 8 + if (id == 0) 7 else 2] = 0xFF.toByte()
            val text = byteArrayOf(1, 2, 3, 0, 4, 5, 6, 0, 7, 8, 9, 0xFF.toByte())
            text.copyInto(bytes, root + id * 19)
        }
        fun instruction(at: Int, op: Int) {
            bytes[at] = op.toByte(); bytes[at + 1] = (op ushr 8).toByte()
        }
        // ((id << 2) + id) << 2 - id, in a different register pair than retail.
        listOf(0x00AB, 0x195B, 0x009B, 0x1B5B, 0x491D, 0x185B).forEachIndexed { i, op ->
            instruction(0x100 + i * 2, op)
        }
        putGbaPointer(bytes, 0x180, root)
        instruction(0x200, 0x00C0); instruction(0x202, 0x491F); instruction(0x204, 0x1840)
        putGbaPointer(bytes, 0x280, names)
        instruction(0x240, 0x2308); instruction(0x242, 0x4358)
        instruction(0x244, 0x491E); instruction(0x246, 0x1840)
        putGbaPointer(bytes, 0x2C0, names) // mixed but agreeing MUL8 and LSL3 consumers
        fun selectedNames(): com.enrpau.dualscreendex.parser.model.ValidationEvidence {
            val session = com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession(RomImage(bytes), com.enrpau.dualscreendex.parser.model.RomHeader(Platform.GBA, "EXTENT INTEGRATION"))
            val strategy = com.enrpau.dualscreendex.parser.family.SemanticDomainStrategy()
            val method = strategy.javaClass.declaredMethods.single { it.name == "resolveAbilityNames" }.apply { isAccessible = true }
            return method.invoke(strategy, session, TableLayout(names, count, 8), com.enrpau.dualscreendex.parser.text.JapanesePokemonTextCodecs.gen3Later, null, false, null, null, com.enrpau.dualscreendex.parser.dataset.abilities.AbilitySemanticDomain((1..9).toSet())) as com.enrpau.dualscreendex.parser.model.ValidationEvidence
        }
        assertEquals(true, selectedNames().compatible)
        assertEquals(count, selectedNames().totalRecords)
        fun resolved(n: Int = count): ResolvedRomLayout {
            val session = com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession(
                RomImage(bytes), com.enrpau.dualscreendex.parser.model.RomHeader(Platform.GBA, "INLINE TEST"),
            )
            return layout(names, n).copy(
                tables = ProfileTables(abilities = TableLayout(names, n, 8)),
                compiledGbaReferences = requireNotNull(session.gbaReferenceIndex).asLegacyCounts(),
                languageManifest = resolvedLanguageManifest(com.enrpau.dualscreendex.parser.text.JapanesePokemonTextCodecs.gen3Later, com.enrpau.dualscreendex.parser.language.LanguageTag.JAPANESE),
            )
        }
        val index = requireNotNull(resolved().compiledGbaReferences?.siteEvidence)
        val inline = com.enrpau.dualscreendex.parser.parse.compiledInlineAbilityTexts(
            RomImage(bytes), index, ParserCancellationToken.NONE,
        )
        assertEquals("consumer root and stride: $index", listOf(com.enrpau.dualscreendex.parser.parse.CompiledInlineAbilityText(root, 19)), inline)
        val decoded = com.enrpau.dualscreendex.parser.text.JapanesePokemonTextCodecs.gen3Later.decodeDetailed(RomImage(bytes), root + 19, 19, ParserCancellationToken.NONE)
        assertEquals("$decoded", "あいう えおか きくけ", decoded.text)
        assertEquals("$decoded", count - 1, inline.single().decode(RomImage(bytes), count, com.enrpau.dualscreendex.parser.text.JapanesePokemonTextCodecs.gen3Later, ParserCancellationToken.NONE)?.size)
        val session = com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession(RomImage(bytes), com.enrpau.dualscreendex.parser.model.RomHeader(Platform.GBA, "BOUNDARY TEST"))
        assertEquals(count, com.enrpau.dualscreendex.parser.parse.compiledInlineAbilityNameCount(session, names, 8, 9, com.enrpau.dualscreendex.parser.text.JapanesePokemonTextCodecs.gen3Later, inline))
        assertNull(com.enrpau.dualscreendex.parser.parse.compiledInlineAbilityNameCount(session, names, 8, count, com.enrpau.dualscreendex.parser.text.JapanesePokemonTextCodecs.gen3Later, inline))
        assertNull(com.enrpau.dualscreendex.parser.parse.compiledInlineAbilityNameCount(session, names, 8, 9, com.enrpau.dualscreendex.parser.text.JapanesePokemonTextCodecs.gen3Later, emptyList()))
        val competingRoot = names + 40 * 8
        repeat(40) { id ->
            byteArrayOf(1, 2, 3, 0, 4, 5, 6, 0, 7, 8, 9, 0xFF.toByte()).copyInto(bytes, competingRoot + id * 19)
        }
        val conflictSession = com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession(RomImage(bytes), session.header)
        assertNull(com.enrpau.dualscreendex.parser.parse.compiledInlineAbilityNameCount(conflictSession, names, 8, 9, com.enrpau.dualscreendex.parser.text.JapanesePokemonTextCodecs.gen3Later, inline + com.enrpau.dualscreendex.parser.parse.CompiledInlineAbilityText(competingRoot, 19)))
        listOf(0x00AB, 0x195B, 0x009B, 0x1B5B, 0x491D, 0x185B).forEachIndexed { i, op -> instruction(0x300 + i * 2, op) }
        putGbaPointer(bytes, 0x380, competingRoot)
        assertEquals(false, selectedNames().compatible) // competing compiled boundary cannot fall back to inherited count
        bytes.fill(0, 0x300, 0x30C)
        val result = AbilityDescriptionMaterializer.materialize(RomImage(bytes), resolved())
        assertEquals("あいう えおか きくけ", result?.descriptions?.get(1))
        assertEquals(count - 1, result?.descriptions?.size)
        assertNull(AbilityDescriptionMaterializer.materialize(RomImage(bytes), resolved(count + 1)))
        bytes[root + 19 + 12] = 1 // post-terminator contamination is not a next record
        assertNull(AbilityDescriptionMaterializer.materialize(RomImage(bytes), resolved()))
        bytes[root + 19 + 12] = 0
        instruction(0x106, 0x1B13) // subtract wrong original-ID register
        assertEquals(false, selectedNames().compatible) // no inferred/legacy extent fallback
        assertNull(AbilityDescriptionMaterializer.materialize(RomImage(bytes), resolved()))
    }

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
