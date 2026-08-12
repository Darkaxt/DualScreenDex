package com.enrpau.dualscreendex.parser.dataset.media

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.resolution.CandidateLayoutIdentity
import com.enrpau.dualscreendex.parser.resolution.CompiledReferenceSites
import com.enrpau.dualscreendex.parser.resolution.ImmutableDatasetLayout
import java.util.Collections

enum class GbaGraphicsMode { RAW_4BPP, LZ77_4BPP, SMOL_4BPP }

enum class GbaPaletteMode { RAW_BGR555, LZ77_BGR555, SMOL_BGR555 }

enum class SpriteBudgetKind {
    TABLE_EXTENT,
    TABLE_ROWS,
    COMPRESSED_INPUT,
    DECODE_OUTPUT,
    DECODE_WORK,
    RETAINED_OUTPUT,
}

data class SpriteDecodeLimits(
    val maxRowsPerTable: Int = 1_048_576,
    val maxCompressedBytesPerTable: Int = 32 * 1_024 * 1_024,
    val maxDecodedBytesPerTable: Int = 64 * 1_024 * 1_024,
    val maxDecodeWorkPerTable: Int = 256 * 1_024 * 1_024,
    val maxRetainedBytesPerTable: Int = 64 * 1_024 * 1_024,
) {
    init {
        require(maxRowsPerTable > 0) { "sprite table row budget must be positive" }
        require(maxCompressedBytesPerTable > 0) { "sprite table compressed-input budget must be positive" }
        require(maxDecodedBytesPerTable > 0) { "sprite table decoded-output budget must be positive" }
        require(maxDecodeWorkPerTable > 0) { "sprite table decode-work budget must be positive" }
        require(maxRetainedBytesPerTable > 0) { "sprite table retained-output budget must be positive" }
    }
}

sealed class SpriteTableLayout : ImmutableDatasetLayout<SpriteTableLayout> {
    abstract val tableOffset: Long
    abstract val count: Long
    abstract val recordStride: Int

    final override fun immutableSnapshot(): SpriteTableLayout = this
}

class Gen1SpriteTableLayout(
    override val tableOffset: Long,
    override val count: Long,
    override val recordStride: Int,
    candidateBanks: Collection<Int>,
    val dimensionsOffset: Int = 10,
    val frontPointerOffset: Int = 11,
    val backPointerOffset: Int = 13,
) : SpriteTableLayout() {
    val candidateBanks: List<Int> = boundedCandidateBanks(candidateBanks)
    override val layoutIdentity: CandidateLayoutIdentity = CandidateLayoutIdentity(
        "sprite:gen1:${tableOffset.toString(16)}:$count:$recordStride:" +
            "d$dimensionsOffset:f$frontPointerOffset:b$backPointerOffset:" +
            this.candidateBanks.joinToString(","),
    )

    init {
        require(tableOffset >= 0 && count > 0 && recordStride > 0)
        require(this.candidateBanks.isNotEmpty() && this.candidateBanks.all { it >= 0 })
        require(dimensionsOffset >= 0 && dimensionsOffset < recordStride)
        require(frontPointerOffset >= 0 && frontPointerOffset + 2 <= recordStride)
        require(backPointerOffset >= 0 && backPointerOffset + 2 <= recordStride)
    }

    override fun equals(other: Any?): Boolean = other is Gen1SpriteTableLayout &&
        tableOffset == other.tableOffset && count == other.count && recordStride == other.recordStride &&
        candidateBanks == other.candidateBanks && dimensionsOffset == other.dimensionsOffset &&
        frontPointerOffset == other.frontPointerOffset && backPointerOffset == other.backPointerOffset

    override fun hashCode(): Int = layoutIdentity.hashCode()

    companion object {
        /** Maximum physical banks addressable by the MBC5 family used by supported GB/GBC ROMs. */
        const val MAX_CANDIDATE_BANKS = 512

        private fun boundedCandidateBanks(candidateBanks: Collection<Int>): List<Int> {
            require(candidateBanks.size in 1..MAX_CANDIDATE_BANKS) {
                "Gen 1 candidate-bank count must be within 1..$MAX_CANDIDATE_BANKS"
            }
            val distinct = LinkedHashSet<Int>(candidateBanks.size)
            candidateBanks.forEach { bank ->
                require(bank >= 0) { "Gen 1 candidate banks must not be negative" }
                distinct += bank
            }
            return immutableList(distinct.sorted())
        }
    }
}

