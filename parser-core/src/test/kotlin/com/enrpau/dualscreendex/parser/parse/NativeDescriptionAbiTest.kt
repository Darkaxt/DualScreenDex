package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ExactTableLayoutSnapshot
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationException
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import com.enrpau.dualscreendex.parser.dataset.descriptions.DescriptionResolver
import com.enrpau.dualscreendex.parser.dataset.descriptions.ResolvedDescriptionLayout
import com.enrpau.dualscreendex.parser.resolution.DatasetResolution
import com.enrpau.dualscreendex.parser.text.GbInlineDescriptions
import com.enrpau.dualscreendex.parser.text.KoreanGen2PokemonTextCodec
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.dataset.descriptions.DescriptionCodec
import com.enrpau.dualscreendex.parser.dataset.descriptions.DescriptionRowOutcome
import com.enrpau.dualscreendex.parser.dataset.descriptions.DescriptionTableLayout
import com.enrpau.dualscreendex.parser.dataset.descriptions.DescriptionTableOutcome
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomHeader
import com.enrpau.dualscreendex.parser.text.JapanesePokemonTextCodecs
import com.enrpau.dualscreendex.parser.language.*
import com.enrpau.dualscreendex.parser.model.*
import com.enrpau.dualscreendex.parser.catalog.RelationshipMaterializers
import org.junit.Assert.*
import org.junit.Test

class NativeDescriptionAbiTest {
    @Test fun genOneCompiledRootAcceptsNativeInlineMetadata() {
        val bytes = ByteArray(0x8000)
        byteArrayOf(0x21, 0x00, 0x48, 0xFA.toByte(), 0x00, 0xC0.toByte(), 0x3D, 0x5F,
            0x16, 0, 0x19, 0x19, 0x2A, 0x5F, 0x56).copyInto(bytes, 0x4100)
        put16(bytes, 0x4800, 0x5000)
        byteArrayOf(0xC0.toByte(), 0xC8.toByte(), 0x50, 7, 69, 0,
            0xB1.toByte(), 0xB2.toByte(), 0xB3.toByte(), 0x50).copyInto(bytes, 0x5000)
        assertNotNull(Gen1CompiledDescriptionResolver.resolve(
            RomImage(bytes), 1, emptyList(), JapanesePokemonTextCodecs.gen1RedBlue,
        ))
    }

    @Test fun nativeGbaCategoryAndDimensionsUseTwentyEightByteAbi() {
        val bytes = nativeGbaRows()
        val outcome = DescriptionCodec(JapanesePokemonTextCodecs.gen3Later).decode(
            session(bytes), DescriptionTableLayout(0x100, 2, 28, listOf(12)),
        )
        assertTrue(outcome.toString(), outcome is DescriptionTableOutcome.Decoded)
        val row = (outcome as DescriptionTableOutcome.Decoded).rows[1]
        assertTrue(row.toString(), row is DescriptionRowOutcome.Decoded)
        row as DescriptionRowOutcome.Decoded
        assertEquals("たね", row.category)
        assertEquals(7, row.height)
        assertEquals(69, row.weight)
        assertEquals("あいう", row.pages.single().text)
    }

    @Test fun nativeGbaDiscoveryRequiresAndAcceptsCompiledTimesTwentyEight() {
        val bytes = nativeGbaRows()
        // lsl r0,r1,3; sub r0,r0,r1; lsl r0,r0,2; ldr r1,[pc,#4]; add r0,r0,r1
        listOf(0x00C8, 0x1A40, 0x0080, 0x4901, 0x1840, 0x4770).forEachIndexed { i, v ->
            put16(bytes, 0x40 + i * 2, v)
        }
        put32(bytes, 0x4C, 0x08000100)
        val evidence = DatasetResolvers.gen3Descriptions(session(bytes), 2, null, JapanesePokemonTextCodecs.gen3Later)
        assertTrue(evidence.toString(), evidence.compatible)
        assertEquals(0x100, evidence.offset)
        assertEquals(28, evidence.recordSize)
    }

