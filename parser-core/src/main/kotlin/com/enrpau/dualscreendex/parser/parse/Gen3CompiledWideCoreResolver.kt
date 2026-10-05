package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ExtentCheck
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.dataset.core.basestats.BaseStatsAbi
import com.enrpau.dualscreendex.parser.dataset.core.basestats.BaseStatsCodec
import com.enrpau.dualscreendex.parser.dataset.core.basestats.BaseStatsRowOutcome
import com.enrpau.dualscreendex.parser.dataset.core.basestats.BaseStatsTableLayout
import com.enrpau.dualscreendex.parser.dataset.core.basestats.BaseStatsTableOutcome
import com.enrpau.dualscreendex.parser.dataset.core.basestats.ResolvedBaseStatsLayout
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import java.util.Collections

internal sealed interface Gen3CompiledWideCoreOutcome {
    data object Absent : Gen3CompiledWideCoreOutcome
    data class Rejected(val reason: String) : Gen3CompiledWideCoreOutcome
    class Resolved(
        val speciesNames: TableLayout,
        val baseStats: ResolvedBaseStatsLayout,
        nativeToDex: Map<Int, Int>,
    ) : Gen3CompiledWideCoreOutcome {
        val nativeToDex: Map<Int, Int> = Collections.unmodifiableMap(LinkedHashMap(nativeToDex))
        val speciesCount: Int get() = baseStats.table.count.toInt()
    }
}

