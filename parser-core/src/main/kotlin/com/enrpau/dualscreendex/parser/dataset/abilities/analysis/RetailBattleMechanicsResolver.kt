package com.enrpau.dualscreendex.parser.dataset.abilities.analysis

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7Address
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7Branch
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7ControlEffect
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7DataOperation
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7DataProcessing
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7DecodeResult
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7Immediate
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7Instruction
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7InstructionSet
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7MemoryTransfer
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7MemoryWidth
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7Register
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7RegisterOperand
import com.enrpau.dualscreendex.parser.analysis.thumb.ThumbDecoder
import com.enrpau.dualscreendex.parser.dataset.moves.MoveDetailsAbi
import com.enrpau.dualscreendex.parser.dataset.moves.ResolvedMoveDetailsLayout
import com.enrpau.dualscreendex.parser.io.RomImage
import java.util.ArrayDeque

data class RetailCallerEvidence(
    val callerLineageEntry: Int,
    val callSite: Int,
    val battleArrayRoot: Int,
    val recordStride: Int,
)

data class RetailBattleMechanicsProof(
    val decodedCallSites: List<Int>,
    val callerEvidence: List<RetailCallerEvidence>,
    val moveTableReferenceSites: List<Int>,
    val literalVeneerSites: List<Int>,
)

data class ResolvedRetailBattleMechanics(
    val routineEntry: Int,
    val abi: BattleMechanicsAbi,
    val mechanics: List<AttackMechanic>,
    val proof: RetailBattleMechanicsProof,
)

sealed interface RetailBattleMechanicsResolution {
    data class Resolved(val layout: ResolvedRetailBattleMechanics) : RetailBattleMechanicsResolution
    data class Unavailable(val reason: String) : RetailBattleMechanicsResolution
    data class Ambiguous(val entries: List<Int>) : RetailBattleMechanicsResolution
    data class BudgetExceeded(val reason: String) : RetailBattleMechanicsResolution
}