    @Test fun genTwoJapaneseSplitConsumerPreservesSpecies99And100() {
        val bytes = ByteArray(0x10000)
        listOf(0x21,0,0x48,0xfa,0,0xc0,0xfe,100,0x38,5,0xd6,99,0x21,0,0x60,
            0x3d,0x5f,0x16,0,0x19,0x19,0x5e,0x23,0x56).forEachIndexed { i,v -> bytes[0x4100+i]=v.toByte() }
        repeat(251) { i ->
            val root = if (i < 99) 0x4800+i*2 else 0x6000+(i-99)*2
            val entry = if (i < 99) 0x4900+i*12 else 0x6200+(i-99)*12
            put16(bytes, root, entry)
            byteArrayOf(0xc0.toByte(),0xc8.toByte(),0x50,7,69,0,0xb1.toByte(),0xb2.toByte(),0x5f).copyInto(bytes,entry)
        }
        val table = Gen2CompiledDescriptionResolver.resolve(session(bytes),251,JapanesePokemonTextCodecs.gen2)
        assertNotNull(table)
        val entries = GbInlineDescriptions.entries(RomImage(bytes),table!!.gbDescriptions!!)
        assertEquals(0x4900+98*12,entries[98]!!.offset)
        assertEquals(0x6200,entries[99]!!.offset)
        assertEquals("あい",GbInlineDescriptions.decode(
            RomImage(bytes),entries[99]!!,JapanesePokemonTextCodecs.gen2)!!.text)
        bytes[0x4100+11]=98
        assertNull(Gen2CompiledDescriptionResolver.resolve(session(bytes),251,JapanesePokemonTextCodecs.gen2))
        bytes[0x4100+11]=99
        bytes.copyInto(bytes,0x4200,0x4100,0x4118)
        bytes.copyInto(bytes,0x7000,0x4800,0x4800+198)
        bytes.copyInto(bytes,0x7200,0x6000,0x6000+304)
        put16(bytes,0x4201,0x7000); put16(bytes,0x420d,0x7200)
        assertNull(Gen2CompiledDescriptionResolver.resolve(session(bytes),251,JapanesePokemonTextCodecs.gen2))
    }

    @Test fun genTwoKoreanConsumerPreservesSpecies128And129AndFirstPair() {
        val bytes = ByteArray(0x10000)
        listOf(0x21,0,0x48,0x78,0x3d,0x06,0,0x4f,0x09,0x09,0x07,0xe6,1,0xc6,2,0x47,0x2a,0x66,0x6f,0xc9)
            .forEachIndexed { i,v -> bytes[0x4100+i]=v.toByte() }
        repeat(251) { i ->
            val entry = 0x8000+(i/128)*0x4000+(i%128)*16
            put16(bytes,0x4800+i*2,0x4000+(i%128)*16)
            byteArrayOf(6,0xbe.toByte(),6,0xd1.toByte(),0x50,7,69,0,3,0x4e,0x50).copyInto(bytes,entry)
        }
        val codec = KoreanGen2PokemonTextCodec.codec
        val table = Gen2CompiledDescriptionResolver.resolve(session(bytes),251,codec)
        assertNotNull(table)
        val entries = GbInlineDescriptions.entries(RomImage(bytes),table!!.gbDescriptions!!)
        assertEquals(0x87f0,entries[127]!!.offset)
        assertEquals(0xc000,entries[128]!!.offset)
        assertEquals(codec.decode(byteArrayOf(3,0x4e,0x50)),GbInlineDescriptions.decode(
            RomImage(bytes),entries[128]!!,codec)!!.text)
        bytes[0x410c]=3
        assertNull(Gen2CompiledDescriptionResolver.resolve(session(bytes),251,codec))
    }

