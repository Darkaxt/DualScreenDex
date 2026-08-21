package com.enrpau.dualscreendex.parser.dataset.natures

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7Branch
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7BranchRegister
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7Compare
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7Condition
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7DataOperation
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7DataProcessing
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7DecodeResult
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7Immediate
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7Instruction
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7MemoryTransfer
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7MemoryWidth
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7Multiply
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7RegisterOperand
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7RotatedImmediate
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7StackTransfer
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7Register
import com.enrpau.dualscreendex.parser.analysis.thumb.ThumbDecoder
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import java.util.ArrayDeque

/** Resolves the Gen III Nature domain from ROM tables and their compiled consumers. */
object Gen3NatureResolver {
    fun resolve(session: RomAnalysisSession): NatureResolution {
        if (session.header.platform != Platform.GBA) {
            return NatureResolution.Unavailable("Nature resolution requires a GBA ROM")
        }
        val references = session.gbaReferenceIndex
            ?: return NatureResolution.Unavailable("Nature resolution requires compiled reference evidence")
        references.overflowReason?.let(NatureResolution::BudgetExceeded)?.let { return it }

        val callTargets = decodedThumbCallTargets(session.rom)
        val modifierCandidates = references.targets.keys.asSequence()
            .filter { it in 0 until session.rom.size }
            .mapNotNull { root ->
                decodeCompleteModifierTable(session.rom, root)?.let { rows -> root to rows }
            }
            .toList()

        val statCandidates = modifierCandidates.mapNotNull { (root, rows) ->
            val evidence = session.nominatedGbaReferenceSites(root)
                ?.takeIf { it.siteEvidenceAvailable }
                ?: return@mapNotNull null
            val proofs = evidence.instructionSites.mapNotNull { site ->
                compiledFunctionContaining(session.rom, site, callTargets)?.let(::statConsumerProof)
            }.distinct()
            val proof = proofs.singleOrNull() ?: return@mapNotNull null
            StatCandidate(root, rows, proof.positivePercent, proof.negativePercent)
        }
        if (statCandidates.isEmpty()) {
            return NatureResolution.Unavailable("no compiled Nature stat table was proven")
        }
        if (statCandidates.size != 1) return NatureResolution.Ambiguous(statCandidates.size)
        val stat = statCandidates.single()

        val flavorCandidates = modifierCandidates.mapNotNull { (root, rows) ->
            if (rows.size > stat.rows.size || root == stat.root) return@mapNotNull null
            val evidence = session.nominatedGbaReferenceSites(root)
                ?.takeIf { it.siteEvidenceAvailable }
                ?: return@mapNotNull null
            if (evidence.instructionSites.any { site ->
                    compiledFunctionContaining(session.rom, site, callTargets)?.let(::isFlavorAccessor) == true
                }
            ) {
                root to rows
            } else {
                null
            }
        }
        val flavor = flavorCandidates.singleOrNull()
        val natureCount = flavor?.second?.size ?: stat.rows.size
        if (stat.rows.drop(natureCount).any { row -> row.any { it != 0 } }) {
            return NatureResolution.Ambiguous(statCandidates.size + flavorCandidates.size)
        }

        val nameCandidates = references.targets.mapNotNull { (root, evidence) ->
            decodeNames(session.rom, root, natureCount)?.let { names ->
                NameCandidate(root, names, evidence.count)
            }
        }
        val maxNameReferences = nameCandidates.maxOfOrNull(NameCandidate::references)
            ?: return NatureResolution.Unavailable("no compiled Nature name table was proven")
        val names = nameCandidates.filter { it.references == maxNameReferences }.singleOrNull()
            ?: return NatureResolution.Ambiguous(nameCandidates.count { it.references == maxNameReferences })

        val records = (0 until natureCount).map { id ->
            NatureRecord(
                id = id,
                name = names.names[id],
                statModifiers = stat.rows[id],
                positivePercent = stat.positivePercent,
                negativePercent = stat.negativePercent,
                flavorModifiers = flavor?.second?.get(id),
            )
        }
        return NatureResolution.Resolved(
            NatureCatalog(
                records = records,
                nameTableOffset = names.root,
                statTableOffset = stat.root,
                flavorTableOffset = flavor?.first,
            ),
        )
    }

