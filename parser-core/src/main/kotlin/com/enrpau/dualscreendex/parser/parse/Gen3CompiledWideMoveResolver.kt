package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ExtentCheck
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.dataset.learnsets.LearnsetCodec
import com.enrpau.dualscreendex.parser.dataset.learnsets.LearnsetFormat
import com.enrpau.dualscreendex.parser.dataset.learnsets.LearnsetRowOutcome
import com.enrpau.dualscreendex.parser.dataset.learnsets.LearnsetTableLayout
import com.enrpau.dualscreendex.parser.dataset.learnsets.LearnsetTableOutcome
import com.enrpau.dualscreendex.parser.dataset.learnsets.ResolvedLearnsetLayout
import com.enrpau.dualscreendex.parser.dataset.learnsets.ResolvedSelectedLearnsetTable
import com.enrpau.dualscreendex.parser.dataset.moves.MoveDetailsAbi
import com.enrpau.dualscreendex.parser.dataset.moves.MoveDetailsCodec
import com.enrpau.dualscreendex.parser.dataset.moves.MoveDetailsRowOutcome
import com.enrpau.dualscreendex.parser.dataset.moves.MoveDetailsTableLayout
import com.enrpau.dualscreendex.parser.dataset.moves.MoveDetailsTableOutcome
import com.enrpau.dualscreendex.parser.model.TableRecordFormat
import com.enrpau.dualscreendex.parser.dataset.learnsets.ResolvedLearnsetSet
import com.enrpau.dualscreendex.parser.dataset.moves.ResolvedMoveDetailsLayout
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec

internal sealed interface Gen3CompiledWideMoveOutcome {
    data object Absent : Gen3CompiledWideMoveOutcome
    data class Rejected(val reason: String) : Gen3CompiledWideMoveOutcome
    class Resolved(
        val names: TableLayout,
        val details: ResolvedMoveDetailsLayout,
        val learnsetTable: TableLayout,
        val learnsets: ResolvedLearnsetSet,
    ) : Gen3CompiledWideMoveOutcome {
        val acquisitionMinimumMoveCount: Int get() = names.count
    }
}