    @Test fun nativeGbMaterializationUsesInlineHeightWeightAndProse() {
        val bytes = ByteArray(0x8000)
        put16(bytes,0x4800,0x5000)
        byteArrayOf(0xc0.toByte(),0xc8.toByte(),0x50,7,69,0,0xb1.toByte(),0xb2.toByte(),0x5f).copyInto(bytes,0x5000)
        val table = TableLayout(0x4800,1,2,gbDescriptions = GbInlineDescriptionLayout(GbDescriptionSegment(0x4800,1,1)))
        val codec = JapanesePokemonTextCodecs.gen2
        val manifest = RomLanguageManifest(codec.language,listOf(RomLanguageProjection(codec.language,codec.id,codec.version,
            LocalizedTableLayout(descriptions=table),emptyList(),LanguageResolutionStatus.RESOLVED)),LanguageResolutionStatus.RESOLVED)
        val layout = ResolvedRomLayout(EngineFamily.GOLD_SILVER,2,Platform.GBC,1,1,ProfileTables(descriptions=table),languageManifest=manifest)
        val row = RelationshipMaterializers.descriptions(RomImage(bytes),layout)[1]
        assertNotNull(row)
        assertEquals(7,row!!.height); assertEquals(69,row.weight); assertEquals("あい",row.text)
        assertTrue(RelationshipMaterializers.descriptions(RomImage(bytes),layout.copy(languageManifest=RomLanguageManifest.UNKNOWN)).isEmpty())
    }

    @Test fun nativeGbaDiscoveryRejectsUnprovenInheritedAndWrongScaleAndAmbiguousRoots() {
        val codec = JapanesePokemonTextCodecs.gen3Later
        val bytes = nativeGbaRows()
        val inherited = TableLayout(0x100,2,28,pointerOffsets=listOf(12))
        assertFalse(DatasetResolvers.gen3Descriptions(session(bytes),2,inherited,codec).compatible)
        putConsumer28(bytes,0x40,0x100)
        put16(bytes,0x42,0x1840) // addition yields x36, not x28
        assertFalse(DatasetResolvers.gen3Descriptions(session(bytes),2,inherited,codec).compatible)
        putConsumer28(bytes,0x40,0x100)
        bytes.copyInto(bytes,0x200,0x100,0x138)
        putConsumer28(bytes,0x80,0x200)
        assertTrue(DatasetResolvers.gen3Descriptions(session(bytes),2,null,codec).ambiguous)
        val typed = DescriptionResolver(DescriptionCodec(codec),codec)
            .resolve(session(nativeGbaRows()),2,selectedLayout=DescriptionTableLayout(0x100,2,28,listOf(12)))
        assertTrue(typed is DatasetResolution.Unavailable)
    }

    @Test fun nativeGbaTypedDiscoveryAgreesWithLegacyAndRejectsMalformedFields() {
        val codec = JapanesePokemonTextCodecs.gen3Later
        val bytes = nativeGbaRows()
        putConsumer28(bytes,0x40,0x100)
        val typed = DescriptionResolver(DescriptionCodec(codec),codec)
            .resolve(session(bytes),2)
        assertTrue(typed.toString(),typed is DatasetResolution.Resolved)
        val layout = DescriptionTableLayout(0x100,2,28,listOf(12))
        repeat(6) { bytes[0x11c+it]=1 }
        var rows = (DescriptionCodec(codec).decode(session(bytes),layout) as DescriptionTableOutcome.Decoded).rows
        assertTrue(rows[1] is DescriptionRowOutcome.Malformed)
        bytes[0x11e]=0
        // FF in a control parameter is not the page terminator; never borrow from next page.
        byteArrayOf(1,2,3,0xf7.toByte()).copyInto(bytes,0x600)
        put32(bytes,0x11c+12,0x08000604)
        byteArrayOf(1,2,0xff.toByte()).copyInto(bytes,0x604)
        rows = (DescriptionCodec(codec).decode(session(bytes),layout) as DescriptionTableOutcome.Decoded).rows
        assertTrue(rows[0] is DescriptionRowOutcome.Malformed)
        assertTrue(rows[1] is DescriptionRowOutcome.Decoded)
    }