enum class Gen2UnownIndirectAuthority { DIRECT_COMPILED_CONSUMER }

/**
 * Session-bound proof that the FFx6 Unown row redirects to an independently referenced table.
 * The constructor is private so a bare offset can never become an indirect-table candidate.
 */
class Gen2UnownIndirectTableEvidence private constructor(
    val romSha256: String,
    val mainTableOffset: Long,
    val mainTableCount: Long,
    val mainRecordStride: Int,
    val indirectTableOffset: Long,
    val authority: Gen2UnownIndirectAuthority,
    val compiledReferenceSites: CompiledReferenceSites,
) {
    val identity: String = "${authority.name}:${romSha256}:" +
        "m${mainTableOffset.toString(16)}:c$mainTableCount:s$mainRecordStride:" +
        "i${indirectTableOffset.toString(16)}:" +
        compiledReferenceSites.offsets.joinToString(",") { it.toString(16) }

    init {
        require(romSha256.length == 64 && romSha256.all { it in '0'..'9' || it in 'a'..'f' })
        require(mainTableOffset >= 0 && mainTableCount > 0 && mainRecordStride >= GEN2_POINTER_ROW_BYTES)
        require(indirectTableOffset >= 0)
        require(!compiledReferenceSites.budgetExceeded)
        require(compiledReferenceSites.offsets.isNotEmpty()) {
            "Gen 2 Unown indirection requires independent compiled-reference sites"
        }
    }

    internal fun isBoundTo(session: RomAnalysisSession, layout: Gen2SpriteTableLayout): Boolean =
        romSha256 == session.rom.sha256 &&
            mainTableOffset == layout.tableOffset &&
            mainTableCount == layout.count &&
            mainRecordStride == layout.recordStride &&
            indirectTableOffset < session.rom.size.toLong() &&
            compiledReferenceSites.offsets.all { site -> site.toLong() + GEN2_REFERENCE_INSTRUCTION_BYTES <= session.rom.size }

    override fun equals(other: Any?): Boolean = other is Gen2UnownIndirectTableEvidence &&
        identity == other.identity

    override fun hashCode(): Int = identity.hashCode()

    companion object {
        fun verifiedDirectCompiledConsumer(
            session: RomAnalysisSession,
            mainTableOffset: Long,
            mainTableCount: Long,
            mainRecordStride: Int,
            indirectTableOffset: Long,
        ): Gen2UnownIndirectTableEvidence {
            require(mainTableOffset < session.rom.size.toLong())
            require(indirectTableOffset < session.rom.size.toLong())
            require(indirectTableOffset >= GB_SWITCHABLE_BANK_START)
            val mainTableEnd = Math.addExact(
                mainTableOffset,
                Math.multiplyExact(mainTableCount, mainRecordStride.toLong()),
            )
            val indirectTableEnd = Math.addExact(
                indirectTableOffset,
                Math.multiplyExact(
                    Gen2SpriteTableLayout.UNOWN_FORM_COUNT.toLong(),
                    mainRecordStride.toLong(),
                ),
            )
            require(mainTableEnd <= session.rom.size.toLong())
            require(indirectTableEnd <= session.rom.size.toLong())
            val compiledReferenceSites = CompiledReferenceSites.of(
                verifiedReferenceSites(
                    session,
                    indirectTableOffset,
                    mainTableOffset,
                    mainTableEnd,
                    indirectTableEnd,
                ),
                session.limits.maxCompiledReferenceSitesPerCandidate,
            )
            require(!compiledReferenceSites.budgetExceeded) {
                "Gen 2 Unown compiled-reference sites exceed the session budget"
            }
            require(compiledReferenceSites.offsets.isNotEmpty()) {
                "Gen 2 Unown indirect root has no independently verified compiled-reference sites"
            }
            return Gen2UnownIndirectTableEvidence(
                romSha256 = session.rom.sha256,
                mainTableOffset = mainTableOffset,
                mainTableCount = mainTableCount,
                mainRecordStride = mainRecordStride,
                indirectTableOffset = indirectTableOffset,
                authority = Gen2UnownIndirectAuthority.DIRECT_COMPILED_CONSUMER,
                compiledReferenceSites = compiledReferenceSites,
            )
        }

        private fun verifiedReferenceSites(
            session: RomAnalysisSession,
            targetOffset: Long,
            mainStart: Long,
            mainEnd: Long,
            indirectEnd: Long,
        ): Sequence<Int> = sequence {
            var site = 0
            val lastSite = session.rom.size - GEN2_REFERENCE_INSTRUCTION_BYTES.toInt()
            while (site <= lastSite) {
                if (
                    isIndependentCompiledSite(site, mainStart, mainEnd, targetOffset, indirectEnd) &&
                    verifiesBankedReferenceInstruction(session, site, targetOffset)
                ) {
                    yield(site)
                }
                site++
            }
        }

        private fun isIndependentCompiledSite(
            site: Int,
            mainStart: Long,
            mainEnd: Long,
            indirectStart: Long,
            indirectEnd: Long,
        ): Boolean {
            val siteStart = site.toLong()
            val siteEnd = siteStart + GEN2_REFERENCE_INSTRUCTION_BYTES
            val outsideMain = siteEnd <= mainStart || siteStart >= mainEnd
            val outsideIndirect = siteEnd <= indirectStart || siteStart >= indirectEnd
            return outsideMain && outsideIndirect
        }

        private fun verifiesBankedReferenceInstruction(
            session: RomAnalysisSession,
            site: Int,
            targetOffset: Long,
        ): Boolean {
            if (site < 0 || site.toLong() + GEN2_REFERENCE_INSTRUCTION_BYTES > session.rom.size.toLong()) return false
            val bank = targetOffset / GB_BANK_SIZE
            if (bank !in 1..0xFF) return false
            val localAddress = GB_SWITCHABLE_BANK_START + targetOffset % GB_BANK_SIZE
            if (localAddress > 0x7FFF) return false
            return session.rom.u8(site) == LOAD_A_IMMEDIATE &&
                session.rom.u8(site + 1) == bank.toInt() &&
                session.rom.u8(site + 2) in LOAD_16_BIT_IMMEDIATE_OPCODES &&
                session.rom.u16le(site + 3) == localAddress.toInt()
        }

        private const val GEN2_POINTER_ROW_BYTES = 6
        private const val GEN2_REFERENCE_INSTRUCTION_BYTES = 5L
        private const val GB_BANK_SIZE = 0x4000L
        private const val GB_SWITCHABLE_BANK_START = 0x4000L
        private const val LOAD_A_IMMEDIATE = 0x3E
        private val LOAD_16_BIT_IMMEDIATE_OPCODES = setOf(0x01, 0x11, 0x21, 0x31)
    }
}

