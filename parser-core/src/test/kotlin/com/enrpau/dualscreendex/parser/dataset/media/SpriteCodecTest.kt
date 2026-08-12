package com.enrpau.dualscreendex.parser.dataset.media

import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import com.enrpau.dualscreendex.parser.model.Platform
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SpriteCodecTest {
    @Test
    fun decodesGbaRawGraphicsAndRawPaletteOnlyUnderTheExplicitAbi() {
        val bytes = ByteArray(0x300)
        putGbaPointer(bytes, 0, 0x100)
        putU16(bytes, 4, 32)
        putGbaPointer(bytes, 8, 0x180)
        putU16(bytes, 12, 0)
        bytes[0x100] = 1
        putU16(bytes, 0x182, 0x001F)
        val layout = GbaSpriteTableLayout(
            tableOffset = 0,
            count = 1,
            recordStride = 8,
            graphicsMode = GbaGraphicsMode.RAW_4BPP,
            palette = GbaPaletteLayout(
                tableOffset = 8,
                recordStride = 8,
                mode = GbaPaletteMode.RAW_BGR555,
                requireRowTag = true,
            ),
        )

        val decoded = SpriteCodec().decode(spriteSession(bytes), layout) as SpriteTableOutcome.Decoded
        val row = decoded.rows.single() as SpriteRowOutcome.Decoded

        assertEquals(8, row.frame.width)
        assertEquals(8, row.frame.height)
        assertEquals(1, row.frame.indexedPixels[0].toInt())
        assertEquals(0x001F, row.frame.paletteBgr555[1].toInt() and 0xFFFF)
    }

    @Test
    fun keepsGbaLz77AndSmolModesExplicitAndRejectsModeMismatch() {
        val bytes = ByteArray(0x500)
        val raw = ByteArray(32).also { it[0] = 1 }
        putGbaPointer(bytes, 0, 0x100)
        putU16(bytes, 4, 32)
        putGbaPointer(bytes, 8, 0x200)
        putU16(bytes, 12, 32)
        gbaLiteral(raw).copyInto(bytes, 0x100)
        smolZero2048().copyInto(bytes, 0x200)

        val lz = SpriteCodec().decode(
            spriteSession(bytes),
            GbaSpriteTableLayout(0, 1, 8, GbaGraphicsMode.LZ77_4BPP),
        ) as SpriteTableOutcome.Decoded
        val smol = SpriteCodec().decode(
            spriteSession(bytes),
            GbaSpriteTableLayout(8, 1, 8, GbaGraphicsMode.SMOL_4BPP, fixedFrameSize = 2048),
        ) as SpriteTableOutcome.Decoded
        val mismatch = SpriteCodec().decode(
            spriteSession(bytes),
            GbaSpriteTableLayout(0, 1, 8, GbaGraphicsMode.SMOL_4BPP),
        ) as SpriteTableOutcome.Decoded

        assertArrayEquals(raw, (lz.rows.single() as SpriteRowOutcome.Decoded).frame.graphicsBytes)
        assertEquals(2048, (smol.rows.single() as SpriteRowOutcome.Decoded).frame.graphicsBytes.size)
        assertTrue(mismatch.rows.single() is SpriteRowOutcome.Malformed)
    }

    @Test
    fun paletteTagDecoyIsMalformedRatherThanSilentlyAccepted() {
        val bytes = ByteArray(0x300)
        putGbaPointer(bytes, 0, 0x100)
        putU16(bytes, 4, 32)
        putGbaPointer(bytes, 8, 0x180)
        putU16(bytes, 12, 99)
        bytes[0x100] = 1
        val layout = GbaSpriteTableLayout(
            0, 1, 8, GbaGraphicsMode.RAW_4BPP,
            palette = GbaPaletteLayout(8, 8, GbaPaletteMode.RAW_BGR555, requireRowTag = true),
        )

        val result = SpriteCodec().decode(spriteSession(bytes), layout) as SpriteTableOutcome.Decoded

        assertTrue(result.rows.single() is SpriteRowOutcome.Malformed)
    }

    @Test
    fun decodesLz77AndSmolPalettesOnlyWhenTheLayoutDeclaresTheirMode() {
        val bytes = ByteArray(0x500)
        putGbaPointer(bytes, 0, 0x100)
        putU16(bytes, 4, 32)
        bytes[0x100] = 1
        putGbaPointer(bytes, 8, 0x180)
        putGbaPointer(bytes, 16, 0x200)
        val rawPalette = ByteArray(32).also { it[2] = 0x1F }
        gbaLiteral(rawPalette).copyInto(bytes, 0x180)
        smolRawPalette().copyInto(bytes, 0x200)

        val lz = SpriteCodec().decode(
            spriteSession(bytes),
            GbaSpriteTableLayout(
                0, 1, 8, GbaGraphicsMode.RAW_4BPP,
                palette = GbaPaletteLayout(8, 8, GbaPaletteMode.LZ77_BGR555),
            ),
        ) as SpriteTableOutcome.Decoded
        val smol = SpriteCodec().decode(
            spriteSession(bytes),
            GbaSpriteTableLayout(
                0, 1, 8, GbaGraphicsMode.RAW_4BPP,
                palette = GbaPaletteLayout(16, 8, GbaPaletteMode.SMOL_BGR555),
            ),
        ) as SpriteTableOutcome.Decoded

        assertEquals(0x001F, (lz.rows.single() as SpriteRowOutcome.Decoded).frame.paletteBgr555[1].toInt())
        assertEquals(0x001F, (smol.rows.single() as SpriteRowOutcome.Decoded).frame.paletteBgr555[1].toInt())
    }

    @Test
    fun classifiesInactiveZeroRowsAndExplicitStandardPlaceholdersWithoutFabricatingPixels() {
        val bytes = ByteArray(0x300)
        putGbaPointer(bytes, 8, 0x100)
        putU16(bytes, 12, 32)
        bytes[0x100] = 1
        val layout = GbaSpriteTableLayout(
            tableOffset = 0,
            count = 2,
            recordStride = 8,
            graphicsMode = GbaGraphicsMode.RAW_4BPP,
            placeholderGraphicsOffsets = setOf(0x100),
        )

        val result = SpriteCodec().decode(spriteSession(bytes), layout) as SpriteTableOutcome.Decoded

        assertTrue(result.rows[0] is SpriteRowOutcome.StructuralEmpty)
        assertTrue(result.rows[1] is SpriteRowOutcome.StandardPlaceholder)
    }

    @Test
    fun enforcesDecodedOutputAndDecompressionWorkBudgetsBeforeAllocation() {
        val bytes = ByteArray(0x200)
        putGbaPointer(bytes, 0, 0x100)
        putU16(bytes, 4, 32)
        gbaLiteral(ByteArray(32)).copyInto(bytes, 0x100)
        val layout = GbaSpriteTableLayout(0, 1, 8, GbaGraphicsMode.LZ77_4BPP)

        val output = SpriteCodec(SpriteDecodeLimits(maxDecodedBytesPerTable = 16)).decode(
            spriteSession(bytes), layout,
        )
        val work = SpriteCodec(SpriteDecodeLimits(maxDecodeWorkPerTable = 8)).decode(
            spriteSession(bytes), layout,
        )

        assertEquals(SpriteBudgetKind.DECODE_OUTPUT, (output as SpriteTableOutcome.BudgetExceeded).budgetKind)
        assertEquals(SpriteBudgetKind.DECODE_WORK, (work as SpriteTableOutcome.BudgetExceeded).budgetKind)
    }

    @Test
    fun decodesGenOneBitplaneAndGenTwoLz3ThroughTheSameRowContract() {
        val gen1 = ByteArray(0x8000)
        gen1[10] = 0x11
        putU16(gen1, 11, 0x4100)
        putU16(gen1, 13, 0x4100)
        gen1ZeroSprite().copyInto(gen1, 0x4100)
        val gen1Result = SpriteCodec().decode(
            spriteSession(gen1, Platform.GB),
            Gen1SpriteTableLayout(0, 1, 28, candidateBanks = listOf(1)),
        ) as SpriteTableOutcome.Decoded

        val gen2 = ByteArray(0x8000)
        gen2[0] = 1
        putU16(gen2, 1, 0x4100)
        gen2[3] = 1
        putU16(gen2, 4, 0x4100)
        (byteArrayOf(0x0F) + ByteArray(16) + byteArrayOf(0xFF.toByte())).copyInto(gen2, 0x4100)
        val gen2Result = SpriteCodec().decode(
            spriteSession(gen2, Platform.GBC),
            Gen2SpriteTableLayout(0, 1, dimensionsByRow = mapOf(0 to 1)),
        ) as SpriteTableOutcome.Decoded

        assertEquals(8, (gen1Result.rows.single() as SpriteRowOutcome.Decoded).frame.width)
        assertEquals(8, (gen2Result.rows.single() as SpriteRowOutcome.Decoded).frame.width)
    }

    @Test
    fun genTwoUnownRequiresAnExplicitIndirectRootAndAllTwentySixBankedRows() {
        val bytes = ByteArray(0x10000)
        repeat(201) { row ->
            val entry = row * 6
            bytes[entry] = 1
            putU16(bytes, entry + 1, 0x4100)
            bytes[entry + 3] = 1
            putU16(bytes, entry + 4, 0x4100)
        }
        repeat(6) { bytes[200 * 6 + it] = 0xFF.toByte() }
        val decoy = 0x4000
        repeat(26) { form ->
            val entry = decoy + form * 6
            bytes[entry] = 1
            putU16(bytes, entry + 1, 0x4100)
            bytes[entry + 3] = 1
            putU16(bytes, entry + 4, 0x4100)
        }
        val proven = 0x8000
        repeat(26) { form ->
            val entry = proven + form * 6
            bytes[entry] = 2
            putU16(bytes, entry + 1, 0x4100)
            bytes[entry + 3] = 2
            putU16(bytes, entry + 4, 0x4100)
        }
        (byteArrayOf(0) + byteArrayOf(0x12) + byteArrayOf(0xFF.toByte())).copyInto(bytes, 0x4100)
        (byteArrayOf(0x0F) + ByteArray(16) + byteArrayOf(0xFF.toByte())).copyInto(bytes, 0x8100)
        val compiledSite = 0xF000
        putGen2CompiledBankedReference(bytes, compiledSite, proven)

        val noProofSession = spriteSession(bytes, Platform.GBC)
        val noProof = SpriteCodec().decode(
            noProofSession,
            Gen2SpriteTableLayout(0, 201),
        ) as SpriteTableOutcome.Decoded
        val provenSession = spriteSession(bytes, Platform.GBC)
        val proof = Gen2UnownIndirectTableEvidence.verifiedDirectCompiledConsumer(
            session = provenSession,
            mainTableOffset = 0,
            mainTableCount = 201,
            mainRecordStride = 6,
            indirectTableOffset = proven.toLong(),
        )
        val withProof = SpriteCodec().decode(
            provenSession,
            Gen2SpriteTableLayout(0, 201, bankRemap = mapOf(2 to 2), unownIndirectTable = proof),
        ) as SpriteTableOutcome.Decoded

        assertTrue(noProof.rows[200] is SpriteRowOutcome.Malformed)
        assertTrue(withProof.rows[200] is SpriteRowOutcome.Decoded)

        putU16(bytes, proven + 7 * 6 + 1, 0)
        val malformedSession = spriteSession(bytes, Platform.GBC)
        val malformedProof = Gen2UnownIndirectTableEvidence.verifiedDirectCompiledConsumer(
            malformedSession,
            0,
            201,
            6,
            proven.toLong(),
        )
        val malformedForm = SpriteCodec().decode(
            malformedSession,
            Gen2SpriteTableLayout(0, 201, bankRemap = mapOf(2 to 2), unownIndirectTable = malformedProof),
        ) as SpriteTableOutcome.Decoded
        assertTrue(malformedForm.rows[200] is SpriteRowOutcome.Malformed)
    }

    @Test
    fun genTwoIdentityIncludesEveryPointerFieldOffset() {
        val ordinary = Gen2SpriteTableLayout(0, 1)
        val relocatedFields = Gen2SpriteTableLayout(
            tableOffset = 0,
            count = 1,
            recordStride = 8,
            frontBankOffset = 1,
            frontPointerOffset = 2,
            backBankOffset = 5,
            backPointerOffset = 6,
        )

        assertNotEquals(ordinary.layoutIdentity, relocatedFields.layoutIdentity)
    }

    @Test
    fun genOneReportsMultipleValidBanksAsTypedAmbiguity() {
        val bytes = ByteArray(0xC000)
        bytes[10] = 0x11
        putU16(bytes, 11, 0x4100)
        putU16(bytes, 13, 0x4100)
        gen1ZeroSprite().copyInto(bytes, 0x4100)
        gen1ZeroSprite().copyInto(bytes, 0x8100)

        val result = SpriteCodec().decode(
            spriteSession(bytes, Platform.GB),
            Gen1SpriteTableLayout(0, 1, 28, candidateBanks = listOf(2, 1)),
        ) as SpriteTableOutcome.Decoded

        val ambiguous = result.rows.single() as SpriteRowOutcome.AmbiguousSources
        assertEquals(listOf(1, 2), ambiguous.sources.map { it.bank })
    }

    @Test
    fun genOneChargesFailedBankAttemptsToTheSharedTableWorkLedger() {
        val bytes = ByteArray(0xC000)
        bytes[10] = 0x11
        putU16(bytes, 11, 0x4100)
        putU16(bytes, 13, 0x4100)
        gen1ZeroSprite(width = 2).copyInto(bytes, 0x4100)
        gen1ZeroSprite(width = 1).copyInto(bytes, 0x8100)

        val result = SpriteCodec(
            SpriteDecodeLimits(maxDecodeWorkPerTable = 900),
        ).decode(
            spriteSession(bytes, Platform.GB),
            Gen1SpriteTableLayout(0, 1, 28, candidateBanks = listOf(1, 2)),
        )

        assertEquals(SpriteBudgetKind.DECODE_WORK, (result as SpriteTableOutcome.BudgetExceeded).budgetKind)
    }

    @Test
    fun unownProofIsBoundToTheRomAndMainLayoutAndRequiresIndependentSites() {
        val noEvidence = spriteSession(ByteArray(0x10000), Platform.GBC)
        assertThrows(IllegalArgumentException::class.java) {
            Gen2UnownIndirectTableEvidence.verifiedDirectCompiledConsumer(
                noEvidence,
                0,
                201,
                6,
                0x8000,
            )
        }
        val sourceBytes = ByteArray(0x10000)
        putGen2CompiledBankedReference(sourceBytes, 0xF000, 0x8000)
        val source = spriteSession(sourceBytes, Platform.GBC)
        val stale = Gen2UnownIndirectTableEvidence.verifiedDirectCompiledConsumer(
            source,
            0,
            201,
            6,
            0x8000,
        )
        val changedBytes = ByteArray(0x10000).also { it[0xFFFF] = 1 }
        repeat(6) { changedBytes[200 * 6 + it] = 0xFF.toByte() }
        val changed = spriteSession(changedBytes, Platform.GBC)

        val result = SpriteCodec().decode(
            changed,
            Gen2SpriteTableLayout(0, 201, unownIndirectTable = stale),
        ) as SpriteTableOutcome.Decoded

        assertTrue(result.rows[200] is SpriteRowOutcome.Malformed)
    }

    @Test
    fun rejectsRowsBeforeOutcomeListAllocationAndMetersEmptyRowWorkAndMetadata() {
        val millionRows = 1_000_000
        val largeEmpty = ByteArray(millionRows * 6)
        val rowBound = SpriteCodec(
            SpriteDecodeLimits(maxRowsPerTable = 8),
        ).decode(
            spriteSession(largeEmpty, Platform.GBC),
            Gen2SpriteTableLayout(0, millionRows.toLong()),
        )
        val workBound = SpriteCodec(
            SpriteDecodeLimits(maxDecodeWorkPerTable = 5),
        ).decode(
            spriteSession(ByteArray(18), Platform.GBC),
            Gen2SpriteTableLayout(0, 3),
        )
        val retainedBound = SpriteCodec(
            SpriteDecodeLimits(maxRetainedBytesPerTable = 1),
        ).decode(
            spriteSession(ByteArray(6), Platform.GBC),
            Gen2SpriteTableLayout(0, 1),
        )

        assertEquals(SpriteBudgetKind.TABLE_ROWS, (rowBound as SpriteTableOutcome.BudgetExceeded).budgetKind)
        assertEquals(SpriteBudgetKind.DECODE_WORK, (workBound as SpriteTableOutcome.BudgetExceeded).budgetKind)
        assertEquals(SpriteBudgetKind.RETAINED_OUTPUT, (retainedBound as SpriteTableOutcome.BudgetExceeded).budgetKind)
    }

    @Test
    fun genOneBoundsBanksBeforeIterationAndChargesInvalidBankAttempts() {
        val oversized = object : AbstractCollection<Int>() {
            override val size: Int = Gen1SpriteTableLayout.MAX_CANDIDATE_BANKS + 1
            override fun iterator(): Iterator<Int> = error("oversized bank collection must not be iterated")
        }
        assertThrows(IllegalArgumentException::class.java) {
            Gen1SpriteTableLayout(0, 1, 28, oversized)
        }

        val bytes = ByteArray(0x8000)
        bytes[10] = 0x11
        putU16(bytes, 11, 0x4100)
        putU16(bytes, 13, 0x4100)
        gen1ZeroSprite().copyInto(bytes, 0x4100)
        val result = SpriteCodec(
            SpriteDecodeLimits(maxDecodeWorkPerTable = 29),
        ).decode(
            spriteSession(bytes, Platform.GB),
            Gen1SpriteTableLayout(0, 1, 28, candidateBanks = listOf(0, 1)),
        )

        assertEquals(SpriteBudgetKind.DECODE_WORK, (result as SpriteTableOutcome.BudgetExceeded).budgetKind)
    }

    @Test
    fun unownProofRequiresActualBoundedCompiledBankAndPointerInstructions() {
        val root = 0x8000
        val validSite = 0xF000
        val embeddedOnlyBytes = ByteArray(0x10000)
        putGen2CompiledBankedReference(embeddedOnlyBytes, root, root)
        val embeddedOnly = spriteSession(embeddedOnlyBytes, Platform.GBC)
        assertThrows(IllegalArgumentException::class.java) {
            Gen2UnownIndirectTableEvidence.verifiedDirectCompiledConsumer(
                embeddedOnly,
                0,
                201,
                6,
                root.toLong(),
            )
        }

        val overBudgetBytes = ByteArray(0x10000)
        putGen2CompiledBankedReference(overBudgetBytes, validSite, root)
        putGen2CompiledBankedReference(overBudgetBytes, validSite + 0x100, root)
        val overBudget = spriteSession(
            overBudgetBytes,
            Platform.GBC,
            limits = ResolutionLimits(maxCompiledReferenceSitesPerCandidate = 1),
        )
        assertThrows(IllegalArgumentException::class.java) {
            Gen2UnownIndirectTableEvidence.verifiedDirectCompiledConsumer(
                overBudget,
                0,
                201,
                6,
                root.toLong(),
            )
        }

        val validBytes = ByteArray(0x10000)
        putGen2CompiledBankedReference(validBytes, validSite, root)
        val valid = spriteSession(validBytes, Platform.GBC)
        val proof = Gen2UnownIndirectTableEvidence.verifiedDirectCompiledConsumer(
            valid,
            0,
            201,
            6,
            root.toLong(),
        )
        assertEquals(listOf(validSite), proof.compiledReferenceSites.offsets)
    }

    @Test
    fun unownProofExcludesTheFullIndirectSpanAtTheActualRecordStride() {
        val bytes = ByteArray(0x10000)
        val root = 0x8000
        val forgedSiteInsideEightByteRows = root + 160
        putGen2CompiledBankedReference(bytes, forgedSiteInsideEightByteRows, root)
        val session = spriteSession(bytes, Platform.GBC)

        assertThrows(IllegalArgumentException::class.java) {
            Gen2UnownIndirectTableEvidence.verifiedDirectCompiledConsumer(
                session = session,
                mainTableOffset = 0,
                mainTableCount = 201,
                mainRecordStride = 8,
                indirectTableOffset = root.toLong(),
            )
        }
    }

    @Test
    fun decodeBudgetsAreWholeTableAndIncludeTileRenderingAndRetainedFrames() {
        val bytes = ByteArray(0x300)
        repeat(2) { row ->
            putGbaPointer(bytes, row * 8, 0x100 + row * 0x40)
            putU16(bytes, row * 8 + 4, 32)
            bytes[0x100 + row * 0x40] = 1
        }
        val layout = GbaSpriteTableLayout(0, 2, 8, GbaGraphicsMode.RAW_4BPP)

        val wholeTable = SpriteCodec(
            SpriteDecodeLimits(maxDecodedBytesPerTable = 191),
        ).decode(spriteSession(bytes), layout)
        val renderWork = SpriteCodec(
            SpriteDecodeLimits(maxDecodeWorkPerTable = 64),
        ).decode(spriteSession(bytes), GbaSpriteTableLayout(0, 1, 8, GbaGraphicsMode.RAW_4BPP))
        val retained = SpriteCodec(
            SpriteDecodeLimits(maxRetainedBytesPerTable = 95),
        ).decode(spriteSession(bytes), GbaSpriteTableLayout(0, 1, 8, GbaGraphicsMode.RAW_4BPP))

        assertEquals(SpriteBudgetKind.DECODE_OUTPUT, (wholeTable as SpriteTableOutcome.BudgetExceeded).budgetKind)
        assertEquals(SpriteBudgetKind.DECODE_WORK, (renderWork as SpriteTableOutcome.BudgetExceeded).budgetKind)
        assertEquals(SpriteBudgetKind.RETAINED_OUTPUT, (retained as SpriteTableOutcome.BudgetExceeded).budgetKind)
    }

    @Test
    fun decodingTwiceProducesEqualImmutableRowEvidenceForValidationAndMaterializationParity() {
        val bytes = ByteArray(0x200)
        putGbaPointer(bytes, 0, 0x100)
        putU16(bytes, 4, 32)
        bytes[0x100] = 1
        val layout = GbaSpriteTableLayout(0, 1, 8, GbaGraphicsMode.RAW_4BPP)
        val codec = SpriteCodec()

        val validation = codec.decode(spriteSession(bytes), layout)
        val materialization = codec.decode(spriteSession(bytes), layout)

        assertEquals(validation, materialization)
    }
}