    @Test fun nativeGbDoesNotBorrowNextRecordOrKoreanTrailTerminator() {
        val bytes = ByteArray(0x8000)
        byteArrayOf(0xc0.toByte(),0x50,7,69,0,0xb1.toByte(),0xb2.toByte(),0x5f,0xc8.toByte(),0x50).copyInto(bytes,0x5000)
        val gb = GbInlineDescriptions
        assertEquals("あい",gb.decode(RomImage(bytes),GbInlineDescriptions.Entry(0x5000,0x5008),JapanesePokemonTextCodecs.gen2)!!.text)
        bytes[0x5007]=0xb3.toByte()
        assertNull(gb.decode(RomImage(bytes),GbInlineDescriptions.Entry(0x5000,0x5008),JapanesePokemonTextCodecs.gen2))
        val ko = KoreanGen2PokemonTextCodec.codec
        byteArrayOf(6,0xbe.toByte(),0x50,7,69,0,3,0x50,3,0x4e,0x50).copyInto(bytes,0x5000)
        // Reserved trail 50 is one malformed pair, never an early standalone terminator.
        assertEquals(1,ko.decodeDetailed(byteArrayOf(3,0x50,3,0x4e,0x50)).invalidUnits)
        assertNull(gb.decode(RomImage(bytes),GbInlineDescriptions.Entry(0x5000,0x500b),ko))
        assertNull(gb.decode(RomImage(bytes),GbInlineDescriptions.Entry(0x5000,0x5009),ko))
        byteArrayOf(3,0x4e,0x50).copyInto(bytes,0x5006)
        assertNull(gb.decode(RomImage(bytes),GbInlineDescriptions.Entry(0x5000,0x5007),ko))
    }

    @Test fun descriptionSegmentMetadataSurvivesCopiesAndExactSnapshots() {
        val inline = GbInlineDescriptionLayout(GbDescriptionSegment(0x4800,99,1),GbDescriptionSegment(0x6000,152,1))
        val table = TableLayout(0x4800,251,2,gbDescriptions=inline)
        val snapshot = ExactTableLayoutSnapshot.from(table)
        assertEquals(inline,snapshot.gbDescriptions)
        assertEquals(snapshot,ExactTableLayoutSnapshot.from(table.copy()))
        assertNotEquals(snapshot,ExactTableLayoutSnapshot.from(table.copy(gbDescriptions=null)))
        assertEquals(table,LocalizedTableLayout(descriptions=table).descriptions)
    }

    @Test(expected=ParserCancellationException::class)
    fun nativeGbaLegacyDiscoveryPropagatesCancellationAfterIndexBuild() {
        val bytes = nativeGbaRows(); putConsumer28(bytes,0x40,0x100)
        var cancelled = false
        val session = RomAnalysisSession(RomImage(bytes),RomHeader(Platform.GBA,""),
            cancellation=ParserCancellationToken {
                if (cancelled) throw ParserCancellationException()
            })
        session.gbaReferenceIndex!!.counts
        cancelled=true
        DatasetResolvers.gen3Descriptions(session,2,null,JapanesePokemonTextCodecs.gen3Later)
    }

    @Test(expected=ParserCancellationException::class)
    fun nativeGbInlineDecodingPropagatesCancellation() {
        GbInlineDescriptions.decode(RomImage(ByteArray(0x8000)),
            GbInlineDescriptions.Entry(0x5000,0x5010),JapanesePokemonTextCodecs.gen2,
            ParserCancellationToken { throw ParserCancellationException() })
    }

    @Test fun nativeGbaUnknownLanguageKeepsOnlyDimensions() {
        val bytes = nativeGbaRows()
        val table = DescriptionTableLayout(0x100,2,28,listOf(12))
        val decoded = DescriptionCodec(JapanesePokemonTextCodecs.gen3Later).decode(session(bytes),table) as DescriptionTableOutcome.Decoded
        val layout = ResolvedRomLayout(EngineFamily.EMERALD,3,Platform.GBA,2,1,ProfileTables(),
            resolvedDatasets=ResolvedDatasetLayouts(descriptions=ResolvedDescriptionLayout(table,decoded.rows)))
        val row = RelationshipMaterializers.descriptions(RomImage(bytes),layout)[1]!!
        assertEquals(7,row.height); assertEquals(69,row.weight)
        assertNull(row.text); assertNull(row.category)
    }