/** Couples bounded name/index consumers to the stat table selected by the false selector branch. */
internal object Gen3CompiledWideCoreResolver {
    fun resolve(session: RomAnalysisSession, codec: PokemonTextCodec): Gen3CompiledWideCoreOutcome {
        session.cancellation.throwIfCancellationRequested()
        if (session.header.platform != Platform.GBA) return Gen3CompiledWideCoreOutcome.Absent
        val rom = session.rom
        val end = minOf(rom.size, MAXIMUM_CODE_BYTES)
        val types = mutableListOf<Int>()
        val names = mutableListOf<Int>()
        val inverse = mutableListOf<Int>()
        val forward = mutableListOf<Int>()
        val statRoots = linkedSetOf<Int>()
        var work = 0
        for (at in 0 until end - 2 step 2) {
            if (at % SCAN_CHECK_BYTES == 0) session.cancellation.throwIfCancellationRequested()
            val opcode = rom.u16le(at)
            when {
                opcode == 0xb5f0 && matches(rom, at, TYPE_RECOGNITION, end) -> types += at
                opcode == 0xb5f0 && matches(rom, at, NAME_PREFIX, end) -> names += at
                opcode == 0xb510 && matches(rom, at, INVERSE_PREFIX, end) -> inverse += at
                opcode == 0xb500 && matches(rom, at, FORWARD_PREFIX, end) -> forward += at
                opcode and 0xff00 == 0x4900 && matches(rom, at, SIX_STATS, end) -> {
                    literalRoot(rom, at)?.let(statRoots::add)
                }
                else -> continue
            }
            if (++work > session.limits.maxProbeWorkPerDataset ||
                types.size + names.size + inverse.size + forward.size + statRoots.size > session.limits.maxCandidatesPerDataset
            ) return rejected("compiled wide-core consumer budget exceeded")
        }
        if (types.isEmpty()) {
            val coupledIndexWitness = statRoots.isNotEmpty() && names.any { nameAt ->
                matches(rom, nameAt, NAME_COPIER, end) && inverse.any { inverseAt ->
                    matches(rom, inverseAt, INVERSE_MAPPER, end) &&
                        literalValue(rom, inverseAt + 20)?.plus(1) == literalValue(rom, inverseAt + 42) &&
                        literalValue(rom, nameAt + 10) == literalValue(rom, inverseAt + 42)?.plus(1) &&
                        forward.any { forwardAt ->
                            matches(rom, forwardAt, FORWARD_MAPPER, end) &&
                                literalRoot(rom, inverseAt + 12)?.let { it == literalRoot(rom, forwardAt + 26) } == true
                        }
                }
            }
            return if (coupledIndexWitness) rejected("coupled widened stat/name/index witness lacks its mode selector")
            else Gen3CompiledWideCoreOutcome.Absent
        }
        if (!codec.supports(3, Platform.GBA)) return rejected("wide-core names require an applicable Gen III codec")
        if (types.any { !matches(rom, it, TYPE_PREDICATE, end) || !validSelectorCall(rom, it + 20, end) }) {
            return rejected("recognized wide type predicate has incomplete or conflicting control flow")
        }
        val typeRoots = types.map { at ->
            val normal = literalRoot(rom, at + 30) ?: return rejected("default stat literal is invalid")
            val variant = literalRoot(rom, at + 56) ?: return rejected("variant stat literal is invalid")
            normal to variant
        }.distinct()
        if (typeRoots.size != 1) return rejected("wide predicates disagree on default/variant stat roots")
        val (defaultRoot, variantRoot) = typeRoots.single()
        if (defaultRoot !in statRoots) return rejected("default wide root lacks six actual affine u16 stat reads")
        if (names.isEmpty() || inverse.isEmpty() || forward.isEmpty()) {
            return rejected("wide-core name/forward/inverse consumers are incomplete")
        }
        if (names.any { !matches(rom, it, NAME_COPIER, end) } ||
            inverse.any { !matches(rom, it, INVERSE_MAPPER, end) } ||
            forward.any { !matches(rom, it, FORWARD_MAPPER, end) }
        ) return rejected("wide-core name/index consumer control flow is malformed")

        val nameContracts = names.map { at ->
            val bound = literalValue(rom, at + 10) ?: return rejected("name bound literal is invalid")
            val root = literalRoot(rom, at + 14) ?: return rejected("name root literal is invalid")
            val width = rom.u16le(at + 16) and 255
            if (width !in 2..MAXIMUM_NAME_WIDTH || (rom.u16le(at + 44) and 255) != width - 1) {
                return rejected("name multiplication and copier bound disagree")
            }
            NameContract(root, bound, width)
        }.distinct()
        val inverseContracts = inverse.map { at ->
            val root = literalRoot(rom, at + 12) ?: return rejected("inverse map literal is invalid")
            val last = literalValue(rom, at + 20) ?: return rejected("inverse scan bound literal is invalid")
            val sentinel = literalValue(rom, at + 42) ?: return rejected("inverse sentinel literal is invalid")
            if (last + 1L != sentinel || sentinel !in 1L..MAXIMUM_MAP_ROWS.toLong()) {
                return rejected("inverse scan bound and not-found sentinel disagree")
            }
            InverseContract(root, sentinel.toInt())
        }.distinct()
        val forwardContracts = forward.map { at ->
            val forms = literalRoot(rom, at + 14) ?: return rejected("form-pointer root literal is invalid")
            val root = literalRoot(rom, at + 26) ?: return rejected("forward map literal is invalid")
            ForwardContract(root, forms)
        }.distinct().filter { candidate -> inverseContracts.any { it.root == candidate.root } }
        val pairedInverseContracts = inverseContracts.filter { candidate -> forwardContracts.any { it.root == candidate.root } }
        if (nameContracts.size != 1 || pairedInverseContracts.size != 1 || forwardContracts.size != 1) {
            return rejected("compiled wide-core name/index roots or bounds are ambiguous " +
                "(names=${nameContracts.size}, inverse pairs=${pairedInverseContracts.size}, forward pairs=${forwardContracts.size})")
        }
        val name = nameContracts.single()
        val map = pairedInverseContracts.single()
        val lookup = forwardContracts.single()
        if (lookup.root != map.root || name.maximumIndex != map.rows + 1L) {
            return rejected("name reserved-slot bound and paired map consumers disagree")
        }
        val rowCount = map.rows + 1
        val roots = setOf(name.root, map.root, lookup.forms, defaultRoot, variantRoot)
        if (roots.size > session.limits.maxProbeRootsPerDataset) return rejected("wide-core root budget exceeded")
        if (map.root % 2 != 0 || lookup.forms % 4 != 0 || defaultRoot % 4 != 0 || variantRoot % 4 != 0) {
            return rejected("wide-core roots violate their consumer alignment")
        }
        val nameCount = (name.maximumIndex + 1).toInt()
        if (!validExtent(session, name.root, nameCount, name.width) ||
            !validExtent(session, map.root, map.rows, 2) ||
            !validExtent(session, lookup.forms, rowCount, 4) ||
            !validExtent(session, defaultRoot, rowCount, BaseStatsAbi.WIDE_STATS_64.recordSize) ||
            !validExtent(session, variantRoot, rowCount, BaseStatsAbi.WIDE_STATS_64.recordSize)
        ) return rejected("wide-core table extent is truncated or exceeds the shared budget")

        val firstNativeByDex = linkedMapOf<Int, Int>()
        repeat(map.rows) { index ->
            session.cancellation.throwIfCancellationRequested()
            val dex = rom.u16le(map.root + index * 2)
            if (dex != 0) firstNativeByDex.putIfAbsent(dex, index + 1)
        }
        if (firstNativeByDex.isEmpty()) return rejected("inverse consumer declares no canonical species")
        val nativeToDex = firstNativeByDex.entries.sortedBy { it.value }.associate { (dex, native) -> native to dex }
        for ((native, dex) in nativeToDex) {
            session.cancellation.throwIfCancellationRequested()
            val slot = lookup.forms + native * 4
            val forwardDex = if (rom.u32le(slot) == 0L) {
                rom.u16le(map.root + (native - 1) * 2)
            } else {
                val form = rom.gbaPointer(slot) ?: return rejected("canonical form pointer is invalid")
                if (!validExtent(session, form, 1, 2)) return rejected("canonical form return is truncated")
                rom.u16le(form)
            }
            if (forwardDex != dex) return rejected("canonical forward/form return conflicts with inverse first match")
            val text = codec.decodeDetailed(rom, name.root + native * name.width, name.width, session.cancellation)
            if (!text.terminated || text.invalidUnits != 0 || text.controlUnits != 0 ||
                text.substitutionUnits != 0 || text.text.none(Char::isLetterOrDigit)
            ) return rejected("canonical species name is malformed under the compiled copier")
        }
        val table = BaseStatsTableLayout(defaultRoot.toLong(), rowCount.toLong(), BaseStatsAbi.WIDE_STATS_64)
        val decoded = BaseStatsCodec().decode(session, table)
        if (decoded !is BaseStatsTableOutcome.Decoded) return rejected("default wide stat table could not be decoded")
        if (decoded.rows.any { it is BaseStatsRowOutcome.Malformed } ||
            nativeToDex.keys.any { decoded.rows[it] !is BaseStatsRowOutcome.Decoded }
        ) return rejected("default wide stat table has malformed rows or incomplete canonical coverage")
        return Gen3CompiledWideCoreOutcome.Resolved(
            TableLayout(name.root, nameCount, name.width), ResolvedBaseStatsLayout(table, decoded.rows), nativeToDex,
        )
    }

