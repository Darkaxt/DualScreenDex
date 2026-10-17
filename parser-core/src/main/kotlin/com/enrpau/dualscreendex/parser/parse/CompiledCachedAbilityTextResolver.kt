package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7Branch
import com.enrpau.dualscreendex.parser.analysis.arm7.Arm7DecodeResult
import com.enrpau.dualscreendex.parser.analysis.thumb.ThumbDecoder
import com.enrpau.dualscreendex.parser.dataset.abilities.AbilityDescriptionTableLayout
import com.enrpau.dualscreendex.parser.dataset.abilities.AbilityNameTableLayout
import com.enrpau.dualscreendex.parser.dataset.abilities.CompiledAbilityTextBinding
import com.enrpau.dualscreendex.parser.dataset.abilities.CompiledSpeciesAbilitySlots
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.parse.CompiledAbilityNativeFlow.Atom
import com.enrpau.dualscreendex.parser.parse.CompiledAbilityNativeFlow.Expression
import com.enrpau.dualscreendex.parser.parse.CompiledAbilityNativeFlow.Companion.constant
import com.enrpau.dualscreendex.parser.parse.CompiledAbilityNativeFlow.Companion.value
import com.enrpau.dualscreendex.parser.resolution.BudgetKind
import kotlin.math.abs