object RetailBattleMechanicsResolver {
    fun resolve(
        session: RomAnalysisSession,
        moveDetails: ResolvedMoveDetailsLayout,
        activeAbilityIds: Set<Int>,
        selectedAbi: BattleMechanicsAbi? = null,
    ): RetailBattleMechanicsResolution {
        if (moveDetails.table.abi != MoveDetailsAbi.RETAIL_12) {
            return RetailBattleMechanicsResolution.Unavailable("selected move ABI is not retail-12")
        }
        val abilityDomain = activeAbilityIds.filterTo(linkedSetOf()) { it > 0 }
        if (abilityDomain.isEmpty()) {
            return RetailBattleMechanicsResolution.Unavailable("typed active ability domain is empty")
        }
        val calls = decodedThumbCalls(session.rom)
            ?: return RetailBattleMechanicsResolution.BudgetExceeded("decoded Thumb call-edge budget exceeded")
        val moveRoot = GBA_ROM_BASE + moveDetails.table.offset.toInt()
        selectedAbi?.let { abi ->
            validateSelectedAbi(abi, moveDetails, moveRoot, abilityDomain)?.let { reason ->
                return RetailBattleMechanicsResolution.Unavailable(reason)
            }
        }
        val moveReferences = decodedLiteralReferences(session.rom, moveRoot)
        if (moveReferences.isEmpty()) {
            return RetailBattleMechanicsResolution.Unavailable(
                "selected move table has no decoded literal reference",
            )
        }
        val candidates = mutableListOf<ResolvedRetailBattleMechanics>()
        var decodedCallTargets = 0
        var provedCallerSites = 0
        var maxProvedCallerSitesPerTarget = 0
        var coherentCallerTargets = 0
        var moveRootRoutineTargets = 0
        var typedFieldCandidates = 0
        var semanticCandidates = 0
        var selectedCallerStrideContradictions = 0
        calls.toSortedMap().forEach { (entry, callSites) ->
            decodedCallTargets++
            val callerEvidence = callSites.mapNotNull { proveCallerArguments(session.rom, it) }
            provedCallerSites += callerEvidence.size
            maxProvedCallerSitesPerTarget = maxOf(maxProvedCallerSitesPerTarget, callerEvidence.size)
            val allCoherent = callerEvidence.groupBy { it.battleArrayRoot to it.recordStride }
                .values
                .filter { evidence -> evidence.map { it.callSite }.distinct().isNotEmpty() }
            val coherent = if (selectedAbi == null) {
                allCoherent
            } else {
                allCoherent.filter { evidence -> evidence.first().recordStride == selectedAbi.record.stride }
                    .also {
                        if (it.isEmpty() && allCoherent.isNotEmpty()) selectedCallerStrideContradictions++
                    }
            }
            if (coherent.size != 1) return@forEach
            coherentCallerTargets++
            val evidence = coherent.single().distinctBy { it.callSite }.sortedBy { it.callSite }
            val stride = evidence.first().recordStride
            val routine = reachableRoutine(session.rom, entry, moveReferences) ?: return@forEach
            if (routine.moveReferenceSites.isEmpty()) return@forEach
            moveRootRoutineTargets++
            val fields = scalarAccessCandidates(session.rom, entry, stride) ?: return@forEach
            val moveAbi = MoveMechanicsAbi(
                tableRoot = moveRoot,
                stride = moveDetails.table.abi.recordSize,
                effect = ScalarField(0, ScalarWidth.U8),
                power = ScalarField(1, ScalarWidth.U8),
                type = ScalarField(2, ScalarWidth.U8),
            )
            val abiCandidates = selectedAbi?.let(::listOf) ?: buildList {
                fields.filter { it.width in setOf(ScalarWidth.U8, ScalarWidth.U16) }.forEach { ability ->
                    fields.filter { it.width in setOf(ScalarWidth.U16, ScalarWidth.U32) }.forEach { attack ->
                        if (ability != attack) {
                            add(
                                BattleMechanicsAbi(
                                    record = BattleRecordAbi(stride = stride, attack = attack, ability = ability),
                                    move = moveAbi,
                                    activeAbilityIds = abilityDomain,
                                    roleContract = BattleRoleContract.DirectPointers(0, 1),
                                ),
                            )
                        }
                    }
                }
            }
            abiCandidates.forEach { abi ->
                val ability = abi.record.ability
                val attack = abi.record.attack
                if (ability !in fields || attack !in fields) return@forEach
                typedFieldCandidates++
                val semantic = BattleRoleProvenance.analyze(
                    image = session.rom,
                    entry = entry,
                    instructionSet = Arm7InstructionSet.THUMB,
                    abi = abi,
                    maxDecodedInstructions = MAX_ROUTINE_INSTRUCTIONS,
                )
                if (semantic.attackMechanics.isEmpty() || semantic.attackMechanics.hasContradictoryEffects()) {
                    return@forEach
                }
                semanticCandidates++
                if (semantic.fieldReads.none { it.role == BattleRecordRole.ATTACKER && it.field == attack && it.access == attack }) {
                    return@forEach
                }
                if (semantic.fieldReads.none { it.role == BattleRecordRole.ATTACKER && it.field == ability && it.access == ability }) {
                    return@forEach
                }
                candidates += ResolvedRetailBattleMechanics(
                    routineEntry = entry,
                    abi = abi,
                    mechanics = semantic.attackMechanics.distinct().sortedBy(AttackMechanic::abilityId),
                    proof = RetailBattleMechanicsProof(
                        decodedCallSites = callSites.distinct().sorted(),
                        callerEvidence = evidence,
                        moveTableReferenceSites = routine.moveReferenceSites.sorted(),
                        literalVeneerSites = routine.literalVeneerSites.sorted(),
                    ),
                )
            }
        }
        val distinct = candidates.distinctBy { candidate ->
            listOf(
                candidate.routineEntry,
                candidate.abi.record.attack,
                candidate.abi.record.ability,
                candidate.mechanics,
            )
        }
        val canonical = collapseMandatoryVeneerAliases(session.rom, distinct)
        return when (canonical.size) {
            0 -> RetailBattleMechanicsResolution.Unavailable(
                (if (selectedAbi != null &&
                    coherentCallerTargets == 0 &&
                    selectedCallerStrideContradictions != 0
                ) {
                    "selected ABI record stride ${selectedAbi.record.stride} contradicts decoded caller evidence; "
                } else {
                    "no routine proved caller roles, selected move-table use, typed fields, and attack mechanics "
                }) +
                    "(decodedCallTargets=$decodedCallTargets, coherentCallerTargets=$coherentCallerTargets, " +
                    "provedCallerSites=$provedCallerSites, maxProvedCallerSitesPerTarget=$maxProvedCallerSitesPerTarget, " +
                    "moveRootRoutineTargets=$moveRootRoutineTargets, typedFieldCandidates=$typedFieldCandidates, " +
                    "semanticCandidates=$semanticCandidates)",
            )
            1 -> RetailBattleMechanicsResolution.Resolved(canonical.single())
            else -> RetailBattleMechanicsResolution.Ambiguous(canonical.map { it.routineEntry }.distinct().sorted())
        }
    }