    private fun decodeCompleteModifierTable(rom: RomImage, root: Int): List<List<Int>>? {
        if (root < 0 || root.toLong() + MIN_NATURES * MODIFIERS_PER_NATURE > rom.size) return null
        val rows = mutableListOf<List<Int>>()
        for (row in 0 until MAX_NATURES + 1) {
            val offset = root.toLong() + row.toLong() * MODIFIERS_PER_NATURE
            if (offset + MODIFIERS_PER_NATURE > rom.size) break
            val values = (0 until MODIFIERS_PER_NATURE).map { column ->
                rom.u8(offset.toInt() + column).toByte().toInt()
            }
            if (!validModifierRow(values)) break
            rows += values
        }
        if (rows.size !in MIN_NATURES..MAX_NATURES) return null
        val nonNeutral = rows.count { row -> row.any { it != 0 } }
        val neutral = rows.size - nonNeutral
        return rows.takeIf { nonNeutral >= 4 && neutral >= 1 }
    }

    private fun validModifierRow(values: List<Int>): Boolean =
        values.size == MODIFIERS_PER_NATURE &&
            values.all { it in -1..1 } &&
            values.count { it > 0 } <= 1 &&
            values.count { it < 0 } <= 1 &&
            ((values.all { it == 0 }) || (values.count { it > 0 } == 1 && values.count { it < 0 } == 1))

    private fun decodeNames(rom: RomImage, root: Int, count: Int): List<String>? {
        if (root and 3 != 0 || root < 0 || root.toLong() + count.toLong() * 4L > rom.size) return null
        val names = (0 until count).map { id ->
            val textRoot = rom.gbaPointer(root + id * 4) ?: return null
            val available = minOf(MAX_NAME_BYTES, rom.size - textRoot)
            val decoded = PokemonTextCodec.gbaEnglish.decodeDetailed(rom.slice(textRoot, available))
            decoded.text.takeIf { name ->
                decoded.terminated && decoded.validRatio == 1.0 &&
                    name.length in 1..MAX_NAME_LENGTH && name.any(Char::isLetter) &&
                    name.all { character -> character.isLetterOrDigit() || character in " -.'" }
            } ?: return null
        }
        return names.takeIf { it.distinct().size >= maxOf(MIN_NATURES, count * 4 / 5) }
    }

    private fun decodedThumbCallTargets(rom: RomImage): Set<Int> = buildSet {
        var offset = 0
        while (offset + 3 < rom.size) {
            val high = rom.u16le(offset)
            val low = rom.u16le(offset + 2)
            if (high and 0xF800 == 0xF000 && low and 0xF800 == 0xF800) {
                val branch = (ThumbDecoder.decode(rom, offset) as? Arm7DecodeResult.Decoded)
                    ?.instruction as? Arm7Branch
                branch?.target?.toInt()?.takeIf { target -> target and 1 == 0 && target in 0 until rom.size }
                    ?.let(::add)
            }
            offset += 2
        }
    }

    private fun compiledFunctionContaining(
        rom: RomImage,
        site: Int,
        callTargets: Set<Int>,
    ): FunctionBody? = findFunctionContaining(rom, site, callTargets, requireCallTarget = true)
        ?: findFunctionContaining(rom, site, callTargets, requireCallTarget = false)

    private fun findFunctionContaining(
        rom: RomImage,
        site: Int,
        callTargets: Set<Int>,
        requireCallTarget: Boolean,
    ): FunctionBody? {
        var offset = site and -2
        while (offset >= 0) {
            val raw = rom.u16le(offset)
            val pushesLinkRegister = raw and 0xFE00 == 0xB400 && raw and 0x0100 != 0
            if (pushesLinkRegister && (!requireCallTarget || offset in callTargets)) {
                val body = decodeReachableFunction(rom, offset)
                if (body != null && site in body.offsets) return body
            }
            offset -= 2
        }
        return null
    }

