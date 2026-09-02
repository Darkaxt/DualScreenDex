package com.enrpau.dualscreendex.parser.dataset.natures

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7Address
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
    fun resolve(session: RomAnalysisSession, codec: PokemonTextCodec?): NatureResolution {
        if (session.header.platform != Platform.GBA) {
            return NatureResolution.Unavailable("Nature resolution requires a GBA ROM")
        }
        val references = session.gbaReferenceIndex
            ?: return NatureResolution.Unavailable("Nature resolution requires compiled reference evidence")
        references.overflowReason?.let(NatureResolution::BudgetExceeded)?.let { return it }
        session.cancellation.throwIfCancellationRequested()

        val callTargets = decodedThumbCallTargets(session.rom, session.cancellation)
        val integratedCandidates = resolveIntegratedCandidates(session, callTargets, codec)
        val separate = resolveSeparateTables(session, callTargets, codec)
        if (integratedCandidates.isEmpty()) return separate

        val separateCandidateCount = when (separate) {
            is NatureResolution.Resolved -> 1
            is NatureResolution.Ambiguous -> separate.candidates
            else -> 0
        }
        if (integratedCandidates.size != 1 || separateCandidateCount > 0) {
            return NatureResolution.Ambiguous(integratedCandidates.size + separateCandidateCount)
        }
        return NatureResolution.Resolved(integratedCandidates.single().catalog)
    }

    private fun resolveSeparateTables(
        session: RomAnalysisSession,
        callTargets: Set<Int>,
        codec: PokemonTextCodec?,
    ): NatureResolution {
        val references = requireNotNull(session.gbaReferenceIndex)
        val modifierCandidates = references.targets.keys.asSequence()
            .filter { it in 0 until session.rom.size }
            .mapNotNull { root ->
                session.cancellation.throwIfCancellationRequested()
                decodeCompleteModifierTable(session.rom, root)?.let { rows -> root to rows }
            }
            .toList()

        val statCandidates = modifierCandidates.mapNotNull { (root, rows) ->
            val evidence = session.nominatedGbaReferenceSites(root)
                ?.takeIf { it.siteEvidenceAvailable }
                ?: return@mapNotNull null
            val proofs = evidence.instructionSites.mapNotNull { site ->
                compiledFunctionContaining(session.rom, site, callTargets, session.cancellation)
                    ?.let(::statConsumerProof)
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
                    compiledFunctionContaining(session.rom, site, callTargets, session.cancellation)
                        ?.let(::isFlavorAccessor) == true
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

        val names = codec?.let { selectedCodec ->
            val nameCandidates = references.targets.mapNotNull { (root, evidence) ->
                decodeNames(
                    session.rom,
                    root,
                    natureCount,
                    selectedCodec,
                    session.cancellation,
                )?.let { decodedNames ->
                    NameCandidate(root, decodedNames, evidence.count)
                }
            }
            val maxNameReferences = nameCandidates.maxOfOrNull(NameCandidate::references)
            nameCandidates.filter { it.references == maxNameReferences }.singleOrNull()
        }

        val records = (0 until natureCount).map { id ->
            NatureRecord(
                id = id,
                name = names?.names?.get(id),
                statModifiers = stat.rows[id],
                positivePercent = stat.positivePercent,
                negativePercent = stat.negativePercent,
                flavorModifiers = flavor?.second?.get(id),
            )
        }
        return NatureResolution.Resolved(
            NatureCatalog(
                records = records,
                nameTableOffset = names?.root,
                statTableOffset = stat.root,
                flavorTableOffset = flavor?.first,
            ),
        )
    }

    private fun resolveIntegratedCandidates(
        session: RomAnalysisSession,
        callTargets: Set<Int>,
        codec: PokemonTextCodec?,
    ): List<IntegratedCandidate> = requireNotNull(session.gbaReferenceIndex).targets.keys.mapNotNull { root ->
        session.cancellation.throwIfCancellationRequested()
        val candidate = decodeIntegratedTable(
            session.rom,
            root,
            codec,
            session.cancellation,
        ) ?: return@mapNotNull null
        val evidence = session.nominatedGbaReferenceSites(root)
            ?.takeIf { it.siteEvidenceAvailable }
            ?: return@mapNotNull null
        if (evidence.instructionSites.none { site ->
                compiledFunctionContaining(session.rom, site, callTargets, session.cancellation)
                    ?.let(::isIntegratedStatConsumer) == true
            }
        ) {
            return@mapNotNull null
        }
        candidate
    }

    private fun decodeIntegratedTable(
        rom: RomImage,
        root: Int,
        codec: PokemonTextCodec?,
        cancellation: ParserCancellationToken,
    ): IntegratedCandidate? {
        if (root < 0 || root and 3 != 0 || root.toLong() + INTEGRATED_TABLE_BYTES > rom.size) return null
        val rows = mutableListOf<List<Int>>()
        repeat(INTEGRATED_NATURES) { id ->
            val row = root + id * INTEGRATED_RECORD_BYTES
            val raised = rom.u8(row + INTEGRATED_RAISED_STAT_OFFSET)
            val lowered = rom.u8(row + INTEGRATED_LOWERED_STAT_OFFSET)
            if (raised != id / MODIFIERS_PER_NATURE + 1 || lowered != id % MODIFIERS_PER_NATURE + 1) {
                return null
            }
            rows += List(MODIFIERS_PER_NATURE) { column ->
                when (column + 1) {
                    raised -> if (raised == lowered) 0 else 1
                    lowered -> -1
                    else -> 0
                }
            }
        }
        val names = codec?.let { selectedCodec ->
            (0 until INTEGRATED_NATURES).map { id ->
                decodeName(
                    rom,
                    root + id * INTEGRATED_RECORD_BYTES,
                    selectedCodec,
                    cancellation,
                ) ?: return@let null
            }.takeIf { it.distinct().size == INTEGRATED_NATURES }
        }
        val records = rows.indices.map { id ->
            NatureRecord(
                id = id,
                name = names?.get(id),
                statModifiers = rows[id],
                positivePercent = 110,
                negativePercent = 90,
            )
        }
        return IntegratedCandidate(
            NatureCatalog(
                records = records,
                nameTableOffset = root.takeIf { names != null },
                statTableOffset = root,
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

    private fun decodeNames(
        rom: RomImage,
        root: Int,
        count: Int,
        codec: PokemonTextCodec,
        cancellation: ParserCancellationToken,
    ): List<String>? {
        if (root and 3 != 0 || root < 0 || root.toLong() + count.toLong() * 4L > rom.size) return null
        val names = (0 until count).map { id ->
            decodeName(rom, root + id * 4, codec, cancellation) ?: return null
        }
        return names.takeIf { it.distinct().size >= maxOf(MIN_NATURES, count * 4 / 5) }
    }

    private fun decodeName(
        rom: RomImage,
        pointerOffset: Int,
        codec: PokemonTextCodec,
        cancellation: ParserCancellationToken,
    ): String? {
        val textRoot = rom.gbaPointer(pointerOffset) ?: return null
        val available = minOf(MAX_NAME_BYTES, rom.size - textRoot)
        val decoded = codec.decodeDetailed(rom, textRoot, available, cancellation)
        return decoded.text.takeIf { name ->
            decoded.terminated && decoded.validRatio == 1.0 &&
                name.length in 1..MAX_NAME_LENGTH && name.any(Char::isLetter) &&
                name.all { character -> character.isLetterOrDigit() || character in " -.'" }
        }
    }

    private fun decodedThumbCallTargets(
        rom: RomImage,
        cancellation: ParserCancellationToken,
    ): Set<Int> = buildSet {
        var offset = 0
        while (offset + 3 < rom.size) {
            if (offset % CANCELLATION_INTERVAL_BYTES == 0) {
                cancellation.throwIfCancellationRequested()
            }
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
        cancellation: ParserCancellationToken,
    ): FunctionBody? = findFunctionContaining(
        rom,
        site,
        callTargets,
        requireCallTarget = true,
        cancellation,
    ) ?: findFunctionContaining(
        rom,
        site,
        callTargets,
        requireCallTarget = false,
        cancellation,
    )

    private fun findFunctionContaining(
        rom: RomImage,
        site: Int,
        callTargets: Set<Int>,
        requireCallTarget: Boolean,
        cancellation: ParserCancellationToken,
    ): FunctionBody? {
        var offset = site and -2
        while (offset >= 0) {
            if ((site - offset) % CANCELLATION_INTERVAL_BYTES == 0) {
                cancellation.throwIfCancellationRequested()
            }
            val raw = rom.u16le(offset)
            val pushesLinkRegister = raw and 0xFE00 == 0xB400 && raw and 0x0100 != 0
            if (pushesLinkRegister && (!requireCallTarget || offset in callTargets)) {
                val body = decodeReachableFunction(rom, offset, cancellation)
                if (body != null && site in body.offsets) return body
            }
            offset -= 2
        }
        return null
    }

    private fun decodeReachableFunction(
        rom: RomImage,
        start: Int,
        cancellation: ParserCancellationToken,
    ): FunctionBody? {
        val pending = ArrayDeque<Int>()
        val decoded = linkedMapOf<Int, Arm7Instruction>()
        pending += start
        while (pending.isNotEmpty() && decoded.size < MAX_FUNCTION_INSTRUCTIONS) {
            cancellation.throwIfCancellationRequested()
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

    private fun isIntegratedStatConsumer(body: FunctionBody): Boolean {
        if (!hasTwentyByteIndex(body)) return false
        val fieldLoads = body.instructions.mapNotNull { instruction ->
            val transfer = instruction as? Arm7MemoryTransfer ?: return@mapNotNull null
            val address = transfer.address as? Arm7Address.RegisterOffset ?: return@mapNotNull null
            if (!transfer.load || transfer.signed || transfer.width != Arm7MemoryWidth.BYTE ||
                !address.add || !address.preIndexed || address.immediate !in INTEGRATED_STAT_OFFSETS
            ) {
                return@mapNotNull null
            }
            IntegratedFieldLoad(address.base, address.immediate, transfer.valueRegister)
        }
        val compares = body.instructions.filterIsInstance<Arm7Compare>()
        val hasFieldComparisons = fieldLoads.groupBy(IntegratedFieldLoad::base).values.any { loads ->
            val raised = loads.filter { it.offset == INTEGRATED_RAISED_STAT_OFFSET }
                .mapTo(mutableSetOf(), IntegratedFieldLoad::value)
            val lowered = loads.filter { it.offset == INTEGRATED_LOWERED_STAT_OFFSET }
                .mapTo(mutableSetOf(), IntegratedFieldLoad::value)
            if (raised.isEmpty() || lowered.isEmpty()) return@any false
            val equality = compares.any { compare ->
                compare.registersRead.any(raised::contains) && compare.registersRead.any(lowered::contains)
            }
            val fieldSelections = compares.count { compare ->
                compare.registersRead.any { register -> register in raised || register in lowered }
            }
            equality && fieldSelections >= 3
        }
        if (!hasFieldComparisons) return false

        val immediates = body.instructions.flatMap(::immediateValues).toSet()
        if (!immediates.containsAll(setOf(90L, 100L, 110L))) return false
        val hasMultiply = body.instructions.any { instruction ->
            instruction is Arm7Multiply ||
                (instruction is Arm7DataProcessing && instruction.operation == Arm7DataOperation.MULTIPLY)
        }
        val hasDivisionCall = body.instructions.any { it is Arm7Branch && it.link }
        return hasMultiply && hasDivisionCall
    }

    private fun hasTwentyByteIndex(body: FunctionBody): Boolean {
        val instructions = body.instructions.sortedBy(Arm7Instruction::offset)
        val directFactor = instructions.filterIsInstance<Arm7DataProcessing>()
            .filter { it.operation == Arm7DataOperation.MOVE && immediateValue(it.second) == 20L }
            .any { factor ->
                instructions.any { instruction ->
                    instruction.offset > factor.offset && instruction.offset - factor.offset <= MAX_INDEX_PROOF_BYTES &&
                        factor.destination in instruction.registersRead &&
                        (instruction is Arm7Multiply ||
                            (instruction is Arm7DataProcessing && instruction.operation == Arm7DataOperation.MULTIPLY))
                }
            }
        if (directFactor) return true

        instructions.forEachIndexed { firstIndex, instruction ->
            val first = instruction as? Arm7DataProcessing ?: return@forEachIndexed
            if (first.operation != Arm7DataOperation.LOGICAL_SHIFT_LEFT || immediateValue(first.second) != 2L) {
                return@forEachIndexed
            }
            val source = (first.first as? Arm7RegisterOperand)?.register ?: return@forEachIndexed
            instructions.drop(firstIndex + 1).forEach { middleInstruction ->
                if (middleInstruction.offset - first.offset > MAX_INDEX_PROOF_BYTES) return@forEach
                val middle = middleInstruction as? Arm7DataProcessing ?: return@forEach
                if (middle.operation != Arm7DataOperation.ADD ||
                    first.destination !in middle.registersRead || source !in middle.registersRead
                ) {
                    return@forEach
                }
                val finalShift = instructions.firstOrNull { finalInstruction ->
                    finalInstruction.offset > middle.offset &&
                        finalInstruction.offset - first.offset <= MAX_INDEX_PROOF_BYTES &&
                        finalInstruction is Arm7DataProcessing &&
                        finalInstruction.operation == Arm7DataOperation.LOGICAL_SHIFT_LEFT &&
                        immediateValue(finalInstruction.second) == 2L &&
                        middle.destination in finalInstruction.registersRead
                }
                if (finalShift != null) return true
            }
        }
        return false
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

    private data class IntegratedCandidate(val catalog: NatureCatalog)
    private data class IntegratedFieldLoad(
        val base: Arm7Register,
        val offset: Int,
        val value: Arm7Register,
    )
    private data class StatProof(val positivePercent: Int, val negativePercent: Int)
    private data class StatCandidate(
        val root: Int,
        val rows: List<List<Int>>,
        val positivePercent: Int,
        val negativePercent: Int,
    )
    private data class NameCandidate(val root: Int, val names: List<String>, val references: Int)

    private const val MODIFIERS_PER_NATURE = 5
    private const val INTEGRATED_NATURES = 25
    private const val INTEGRATED_RECORD_BYTES = 20
    private const val INTEGRATED_TABLE_BYTES = INTEGRATED_NATURES * INTEGRATED_RECORD_BYTES
    private const val INTEGRATED_RAISED_STAT_OFFSET = 4
    private const val INTEGRATED_LOWERED_STAT_OFFSET = 5
    private val INTEGRATED_STAT_OFFSETS = setOf(INTEGRATED_RAISED_STAT_OFFSET, INTEGRATED_LOWERED_STAT_OFFSET)
    private const val MAX_INDEX_PROOF_BYTES = 12
    private const val MIN_NATURES = 5
    private const val MAX_NATURES = 64
    private const val MAX_NAME_BYTES = 32
    private const val MAX_NAME_LENGTH = 24
    private const val MAX_FUNCTION_INSTRUCTIONS = 1_024
    private const val CANCELLATION_INTERVAL_BYTES = 4_096
}