    private fun collapseMandatoryVeneerAliases(
        image: RomImage,
        candidates: List<ResolvedRetailBattleMechanics>,
    ): List<ResolvedRetailBattleMechanics> {
        val remaining = candidates.toMutableList()
        var changed: Boolean
        do {
            changed = false
            val alias = remaining.firstNotNullOfOrNull { source ->
                remaining.singleOrNull { target ->
                    target !== source &&
                        source.abi.sameContractAs(target.abi) &&
                        source.mechanics == target.mechanics &&
                        shareMandatoryVeneerContinuation(image, source, target)
                }?.let { target -> source to target }
            }
            if (alias != null) {
                val (source, target) = alias
                remaining.remove(source)
                remaining[remaining.indexOf(target)] = target.copy(
                    proof = RetailBattleMechanicsProof(
                        decodedCallSites = (source.proof.decodedCallSites + target.proof.decodedCallSites)
                            .distinct()
                            .sorted(),
                        callerEvidence = (source.proof.callerEvidence + target.proof.callerEvidence)
                            .distinctBy { it.callSite }
                            .sortedBy { it.callSite },
                        moveTableReferenceSites =
                            (source.proof.moveTableReferenceSites + target.proof.moveTableReferenceSites).distinct().sorted(),
                        literalVeneerSites =
                            (source.proof.literalVeneerSites + target.proof.literalVeneerSites).distinct().sorted(),
                    ),
                )
                changed = true
            }
        } while (changed)
        return remaining
    }

    private fun BattleMechanicsAbi.sameContractAs(other: BattleMechanicsAbi): Boolean =
        record == other.record &&
            move == other.move &&
            activeAbilityIds == other.activeAbilityIds &&
            withheldAbilityIds == other.withheldAbilityIds &&
            roleContract == other.roleContract &&
            moveParameterRegister == other.moveParameterRegister

    private fun shareMandatoryVeneerContinuation(
        image: RomImage,
        source: ResolvedRetailBattleMechanics,
        target: ResolvedRetailBattleMechanics,
    ): Boolean = source.proof.literalVeneerSites
        .mapNotNull { site ->
            decoded(image, site + 2)
                ?.let { literalThumbVeneer(image, it) }
                ?.takeIf { it.loadOffset == site }
                ?.targetOffset
        }
        .distinct()
        .singleOrNull { continuation ->
            allEntryPathsReach(image, source.routineEntry, continuation) &&
                allEntryPathsReach(image, target.routineEntry, continuation)
        } != null

    private fun allEntryPathsReach(image: RomImage, source: Int, target: Int): Boolean {
        if (source == target) return true
        val memo = mutableMapOf<Int, Boolean>()
        val visiting = mutableSetOf<Int>()
        var decodedCount = 0

        fun reaches(offset: Int): Boolean {
            if (offset == target) return true
            memo[offset]?.let { return it }
            if (!visiting.add(offset) || ++decodedCount > MAX_ALIAS_INSTRUCTIONS) return false
            val instruction = decoded(image, offset)
            val successors = when (val control = instruction?.controlEffect) {
                is Arm7ControlEffect.Sequential, is Arm7ControlEffect.Call ->
                    setOf(offset + instruction.size)
                is Arm7ControlEffect.DirectBranch -> buildSet {
                    add(control.target.toInt() and -2)
                    if (control.conditional) add(offset + instruction.size)
                }
                is Arm7ControlEffect.IndirectBranch, is Arm7ControlEffect.ProgramCounterWrite ->
                    literalThumbVeneer(image, instruction)?.targetOffset?.let(::setOf).orEmpty()
                else -> emptySet()
            }
            val result = successors.isNotEmpty() && successors.all { it in 0 until image.size && reaches(it) }
            visiting.remove(offset)
            memo[offset] = result
            return result
        }

        return reaches(source)
    }