class Gen2SpriteTableLayout(
    override val tableOffset: Long,
    override val count: Long,
    override val recordStride: Int = 6,
    val bankAdjustment: Int = 0,
    bankRemap: Map<Int, Int> = emptyMap(),
    dimensionsByRow: Map<Int, Int> = emptyMap(),
    val unownIndirectTable: Gen2UnownIndirectTableEvidence? = null,
    val frontBankOffset: Int = 0,
    val frontPointerOffset: Int = 1,
    val backBankOffset: Int = 3,
    val backPointerOffset: Int = 4,
) : SpriteTableLayout() {
    val bankRemap: Map<Int, Int> = immutableMap(bankRemap.toSortedMap())
    val dimensionsByRow: Map<Int, Int> = immutableMap(dimensionsByRow.toSortedMap())
    override val layoutIdentity: CandidateLayoutIdentity = CandidateLayoutIdentity(
        "sprite:gen2:${tableOffset.toString(16)}:$count:$recordStride:a$bankAdjustment:" +
            "u${unownIndirectTable?.identity ?: "none"}:r$UNOWN_SPECIES_ROW:n$UNOWN_FORM_COUNT:" +
            "fb$frontBankOffset:fp$frontPointerOffset:bb$backBankOffset:bp$backPointerOffset:" +
            "m${this.bankRemap.entries.joinToString(",") { "${it.key}>${it.value}" }}:" +
            "d${this.dimensionsByRow.entries.joinToString(",") { "${it.key}>${it.value}" }}",
    )

    init {
        require(tableOffset >= 0 && count > 0 && recordStride > 0)
        require(frontBankOffset >= 0 && frontBankOffset < recordStride)
        require(frontPointerOffset >= 0 && frontPointerOffset + 2 <= recordStride)
        require(backBankOffset >= 0 && backBankOffset < recordStride)
        require(backPointerOffset >= 0 && backPointerOffset + 2 <= recordStride)
        require(this.bankRemap.all { (stored, mapped) -> stored in 0..255 && mapped >= 0 })
        require(this.dimensionsByRow.all { (row, width) -> row >= 0 && row.toLong() < count && width in 1..15 })
    }

    override fun equals(other: Any?): Boolean = other is Gen2SpriteTableLayout &&
        tableOffset == other.tableOffset && count == other.count && recordStride == other.recordStride &&
        bankAdjustment == other.bankAdjustment && bankRemap == other.bankRemap &&
        dimensionsByRow == other.dimensionsByRow && unownIndirectTable == other.unownIndirectTable &&
        frontBankOffset == other.frontBankOffset && frontPointerOffset == other.frontPointerOffset &&
        backBankOffset == other.backBankOffset && backPointerOffset == other.backPointerOffset

    override fun hashCode(): Int = layoutIdentity.hashCode()

    companion object {
        const val UNOWN_FORM_COUNT = 26
        const val UNOWN_SPECIES_ROW = 200
        const val POINTER_ROW_BYTES = 6
    }
}