    private fun decodeReachableFunction(rom: RomImage, start: Int): FunctionBody? {
        val pending = ArrayDeque<Int>()
        val decoded = linkedMapOf<Int, Arm7Instruction>()
        pending += start
        while (pending.isNotEmpty() && decoded.size < MAX_FUNCTION_INSTRUCTIONS) {
            val offset = pending.removeFirst()
            if (offset in decoded || offset and 1 != 0 || offset !in 0 until rom.size) continue
            val instruction = (ThumbDecoder.decode(rom, offset) as? Arm7DecodeResult.Decoded)?.instruction
                ?: return null
            decoded[offset] = instruction
            when (instruction) {
                is Arm7Branch -> when {
                    instruction.link -> pending += offset + instruction.size
                    instruction.condition == Arm7Condition.ALWAYS ->
                        instruction.target.toInt().takeIf { it in 0 until rom.size }?.let(pending::add)
                    else -> {
                        instruction.target.toInt().takeIf { it in 0 until rom.size }?.let(pending::add)
                        pending += offset + instruction.size
                    }
                }
                is Arm7BranchRegister -> if (instruction.link) pending += offset + instruction.size
                is Arm7StackTransfer -> if (!(instruction.load && Arm7Register.PC in instruction.registers)) {
                    pending += offset + instruction.size
                }
                else -> pending += offset + instruction.size
            }
        }
        return if (pending.isEmpty()) FunctionBody(start, decoded.values.toList()) else null
    }

    private fun statConsumerProof(body: FunctionBody): StatProof? {
        if (!hasSignedByteSemantics(body)) return null
        if (body.instructions.none { it is Arm7Multiply } &&
            body.instructions.none { it is Arm7DataProcessing && it.operation == Arm7DataOperation.MULTIPLY }
        ) return null
        val compareImmediates = body.instructions.filterIsInstance<Arm7Compare>()
            .flatMap { listOfNotNull(immediateValue(it.first), immediateValue(it.second)) }
            .toSet()
        val constructsNegativeOne = body.instructions.any { instruction ->
            instruction is Arm7DataProcessing && instruction.operation == Arm7DataOperation.SUBTRACT &&
                instruction.first?.let(::immediateValue) == 0L
        }
        val testsNegativeOneByIncrement = body.instructions.zipWithNext().any { (first, second) ->
            first is Arm7DataProcessing && first.operation == Arm7DataOperation.ADD &&
                immediateValue(first.second) == 1L && second is Arm7Branch &&
                !second.link && second.condition == Arm7Condition.EQUAL
        }
        if (
            1L !in compareImmediates ||
            (0L !in compareImmediates && !constructsNegativeOne && !testsNegativeOneByIncrement)
        ) return null
        reducedTenthsProof(body)?.let { return it }

        val immediates = body.instructions.flatMap(::immediateValues).toSet()
        if (100L !in immediates) return null
        val directPositives = body.instructions.mapNotNull { instruction ->
            (instruction as? Arm7DataProcessing)
                ?.takeIf { it.operation == Arm7DataOperation.MOVE }
                ?.second
                ?.let(::immediateValue)
                ?.takeIf { it in 101L..200L }
        }
        val additivePositives = body.instructions.mapNotNull { instruction ->
            (instruction as? Arm7DataProcessing)
                ?.takeIf { it.operation == Arm7DataOperation.ADD }
                ?.second
                ?.let(::immediateValue)
                ?.takeIf { it in 100L..199L }
                ?.plus(1L)
        }
        val positives = (directPositives + additivePositives).distinct()
        val negatives = immediates.filter { it in 50L..99L }
        val positive = positives.singleOrNull()?.toInt() ?: return null
        val negative = negatives.singleOrNull()?.toInt() ?: return null
        return StatProof(positive, negative)
    }

