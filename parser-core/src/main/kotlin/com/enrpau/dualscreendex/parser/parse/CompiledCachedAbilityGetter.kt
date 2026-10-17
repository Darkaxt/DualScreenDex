package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.parse.CompiledAbilityNativeFlow.Atom
import com.enrpau.dualscreendex.parser.parse.CompiledAbilityNativeFlow.Expression
import com.enrpau.dualscreendex.parser.parse.CompiledAbilityNativeFlow.Companion.constant
import com.enrpau.dualscreendex.parser.parse.CompiledAbilityNativeFlow.Companion.value

/** Proves static default field groups; paths calling runtime selectors are explicitly excluded. */
internal class CompiledCachedAbilityGetter(
    private val rom: RomImage,
    private val core: TableLayout,
    private val budget: CompiledAbilityProbeBudget,
    private val flow: CompiledAbilityNativeFlow,
) {
    data class Group(val entry: Int, val offsets: List<Int>, val excludedRuntimeCalls: List<Int>)
    data class Channels(val entry: Int, val groups: List<Group>, val offsets: List<Int>)
    private val species = Atom.Input(0, 16)
    private val slot = Atom.Input(1, 8)
    private val stride = core.stride ?: core.recordSize
    private val cache = mutableMapOf<Int, Group?>()

    fun group(entry: Int): Group? {
        if (entry !in cache) cache[entry] = groupBounded(entry)
        return cache[entry]
    }

    private fun groupBounded(entry: Int): Group? {
        budget.root()
        if (entry < 0xC0 || entry and 1 != 0 || core.count !in 1..65536 || core.recordSize <= 0 ||
            stride < core.recordSize || core.offset.toLong() + (core.count - 1L) * stride + core.recordSize > rom.size
        ) return null
        val result = flow.analyze(flow.initial(entry, listOf(value(species), value(slot))), span(entry), stopAtCalls = true)
        if (!result.complete || result.exits.isNotEmpty() || result.returns.isEmpty()) return null
        val native = result.returns.filter { it.value != constant(0) }
        if (native.isEmpty()) return null
        val groups = native.mapNotNull { returned ->
            val read = returned.value.atom as? Atom.Read ?: return@mapNotNull null
            if (read.bytes != 2 || read.address.terms != mapOf(species to stride.toLong(), slot to 2L)) return@mapNotNull null
            val bounds = returned.state.bounds[slot] ?: return@mapNotNull null
            val field = read.address.constant - 0x08000000L - core.offset
            if (bounds.first != 0L || bounds.last !in 0..7 || field !in 0..Int.MAX_VALUE.toLong()) return@mapNotNull null
            (0..bounds.last.toInt()).map { field.toInt() + it * 2 }
        }.distinct()
        val fields = groups.singleOrNull() ?: return null
        if (fields.any { it < 0 || it + 2 > core.recordSize || (core.offset.toLong() + it) and 1L != 0L } || stride and 1 != 0) return null
        // Every call-free return is zero or a selected-core member in this exact group.
        if (native.any { returned ->
            val read = returned.value.atom as? Atom.Read
            if (read?.bytes != 2) true else {
                val address = read.address
                val slotScale = address.terms[slot] ?: 0L
                val bounds = if (slotScale == 0L) 0L..0L else returned.state.bounds[slot] ?: (0L..255L)
                val expected = if (slotScale == 0L) mapOf(species to stride.toLong()) else mapOf(species to stride.toLong(), slot to 2L)
                address.terms != expected || slotScale !in setOf(0L, 2L) || bounds.last - bounds.first > 7 ||
                    (bounds.first..bounds.last).any { selected ->
                        val field = address.constant - 0x08000000L - core.offset + selected * slotScale
                        field !in fields.map(Int::toLong)
                    }
            }
        }) return null
        return Group(entry, fields, result.excludedCalls.map { it.site }.distinct().sorted())
    }

    fun channels(entry: Int, count: Int): Channels? {
        if (entry < 0xC0 || entry and 1 != 0 || count !in 2..8) return null
        budget.root()
        val groups = (0 until count).map { channel ->
            val result = flow.analyze(flow.initial(entry, listOf(value(species), value(slot), constant(channel.toLong()))), span(entry))
            if (!result.complete || result.exits.isNotEmpty() || result.returns.size != 1 || result.calls.size != 1) return null
            val invocation = result.calls.single()
            val returned = result.returns.single().value
            val direct = returned.atom as? Atom.Call
            val narrowed = returned.atom as? Atom.Narrow
            val called = direct ?: (narrowed?.takeIf { it.bits == 16 }?.value?.atom as? Atom.Call) ?: return null
            if (called.entry != invocation.entry || called.arguments != invocation.arguments ||
                invocation.arguments[0] != value(species) || invocation.arguments[1] !=
                (if (channel == 0) value(slot) else constant(channel - 1L))
            ) return null
            group(invocation.entry) ?: return null
        }
        val base = groups.first()
        val additional = groups.drop(1).distinct()
        val innate = additional.singleOrNull() ?: return null
        if (base == innate || innate.offsets.size != count - 1 || base.offsets.size + innate.offsets.size > 8 ||
            base.offsets.intersect(innate.offsets.toSet()).isNotEmpty()
        ) return null
        return Channels(entry, listOf(base, innate), base.offsets + innate.offsets)
    }

    private fun span(entry: Int): IntRange = entry..minOf(rom.size - 2, entry + 510)
}