    private fun validateSelectedAbi(
        abi: BattleMechanicsAbi,
        moveDetails: ResolvedMoveDetailsLayout,
        moveRoot: Int,
        abilityDomain: Set<Int>,
    ): String? {
        if (abi.roleContract != BattleRoleContract.DirectPointers(0, 1)) {
            return "selected ABI role contract is not the decoded retail direct-pointer contract"
        }
        if (abi.activeAbilityIds != abilityDomain.sorted()) {
            return "selected ABI ability domain contradicts the parser-selected ability domain"
        }
        if (abi.move.tableRoot != moveRoot ||
            abi.move.stride != moveDetails.table.abi.recordSize ||
            abi.move.effect != ScalarField(0, ScalarWidth.U8) ||
            abi.move.power != ScalarField(1, ScalarWidth.U8) ||
            abi.move.type != ScalarField(2, ScalarWidth.U8)
        ) {
            return "selected ABI move layout contradicts the parser-selected retail-12 layout"
        }
        return null
    }

    private fun decodedThumbCalls(image: RomImage): Map<Int, List<Int>>? {
        val calls = linkedMapOf<Int, MutableList<Int>>()
        var edges = 0
        var offset = 0
        while (offset <= image.size - 4) {
            val instruction = decoded(image, offset)
            val branch = instruction as? Arm7Branch
            if (branch?.link == true && branch.instructionSet == Arm7InstructionSet.THUMB) {
                edges++
                if (edges > MAX_CALL_EDGES) return null
                val target = branch.target.toInt() and -2
                if (target in 0 until image.size && target and 1 == 0) {
                    calls.getOrPut(target) { mutableListOf() } += offset
                }
            }
            offset += 2
        }
        return calls
    }

    private fun decodedLiteralReferences(image: RomImage, target: Int): Set<Int> = buildSet {
        var offset = 0
        while (offset <= image.size - 2) {
            val transfer = decoded(image, offset) as? Arm7MemoryTransfer
            val address = transfer?.address as? Arm7Address.PcRelative
            if (transfer?.load == true && transfer.width == Arm7MemoryWidth.WORD &&
                address != null && address.resolvedAddress in 0..image.size - 4L &&
                image.u32le(address.resolvedAddress.toInt()).toInt() == target
            ) {
                add(offset)
            }
            offset += 2
        }
    }

    private fun proveCallerArguments(image: RomImage, callSite: Int): RetailCallerEvidence? {
        val entry = callerLineageEntry(image, callSite)
        val values = mutableMapOf<Arm7Register, CallerValue>()
        var offset = entry
        while (offset < callSite) {
            val instruction = decoded(image, offset) ?: return null
            executeCallerInstruction(image, instruction, values)
            if (instruction.controlEffect !is Arm7ControlEffect.Sequential &&
                instruction.controlEffect !is Arm7ControlEffect.Call
            ) return null
            offset += instruction.size
        }
        val attacker = values[Arm7Register.R0] as? CallerValue.BattlePointer ?: return null
        val defender = values[Arm7Register.R1] as? CallerValue.BattlePointer ?: return null
        if (attacker.root != defender.root || attacker.stride != defender.stride) return null
        if (attacker.root !in GBA_EWRAM_START until GBA_IWRAM_END_EXCLUSIVE) return null
        return RetailCallerEvidence(entry, callSite, attacker.root, attacker.stride)
    }

    private fun callerLineageEntry(image: RomImage, callSite: Int): Int {
        var cursor = callSite
        while (cursor > 0) {
            val previous = previousThumbInstruction(image, cursor) ?: return cursor
            when (previous.controlEffect) {
                is Arm7ControlEffect.Sequential,
                is Arm7ControlEffect.Call,
                -> cursor = previous.offset
                else -> return cursor
            }
        }
        return 0
    }