/** Selected-core defaults -> object fields -> summary cache -> the same native text ID. */
internal class CompiledCachedAbilityTextResolver(
    private val session: RomAnalysisSession,
    private val core: TableLayout,
    private val budget: CompiledAbilityProbeBudget,
) {
    private val rom = session.rom
    private val flow = CompiledAbilityNativeFlow(rom, budget)
    private val getter = CompiledCachedAbilityGetter(rom, core, budget, flow)
    private val apiCache = mutableMapOf<Pair<Int, List<Expression>>, Api?>()
    private val channelCache = mutableMapOf<Pair<Int, Int>, CompiledCachedAbilityGetter.Channels?>()
    private val loopCache = mutableMapOf<Pair<Int, Int>, List<Loop>>()
    private data class Source(val global: Long, val offset: Int)
    private data class Text(val entry: Int, val source: Source, val nameSite: Int, val descriptionSite: Int)
    private data class Summary(val getter: Int, val field: Int, val count: Int)
    private data class Loop(val target: Int, val end: Int, val count: Int)
    private data class Producer(val channels: CompiledCachedAbilityGetter.Channels, val setter: Int, val firstField: Int)
    private data class Api(val returned: Expression, val writes: List<Pair<CompiledAbilityNativeFlow.Cell, Expression>>)

    fun resolve(names: Int, descriptions: Int): List<CompiledAbilityTextBinding> {
        val nameSites = sites(names) ?: return emptyList()
        val descriptionSites = sites(descriptions) ?: return emptyList()
        val widths = nameSites.mapNotNull { site ->
            budget.step()
            val width = compiledFixedStrideConsumer(rom, site) ?: inlineTextStride(rom, site)
            if (width != null) width else if (ownedLiteralData(site)) null else return emptyList()
        }.distinct()
        val width = widths.singleOrNull()?.takeIf { it in 8..32 } ?: return emptyList()
        val accessor = CompiledAbilityAccessor(rom, budget)
        val analyzed = mutableMapOf<Int, List<Text>>()
        val texts = mutableListOf<Text>()
        for (site in descriptionSites) {
            val candidates = accessor.entriesBefore(site).flatMap { entry ->
                analyzed.getOrPut(entry) { text(entry, names, descriptions, width) }
            }
            if (candidates.isEmpty()) return emptyList()
            texts += candidates
        }
        val contracts = texts.distinct().groupBy { it.source.global }
        if (contracts.isEmpty()) return emptyList()
        val coreSites = sites(core.offset) ?: return emptyList()
        val producers by lazy { producers(coreSites) }
        val result = mutableListOf<CompiledAbilityTextBinding>()
        for ((global, owned) in contracts) {
            val offsets = owned.map { it.source.offset }.distinct().sorted()
            if (offsets.size !in 2..8 || offsets != List(offsets.size) { offsets.first() + it * 2 }) continue
            val source = Source(global, offsets.first())
            for (summary in summaries(source, offsets.size, owned.map { it.entry })) {
                for (producer in producers.filter { it.firstField == summary.field && it.channels.groups.last().offsets.size == summary.count - 1 }) {
                    if (!aliases(summary, producer.setter)) continue
                    val fields = producer.channels.offsets
                    val slots = CompiledSpeciesAbilitySlots(core.offset, core.count, core.stride ?: core.recordSize,
                        fields.first(), 2, fields.size, core.recordSize, fields)
                    var maximum = 0
                    for (species in 1 until core.count) {
                        budget.step()
                        maximum = maxOf(maximum, slots.read(rom, species).maxOrNull() ?: 0)
                    }
                    val count = extent(names, descriptions, width, maximum) ?: continue
                    val text = owned.first()
                    if (result.size >= session.limits.maxCandidatesPerDataset) throw CompiledAbilityBudgetExceeded(
                        BudgetKind.CANDIDATES, result.size + 1L, session.limits.maxCandidatesPerDataset.toLong())
                    result += CompiledAbilityTextBinding(
                        AbilityNameTableLayout(names, count, width, terminatedInlineArray = true),
                        AbilityDescriptionTableLayout(descriptions, count), slots, producer.channels.entry,
                        text.nameSite, text.descriptionSite, staticDefaultOnly = true,
                    )
                }
            }
        }
        return result.distinctBy { Triple(it.names, it.descriptions, it.speciesSlots) }
    }

    private fun ownedLiteralData(site: Int): Boolean {
        val cell = site and -4
        val accessor = CompiledAbilityAccessor(rom, budget)
        for (load in maxOf(0xC0, cell - 128) until cell step 2) {
            budget.step()
            val op = rom.u16le(load)
            if (op and 0xF800 != 0x4800 || ((load + 4) and -4) + (op and 255) * 4 != cell) continue
            for (entry in accessor.entriesBefore(load)) {
                budget.root()
                val proof = flow.analyze(flow.initial(entry), entry..minOf(cell - 2, entry + 510))
                // A complete returning function reads this word as data after its
                // return boundary. Do not silently drop an unsupported code consumer.
                if (proof.complete && proof.exits.isEmpty() && proof.returns.isNotEmpty() &&
                    load in proof.visitedSites && proof.returns.all { it.state.pc < cell }
                ) return true
            }
        }
        return false
    }

    private fun text(entry: Int, names: Int, descriptions: Int, width: Int): List<Text> {
        budget.root()
        val result = flow.analyze(flow.initial(entry), span(entry, 512))
        if (!result.complete || result.exits.isNotEmpty() || result.returns.isEmpty()) return emptyList()
        val namesAt = mutableListOf<Pair<Atom.Read, Int>>()
        val descriptionsAt = mutableListOf<Pair<Atom.Read, Int>>()
        for (call in result.calls) {
            for (argument in call.arguments) {
                val atom = indexedId(argument, names, width)
                if (atom != null) namesAt += atom to call.site
                val pointer = argument.atom as? Atom.Read
                if (pointer?.bytes == 4) indexedId(pointer.address, descriptions, 4)?.let { descriptionsAt += it to call.site }
            }
        }
        return namesAt.flatMap { (id, nameSite) ->
            val source = source(id) ?: return@flatMap emptyList()
            descriptionsAt.filter { it.first == id && it.second > nameSite && abs(it.second.toLong() - nameSite) <= 128 }
                .map { Text(entry, source, nameSite, it.second) }
        }.distinct()
    }

    private fun indexedId(expression: Expression, root: Int, scale: Int): Atom.Read? {
        if (!expression.known || expression.constant != 0x08000000L + root || expression.terms.size != 1) return null
        val term = expression.terms.entries.single()
        return (term.key as? Atom.Read)?.takeIf { it.bytes == 2 && term.value == scale.toLong() }
    }

    private fun source(id: Atom.Read): Source? {
        if (id.bytes != 2 || id.address.terms.size != 1 || id.address.constant !in 0..254 || id.address.constant and 1L != 0L) return null
        val term = id.address.terms.entries.single()
        val pointer = term.key as? Atom.Read ?: return null
        val global = pointer.address.number ?: return null
        return Source(global, id.address.constant.toInt()).takeIf {
            term.value == 1L && pointer.bytes == 4 && global and 3L == 0L && isRam(global)
        }
    }

    private fun summaries(source: Source, count: Int, entries: List<Int>): List<Summary> {
        val candidates = linkedSetOf<Int>()
        // A bounded nomination window is not a claim about every writer in the game.
        // Only a complete finite cache loop and exact object-field alias grant authority.
        val start = maxOf(0xC0, entries.min() - 16384) and -2
        val end = minOf(rom.size - 2, entries.max() + 512) and -2
        for (site in start..end step 2) {
            budget.step()
            if (literal(site) == source.global) candidates += site
        }
        val result = mutableListOf<Summary>()
        val localLoops = loops(start, end).filter { it.count == count }
        for (site in candidates) {
            for (loop in localLoops) {
                budget.step()
                if (loop.target < site || loop.end > site + 512) continue
                val trace = flow.analyze(flow.initial(site), site..loop.end)
                if (!trace.complete) continue
                val completed = trace.exits.filter { it.pc == loop.end + 2 }
                if (completed.isEmpty()) continue
                // Certify one complete path at a time; a union of stores from disjoint
                // branches cannot establish a cache that no path actually produced.
                val contracts = completed.map { state -> summary(state, source, count) }
                if (contracts.any { it == null }) continue
                result += contracts.filterNotNull()
            }
        }
        return result.distinct()
    }

    private fun summary(state: CompiledAbilityNativeFlow.State, source: Source, count: Int): Summary? {
        val writes = state.memory.filterKeys { cell ->
            cell.bytes == 2 && (cell.address.terms.entries.singleOrNull()?.let { (atom, coefficient) ->
                val pointer = atom as? Atom.Read
                coefficient == 1L && pointer?.bytes == 4 && pointer.address.number == source.global
            } == true) && cell.address.constant in source.offset.toLong() until source.offset + count * 2L
        }.entries.sortedBy { it.key.address.constant }
        if (writes.map { it.key.address.constant } != List(count) { source.offset + it * 2L }) return null
        val getters = writes.map { storedCall(it.value) ?: return null }
        val first = getters.first()
        val field = first.arguments[1].number?.takeIf { it in 0L..256L - count }?.toInt() ?: return null
        if (getters.indices.any { index ->
            val call = getters[index]
            call.entry != first.entry || call.arguments[0] != first.arguments[0] || call.arguments[1].number != field + index.toLong()
        }) return null
        return Summary(first.entry, field, count)
    }

    private fun producers(coreSites: List<Int>): List<Producer> {
        val candidates = linkedSetOf<Loop>()
        val ranges = mutableListOf<IntRange>()
        for (site in coreSites.sorted()) {
            val next = maxOf(0xC0, site - 256)..minOf(rom.size - 2, site + 512)
            val previous = ranges.lastOrNull()
            if (previous != null && next.first <= previous.last + 2) {
                ranges[ranges.lastIndex] = previous.first..maxOf(previous.last, next.last)
            } else ranges += next
        }
        for (range in ranges) candidates += loops(range.first, range.last)
        val result = mutableListOf<Producer>()
        for (loop in candidates) {
            val calls = loopCalls(loop) ?: continue
            if (calls.size != 2) continue
            // Callee ownership is cheaper and stronger than symbolically unrolling
            // every nearby loop that happens to compare a small counter.
            val channelKey = calls.first() to loop.count
            if (channelKey !in channelCache) channelCache[channelKey] = getter.channels(channelKey.first, channelKey.second)
            val ownedChannels = channelCache[channelKey] ?: continue
            for (start in maxOf(0xC0, loop.target - 16)..loop.target step 2) {
                budget.step()
                if (rom.u16le(start) and 0xF8FF != 0x2000) continue
                val trace = flow.analyze(flow.initial(start), start..loop.end)
                if (!trace.complete || trace.exits.size != 1 || trace.exits.single().pc != loop.end + 2) continue
                val setters = trace.calls.mapNotNull { call ->
                    val field = call.arguments[1].number ?: return@mapNotNull null
                    if (field !in 0..255) return@mapNotNull null
                    val source = flow.read(call.state, call.arguments[2], 2)
                    val getterCall = storedCall(source) ?: return@mapNotNull null
                    Triple(call, getterCall, field.toInt())
                }.distinctBy { Triple(it.first.site, it.first.arguments, it.second) }
                if (setters.size != loop.count) continue
                val ordered = setters.sortedBy { it.third }
                val first = ordered.first()
                if (ordered.indices.any { index ->
                    val (setter, called, field) = ordered[index]
                    setter.entry != first.first.entry || setter.arguments[0] != first.first.arguments[0] ||
                        field != first.third + index || called.entry != first.second.entry ||
                        called.arguments.take(2) != first.second.arguments.take(2) || called.arguments[2].number != index.toLong()
                }) continue
                if (first.second.entry != ownedChannels.entry || first.first.entry != calls.last()) continue
                result += Producer(ownedChannels, first.first.entry, first.third)
            }
        }
        return result.distinct()
    }

    private fun loopCalls(loop: Loop): List<Int>? {
        val calls = mutableListOf<Int>()
        var at = loop.target
        while (at < loop.end) {
            budget.step()
            val instruction = (ThumbDecoder.decode(rom, at) as? Arm7DecodeResult.Decoded)?.instruction ?: return null
            if (instruction is Arm7Branch && instruction.link) {
                if (instruction.target !in 0xC0 until rom.size.toLong()) return null
                calls += instruction.target.toInt()
            }
            at += instruction.size
        }
        return calls.takeIf { at == loop.end }
    }

    private fun aliases(summary: Summary, setter: Int): Boolean {
        val objectValue = value(Atom.Input(0))
        val sourceValue = value(Atom.Input(2))
        var firstOffset: Long? = null
        for (index in 0 until summary.count) {
            val arguments = listOf(objectValue, constant(summary.field + index.toLong()), sourceValue, CompiledAbilityNativeFlow.UNKNOWN)
            val read = api(summary.getter, arguments, 0) ?: return false
            if (read.writes.isNotEmpty()) return false
            val loaded = read.returned.atom as? Atom.Read ?: return false
            if (loaded.bytes != 2 || loaded.address.terms != objectValue.terms || loaded.address.constant < 0) return false
            if (index == 0) firstOffset = loaded.address.constant
            if (loaded.address.constant != requireNotNull(firstOffset) + index * 2) return false
            val written = api(setter, arguments, 0) ?: return false
            if (written.writes != listOf(CompiledAbilityNativeFlow.Cell(loaded.address, 2) to value(Atom.Read(2, sourceValue)))) return false
        }
        return true
    }

    private fun api(entry: Int, arguments: List<Expression>, depth: Int): Api? {
        val key = entry to arguments
        if (key in apiCache) return apiCache[key]
        if (depth >= 4 || entry < 0xC0 || entry and 1 != 0) return null
        budget.root()
        apiCache[key] = null
        val children = mutableListOf<Api>()
        var complete = true
        val result = flow.analyze(flow.initial(entry, arguments), span(entry, 2048)) { call ->
            val child = api(call.entry, call.arguments, depth + 1)
            if (child == null) complete = false else children += child
            child?.returned
        }
        if (!complete || !result.complete || result.exits.isNotEmpty() || result.returns.isEmpty()) return null
        val writes = (children.flatMap { it.writes } + result.stores.filterNot { store ->
            store.cell.address.terms == mapOf(Atom.Stack to 1L)
        }.map { it.cell to it.value }).distinct()
        val returned = result.returns.map { it.value }.distinct().singleOrNull() ?: CompiledAbilityNativeFlow.UNKNOWN
        return Api(returned, writes).also { apiCache[key] = it }
    }

    private fun storedCall(expression: Expression): Atom.Call? {
        val narrow = expression.atom as? Atom.Narrow
        return if (narrow?.bits == 16) narrow.value.atom as? Atom.Call else expression.atom as? Atom.Call
    }

    private fun loops(start: Int, end: Int): List<Loop> = loopCache.getOrPut(start to end) {
        val result = mutableListOf<Loop>()
        for (site in (start and -2)..(end and -2) step 2) {
            budget.step()
            if (site < 2 || site + 2 > rom.size) continue
            val compare = rom.u16le(site - 2)
            val branch = rom.u16le(site)
            if (compare and 0xF800 != 0x2800 || compare and 255 !in 1..7 || branch ushr 8 !in setOf(0xD9, 0xDD)) continue
            val target = site + 4 + (branch and 255).toByte().toInt() * 2
            if (target in maxOf(start, site - 96)..site - 2 &&
                (target until site step 2).none { rom.u16le(it) and 0xF000 == 0xD000 }
            ) result += Loop(target, site, (compare and 255) + 1)
        }
        result
    }

    private fun sites(root: Int): List<Int>? {
        val evidence = session.nominatedGbaReferenceSites(root) ?: return null
        if (evidence.siteBudgetExceeded) throw CompiledAbilityBudgetExceeded(
            BudgetKind.REFERENCE_SITES, evidence.observedSites.toLong(), evidence.limitSites.toLong())
        if (!evidence.siteEvidenceAvailable || evidence.count != evidence.instructionSites.size) return null
        return evidence.instructionSites.filter { it >= 0xC0 }.takeIf { it.isNotEmpty() }
    }

    private fun literal(site: Int): Long? {
        val op = rom.u16le(site)
        if (op and 0xF800 != 0x4800) return null
        val cell = ((site.toLong() + 4) and -4L) + (op and 255) * 4L
        return cell.takeIf { it in 0..rom.size.toLong() - 4 }?.let { rom.u32le(it.toInt()) }
    }

    private fun extent(names: Int, descriptions: Int, width: Int, maximum: Int): Int? {
        val distance = descriptions.toLong() - names
        if (distance <= 0 || maximum <= 0) return null
        val counts = (0..3).mapNotNull { padding ->
            val bytes = distance - padding
            if (bytes <= 0 || bytes % width != 0L || (names + bytes + 3) and -4L != descriptions.toLong()) return@mapNotNull null
            val count = bytes / width
            // The proved unsigned-halfword contract bounds the namespace, not an arbitrary replacement cap.
            count.takeIf { it in maximum + 1L..65536L }?.toInt()
        }
        val count = counts.singleOrNull() ?: return null
        budget.extent(distance); budget.extent(count * 4L)
        if (descriptions.toLong() + count * 4L > rom.size) return null
        if ((names.toLong() + count * width until descriptions.toLong()).any { rom.u8(it.toInt()) !in setOf(0, 255) }) return null
        return count
    }

    private fun span(entry: Int, bytes: Int) = entry..minOf(rom.size - 2, entry + bytes - 2)
    private fun isRam(address: Long) = address in 0x02000000L until 0x02040000L || address in 0x03000000L until 0x03008000L
}