class GbaPaletteLayout(
    val tableOffset: Long,
    val recordStride: Int,
    val mode: GbaPaletteMode,
    val pointerOffset: Int = 0,
    val rowTagOffset: Int = 4,
    val requireRowTag: Boolean = false,
) {
    val identity: String = "${tableOffset.toString(16)}:$recordStride:${mode.name}:" +
        "p$pointerOffset:t$rowTagOffset:r${if (requireRowTag) 1 else 0}"

    init {
        require(tableOffset >= 0 && recordStride > 0)
        require(pointerOffset >= 0 && pointerOffset + 4 <= recordStride)
        require(!requireRowTag || rowTagOffset >= 0 && rowTagOffset + 2 <= recordStride)
    }

    override fun equals(other: Any?): Boolean = other is GbaPaletteLayout &&
        tableOffset == other.tableOffset && recordStride == other.recordStride && mode == other.mode &&
        pointerOffset == other.pointerOffset && rowTagOffset == other.rowTagOffset &&
        requireRowTag == other.requireRowTag

    override fun hashCode(): Int = identity.hashCode()
}

class GbaSpriteTableLayout(
    override val tableOffset: Long,
    override val count: Long,
    override val recordStride: Int,
    val graphicsMode: GbaGraphicsMode,
    val graphicsPointerOffset: Int = 0,
    val frameSizeOffset: Int = 4,
    val fixedFrameSize: Int? = null,
    val palette: GbaPaletteLayout? = null,
    placeholderGraphicsOffsets: Set<Long> = emptySet(),
) : SpriteTableLayout() {
    val placeholderGraphicsOffsets: Set<Long> = immutableSet(placeholderGraphicsOffsets.toSortedSet())
    override val layoutIdentity: CandidateLayoutIdentity = CandidateLayoutIdentity(
        "sprite:gba:${tableOffset.toString(16)}:$count:$recordStride:${graphicsMode.name}:" +
            "p$graphicsPointerOffset:s$frameSizeOffset:f${fixedFrameSize ?: 0}:" +
            "pal${palette?.identity ?: "none"}:ph${this.placeholderGraphicsOffsets.joinToString(",") { it.toString(16) }}",
    )

    init {
        require(tableOffset >= 0 && count > 0 && recordStride > 0)
        require(graphicsPointerOffset >= 0 && graphicsPointerOffset + 4 <= recordStride)
        require(fixedFrameSize != null || frameSizeOffset >= 0 && frameSizeOffset + 2 <= recordStride)
        require(fixedFrameSize == null || fixedFrameSize > 0)
        require(this.placeholderGraphicsOffsets.all { it >= 0 })
    }

    override fun equals(other: Any?): Boolean = other is GbaSpriteTableLayout &&
        tableOffset == other.tableOffset && count == other.count && recordStride == other.recordStride &&
        graphicsMode == other.graphicsMode && graphicsPointerOffset == other.graphicsPointerOffset &&
        frameSizeOffset == other.frameSizeOffset && fixedFrameSize == other.fixedFrameSize &&
        palette == other.palette && placeholderGraphicsOffsets == other.placeholderGraphicsOffsets

    override fun hashCode(): Int = layoutIdentity.hashCode()
}