    private fun previousThumbInstruction(image: RomImage, endExclusive: Int): Arm7Instruction? {
        val halfword = (endExclusive - 2).takeIf { it >= 0 }?.let { decoded(image, it) }
        if (halfword != null && halfword.offset + halfword.size == endExclusive) return halfword
        val word = (endExclusive - 4).takeIf { it >= 0 }?.let { decoded(image, it) }
        return word?.takeIf { it.offset + it.size == endExclusive }
    }

    private fun executeCallerInstruction(
        image: RomImage,
        instruction: Arm7Instruction,
        values: MutableMap<Arm7Register, CallerValue>,
    ) {
        if (instruction.controlEffect is Arm7ControlEffect.Call) {
            CALLER_VOLATILE_REGISTERS.forEach { values[it] = CallerValue.Unknown }
            return
        }
        when (instruction) {
            is Arm7DataProcessing -> {
                val first = instruction.first?.let { callerOperand(it, values) }
                val second = callerOperand(instruction.second, values)
                values[instruction.destination] = when (instruction.operation) {
                    Arm7DataOperation.MOVE -> second
                    Arm7DataOperation.ADD -> callerAdd(first, second)
                    Arm7DataOperation.MULTIPLY -> callerMultiply(first, second)
                    else -> CallerValue.Unknown
                }
            }
            is Arm7MemoryTransfer -> if (instruction.load) {
                values[instruction.valueRegister] = callerLoad(image, instruction, values)
            } else {
                instruction.registersWritten.forEach { values[it] = CallerValue.Unknown }
            }
            else -> instruction.registersWritten.forEach { values[it] = CallerValue.Unknown }
        }
    }

    private fun callerOperand(
        operand: Any,
        values: Map<Arm7Register, CallerValue>,
    ): CallerValue = when (operand) {
        is Arm7Immediate -> CallerValue.Constant(operand.value.toInt())
        is Arm7RegisterOperand -> values[operand.register] ?: CallerValue.Unknown
        else -> CallerValue.Unknown
    }

    private fun callerLoad(
        image: RomImage,
        instruction: Arm7MemoryTransfer,
        values: Map<Arm7Register, CallerValue>,
    ): CallerValue = when (val address = instruction.address) {
        is Arm7Address.PcRelative -> if (instruction.width == Arm7MemoryWidth.WORD &&
            address.resolvedAddress in 0..image.size - 4L
        ) {
            CallerValue.Absolute(image.u32le(address.resolvedAddress.toInt()).toInt())
        } else CallerValue.Unknown
        is Arm7Address.RegisterOffset -> {
            val base = values[address.base]
            if (base is CallerValue.Absolute && address.index == null) {
                CallerValue.Index(base.address + if (address.add) address.immediate else -address.immediate)
            } else CallerValue.Unknown
        }
        else -> CallerValue.Unknown
    }

    private fun callerAdd(first: CallerValue?, second: CallerValue): CallerValue = when {
        first is CallerValue.ScaledIndex && second is CallerValue.Absolute ->
            CallerValue.BattlePointer(second.address, first.stride)
        second is CallerValue.ScaledIndex && first is CallerValue.Absolute ->
            CallerValue.BattlePointer(first.address, second.stride)
        first is CallerValue.BattlePointer && second is CallerValue.Constant && second.value == 0 -> first
        second is CallerValue.BattlePointer && first is CallerValue.Constant && first.value == 0 -> second
        else -> CallerValue.Unknown
    }

    private fun callerMultiply(first: CallerValue?, second: CallerValue): CallerValue = when {
        first is CallerValue.Index && second is CallerValue.Constant && second.value > 0 ->
            CallerValue.ScaledIndex(first.origin, second.value)
        second is CallerValue.Index && first is CallerValue.Constant && first.value > 0 ->
            CallerValue.ScaledIndex(second.origin, first.value)
        else -> CallerValue.Unknown
    }