    private fun rejected(reason: String) = Gen3CompiledWideCoreOutcome.Rejected(reason)

    private fun validExtent(session: RomAnalysisSession, root: Int, count: Int, width: Int): Boolean =
        session.limits.checkTableExtent(root.toLong(), count.toLong(), width.toLong(), session.rom.size.toLong()) is ExtentCheck.Valid

    private fun literalValue(rom: RomImage, instruction: Int): Long? {
        if (instruction < 0 || instruction.toLong() + 2 > rom.size) return null
        val opcode = rom.u16le(instruction)
        if (opcode and 0xf800 != 0x4800) return null
        val slot = ((instruction + 4) and -4) + (opcode and 255) * 4
        return if (slot.toLong() + 4 <= rom.size) rom.u32le(slot) else null
    }

    private fun literalRoot(rom: RomImage, instruction: Int): Int? = literalValue(rom, instruction)
        ?.takeIf { it in 0x08000000L..0x09ffffffL }
        ?.minus(0x08000000L)?.toInt()?.takeIf { it in 0 until rom.size }

    private fun validSelectorCall(rom: RomImage, at: Int, codeEnd: Int): Boolean {
        val displacement = ((rom.u16le(at) and 0x7ff) shl 12) or ((rom.u16le(at + 2) and 0x7ff) shl 1)
        val signed = if (displacement and 0x400000 != 0) displacement or -0x800000 else displacement
        val target = at.toLong() + 4 + signed
        return target >= 0 && target + 2 <= codeEnd && target % 2 == 0L
    }

    private fun matches(rom: RomImage, at: Int, pattern: IntArray, end: Int): Boolean {
        if (at < 0 || at.toLong() + pattern.size * 2 > end) return false
        return pattern.indices.all { index ->
            val word = rom.u16le(at + index * 2)
            when (val expected = pattern[index]) {
                ANY -> true
                LITERAL_R0 -> word and 0xff00 == 0x4800
                LITERAL_R1 -> word and 0xff00 == 0x4900
                LITERAL_R3 -> word and 0xff00 == 0x4b00
                LITERAL_R4 -> word and 0xff00 == 0x4c00
                LITERAL_R7 -> word and 0xff00 == 0x4f00
                BL_HIGH -> word and 0xf800 == 0xf000
                BL_LOW -> word and 0xf800 == 0xf800
                MOV_R0 -> word and 0xff00 == 0x2000
                CMP_R1 -> word and 0xff00 == 0x2900
                BNE -> word and 0xff00 == 0xd100
                else -> word == expected
            }
        }
    }

    private data class NameContract(val root: Int, val maximumIndex: Long, val width: Int)
    private data class InverseContract(val root: Int, val rows: Int)
    private data class ForwardContract(val root: Int, val forms: Int)