    @Test fun nativeGbEntryBoundsStopAtTheBankBoundary() {
        val bytes = ByteArray(0xc000)
        put16(bytes,0x4800,0x7ff8)
        byteArrayOf(0xc0.toByte(),0x50,7,69,0,0xb1.toByte(),0xb2.toByte(),0xb3.toByte(),0x50).copyInto(bytes,0x7ff8)
        val entries = GbInlineDescriptions.entries(RomImage(bytes),
            GbInlineDescriptionLayout(GbDescriptionSegment(0x4800,1,1)))
        assertEquals(0x8000,entries.single()!!.endExclusive)
        assertNull(GbInlineDescriptions.decode(RomImage(bytes),entries.single()!!,JapanesePokemonTextCodecs.gen2))
    }

    @Test(expected=ParserCancellationException::class)
    fun nativeGenTwoDiscoveryPropagatesCancellation() {
        val session = RomAnalysisSession(RomImage(ByteArray(0x8000)),RomHeader(Platform.GBC,""),
            cancellation=ParserCancellationToken { throw ParserCancellationException() })
        Gen2CompiledDescriptionResolver.resolve(session,251,JapanesePokemonTextCodecs.gen2)
    }

    @Test(expected=ParserCancellationException::class)
    fun nativeGenOneDiscoveryPropagatesCancellation() {
        Gen1CompiledDescriptionResolver.resolve(RomImage(ByteArray(0x8000)),151,emptyList(),JapanesePokemonTextCodecs.gen1RedBlue,
            ParserCancellationToken { throw ParserCancellationException() })
    }

    @Test fun nativeGenTwoDiscoveryHonorsExtentBudget() {
        val bytes = ByteArray(0x10000)
        listOf(0x21,0,0x48,0x78,0x3d,0x06,0,0x4f,0x09,0x09,0x07,0xe6,1,0xc6,2,0x47,0x2a,0x66,0x6f,0xc9)
            .forEachIndexed { i,v -> bytes[0x4100+i]=v.toByte() }
        put16(bytes,0x4800,0x4000)
        byteArrayOf(6,0xbe.toByte(),6,0xd1.toByte(),0x50,7,69,0,3,0x4e,0x50).copyInto(bytes,0x8000)
        val limited = RomAnalysisSession(RomImage(bytes),RomHeader(Platform.GBC,""),
            limits=ResolutionLimits(maxDatasetExtentBytes=1))
        assertNull(Gen2CompiledDescriptionResolver.resolve(limited,1,KoreanGen2PokemonTextCodec.codec))
    }

    @Test fun nativeGbaLegacyDiscoveryHonorsExtentBudget() {
        val bytes = nativeGbaRows(); putConsumer28(bytes,0x40,0x100)
        val limited = RomAnalysisSession(RomImage(bytes),RomHeader(Platform.GBA,""),
            limits=ResolutionLimits(maxDatasetExtentBytes=1))
        val evidence = DatasetResolvers.gen3Descriptions(limited,2,null,JapanesePokemonTextCodecs.gen3Later)
        assertFalse(evidence.compatible)
        assertTrue(evidence.reviewRecommended)
    }

    private fun putConsumer28(bytes: ByteArray, offset: Int, root: Int) {
        listOf(0x00c8,0x1a40,0x0080,0x4901,0x1840,0x4770).forEachIndexed { i,v -> put16(bytes,offset+i*2,v) }
        put32(bytes,offset+12,0x08000000+root)
    }

    private fun nativeGbaRows(): ByteArray = ByteArray(0x1000).also { bytes ->
        repeat(2) { i ->
            val row = 0x100 + i * 28
            bytes[row] = 0x10; bytes[row + 1] = 0x18
            put16(bytes, row + 6, if (i == 0) 0 else 7)
            put16(bytes, row + 8, if (i == 0) 0 else 69)
            put32(bytes, row + 12, 0x08000600 + i * 16)
            byteArrayOf(1, 2, 3, 0xFF.toByte()).copyInto(bytes, 0x600 + i * 16)
        }
    }

    private fun session(bytes: ByteArray) = RomAnalysisSession(RomImage(bytes), RomHeader(Platform.GBA, ""))
    private fun put16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte(); bytes[offset + 1] = (value ushr 8).toByte()
    }
    private fun put32(bytes: ByteArray, offset: Int, value: Int) {
        repeat(4) { bytes[offset + it] = (value ushr (8 * it)).toByte() }
    }
}
