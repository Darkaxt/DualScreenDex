package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.detect.RomHeaderReader
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.CapabilityEvidence
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.ParserProbe
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.ProfileTables
import com.enrpau.dualscreendex.parser.model.RomCapability
import com.enrpau.dualscreendex.parser.model.RomHeader
import com.enrpau.dualscreendex.parser.model.RomProfile
import com.enrpau.dualscreendex.parser.model.ScoreEvidence
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.model.ValidationEvidence
import com.enrpau.dualscreendex.parser.profile.KnownProfiles
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import com.enrpau.dualscreendex.parser.validate.TableValidators

interface FamilyParser {
    val family: EngineFamily
    fun probe(rom: RomImage, header: RomHeader = RomHeaderReader.read(rom)): ParserProbe
}

object FamilyParsers {
    val all: List<FamilyParser> = EngineFamily.entries.map(::ConfiguredFamilyParser)
}

private class ConfiguredFamilyParser(
    override val family: EngineFamily,
) : FamilyParser {
    override fun probe(rom: RomImage, header: RomHeader): ParserProbe {
        val expectedPlatform = platformFor(family)
        val platformPassed = when (expectedPlatform) {
            Platform.GB -> header.platform == Platform.GB || header.platform == Platform.GBC
            Platform.GBC -> header.platform == Platform.GBC || header.platform == Platform.GB
            else -> header.platform == expectedPlatform
        }
        if (!platformPassed) {
            return ParserProbe(
                family, 0, false, 0,
                listOf(ScoreEvidence("platform", 0, 10, "expected $expectedPlatform, found ${header.platform}")),
                emptyList(),
            )
        }

        val exact = KnownProfiles.bySha256(rom.sha256)?.takeIf { it.family == family }
        val baseProfile = exact ?: closestProfile(header)
        val identityMatched = identityMatches(header)
        val score = mutableListOf<ScoreEvidence>()
        score += ScoreEvidence("platform", 10, 10, "${header.platform} is compatible")
        score += ScoreEvidence(
            "engine identity", if (identityMatched) 20 else 0, 20,
            if (identityMatched) "title/game code matches $family" else "title/game code does not match $family",
        )

        val tables = resolveTables(rom, baseProfile)
        val generation = generationFor(family)
        val codec = if (generation == 3) PokemonTextCodec.gbaEnglish else PokemonTextCodec.gbEnglish
        val speciesCount = inferSpeciesCount(rom, tables, codec, baseProfile)
        val moveCount = inferMoveCount(rom, tables.moveNames, codec, baseProfile)
        val baseStatsLayout = tables.baseStats?.let { layout ->
            if (generation == 3 && speciesCount != null) {
                val inferredSize = TableValidators.inferBaseStatsRecordSize(
                    rom, layout.offset, speciesCount, generation,
                )
                if (inferredSize != null) layout.copy(recordSize = inferredSize) else layout
            } else {
                layout
            }
        }

        val names = validateNames(rom, tables.speciesNames, speciesCount, codec)
        val stats = baseStatsLayout?.let {
            val validationCount = if (generation == 1) it.count else speciesCount ?: it.count
            TableValidators.baseStats(rom, it.offset, validationCount, it.recordSize, generation)
        } ?: missing("species base-stat table not resolved")
        val moveNames = validateNames(rom, tables.moveNames, moveCount, codec)
        val moveData = tables.moveData?.let {
            TableValidators.moveData(rom, it.offset, moveCount ?: it.count, it.recordSize, generation)
        } ?: missing("move-data table not resolved")
        val typeChart = tables.typeChart?.let {
            TableValidators.typeChart(rom, it.offset, generation)
        } ?: missing("type-chart table not resolved")
        val sprites = if (generation == 3 && tables.sprites != null) {
            TableValidators.gbaPointerTable(
                rom, tables.sprites.offset, speciesCount ?: tables.sprites.count, tables.sprites.recordSize,
            )
        } else {
            missing("sprite pointer validation is only implemented for GBA")
        }
        val abilities = if (generation == 3) {
            tables.abilities?.let {
                val count = inferAbilityCount(rom, it, codec, baseProfile)
                validateNames(rom, it, count, codec)
            } ?: missing("ability-name table not resolved")
        } else {
            missing("abilities are not part of this engine")
        }

        score += ScoreEvidence("species names", if (names.compatible) 15 else 0, 15, names.summary())
        score += ScoreEvidence("base stats", if (stats.compatible) 15 else 0, 15, stats.summary())
        val movePoints = when {
            moveNames.compatible && moveData.compatible -> 15
            moveNames.compatible || moveData.compatible -> 7
            else -> 0
        }
        score += ScoreEvidence("moves", movePoints, 15, "names=${moveNames.compatible}, data=${moveData.compatible}")
        val crossPoints = (if (names.compatible && stats.compatible) 10 else 0) +
            (if (moveNames.compatible && moveData.compatible) 5 else 0)
        score += ScoreEvidence("cross-table integrity", crossPoints, 15, "species=${names.compatible && stats.compatible}, moves=${moveNames.compatible && moveData.compatible}")
        score += ScoreEvidence("sprites", if (sprites.compatible) 10 else 0, 10, sprites.summary())

        val capabilities = buildCapabilities(names, stats, moveNames, moveData, typeChart, sprites, abilities)
        val anchors = listOf(
            identityMatched,
            tables.speciesNames != null,
            names.compatible,
            stats.compatible,
            moveNames.compatible && moveData.compatible,
        ).count { it }
        val total = if (exact != null) 100 else score.sumOf { it.points }

        return ParserProbe(
            family = family,
            score = total,
            hardGatePassed = true,
            anchors = anchors,
            scoreEvidence = score,
            capabilities = capabilities,
            profileName = exact?.name ?: baseProfile?.name,
            exactProfile = exact != null,
            diagnostics = buildList {
                if (baseProfile == null) add("no official family profile matched the header")
                if (exact == null && baseProfile != null) add("using ${baseProfile.name} as structural ancestor")
                if (generation == 3 && tables.speciesNames?.offset != baseProfile?.tables?.speciesNames?.offset) {
                    add("resolved relocated GBA table pointers")
                }
                if (baseStatsLayout != null && baseStatsLayout.recordSize != baseProfile?.tables?.baseStats?.recordSize) {
                    add("inferred base-stat record size ${baseStatsLayout.recordSize}")
                }
            },
        )
    }

    private fun closestProfile(header: RomHeader): RomProfile? {
        val candidates = KnownProfiles.forFamily(family)
        return candidates.firstOrNull { profile ->
            profile.gameCode != null && profile.gameCode == header.gameCode
        } ?: candidates.firstOrNull { profile ->
            header.title.startsWith(profile.title.take(8), ignoreCase = true)
        } ?: candidates.firstOrNull()
    }

    private fun identityMatches(header: RomHeader): Boolean = when (family) {
        EngineFamily.RED_BLUE -> header.title.startsWith("POKEMON RED") || header.title.startsWith("POKEMON BLUE")
        EngineFamily.YELLOW -> header.title.startsWith("POKEMON YELLOW")
        EngineFamily.GOLD_SILVER -> header.title.contains("GLD") || header.title.contains("SLV")
        EngineFamily.CRYSTAL -> header.title.contains("CRYSTAL")
        EngineFamily.RUBY_SAPPHIRE -> header.gameCode in setOf("AXVE", "AXPE")
        EngineFamily.EMERALD -> header.gameCode == "BPEE"
        EngineFamily.FIRERED_LEAFGREEN -> header.gameCode in setOf("BPRE", "BPGE")
    }

    private fun resolveTables(rom: RomImage, profile: RomProfile?): ProfileTables {
        val inherited = profile?.tables ?: ProfileTables()
        if (generationFor(family) != 3) return inherited

        val headerPointers = if (family == EngineFamily.EMERALD || family == EngineFamily.FIRERED_LEAFGREEN) {
            GbaHeaderPointers(
                speciesNames = rom.gbaPointerOrNull(0x144),
                moveNames = rom.gbaPointerOrNull(0x148),
                sprites = rom.gbaPointerOrNull(0x128),
                baseStats = rom.gbaPointerOrNull(0x1BC),
                abilities = rom.gbaPointerOrNull(0x1C0),
                moveData = rom.gbaPointerOrNull(0x1CC),
            )
        } else {
            GbaHeaderPointers(speciesNames = locateRubySapphireNames(rom))
        }

        return ProfileTables(
            speciesNames = headerPointers.speciesNames?.let { TableLayout(it, inherited.speciesNames?.count ?: 412, 11) }
                ?: inherited.speciesNames,
            baseStats = headerPointers.baseStats?.let { TableLayout(it, inherited.baseStats?.count ?: 412, 28) }
                ?: inherited.baseStats,
            moveNames = headerPointers.moveNames?.let { TableLayout(it, inherited.moveNames?.count ?: 355, 13) }
                ?: inherited.moveNames,
            moveData = headerPointers.moveData?.let { TableLayout(it, inherited.moveData?.count ?: 355, 12) }
                ?: inherited.moveData,
            typeChart = inherited.typeChart,
            evolutions = inherited.evolutions,
            learnsets = inherited.learnsets,
            sprites = headerPointers.sprites?.let { TableLayout(it, inherited.sprites?.count ?: 412, 8) }
                ?: inherited.sprites,
            descriptions = inherited.descriptions,
            abilities = headerPointers.abilities?.let { TableLayout(it, inherited.abilities?.count ?: 78, 13) }
                ?: inherited.abilities,
        )
    }

    private fun inferSpeciesCount(
        rom: RomImage,
        tables: ProfileTables,
        codec: PokemonTextCodec,
        profile: RomProfile?,
    ): Int? {
        val layout = tables.speciesNames
        if (layout == null) return null
        if (KnownProfiles.bySha256(rom.sha256) != null) return layout.count
        if (generationFor(family) != 3) return layout.count
        val boundaryCount = TableValidators.inferCountFromFollowingTable(
            offset = layout.offset,
            recordSize = layout.recordSize,
            followingOffsets = listOfNotNull(
                tables.baseStats?.offset,
                tables.moveNames?.offset,
                tables.moveData?.offset,
                tables.typeChart?.offset,
                tables.sprites?.offset,
                tables.abilities?.offset,
            ),
            minimumCount = 300,
            maximumCount = 2048,
        )
        return boundaryCount
            ?: TableValidators.inferFixedNameCount(rom, layout.offset, layout.recordSize, codec, 300, 2048)
            ?: profile?.internalSpeciesCount
            ?: layout.count
    }

    private fun inferMoveCount(
        rom: RomImage,
        layout: TableLayout?,
        codec: PokemonTextCodec,
        profile: RomProfile?,
    ): Int? {
        if (layout == null) return null
        if (layout.variableLength || KnownProfiles.bySha256(rom.sha256) != null) return layout.count
        return TableValidators.inferFixedNameCount(rom, layout.offset, layout.recordSize, codec, 100, 2048)
            ?: profile?.moveCount?.plus(1)
            ?: layout.count
    }

    private fun inferAbilityCount(
        rom: RomImage,
        layout: TableLayout,
        codec: PokemonTextCodec,
        profile: RomProfile?,
    ): Int = if (KnownProfiles.bySha256(rom.sha256) != null) {
        layout.count
    } else {
        TableValidators.inferFixedNameCount(rom, layout.offset, layout.recordSize, codec, 10, 512)
            ?: profile?.tables?.abilities?.count
            ?: layout.count
    }

    private fun validateNames(
        rom: RomImage,
        layout: TableLayout?,
        inferredCount: Int?,
        codec: PokemonTextCodec,
    ): ValidationEvidence {
        if (layout == null || inferredCount == null) return missing("name table not resolved")
        return if (layout.variableLength) {
            TableValidators.variableNames(rom, layout.offset, inferredCount, codec)
        } else {
            val minimumRatio = if (generationFor(family) == 1) 0.70 else 0.85
            TableValidators.fixedNames(rom, layout.offset, inferredCount, layout.recordSize, codec, minimumRatio)
        }
    }

    private fun buildCapabilities(
        names: ValidationEvidence,
        stats: ValidationEvidence,
        moveNames: ValidationEvidence,
        moveData: ValidationEvidence,
        typeChart: ValidationEvidence,
        sprites: ValidationEvidence,
        abilities: ValidationEvidence,
    ): List<CapabilityEvidence> {
        fun evidence(
            capability: RomCapability,
            value: ValidationEvidence,
            status: CapabilityStatus = if (value.compatible) CapabilityStatus.AVAILABLE else CapabilityStatus.NOT_FOUND,
        ) = CapabilityEvidence(
            capability, value.compatible, value.confidence, value.offset,
            value.totalRecords.takeIf { it > 0 }, value.recordSize, value.reasons, status,
        )
        val catalog = ValidationEvidence(
            names.compatible && stats.compatible,
            minOf(names.validRecords, stats.validRecords),
            maxOf(names.totalRecords, stats.totalRecords),
            minOf(names.confidence, stats.confidence),
            names.reasons + stats.reasons,
            names.offset,
            names.recordSize,
        )
        return listOf(
            evidence(RomCapability.SPECIES_CATALOG, catalog),
            evidence(RomCapability.SPECIES_NAMES, names),
            evidence(RomCapability.SPECIES_TYPES, stats),
            evidence(RomCapability.TYPE_CHART, typeChart),
            evidence(RomCapability.BASE_STATS, stats),
            evidence(RomCapability.SPRITES, sprites),
            evidence(RomCapability.POKEDEX_DESCRIPTIONS, missing("not implemented in parser POC")),
            evidence(RomCapability.EVOLUTIONS, missing("not implemented in parser POC")),
            evidence(RomCapability.MOVE_CATALOG, moveNames),
            evidence(RomCapability.MOVE_DETAILS, moveData),
            evidence(RomCapability.LEARNSETS, missing("not implemented in parser POC")),
            evidence(
                RomCapability.ABILITIES,
                abilities,
                if (generationFor(family) < 3) CapabilityStatus.NOT_APPLICABLE
                else if (abilities.compatible) CapabilityStatus.AVAILABLE
                else CapabilityStatus.NOT_FOUND,
            ),
        )
    }

    private fun locateRubySapphireNames(rom: RomImage): Int? {
        val suffix = byteArrayOf(0x30, 0xB5.toByte(), 0x00, 0x25, 0x08, 0x4C, 0xC8.toByte(), 0xF7.toByte())
        val signature = rom.findAll(suffix).firstOrNull() ?: return null
        return if (signature >= 4) rom.gbaPointerOrNull(signature - 4) else null
    }

    private fun RomImage.gbaPointerOrNull(offset: Int): Int? = try {
        gbaPointer(offset)
    } catch (_: RuntimeException) {
        null
    }

    private data class GbaHeaderPointers(
        val speciesNames: Int? = null,
        val moveNames: Int? = null,
        val sprites: Int? = null,
        val baseStats: Int? = null,
        val abilities: Int? = null,
        val moveData: Int? = null,
    )
}

private fun platformFor(family: EngineFamily): Platform = when (family) {
    EngineFamily.RED_BLUE, EngineFamily.YELLOW -> Platform.GB
    EngineFamily.GOLD_SILVER, EngineFamily.CRYSTAL -> Platform.GBC
    else -> Platform.GBA
}

private fun generationFor(family: EngineFamily): Int = when (family) {
    EngineFamily.RED_BLUE, EngineFamily.YELLOW -> 1
    EngineFamily.GOLD_SILVER, EngineFamily.CRYSTAL -> 2
    else -> 3
}

private fun missing(reason: String) = ValidationEvidence(false, 0, 0, 0.0, listOf(reason))

private fun ValidationEvidence.summary(): String = if (compatible) {
    "$validRecords/$totalRecords valid at ${offset?.let { "0x${it.toString(16).uppercase()}" } ?: "unknown"}"
} else {
    reasons.joinToString("; ").ifBlank { "$validRecords/$totalRecords valid" }
}