/** Couples ordinary acquisition, name-copy and prefix-field consumers, without guessing a full move bound. */
internal object Gen3CompiledWideMoveResolver {
    fun resolve(
        session: RomAnalysisSession,
        core: Gen3CompiledWideCoreOutcome.Resolved,
        codec: PokemonTextCodec,
    ): Gen3CompiledWideMoveOutcome {
        session.cancellation.throwIfCancellationRequested()
        val end = minOf(session.rom.size, 0x100000)
        var work = 0
        fun consumeWork() {
            if (++work > session.limits.maxProbeWorkPerDataset) throw WideMoveBudgetStop()
        }
        val acquisitions = mutableListOf<Int>()
        val nameConsumers = mutableListOf<Int>()
        try {
            for (at in 0 until end - 2 step 2) {
                if (at % 4096 == 0) session.cancellation.throwIfCancellationRequested()
                when (session.rom.u16le(at)) {
                    0x0600 -> if (matches(session, at, ACQUISITION_PREFIX, end)) acquisitions += at else continue
                    0x880a -> if (matches(session, at, NAME_PREFIX, end)) nameConsumers += at else continue
                    else -> continue
                }
                consumeWork()
                if (acquisitions.size + nameConsumers.size > session.limits.maxCandidatesPerDataset) throw WideMoveBudgetStop()
            }
            if (acquisitions.isEmpty() && nameConsumers.isEmpty()) return Gen3CompiledWideMoveOutcome.Absent
            if (acquisitions.isEmpty() || nameConsumers.isEmpty()) return rejected("compiled ordinary acquisition/name consumers are incomplete")
            val acquisitionContracts = acquisitions.map { at ->
                val stride = when {
                    matches(session, at, acquisitionPattern(56), end) -> 56
                    matches(session, at, acquisitionPattern(20), end) -> 20
                    else -> return rejected("recognized ordinary list consumer has malformed control flow or stride")
                }
                if (!validCall(session, at + 0x2e, end) || !validCall(session, at + 0x5c, end)) {
                    return rejected("ordinary acquisition call leaves the bounded code domain")
                }
                val root = literalRoot(session, at + 4) ?: return rejected("ordinary list root is invalid")
                if (literalRoot(session, at + 0x62) != root || literalValue(session, at + 0x10) != 65535L) {
                    return rejected("ordinary list root/sentinel witnesses disagree")
                }
                AcquisitionContract(root, literalRoot(session, at + 0x20)
                    ?: return rejected("ordinary move-data root is invalid"), stride)
            }.distinct()
            if (acquisitionContracts.size != 1) return rejected("ordinary acquisition roots or strides are ambiguous")
            val acquisition = acquisitionContracts.single()
            val names = nameConsumers.map { at ->
                if (!matches(session, at, namePattern(acquisition.stride), end)) {
                    return rejected("compiled move-name/power join has conflicting geometry")
                }
                val target = callTarget(session, at + 10, end) ?: return rejected("move-name copier call is invalid")
                if (!matches(session, target, EOS_COPIER, end)) return rejected("move-name copier lacks complete EOS/return control flow")
                val nameRoot = literalRoot(session, at + 6) ?: return rejected("move-name root is invalid")
                if (literalRoot(session, at + 16) != acquisition.details) return rejected("move-name/power root conflicts with ordinary acquisition")
                nameRoot
            }.distinct()
            if (names.size != 1) return rejected("compiled move-name roots are ambiguous")
            val nameRoot = names.single()
            if (setOf(nameRoot, acquisition.pointers, acquisition.details).size > session.limits.maxProbeRootsPerDataset) {
                throw WideMoveBudgetStop()
            }
            if (acquisition.pointers % 4 != 0 || acquisition.details % 4 != 0) return rejected("move/list roots violate their compiled alignment")
            val fields = GbaAffineMoveFieldWitnesses.collect(session, end, ::consumeWork)
            if (REQUIRED_FIELDS.any { (offset, width, signed) ->
                    GbaMoveFieldWitness(acquisition.details, acquisition.stride, offset, width, signed) !in fields
                }) return rejected("ordinary move root lacks independent complete scalar-prefix and signed-priority reads")
            if (!codec.supports(3, session.header.platform)) return rejected("move names require an applicable Gen III codec")
            val table = LearnsetTableLayout(acquisition.pointers.toLong(), core.speciesCount, LearnsetFormat.MoveU16LevelU16)
            val decoded = LearnsetCodec().decodeCanonicalGen3(session, table, core.nativeToDex.keys, 65536)
            if (decoded !is LearnsetTableOutcome.Decoded || core.nativeToDex.keys.any { decoded.rows[it] !is LearnsetRowOutcome.Decoded }) {
                return rejected("canonical ordinary lists have invalid pointers, entries, termination or budgets")
            }
            val referenced = decoded.rows.filterIsInstance<LearnsetRowOutcome.Decoded>().flatMap { it.entries }.map { it.moveId }.toSet()
            val count = referenced.maxOrNull()?.plus(1) ?: return rejected("ordinary lists prove no acquisition move domain")
            val abi = if (acquisition.stride == 56) MoveDetailsAbi.ALIGNED_BYTE_TARGET_MOVE_56 else MoveDetailsAbi.ALIGNED_BYTE_TARGET_MOVE_20
            if (session.limits.checkTableExtent(nameRoot.toLong(), count.toLong(), 17, session.rom.size.toLong()) !is ExtentCheck.Valid) {
                return rejected("acquisition-minimum names exceed the physical extent or budget")
            }
            for (id in 1 until count) {
                session.cancellation.throwIfCancellationRequested()
                val text = codec.decodeDetailed(session.rom, nameRoot + id * 17, 17, session.cancellation)
                if (!text.terminated || text.invalidUnits != 0 || text.controlUnits != 0 || text.substitutionUnits != 0 ||
                    text.text.none(Char::isLetterOrDigit)) return rejected("acquisition-minimum move name is malformed")
            }
            val moveTable = MoveDetailsTableLayout(acquisition.details.toLong(), count.toLong(), abi)
            val details = MoveDetailsCodec().decode(session, moveTable)
            if (details !is MoveDetailsTableOutcome.Decoded || details.rows.any { it is MoveDetailsRowOutcome.Malformed } ||
                referenced.any { details.rows[it] !is MoveDetailsRowOutcome.Decoded }) {
                return rejected("acquisition-minimum move details or references are incomplete")
            }
            val selected = ResolvedSelectedLearnsetTable(ResolvedLearnsetLayout(table, decoded.rows), 1.0, acquisitions.size)
            return Gen3CompiledWideMoveOutcome.Resolved(
                TableLayout(nameRoot, count, 17), ResolvedMoveDetailsLayout(moveTable, details.rows),
                TableLayout(acquisition.pointers, core.speciesCount, 4, variableLength = true, elementSize = 4,
                    format = TableRecordFormat.GEN3_MOVE_U16_LEVEL_U16),
                ResolvedLearnsetSet(listOf(selected), acquisition.pointers.toLong(), null),
            )
        } catch (_: WideMoveBudgetStop) {
            return rejected("compiled wide-move root/candidate/consumer work budget exceeded")
        }
    }

    private fun rejected(reason: String) = Gen3CompiledWideMoveOutcome.Rejected(reason)
    private data class AcquisitionContract(val pointers: Int, val details: Int, val stride: Int)
    private data class Field(val offset: Int, val width: Int, val signed: Boolean = false)
    private val REQUIRED_FIELDS = listOf(Field(0, 2), Field(2, 1), Field(3, 1), Field(4, 1), Field(5, 1),
        Field(6, 1), Field(7, 1), Field(8, 1, true), Field(12, 4), Field(16, 1), Field(17, 1))