    private fun reachableRoutine(image: RomImage, entry: Int, moveReferences: Set<Int>): RoutineEvidence? {
        val queue = ArrayDeque<Int>().apply { add(entry) }
        val visited = linkedMapOf<Int, Arm7Instruction>()
        val veneerSites = linkedSetOf<Int>()
        while (queue.isNotEmpty() && visited.size < MAX_ROUTINE_INSTRUCTIONS) {
            val offset = queue.removeFirst()
            if (offset in visited) continue
            val instruction = decoded(image, offset) ?: continue
            visited[offset] = instruction
            when (val control = instruction.controlEffect) {
                is Arm7ControlEffect.Sequential -> queue += offset + instruction.size
                is Arm7ControlEffect.DirectBranch -> {
                    if (control.conditional) queue += offset + instruction.size
                    val target = control.target.toInt() and -2
                    if (target in 0 until image.size) queue += target
                }
                is Arm7ControlEffect.Call -> queue += offset + instruction.size
                is Arm7ControlEffect.IndirectBranch, is Arm7ControlEffect.ProgramCounterWrite -> {
                    literalThumbVeneer(image, instruction)?.let { veneer ->
                        veneerSites += veneer.loadOffset
                        queue += veneer.targetOffset
                    }
                }
                else -> Unit
            }
        }
        if (visited.size == MAX_ROUTINE_INSTRUCTIONS && queue.isNotEmpty()) return null
        return RoutineEvidence(
            instructions = visited.values.toList(),
            moveReferenceSites = visited.keys.intersect(moveReferences),
            literalVeneerSites = veneerSites,
        )
    }

    private fun scalarAccessCandidates(
        image: RomImage,
        entry: Int,
        stride: Int,
    ): Set<ScalarField>? {
        val initial = PointerOffsetState(
            mapOf(
                Arm7Register.R0 to setOf(0),
                Arm7Register.R1 to setOf(0),
            ),
        )
        val states = mutableMapOf(entry to initial)
        val queue = ArrayDeque<Int>().apply { add(entry) }
        val fields = linkedSetOf<ScalarField>()
        var processed = 0
        while (queue.isNotEmpty()) {
            if (++processed > MAX_POINTER_STATE_TRANSFERS) return null
            val offset = queue.removeFirst()
            val instruction = decoded(image, offset) ?: continue
            val output = transferPointerOffsets(states.getValue(offset), instruction, stride, fields)
            routineSuccessors(image, instruction).forEach { successor ->
                val merged = states[successor]?.merge(output) ?: output
                if (merged != states[successor]) {
                    states[successor] = merged
                    queue += successor
                }
            }
        }
        return fields
    }

    private fun transferPointerOffsets(
        input: PointerOffsetState,
        instruction: Arm7Instruction,
        stride: Int,
        fields: MutableSet<ScalarField>,
    ): PointerOffsetState {
        val values = input.registers.toMutableMap()
        if (instruction.controlEffect is Arm7ControlEffect.Call) {
            CALLER_VOLATILE_REGISTERS.forEach(values::remove)
            return PointerOffsetState(values)
        }
        when (instruction) {
            is Arm7DataProcessing -> {
                val first = instruction.first?.let { pointerOperand(it, values) }
                val second = pointerOperand(instruction.second, values)
                val result = when (instruction.operation) {
                    Arm7DataOperation.MOVE -> second.offsets
                    Arm7DataOperation.ADD -> combinePointerAndConstant(first, second, add = true, stride)
                    Arm7DataOperation.SUBTRACT -> combinePointerAndConstant(first, second, add = false, stride)
                    else -> null
                }
                if (result.isNullOrEmpty()) values.remove(instruction.destination)
                else values[instruction.destination] = result
            }
            is Arm7MemoryTransfer -> {
                val address = instruction.address as? Arm7Address.RegisterOffset
                if (address != null && address.index == null) {
                    val baseOffsets = values[address.base].orEmpty()
                    val delta = if (address.add) address.immediate else -address.immediate
                    val width = instruction.width.toScalarWidth()
                    if (instruction.load && width != null) {
                        baseOffsets.map { it + delta }
                            .filter { it >= 0 && it + width.bytes <= stride }
                            .mapTo(fields) { ScalarField(it, width) }
                    }
                    if (address.writeBack) {
                        val adjusted = baseOffsets.mapTo(linkedSetOf()) { it + delta }
                            .filterTo(linkedSetOf()) { it in 0 until stride }
                        if (adjusted.isEmpty()) values.remove(address.base)
                        else values[address.base] = adjusted
                    }
                }
                if (instruction.load) values.remove(instruction.valueRegister)
            }
            else -> instruction.registersWritten.forEach(values::remove)
        }
        return PointerOffsetState(values)
    }