class SpriteSemanticDomain(
    val tableRowCount: Long,
    activeRowIndices: Set<Int>,
) {
    val activeRowIndices: List<Int> = immutableList(activeRowIndices.sorted())

    init {
        require(tableRowCount > 0)
        require(this.activeRowIndices.isNotEmpty())
        require(this.activeRowIndices.all { it >= 0 && it.toLong() < tableRowCount })
    }

    override fun equals(other: Any?): Boolean = other is SpriteSemanticDomain &&
        tableRowCount == other.tableRowCount && activeRowIndices == other.activeRowIndices

    override fun hashCode(): Int = 31 * tableRowCount.hashCode() + activeRowIndices.hashCode()
}

class DecodedSpriteFrame(
    val width: Int,
    val height: Int,
    graphicsBytes: ByteArray,
    indexedPixels: ByteArray,
    paletteBgr555: ShortArray = shortArrayOf(),
) {
    private val graphicsSnapshot = graphicsBytes.copyOf()
    private val indexedSnapshot = indexedPixels.copyOf()
    private val paletteSnapshot = paletteBgr555.copyOf()
    val graphicsBytes: ByteArray get() = graphicsSnapshot.copyOf()
    val indexedPixels: ByteArray get() = indexedSnapshot.copyOf()
    val paletteBgr555: ShortArray get() = paletteSnapshot.copyOf()

    init {
        require(width > 0 && height > 0 && indexedSnapshot.size == width * height)
        require(paletteSnapshot.isEmpty() || paletteSnapshot.size == 16)
    }

    override fun equals(other: Any?): Boolean = other is DecodedSpriteFrame &&
        width == other.width && height == other.height &&
        graphicsSnapshot.contentEquals(other.graphicsSnapshot) &&
        indexedSnapshot.contentEquals(other.indexedSnapshot) &&
        paletteSnapshot.contentEquals(other.paletteSnapshot)

    override fun hashCode(): Int {
        var result = 31 * width + height
        result = 31 * result + graphicsSnapshot.contentHashCode()
        result = 31 * result + indexedSnapshot.contentHashCode()
        result = 31 * result + paletteSnapshot.contentHashCode()
        return result
    }
}

data class Gen1SpriteSource(val bank: Int, val offset: Int) {
    init {
        require(bank >= 0 && offset >= 0)
    }
}

sealed interface SpriteRowOutcome {
    val rowIndex: Int

    data class Decoded(override val rowIndex: Int, val frame: DecodedSpriteFrame) : SpriteRowOutcome
    data class StructuralEmpty(override val rowIndex: Int) : SpriteRowOutcome
    data class StandardPlaceholder(override val rowIndex: Int, val graphicsOffset: Long) : SpriteRowOutcome

    class AmbiguousSources(
        override val rowIndex: Int,
        sources: Collection<Gen1SpriteSource>,
    ) : SpriteRowOutcome {
        val sources: List<Gen1SpriteSource> = immutableList(
            sources.distinct().sortedWith(compareBy(Gen1SpriteSource::bank, Gen1SpriteSource::offset)),
        )

        init {
            require(this.sources.size > 1) { "ambiguous Gen 1 sprite row requires multiple valid sources" }
        }

        override fun equals(other: Any?): Boolean = other is AmbiguousSources &&
            rowIndex == other.rowIndex && sources == other.sources

        override fun hashCode(): Int = 31 * rowIndex + sources.hashCode()
    }

