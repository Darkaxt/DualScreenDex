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
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import com.enrpau.dualscreendex.parser.model.ScoreEvidence
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.model.TableRecordFormat
import com.enrpau.dualscreendex.parser.model.ValidationEvidence
import com.enrpau.dualscreendex.parser.profile.KnownProfiles
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import com.enrpau.dualscreendex.parser.validate.TableValidators
import com.enrpau.dualscreendex.parser.validate.PokemonDatasetValidators
import com.enrpau.dualscreendex.parser.validate.SpriteValidators

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

        val generation = generationFor(family)
        val expansion = if (generation == 3 && identityMatched) PokeemeraldExpansionResolver.resolve(rom) else null
        var tables = expansion?.tables ?: resolveTables(rom, baseProfile)
        val codec = if (generation == 3) PokemonTextCodec.gbaEnglish else PokemonTextCodec.gbEnglish
        val speciesCount = expansion?.speciesCount ?: inferSpeciesCount(rom, tables, codec, baseProfile)
        val moveCount = expansion?.moveCount ?: inferMoveCount(rom, tables.moveNames, codec, baseProfile)
        if (generation == 3 && expansion == null && speciesCount != null && moveCount != null) {
            tables = Gen3DynamicTableResolver.resolve(rom, tables, speciesCount, moveCount)
        }
        var baseStatsLayout = tables.baseStats?.let { layout ->
            if (generation == 3 && speciesCount != null && expansion == null) {
                val inferredSize = TableValidators.inferBaseStatsRecordSize(
                    rom, layout.offset, speciesCount, generation,
                )
                if (inferredSize != null) layout.copy(recordSize = inferredSize) else layout
            } else {
                layout
            }
        }

        var speciesNamesLayout = tables.speciesNames
        var names = validateNames(rom, speciesNamesLayout, speciesCount, codec)
        if (generation == 2 && !names.compatible && speciesCount != null) {
            TableValidators.locateFixedNameTable(
                rom, speciesCount, 8..16, codec, preferredOffset = speciesNamesLayout?.offset,
            )?.let { relocated ->
                speciesNamesLayout = TableLayout(
                    offset = requireNotNull(relocated.offset),
                    count = speciesCount,
                    recordSize = requireNotNull(relocated.recordSize),
                )
                names = relocated
            }
        }
        var stats = baseStatsLayout?.let {
            val validationCount = if (generation == 1) it.count else speciesCount ?: it.count
            TableValidators.baseStats(rom, it.offset, validationCount, it.recordSize, generation)
        } ?: missing("species base-stat table not resolved")
        if (generation == 2 && !stats.compatible && speciesCount != null) {
            TableValidators.locateBaseStatTable(rom, speciesCount, 28..64, generation)?.let { relocated ->
                baseStatsLayout = TableLayout(
                    offset = requireNotNull(relocated.offset),
                    count = speciesCount,
                    recordSize = requireNotNull(relocated.recordSize),
                )
                stats = relocated
            }
        }
        var moveNamesLayout = tables.moveNames
        var moveNames = validateNames(rom, moveNamesLayout, moveCount, codec)
        if (
            generation == 2 && exact == null && moveCount != null && moveNamesLayout?.variableLength == true &&
            tables.moveData?.let { hasCanonicalGen2MovePrefix(rom, it) } == true
        ) {
            TableValidators.locateVariableNameSequenceNear(
                rom = rom,
                approximateOffset = moveNamesLayout.offset,
                codec = codec,
                expectedNames = listOf("POUND", "KARATE CHOP", "DOUBLESLAP"),
            )?.let { relocatedOffset ->
                val relocated = TableValidators.variableNames(rom, relocatedOffset, moveCount, codec)
                if (relocated.compatible) {
                    moveNamesLayout = moveNamesLayout.copy(offset = relocatedOffset)
                    moveNames = relocated.copy(reasons = listOf("matched leading canonical Gen 2 move records"))
                }
            }
        }
        val moveData = tables.moveData?.let {
            if (expansion != null) {
                TableValidators.pokeemeraldExpansionMoveData(rom, it, moveCount ?: it.count)
            } else if (it.format == TableRecordFormat.CFRU_MOVE_16) {
                TableValidators.cfruMoveData(rom, it.offset, moveCount ?: it.count)
            } else {
                TableValidators.moveData(rom, it.offset, moveCount ?: it.count, it.recordSize, generation)
            }
        } ?: missing("move-data table not resolved")
        var effectiveMoveCount = DatasetResolvers.reconciledMoveCount(moveCount, moveData)
        if (effectiveMoveCount != moveCount && effectiveMoveCount != null && moveNamesLayout != null) {
            val reconciledNames = validateNames(rom, moveNamesLayout, effectiveMoveCount, codec)
            if (reconciledNames.compatible) {
                moveNames = reconciledNames.copy(
                    reasons = reconciledNames.reasons + "bounded the move catalog by the validated move-data prefix",
                )
            } else {
                effectiveMoveCount = moveCount
            }
        }
        val typeChart = if (expansion != null) {
            val chart = requireNotNull(tables.typeChart)
            ValidationEvidence(
                compatible = true,
                validRecords = chart.count,
                totalRecords = chart.count,
                confidence = 1.0,
                reasons = listOf("validated expansion Q4.12 type-effectiveness matrix"),
                offset = chart.offset,
                recordSize = chart.recordSize,
                elementSize = chart.elementSize,
            )
        } else if (generation == 3) {
            TableValidators.resolveGen3TypeChart(rom, tables.typeChart?.offset)
        } else {
            tables.typeChart?.let {
                TableValidators.typeChart(rom, it.offset, generation)
            } ?: missing("type-chart table not resolved")
        }
        val sprites = when (generation) {
            1 -> tables.sprites?.let {
                SpriteValidators.gen1(rom, it.offset, it.count, it.recordSize, it.banks.toIntArray())
            } ?: missing("Gen 1 sprite references not resolved")
            2 -> tables.sprites?.let {
                SpriteValidators.gen2(rom, it.offset, it.count, it.bankAdjustment)
            } ?: missing("Gen 2 sprite pointer table not resolved")
            else -> if (expansion != null) {
                PokeemeraldExpansionResolver.validateSprites(rom, expansion)
            } else {
                tables.sprites?.let {
                    SpriteValidators.gen3(rom, it.offset, speciesCount ?: it.count, it.recordSize)
                } ?: missing("Gen 3 sprite pointer table not resolved")
            }
        }
        val descriptions = when (generation) {
            1 -> tables.descriptions?.let {
                PokemonDatasetValidators.gen1Descriptions(
                    rom, it.offset, it.count, it.bank ?: 0, codec,
                )
            } ?: missing("Gen 1 Pokédex description table not resolved")
            2 -> tables.descriptions?.let {
                PokemonDatasetValidators.gen2Descriptions(
                    rom, it.offset, it.count, it.banks.toIntArray(), codec = codec,
                )
            } ?: missing("Gen 2 Pokédex description table not resolved")
            else -> if (expansion != null) {
                PokeemeraldExpansionResolver.validateDescriptions(rom, expansion)
            } else {
                DatasetResolvers.gen3Descriptions(
                    rom,
                    if (exact != null) tables.descriptions?.count ?: 387 else speciesCount ?: baseProfile?.internalSpeciesCount ?: 412,
                    tables.descriptions,
                    codec,
                )
            }
        }
        val evolutionAndLearnset = if (generation < 3) {
            val layout = tables.evolutions ?: tables.learnsets
            layout?.let {
                PokemonDatasetValidators.gen12EvolutionsAndLearnsets(
                    rom,
                    it.offset,
                    it.count,
                    it.bank ?: 0,
                    moveCount ?: baseProfile?.moveCount ?: it.count,
                    generation,
                )
            }
        } else {
            null
        }
        val evolutions = evolutionAndLearnset?.evolutions ?: if (generation == 3) {
            if (expansion != null) {
                PokeemeraldExpansionResolver.validateEvolutions(rom, expansion)
            } else {
                DatasetResolvers.gen3Evolutions(
                    rom, speciesCount ?: baseProfile?.internalSpeciesCount ?: 412, tables.evolutions,
                )
            }
        } else {
            missing("combined evolution/learnset table not resolved")
        }
        val learnsets = evolutionAndLearnset?.learnsets ?: if (generation == 3) {
            if (expansion != null) {
                PokeemeraldExpansionResolver.validateLearnsets(rom, expansion)
            } else {
                DatasetResolvers.gen3Learnsets(
                    rom,
                    speciesCount ?: baseProfile?.internalSpeciesCount ?: 412,
                    effectiveMoveCount ?: baseProfile?.moveCount?.plus(1) ?: 355,
                    tables.learnsets,
                )
            }
        } else {
            missing("combined evolution/learnset table not resolved")
        }
        val abilities = if (generation == 3) {
            tables.abilities?.let {
                if (expansion != null) {
                    TableValidators.names(rom, it, expansion.abilityCount, codec)
                } else {
                    resolveAbilities(rom, it, codec, baseProfile, exact != null)
                }
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

        val capabilities = buildCapabilities(
            names, stats, moveNames, moveData, typeChart, sprites, descriptions, evolutions, learnsets, abilities,
        )
        val resolvedTables = ProfileTables(
            speciesNames = resolvedLayout(speciesNamesLayout, names),
            baseStats = resolvedLayout(baseStatsLayout, stats),
            moveNames = resolvedLayout(moveNamesLayout, moveNames),
            moveData = resolvedLayout(tables.moveData, moveData),
            typeChart = resolvedLayout(tables.typeChart, typeChart, variableLength = true),
            evolutions = resolvedLayout(tables.evolutions, evolutions),
            learnsets = resolvedLayout(tables.learnsets, learnsets),
            sprites = resolvedLayout(tables.sprites, sprites),
            descriptions = resolvedLayout(tables.descriptions, descriptions),
            abilities = resolvedLayout(tables.abilities, abilities),
        )
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
                if (generation == 2 && speciesNamesLayout?.offset != baseProfile?.tables?.speciesNames?.offset) {
                    add("resolved relocated Gen 2 species-name table")
                }
                if (generation == 2 && baseStatsLayout?.offset != baseProfile?.tables?.baseStats?.offset) {
                    add("resolved relocated Gen 2 base-stat table")
                }
                if (generation == 2 && moveNamesLayout?.offset != baseProfile?.tables?.moveNames?.offset) {
                    add("resolved relocated Gen 2 move-name table")
                }
                if (baseStatsLayout != null && baseStatsLayout.recordSize != baseProfile?.tables?.baseStats?.recordSize) {
                    add("inferred base-stat record size ${baseStatsLayout.recordSize}")
                }
                if (expansion != null) {
                    add(
                        "resolved pokeemerald-expansion ${expansion.metadata.versionMajor}." +
                            "${expansion.metadata.versionMinor}.${expansion.metadata.versionPatch}; " +
                            "first species=${expansion.firstRegisters.speciesName}/Dex ${expansion.firstRegisters.speciesNationalDex}, " +
                            "move=${expansion.firstRegisters.moveName}, ability=${expansion.firstRegisters.abilityName}",
                    )
                }
            },
            resolvedLayout = ResolvedRomLayout(
                family = family,
                generation = generation,
                platform = header.platform,
                speciesCount = speciesCount,
                moveCount = effectiveMoveCount,
                tables = resolvedTables,
                pokeemeraldExpansion = expansion?.metadata,
            ),
        )
    }

    private fun resolvedLayout(
        inherited: TableLayout?,
        evidence: ValidationEvidence,
        variableLength: Boolean = inherited?.variableLength ?: false,
    ): TableLayout? {
        if (!evidence.compatible || evidence.offset == null) return null
        val count = evidence.totalRecords.takeIf { it > 0 } ?: inherited?.count ?: 0
        val recordSize = evidence.recordSize ?: inherited?.recordSize ?: 0
        return inherited?.copy(
            offset = evidence.offset,
            count = count,
            recordSize = recordSize,
            variableLength = variableLength,
            elementSize = evidence.elementSize ?: inherited.elementSize,
        ) ?: TableLayout(
            offset = evidence.offset,
            count = count,
            recordSize = recordSize,
            variableLength = variableLength,
            elementSize = evidence.elementSize,
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

    private fun hasCanonicalGen2MovePrefix(rom: RomImage, layout: TableLayout): Boolean {
        if (layout.count < 3 || layout.recordSize < 7) return false
        val expected = listOf(
            intArrayOf(40, 0, 255, 35),
            intArrayOf(50, 1, 255, 25),
            intArrayOf(15, 0, 216, 10),
        )
        return expected.indices.all { index ->
            val base = layout.offset + index * layout.recordSize
            val values = intArrayOf(rom.u8(base + 2), rom.u8(base + 3), rom.u8(base + 4), rom.u8(base + 5))
            values.contentEquals(expected[index])
        }
    }

    private fun identityMatches(header: RomHeader): Boolean = when (family) {
        EngineFamily.RED_BLUE -> header.title.startsWith("POKEMON RED") || header.title.startsWith("POKEMON BLUE")
        EngineFamily.YELLOW -> header.title.startsWith("POKEMON YELLOW")
        EngineFamily.GOLD_SILVER -> header.title.contains("GLD") || header.title.contains("SLV")
        EngineFamily.CRYSTAL -> header.title.contains("CRYSTAL")
        EngineFamily.RUBY_SAPPHIRE -> header.gameCode in setOf("AXVE", "AXPE") ||
            header.title.startsWith("POKEMON RUBY") || header.title.startsWith("POKEMON SAPP")
        EngineFamily.EMERALD -> header.gameCode == "BPEE" || header.title.startsWith("POKEMON EMER")
        EngineFamily.FIRERED_LEAFGREEN -> header.gameCode in setOf("BPRE", "BPGE") ||
            header.title.startsWith("POKEMON FIRE") || header.title.startsWith("POKEMON LEAF")
    }

    private fun resolveTables(rom: RomImage, profile: RomProfile?): ProfileTables {
        var inherited = profile?.tables ?: ProfileTables()
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
            val locatedNames = locateRubySapphireNames(rom)
            val expectedNames = inherited.speciesNames?.offset
            if (locatedNames != null && expectedNames != null && locatedNames != expectedNames) {
                inherited = inherited.relocatedBy(locatedNames - expectedNames)
            }
            GbaHeaderPointers(speciesNames = locatedNames)
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

    private fun ProfileTables.relocatedBy(delta: Int): ProfileTables {
        fun TableLayout?.relocated() = this?.copy(offset = offset + delta)
        return copy(
            speciesNames = speciesNames.relocated(),
            baseStats = baseStats.relocated(),
            moveNames = moveNames.relocated(),
            moveData = moveData.relocated(),
            typeChart = typeChart.relocated(),
            evolutions = evolutions.relocated(),
            learnsets = learnsets.relocated(),
            sprites = sprites.relocated(),
            descriptions = descriptions.relocated(),
            abilities = abilities.relocated(),
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

    private fun resolveAbilities(
        rom: RomImage,
        layout: TableLayout,
        codec: PokemonTextCodec,
        profile: RomProfile?,
        exact: Boolean,
    ): ValidationEvidence {
        val inherited = validateNames(rom, layout, inferAbilityCount(rom, layout, codec, profile), codec)
        if (exact || inherited.compatible) return inherited

        return (8..32).mapNotNull { recordSize ->
            val count = TableValidators.inferFixedNameCount(
                rom, layout.offset, recordSize, codec, minimumCount = 10, maximumCount = 512,
            ) ?: return@mapNotNull null
            TableValidators.fixedNames(rom, layout.offset, count, recordSize, codec)
        }.filter { it.compatible }
            .maxWithOrNull(compareBy<ValidationEvidence> { it.validRecords }.thenBy { it.confidence })
            ?: inherited
    }

    private fun validateNames(
        rom: RomImage,
        layout: TableLayout?,
        inferredCount: Int?,
        codec: PokemonTextCodec,
    ): ValidationEvidence {
        if (layout == null || inferredCount == null) return missing("name table not resolved")
        val minimumRatio = if (generationFor(family) == 1) 0.70 else 0.85
        return TableValidators.names(rom, layout, inferredCount, codec, minimumRatio)
    }

    private fun buildCapabilities(
        names: ValidationEvidence,
        stats: ValidationEvidence,
        moveNames: ValidationEvidence,
        moveData: ValidationEvidence,
        typeChart: ValidationEvidence,
        sprites: ValidationEvidence,
        descriptions: ValidationEvidence,
        evolutions: ValidationEvidence,
        learnsets: ValidationEvidence,
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
            evidence(RomCapability.POKEDEX_DESCRIPTIONS, descriptions),
            evidence(RomCapability.EVOLUTIONS, evolutions),
            evidence(RomCapability.MOVE_CATALOG, moveNames),
            evidence(RomCapability.MOVE_DETAILS, moveData),
            evidence(RomCapability.LEARNSETS, learnsets),
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
