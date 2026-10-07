package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.dataset.abilities.AbilityDescriptionTableLayout
import com.enrpau.dualscreendex.parser.dataset.abilities.AbilityNameTableLayout
import com.enrpau.dualscreendex.parser.dataset.abilities.CompiledAbilityTextBinding
import com.enrpau.dualscreendex.parser.dataset.abilities.CompiledSpeciesAbilitySlots
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.model.TableRecordFormat
import com.enrpau.dualscreendex.parser.resolution.BudgetKind
import java.util.Collections
import kotlin.math.abs

sealed interface CompiledAbilityTextResolution {
    data class Resolved(val binding: CompiledAbilityTextBinding) : CompiledAbilityTextResolution
    data class Unavailable(val reason: String) : CompiledAbilityTextResolution
    class Ambiguous(bindings: Collection<CompiledAbilityTextBinding>) : CompiledAbilityTextResolution {
        val bindings: List<CompiledAbilityTextBinding> = Collections.unmodifiableList(bindings.toList())
    }
    data class BudgetExceeded(val kind: BudgetKind, val observed: Long, val limit: Long) : CompiledAbilityTextResolution
}

/** Published roles plus complete compiled consumers/getter and an independently owned aligned boundary. */
object CompiledAbilityTextResolver {
    @JvmStatic
    fun resolve(session: RomAnalysisSession, core: TableLayout): CompiledAbilityTextResolution {
        session.cancellation.throwIfCancellationRequested()
        return try { resolveBounded(session, core, CompiledAbilityProbeBudget(session)) }
        catch (failure: CompiledAbilityBudgetExceeded) {
            CompiledAbilityTextResolution.BudgetExceeded(failure.kind, failure.observed, failure.limit)
        }
    }