    private const val MAXIMUM_CODE_BYTES = 0x100000
    private const val MAXIMUM_MAP_ROWS = 65534
    private const val MAXIMUM_NAME_WIDTH = 64
    private const val SCAN_CHECK_BYTES = 4096
    private const val ANY = -1
    private const val LITERAL_R0 = -2
    private const val LITERAL_R1 = -3
    private const val LITERAL_R3 = -4
    private const val LITERAL_R4 = -5
    private const val LITERAL_R7 = -6
    private const val BL_HIGH = -7
    private const val BL_LOW = -8
    private const val MOV_R0 = -9
    private const val CMP_R1 = -10
    private const val BNE = -11

    // Branch displacements are part of each complete consumer grammar. Literal data is skipped,
    // never decoded as instructions. Only PC-load immediates and relocatable BL operands vary.
    private val TYPE_RECOGNITION = intArrayOf(
        0xb5f0, 0x0400, 0x0c04, 0x1c27, 0x0609, 0x0e0d, 0x1c2e, 0x2c00,
        ANY, MOV_R0, BL_HIGH, BL_LOW, 0x0600, 0x2800, BNE, LITERAL_R0,
        0x01a1, 0x1809, 0x7b08, 0x42a8, ANY, 0x7b48, 0x42a8,
    )
    private val TYPE_PREDICATE = intArrayOf(
        0xb5f0, 0x0400, 0x0c04, 0x1c27, 0x0609, 0x0e0d, 0x1c2e, 0x2c00,
        0xd020, MOV_R0, BL_HIGH, BL_LOW, 0x0600, 0x2800, 0xd10c, LITERAL_R0,
        0x01a1, 0x1809, 0x7b08, 0x42a8, 0xd00f, 0x7b48, 0x42a8, 0xd00c,
        0xe010, ANY, ANY, ANY, LITERAL_R0, 0x01b9, 0x1809, 0x7b08, 0x42b0,
        0xd002, 0x7b48, 0x42b0, 0xd104, 0x2001, 0xe003, ANY, ANY, ANY,
        0x2000, 0xbcf0, 0xbc02, 0x4708,
    )
    private val NAME_PREFIX = intArrayOf(0xb5f0, 0x1c06, 0x0409, 0x0c0d, 0x2100, LITERAL_R0, 0x4684, LITERAL_R7)
    private val NAME_COPIER = intArrayOf(
        0xb5f0, 0x1c06, 0x0409, 0x0c0d, 0x2100, LITERAL_R0, 0x4684, LITERAL_R7,
        MOV_R0, 0x4368, 0x19c3, 0x1c32, 0xe007, ANY, ANY, ANY, ANY, ANY,
        0x3301, 0x3201, 0x3101, 0x1874, CMP_R1, 0xdc09, 0x4565, 0xd902,
        0x19c8, 0x7800, 0xe000, 0x7818, 0x7010, 0x7820, 0x28ff, 0xd1ef,
        0x20ff, 0x7020, 0xbcf0, 0xbc01, 0x4700,
    )
    private val INVERSE_PREFIX = intArrayOf(0xb510, 0x0400, 0x0c02, 0x2a00, 0xd01c, 0x2100, LITERAL_R3)
    private val INVERSE_MAPPER = intArrayOf(
        0xb510, 0x0400, 0x0c02, 0x2a00, 0xd01c, 0x2100, LITERAL_R3,
        0x8818, 0x4290, 0xd00a, LITERAL_R4, 0x1c48, 0x0400, 0x0c01,
        0x42a1, 0xd804, 0x0048, 0x18c0, 0x8800, 0x4290, 0xd1f5,
        LITERAL_R0, 0x4281, 0xd009, 0x1c48, 0x0400, 0x0c00, 0xe006,
        ANY, ANY, ANY, ANY, ANY, ANY, 0x2000, 0xbc10, 0xbc02, 0x4708,
    )
    private val FORWARD_PREFIX = intArrayOf(0xb500, 0x0400, 0x0c02, 0x2a00, 0xd101, 0x2000, 0xe011, LITERAL_R1)
    private val FORWARD_MAPPER = intArrayOf(
        0xb500, 0x0400, 0x0c02, 0x2a00, 0xd101, 0x2000, 0xe011,
        LITERAL_R1, 0x0090, 0x1840, 0x6800, 0x2800, 0xd10a, LITERAL_R0,
        0x1e51, 0x0049, 0x1809, 0x8808, 0xe005, ANY, ANY, ANY, ANY, ANY,
        0x8800, 0xbc02, 0x4708,
    )
    private val SIX_STATS = intArrayOf(LITERAL_R1, 0x01a8, 0x1840, 0x8842, 0x4694, 0x8901, 0x8887, 0x8944, 0x8803, 0x88c0)
}
