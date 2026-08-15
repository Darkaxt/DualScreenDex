package com.enrpau.dualscreendex.parser.family

import com.enrpau.dualscreendex.parser.analysis.ExactProfileSnapshot
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomCapability
import com.enrpau.dualscreendex.parser.model.RomHeader
import java.util.Collections

/** Format and discovery strategy lineages that a family may compose. */
enum class EngineLineage {
    GEN1_RETAIL,
    GEN2_RETAIL,
    GEN3_RETAIL,
    CFRU,
    POKEEMERALD_EXPANSION,
}

/**
 * Declarative family identity, format, and capability applicability.
 *
 * A lineage is a strategy family, not a ROM identity. Derived engines can therefore compose a
 * retail ancestry with CFRU or pokeemerald-expansion discovery without pretending those formats
 * are new values of [EngineFamily].
 */
class EngineFamilyDefinition(
    val family: EngineFamily,
    val formatGeneration: Int,
    val expectedPlatform: Platform,
    compatiblePlatforms: Set<Platform>,
    lineages: Set<EngineLineage>,
    applicableCapabilities: Set<RomCapability>,
    private val identityRule: (RomHeader) -> Boolean,
) {
    val compatiblePlatforms: Set<Platform> = immutableSet(compatiblePlatforms)
    val lineages: Set<EngineLineage> = immutableSet(lineages)
    val applicableCapabilities: Set<RomCapability> = immutableSet(applicableCapabilities)

    init {
        require(formatGeneration in 1..3) { "format generation must be 1, 2, or 3" }
        require(this.compatiblePlatforms.isNotEmpty()) { "family must declare compatible platforms" }
        require(this.lineages.isNotEmpty()) { "family must compose at least one engine lineage" }
    }

    fun isPlatformCompatible(platform: Platform): Boolean = platform in compatiblePlatforms

    fun matchesIdentity(header: RomHeader): Boolean = identityRule(header)

    fun authoritativeExactProfile(session: RomAnalysisSession): ExactProfileSnapshot? =
        session.exactProfileSnapshot?.takeIf { it.family == family }
}

object EngineFamilyDefinitions {
    private val gen1Capabilities = RomCapability.entries.toSet() - setOf(
        RomCapability.ABILITIES,
        RomCapability.ABILITY_DESCRIPTIONS,
        RomCapability.ABILITY_MECHANICS,
        RomCapability.EGG_MOVES,
        RomCapability.TUTOR_MOVES,
    )
    private val gen2Capabilities = RomCapability.entries.toSet() - setOf(
        RomCapability.ABILITIES,
        RomCapability.ABILITY_DESCRIPTIONS,
        RomCapability.ABILITY_MECHANICS,
    )
    private val gen3Capabilities = RomCapability.entries.toSet()
    private val gbaLineages = setOf(
        EngineLineage.GEN3_RETAIL,
        EngineLineage.CFRU,
        EngineLineage.POKEEMERALD_EXPANSION,
    )

    val all: List<EngineFamilyDefinition> = listOf(
        definition(
            EngineFamily.RED_BLUE,
            1,
            setOf(Platform.GB, Platform.GBC),
            setOf(EngineLineage.GEN1_RETAIL),
            gen1Capabilities,
        ) {
            it.title.startsWith("POKEMON RED") ||
                it.title.startsWith("POKEMON BLUE") ||
                it.title.startsWith("POKEMON GREEN")
        },
        definition(
            EngineFamily.YELLOW,
            1,
            setOf(Platform.GB, Platform.GBC),
            setOf(EngineLineage.GEN1_RETAIL),
            gen1Capabilities,
        ) { it.title.startsWith("POKEMON YELLOW") },
        definition(
            EngineFamily.GOLD_SILVER,
            2,
            setOf(Platform.GB, Platform.GBC),
            setOf(EngineLineage.GEN2_RETAIL),
            gen2Capabilities,
        ) { it.title.contains("GLD") || it.title.contains("SLV") },
        definition(
            EngineFamily.CRYSTAL,
            2,
            setOf(Platform.GB, Platform.GBC),
            setOf(EngineLineage.GEN2_RETAIL),
            gen2Capabilities,
        ) { it.title.contains("CRYSTAL") },
        definition(
            EngineFamily.RUBY_SAPPHIRE,
            3,
            setOf(Platform.GBA),
            gbaLineages,
            gen3Capabilities,
        ) {
            it.gameCode in setOf("AXVE", "AXPE") ||
                it.title.startsWith("POKEMON RUBY") || it.title.startsWith("POKEMON SAPP")
        },
        definition(
            EngineFamily.EMERALD,
            3,
            setOf(Platform.GBA),
            gbaLineages,
            gen3Capabilities,
        ) { it.gameCode == "BPEE" || it.title.startsWith("POKEMON EMER") },
        definition(
            EngineFamily.FIRERED_LEAFGREEN,
            3,
            setOf(Platform.GBA),
            gbaLineages,
            gen3Capabilities,
        ) {
            it.gameCode in setOf("BPRE", "BPGE") ||
                it.title.startsWith("POKEMON FIRE") || it.title.startsWith("POKEMON LEAF")
        },
    )

    val byFamily: Map<EngineFamily, EngineFamilyDefinition> = Collections.unmodifiableMap(
        all.associateBy(EngineFamilyDefinition::family),
    )

    init {
        require(byFamily.keys == EngineFamily.entries.toSet()) {
            "every supported engine family must have exactly one definition"
        }
    }

    private fun definition(
        family: EngineFamily,
        generation: Int,
        platforms: Set<Platform>,
        lineages: Set<EngineLineage>,
        capabilities: Set<RomCapability>,
        identity: (RomHeader) -> Boolean,
    ) = EngineFamilyDefinition(
        family,
        generation,
        when (family) {
            EngineFamily.RED_BLUE, EngineFamily.YELLOW -> Platform.GB
            EngineFamily.GOLD_SILVER, EngineFamily.CRYSTAL -> Platform.GBC
            else -> Platform.GBA
        },
        platforms,
        lineages,
        capabilities,
        identity,
    )
}

private fun <T> immutableSet(values: Set<T>): Set<T> =
    Collections.unmodifiableSet(LinkedHashSet(values))
