package com.enrpau.dualscreendex.parser.dataset.media

import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import com.enrpau.dualscreendex.parser.resolution.BudgetKind
import com.enrpau.dualscreendex.parser.resolution.DatasetResolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpriteResolverTest {
    @Test
    fun directCompiledEvidenceOutranksPublishedCompiledInheritedAndStructuralCandidates() {
        val bytes = rawTables(5)
        val layouts = (0 until 5).map { row ->
            GbaSpriteTableLayout((row * 8).toLong(), 1, 8, GbaGraphicsMode.RAW_4BPP)
        }
        val session = spriteSession(bytes, references = mapOf(8 to 2, 16 to 3))
        val result = SpriteResolver().resolve(
            session = session,
            semanticDomain = SpriteSemanticDomain(1, setOf(0)),
            directCompiledConsumerLayouts = listOf(layouts[0]),
            publishedLayouts = listOf(layouts[1]),
            compiledReferenceLayouts = listOf(layouts[2]),
            inheritedLayouts = listOf(layouts[3]),
            structuralLayouts = listOf(layouts[4]),
            allowStructuralAnchors = true,
        ) as DatasetResolution.Resolved

        assertEquals(layouts[0].layoutIdentity, result.candidate.layout.table.layoutIdentity)
    }

    @Test
    fun validatedDirectCandidatesResolveBeforeLowerAuthorityReferenceIndexConstruction() {
        val bytes = rawTables(2)
        var indexBuilds = 0
        val direct = GbaSpriteTableLayout(0, 1, 8, GbaGraphicsMode.RAW_4BPP)
        val compiled = GbaSpriteTableLayout(8, 1, 8, GbaGraphicsMode.RAW_4BPP)
        val session = spriteSession(
            bytes,
            references = mapOf(8 to 99),
            onReferenceIndexBuild = { indexBuilds++ },
        )

        val result = SpriteResolver().resolve(
            session,
            SpriteSemanticDomain(1, setOf(0)),
            directCompiledConsumerLayouts = listOf(direct),
            compiledReferenceLayouts = listOf(compiled),
        )

        assertTrue(result is DatasetResolution.Resolved)
        assertEquals(0, indexBuilds)
    }

    @Test
    fun matchingExactProfileIsTheFastPathButStillUsesTheCodec() {
        val bytes = rawTables(2)
        val exact = GbaSpriteTableLayout(0, 1, 8, GbaGraphicsMode.RAW_4BPP)
        val inherited = GbaSpriteTableLayout(8, 1, 8, GbaGraphicsMode.RAW_4BPP)
        val session = spriteSession(bytes, exactLayout = exact)

        val result = SpriteResolver().resolve(
            session,
            SpriteSemanticDomain(1, setOf(0)),
            exactLayouts = listOf(exact),
            inheritedLayouts = listOf(inherited),
        ) as DatasetResolution.Resolved

        assertEquals(exact.layoutIdentity, result.candidate.layout.table.layoutIdentity)
        assertTrue(result.candidate.layout.rows.single() is SpriteRowOutcome.Decoded)
    }

    @Test
    fun equalIndependentCandidatesRemainAmbiguousRegardlessOfOffsetOrder() {
        val bytes = rawTables(2)
        val low = GbaSpriteTableLayout(0, 1, 8, GbaGraphicsMode.RAW_4BPP)
        val high = GbaSpriteTableLayout(8, 1, 8, GbaGraphicsMode.RAW_4BPP)
        val session = spriteSession(bytes)
        val resolver = SpriteResolver()

        val first = resolver.resolve(
            session,
            SpriteSemanticDomain(1, setOf(0)),
            inheritedLayouts = listOf(low, high),
        ) as DatasetResolution.Ambiguous
        val second = resolver.resolve(
            session,
            SpriteSemanticDomain(1, setOf(0)),
            inheritedLayouts = listOf(high, low),
        ) as DatasetResolution.Ambiguous

        assertEquals(first.candidates.map { it.layoutIdentity }, second.candidates.map { it.layoutIdentity })
    }

    @Test
    fun inactiveExpansionRowsDoNotHideAnActiveMalformedRow() {
        val bytes = ByteArray(0x200)
        putGbaPointer(bytes, 8, 0x1F8)
        putU16(bytes, 12, 32)
        val layout = GbaSpriteTableLayout(0, 2, 8, GbaGraphicsMode.RAW_4BPP)

        val result = SpriteResolver().resolve(
            spriteSession(bytes),
            SpriteSemanticDomain(2, setOf(1)),
            inheritedLayouts = listOf(layout),
        ) as DatasetResolution.Partial

        assertTrue(result.reasons.any { it.contains("semantic coverage is 0/1") })
        assertTrue(result.candidate.layout.rows[0] is SpriteRowOutcome.StructuralEmpty)
        assertTrue(result.candidate.layout.rows[1] is SpriteRowOutcome.Malformed)
    }

    @Test
    fun decoderBudgetExhaustionIsReturnedAsTypedResolutionEvidence() {
        val bytes = ByteArray(0x200)
        putGbaPointer(bytes, 0, 0x100)
        putU16(bytes, 4, 32)
        gbaLiteral(ByteArray(32)).copyInto(bytes, 0x100)
        val resolver = SpriteResolver(SpriteCodec(SpriteDecodeLimits(maxDecodeWorkPerTable = 8)))

        val result = resolver.resolve(
            spriteSession(bytes),
            SpriteSemanticDomain(1, setOf(0)),
            inheritedLayouts = listOf(GbaSpriteTableLayout(0, 1, 8, GbaGraphicsMode.LZ77_4BPP)),
        ) as DatasetResolution.BudgetExceeded

        assertEquals(BudgetKind.PROBE_WORK, result.budgetKind)
    }

    @Test
    fun probeAttemptsAreBoundedEvenWhenLayoutsShareOneRoot() {
        val bytes = rawTables(1)
        val session = spriteSession(
            bytes,
            limits = ResolutionLimits(maxProbeWorkPerDataset = 1),
        )
        val root = 0L

        val result = SpriteResolver().resolve(
            session,
            SpriteSemanticDomain(1, setOf(0)),
            inheritedLayouts = listOf(
                GbaSpriteTableLayout(root, 1, 8, GbaGraphicsMode.RAW_4BPP),
                GbaSpriteTableLayout(root, 1, 8, GbaGraphicsMode.LZ77_4BPP),
            ),
        ) as DatasetResolution.BudgetExceeded

        assertEquals(BudgetKind.PROBE_WORK, result.budgetKind)
    }

    private fun rawTables(count: Int): ByteArray {
        val bytes = ByteArray(0x400)
        repeat(count) { row ->
            putGbaPointer(bytes, row * 8, 0x100 + row * 0x20)
            putU16(bytes, row * 8 + 4, 32)
            bytes[0x100 + row * 0x20] = 1
        }
        return bytes
    }
}