    private fun resolveBounded(
        session: RomAnalysisSession,
        core: TableLayout,
        budget: CompiledAbilityProbeBudget,
    ): CompiledAbilityTextResolution {
        budget.step()
        if (session.header.platform != Platform.GBA || core.variableLength || core.valuesArePointers ||
            core.bank != null || core.banks.isNotEmpty() || core.pointerOffsets.isNotEmpty() ||
            core.bankAdjustment != 0 || core.bankRemap.isNotEmpty() || core.format != TableRecordFormat.STANDARD
        ) return unavailable("selected species core has unsupported storage")
        val index = session.gbaReferenceIndex ?: return unavailable("compiled reference evidence is unavailable")
        if (index.overflowed) return CompiledAbilityTextResolution.BudgetExceeded(
            BudgetKind.REFERENCE_TARGETS, index.observedTargets.toLong(), index.limitTargets.toLong(),
        )
        val roles = GbaPublishedHeaderResolver.abilityPointerRolesForCore(session.rom, core.offset)
        if (roles.isEmpty()) return unavailable("no published ability roles agree with the selected core")
        val accessor = CompiledAbilityAccessor(session.rom, budget)
        val getter = CompiledAbilityGetter(session.rom, core, budget)
        val accessorCache = mutableMapOf<Int, List<CompiledAbilityAccessor.TextAccess>?>()
        val getterCache = mutableMapOf<Int, CompiledSpeciesAbilitySlots?>()
        fun accesses(site: Int): List<CompiledAbilityAccessor.TextAccess> = accessor.entriesBefore(site).flatMap { entry ->
            if (entry !in accessorCache) accessorCache[entry] = accessor.analyze(entry)
            accessorCache[entry].orEmpty()
        }
        val bindings = mutableListOf<CompiledAbilityTextBinding>()
        for (role in roles) {
            budget.root(); budget.step()
            for (root in listOf(role.names, role.descriptions)) {
                val evidence = index.target(root)
                if (evidence?.siteBudgetExceeded == true) throw CompiledAbilityBudgetExceeded(
                    BudgetKind.REFERENCE_SITES, evidence.observedSites.toLong(), evidence.limitSites.toLong(),
                )
            }
            val nameSites = executableGbaTextSites(index, role.names) ?: continue
            val descriptionSites = executableGbaTextSites(index, role.descriptions) ?: continue
            val width = compiledAbilityNameStride(session, role.names)?.takeIf { it in 8..32 } ?: continue
            if (descriptionSites.any { site -> budget.step(); !pointerIndexConsumer(session, site) }) continue
            val descriptions = descriptionSites.flatMap(::accesses).filter {
                it.root == role.descriptions && it.pointerText && it.scale == 4
            }.distinct()
            for (description in descriptions) {
                val names = nameSites.filter { abs(it.toLong() - description.entry) <= 256L }.flatMap(::accesses).filter {
                    it.root == role.names && !it.pointerText && it.scale == width &&
                        it.source == description.source && it.sink == description.sink &&
                        it.interveningCalls == description.interveningCalls && abs(it.entry.toLong() - description.entry) <= 256L
                }.distinct()
                if (names.isEmpty()) continue
                val entry = description.source.getter
                if (entry !in getterCache) getterCache[entry] = getter.resolve(entry)
                val slots = getterCache[entry] ?: continue
                var maximumId = 0
                for (speciesId in 1 until slots.speciesCount) {
                    budget.step()
                    maximumId = maxOf(maximumId, slots.read(session.rom, speciesId).maxOrNull() ?: 0)
                }
                val count = adjacentNameCount(role.names, role.descriptions, width, maximumId) ?: continue
                val nameBytes = role.descriptions.toLong() - role.names
                budget.extent(nameBytes); budget.extent(count.toLong() * 4)
                if (role.descriptions.toLong() + count.toLong() * 4 > session.rom.size) continue
                val payloadEnd = role.names.toLong() + count.toLong() * width
                if ((payloadEnd until role.descriptions.toLong()).any { offset ->
                    session.rom.u8(offset.toInt()) !in setOf(0, 255)
                }) continue
                for (name in names) {
                    if (bindings.size >= session.limits.maxCandidatesPerDataset) throw CompiledAbilityBudgetExceeded(
                        BudgetKind.CANDIDATES, bindings.size + 1L, session.limits.maxCandidatesPerDataset.toLong(),
                    )
                    bindings += CompiledAbilityTextBinding(
                        AbilityNameTableLayout(role.names, count, width, terminatedInlineArray = true),
                        AbilityDescriptionTableLayout(role.descriptions, count),
                        slots, entry, name.entry, description.entry,
                    )
                }
            }
        }
        val distinct = bindings.distinctBy { Triple(it.names, it.descriptions, it.speciesSlots) }
        return when (distinct.size) {
            0 -> unavailable("published ability roles lack coherent paired consumers, a native getter or a bounded extent")
            1 -> CompiledAbilityTextResolution.Resolved(distinct.single())
            else -> CompiledAbilityTextResolution.Ambiguous(distinct)
        }
    }

    private fun adjacentNameCount(names: Int, descriptions: Int, width: Int, maximumId: Int): Int? {
        val distance = descriptions.toLong() - names
        if (distance <= 0 || maximumId <= 0) return null
        return (0..3).mapNotNull { padding ->
            val bytes = distance - padding
            if (bytes <= 0 || bytes % width != 0L || (names + bytes + 3) and -4L != descriptions.toLong()) return@mapNotNull null
            val count = bytes / width
            count.takeIf { it in maxOf(2L, maximumId.toLong() + 1L)..512L }?.toInt()
        }.singleOrNull()
    }

    private fun pointerIndexConsumer(session: RomAnalysisSession, site: Int): Boolean {
        val rom = session.rom
        if (site < 0 || site.toLong() + 8 > rom.size) return false
        val root = rom.u16le(site); val shift = rom.u16le(site + 2)
        val add = rom.u16le(site + 4); val load = rom.u16le(site + 6)
        if (root and 0xF800 != 0x4800 || shift and 0xF800 != 0 ||
            (shift ushr 6) and 31 != 2 || add and 0xFE00 != 0x1800 ||
            load and 0xF800 != 0x6800 || (load ushr 6) and 31 != 0
        ) return false
        val base = (root ushr 8) and 7; val product = shift and 7
        return base != product && add and 7 == product &&
            setOf((add ushr 3) and 7, (add ushr 6) and 7) == setOf(base, product) &&
            (load ushr 3) and 7 == product
    }

    private fun unavailable(reason: String) = CompiledAbilityTextResolution.Unavailable(reason)
}