    class Malformed(override val rowIndex: Int, reasons: Collection<String>) : SpriteRowOutcome {
        val reasons: List<String> = immutableList(reasons.distinct().sorted())

        init {
            require(this.reasons.isNotEmpty())
        }

        override fun equals(other: Any?): Boolean = other is Malformed &&
            rowIndex == other.rowIndex && reasons == other.reasons

        override fun hashCode(): Int = 31 * rowIndex + reasons.hashCode()
    }
}

class ResolvedSpriteLayout(
    table: SpriteTableLayout,
    rows: Collection<SpriteRowOutcome>,
    val semanticDomain: SpriteSemanticDomain,
) : ImmutableDatasetLayout<ResolvedSpriteLayout> {
    val table: SpriteTableLayout = table.immutableSnapshot()
    val rows: List<SpriteRowOutcome> = immutableList(rows.toList())
    override val layoutIdentity: CandidateLayoutIdentity = this.table.layoutIdentity

    init {
        require(this.table.count == semanticDomain.tableRowCount)
        require(this.table.count in 1..Int.MAX_VALUE.toLong())
        require(this.rows.size == this.table.count.toInt())
        require(this.rows.map { it.rowIndex } == this.rows.indices.toList())
    }

    override fun immutableSnapshot(): ResolvedSpriteLayout = this

    override fun equals(other: Any?): Boolean = other is ResolvedSpriteLayout &&
        table == other.table && rows == other.rows && semanticDomain == other.semanticDomain

    override fun hashCode(): Int = 31 * (31 * table.hashCode() + rows.hashCode()) + semanticDomain.hashCode()
}

sealed interface SpriteTableOutcome {
    val layout: SpriteTableLayout

    class Decoded(
        override val layout: SpriteTableLayout,
        rows: Collection<SpriteRowOutcome>,
    ) : SpriteTableOutcome {
        val rows: List<SpriteRowOutcome> = immutableList(rows.toList())

        override fun equals(other: Any?): Boolean = other is Decoded &&
            layout == other.layout && rows == other.rows

        override fun hashCode(): Int = 31 * layout.hashCode() + rows.hashCode()
    }

    data class Rejected(override val layout: SpriteTableLayout, val reason: String) : SpriteTableOutcome

    data class BudgetExceeded(
        override val layout: SpriteTableLayout,
        val budgetKind: SpriteBudgetKind,
        val observed: Long,
        val limit: Long,
        val reason: String,
    ) : SpriteTableOutcome
}

data class SpriteSpeciesIdentity(
    val speciesId: Int,
    val name: String?,
    val canonicalDexNumber: Int?,
) {
    init {
        require(speciesId >= 0)
    }
}

sealed interface SpriteProjection {
    data class Explicit(val frame: DecodedSpriteFrame) : SpriteProjection
    data class Inferred(val donorSpeciesId: Int, val frame: DecodedSpriteFrame) : SpriteProjection
    class Ambiguous(donorSpeciesIds: Collection<Int>) : SpriteProjection {
        val donorSpeciesIds: List<Int> = immutableList(donorSpeciesIds.distinct().sorted())

        init {
            require(this.donorSpeciesIds.size > 1)
        }

        override fun equals(other: Any?): Boolean = other is Ambiguous && donorSpeciesIds == other.donorSpeciesIds
        override fun hashCode(): Int = donorSpeciesIds.hashCode()
    }
    data class Missing(val reason: String) : SpriteProjection
}

private fun <T> immutableList(values: Collection<T>): List<T> =
    Collections.unmodifiableList(values.toList())

private fun <K, V> immutableMap(values: Map<K, V>): Map<K, V> =
    Collections.unmodifiableMap(LinkedHashMap(values))

private fun <T> immutableSet(values: Set<T>): Set<T> =
    Collections.unmodifiableSet(LinkedHashSet(values))