    private fun literalValue(session: RomAnalysisSession, at: Int): Long? {
        val word = session.rom.u16le(at)
        if (word and 0xf800 != 0x4800) return null
        val slot = ((at + 4) and -4) + (word and 255) * 4
        return if (slot.toLong() + 4 <= session.rom.size) session.rom.u32le(slot) else null
    }

    private fun literalRoot(session: RomAnalysisSession, at: Int): Int? = literalValue(session, at)
        ?.takeIf { it in 0x08000000L..0x09ffffffL }?.minus(0x08000000)?.toInt()
        ?.takeIf { it in 0 until session.rom.size }

    private fun validCall(session: RomAnalysisSession, at: Int, end: Int): Boolean = callTarget(session, at, end) != null

    private fun callTarget(session: RomAnalysisSession, at: Int, end: Int): Int? {
        if (at < 0 || at.toLong() + 4 > end) return null
        val high = session.rom.u16le(at)
        val low = session.rom.u16le(at + 2)
        if (high and 0xf800 != 0xf000 || low and 0xf800 != 0xf800) return null
        val displacement = ((high and 0x7ff) shl 12) or ((low and 0x7ff) shl 1)
        val signed = if (displacement and 0x400000 != 0) displacement or -0x800000 else displacement
        return (at.toLong() + 4 + signed).takeIf { it >= 0 && it + 2 <= end && it % 2 == 0L }?.toInt()
    }

    private fun matches(session: RomAnalysisSession, at: Int, pattern: IntArray, end: Int): Boolean {
        if (at < 0 || at.toLong() + pattern.size * 2 > end) return false
        return pattern.indices.all { index ->
            val word = session.rom.u16le(at + index * 2)
            when (val expected = pattern[index]) {
                LITERAL_R0 -> word and 0xff00 == 0x4800
                LITERAL_R2 -> word and 0xff00 == 0x4a00
                LITERAL_R3 -> word and 0xff00 == 0x4b00
                LITERAL_R4 -> word and 0xff00 == 0x4c00
                BL_HIGH -> word and 0xf800 == 0xf000
                BL_LOW -> word and 0xf800 == 0xf800
                CMP_R0 -> word and 0xff00 == 0x2800
                else -> word == expected
            }
        }
    }

    private const val LITERAL_R0 = -1
    private const val LITERAL_R2 = -2
    private const val LITERAL_R3 = -3
    private const val LITERAL_R4 = -4
    private const val BL_HIGH = -5
    private const val BL_LOW = -6
    private const val CMP_R0 = -7
    private val ACQUISITION_PREFIX = intArrayOf(0x0600, 0x0e07, LITERAL_R4, 0x4640, 0x0082, 0x1910,
        0x6801, 0x8808, LITERAL_R3, 0x4298)
    private val NAME_PREFIX = intArrayOf(0x880a, 0x0111, 0x1889, LITERAL_R2, 0x1889, BL_HIGH, BL_LOW)
    private val EOS_COPIER = intArrayOf(0xb500, 0x1c03, 0xe002, 0x701a, 0x3301, 0x3101, 0x780a,
        0x1c10, 0x28ff, 0xd1f8, 0x20ff, 0x7018, 0x1c18, 0xbc02, 0x4708)

    private fun acquisitionPattern(stride: Int): IntArray {
        val shift = if (stride == 56) 3 else 2
        return intArrayOf(0x0600, 0x0e07, LITERAL_R4, 0x4640, 0x0082, 0x1910, 0x6801, 0x8808,
            LITERAL_R3, 0x4298, 0xd031, 0x8848, 0x42b8, 0xdc2e, 0x4699, 0x2500,
            LITERAL_R3, 0x469a, 0x1914, 0x6820, 0x1828, 0x8801, 0x1c30, BL_HIGH, BL_LOW,
            0x0400, 0x0c00, 0x4548, 0xd112, 0x6820, 0x1828, 0x8801,
            (shift shl 6) or 8, if (stride == 56) 0x1a40 else 0x1840, shift shl 6,
            0x4653, 0x18c2, 0x7c10, 0x2802, 0xd007, 0x8810, CMP_R0, 0xd004, CMP_R0, 0xd002,
            0x1c30, BL_HIGH, BL_LOW, 0x3504, LITERAL_R4, 0x4640, 0x0082, 0x1910, 0x6800, 0x1829,
            0x8808, 0x4548, 0xd002, 0x8848, 0x42b8, 0xddd4, 0xbc38, 0x4698, 0x46a1, 0x46aa,
            0xbcf0, 0xbc01, 0x4700)
    }

    private fun namePattern(stride: Int): IntArray {
        val shift = if (stride == 56) 3 else 2
        return intArrayOf(0x880a, 0x0111, 0x1889, LITERAL_R2, 0x1889, BL_HIGH, BL_LOW,
            LITERAL_R0, LITERAL_R3, 0x4652, 0x6811, 0x7949, 0x0049, 0x1989, 0x880a,
            (shift shl 6) or 0x11, if (stride == 56) 0x1a89 else 0x1889, (shift shl 6) or 9,
            0x18c9, 0x7889)
    }
}