    private fun pointerOperand(
        operand: Any,
        values: Map<Arm7Register, Set<Int>>,
    ): PointerOperand = when (operand) {
        is Arm7Immediate -> PointerOperand(constant = operand.value.toInt())
        is Arm7RegisterOperand -> PointerOperand(offsets = values[operand.register])
        else -> PointerOperand()
    }

    private fun combinePointerAndConstant(
        first: PointerOperand?,
        second: PointerOperand,
        add: Boolean,
        stride: Int,
    ): Set<Int>? {
        val pointer = first?.offsets ?: if (add) second.offsets else null
        val constant = second.constant ?: if (add) first?.constant else null
        if (pointer == null || constant == null) return null
        val delta = if (add) constant else -constant
        return pointer.mapTo(linkedSetOf()) { it + delta }.filterTo(linkedSetOf()) { it in 0 until stride }
    }

    private fun routineSuccessors(image: RomImage, instruction: Arm7Instruction): Set<Int> = buildSet {
        when (val control = instruction.controlEffect) {
            is Arm7ControlEffect.Sequential -> add(instruction.offset + instruction.size)
            is Arm7ControlEffect.DirectBranch -> {
                if (control.conditional) add(instruction.offset + instruction.size)
                val target = control.target.toInt() and -2
                if (target in 0 until image.size) add(target)
            }
            is Arm7ControlEffect.Call -> add(instruction.offset + instruction.size)
            is Arm7ControlEffect.IndirectBranch, is Arm7ControlEffect.ProgramCounterWrite ->
                literalThumbVeneer(image, instruction)?.let { add(it.targetOffset) }
            else -> Unit
        }
    }

    private fun List<AttackMechanic>.hasContradictoryEffects(): Boolean =
        groupBy { it.abilityId to it.predicates }.values.any { mechanics ->
            mechanics.map { it.effect }.distinct().size > 1
        }

    private fun decoded(image: RomImage, offset: Int): Arm7Instruction? =
        (ThumbDecoder.decode(image, offset) as? Arm7DecodeResult.Decoded)?.instruction

    private fun Arm7MemoryWidth.toScalarWidth(): ScalarWidth? = when (this) {
        Arm7MemoryWidth.BYTE -> ScalarWidth.U8
        Arm7MemoryWidth.HALFWORD -> ScalarWidth.U16
        Arm7MemoryWidth.WORD -> ScalarWidth.U32
    }

    private sealed interface CallerValue {
        data object Unknown : CallerValue
        data class Constant(val value: Int) : CallerValue
        data class Absolute(val address: Int) : CallerValue
        data class Index(val origin: Int) : CallerValue
        data class ScaledIndex(val origin: Int, val stride: Int) : CallerValue
        data class BattlePointer(val root: Int, val stride: Int) : CallerValue
    }

    private data class RoutineEvidence(
        val instructions: List<Arm7Instruction>,
        val moveReferenceSites: Set<Int>,
        val literalVeneerSites: Set<Int>,
    )

    private data class PointerOffsetState(
        val registers: Map<Arm7Register, Set<Int>>,
    ) {
        fun merge(other: PointerOffsetState): PointerOffsetState {
            val merged = linkedMapOf<Arm7Register, Set<Int>>()
            (registers.keys + other.registers.keys).forEach { register ->
                val values = registers[register].orEmpty() + other.registers[register].orEmpty()
                if (values.isNotEmpty()) merged[register] = values
            }
            return PointerOffsetState(merged)
        }
    }

    private data class PointerOperand(
        val offsets: Set<Int>? = null,
        val constant: Int? = null,
    )

    private const val GBA_ROM_BASE = 0x0800_0000
    private const val GBA_EWRAM_START = 0x0200_0000
    private const val GBA_IWRAM_END_EXCLUSIVE = 0x0300_8000
    private const val MAX_CALL_EDGES = 262_144
    private const val MAX_ROUTINE_INSTRUCTIONS = 4_096
    private const val MAX_POINTER_STATE_TRANSFERS = 65_536
    private const val MAX_ALIAS_INSTRUCTIONS = 512
    private val CALLER_VOLATILE_REGISTERS = setOf(
        Arm7Register.R0,
        Arm7Register.R1,
        Arm7Register.R2,
        Arm7Register.R3,
        Arm7Register.R12,
        Arm7Register.LR,
    )
}