    /**
     * The matching FireRed compiler reduces the source operations `stat * 110 / 100` and
     * `stat * 90 / 100` to `stat * 11 / 10` and `stat * 9 / 10` when the intermediate value
     * retains the original u16 semantics. Prove the complete lowered arithmetic shape instead
     * of requiring the unreduced source constants to survive compilation.
     */
    private fun reducedTenthsProof(body: FunctionBody): StatProof? {
        val moves = body.instructions.filterIsInstance<Arm7DataProcessing>()
            .filter { it.operation == Arm7DataOperation.MOVE }
        val factors = moves.filter { it.destination == Arm7Register.R0 }
            .mapNotNull { immediateValue(it.second) }
            .toSet()
        if (11L !in factors || 9L !in factors) return null

        val multipliesNumerator = body.instructions.any { instruction ->
            instruction is Arm7DataProcessing && instruction.operation == Arm7DataOperation.MULTIPLY &&
                instruction.destination == Arm7Register.R0 &&
                instruction.first == Arm7RegisterOperand(Arm7Register.R0)
        }
        if (!multipliesNumerator) return null

        val dividesByTen = body.instructions.zipWithNext().any { (first, second) ->
            first is Arm7DataProcessing && first.operation == Arm7DataOperation.MOVE &&
                first.destination == Arm7Register.R1 && immediateValue(first.second) == 10L &&
                second is Arm7Branch && second.link
        }
        return if (dividesByTen) StatProof(positivePercent = 110, negativePercent = 90) else null
    }

    private fun isFlavorAccessor(body: FunctionBody): Boolean {
        val byteLoads = body.instructions.count { instruction ->
            instruction is Arm7MemoryTransfer && instruction.load &&
                instruction.width == Arm7MemoryWidth.BYTE
        }
        val hasMultiply = body.instructions.any {
            it is Arm7Multiply || (it is Arm7DataProcessing && it.operation == Arm7DataOperation.MULTIPLY)
        }
        val hasConditionalBranch = body.instructions.any {
            it is Arm7Branch && !it.link && it.condition != Arm7Condition.ALWAYS
        }
        return byteLoads == 1 && hasSignedByteSemantics(body) && !hasMultiply && !hasConditionalBranch
    }

    private fun hasSignedByteSemantics(body: FunctionBody): Boolean {
        if (body.instructions.any { instruction ->
                instruction is Arm7MemoryTransfer && instruction.load && instruction.signed &&
                    instruction.width == Arm7MemoryWidth.BYTE
            }
        ) return true
        return body.instructions.zipWithNext().any { (first, second) ->
            first is Arm7DataProcessing && second is Arm7DataProcessing &&
                first.operation == Arm7DataOperation.LOGICAL_SHIFT_LEFT &&
                second.operation == Arm7DataOperation.ARITHMETIC_SHIFT_RIGHT &&
                first.destination == second.destination && immediateValue(first.second) == 24L &&
                immediateValue(second.second) == 24L
        }
    }

    private fun immediateValues(instruction: Arm7Instruction): List<Long> = when (instruction) {
        is Arm7DataProcessing -> listOfNotNull(
            instruction.first?.let(::immediateValue),
            immediateValue(instruction.second),
        )
        is Arm7Compare -> listOfNotNull(immediateValue(instruction.first), immediateValue(instruction.second))
        else -> emptyList()
    }

    private fun immediateValue(operand: Any): Long? = when (operand) {
        is Arm7Immediate -> operand.value
        is Arm7RotatedImmediate -> operand.value
        else -> null
    }

    private data class FunctionBody(val start: Int, val instructions: List<Arm7Instruction>) {
        val offsets: Set<Int> = instructions.mapTo(linkedSetOf(), Arm7Instruction::offset)
    }

    private data class StatProof(val positivePercent: Int, val negativePercent: Int)
    private data class StatCandidate(
        val root: Int,
        val rows: List<List<Int>>,
        val positivePercent: Int,
        val negativePercent: Int,
    )
    private data class NameCandidate(val root: Int, val names: List<String>, val references: Int)

    private const val MODIFIERS_PER_NATURE = 5
    private const val MIN_NATURES = 5
    private const val MAX_NATURES = 64
    private const val MAX_NAME_BYTES = 32
    private const val MAX_NAME_LENGTH = 24
    private const val MAX_FUNCTION_INSTRUCTIONS = 1_024
}
