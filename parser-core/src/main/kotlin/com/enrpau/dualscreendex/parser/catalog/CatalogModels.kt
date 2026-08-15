package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.model.CapabilityEvidence
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomCapability

data class CatalogField<T>(
    val status: CapabilityStatus,
    val value: T? = null,
    val reasons: List<String> = emptyList(),
) {
    init {
        require(status == CapabilityStatus.AVAILABLE || value == null) {
            "unavailable catalog fields cannot carry a value"
        }
        require(status != CapabilityStatus.AVAILABLE || value != null) {
            "available catalog fields require a value"
        }
    }

    companion object {
        fun <T> available(value: T): CatalogField<T> = CatalogField(CapabilityStatus.AVAILABLE, value)
        fun <T> notFound(reason: String): CatalogField<T> =
            CatalogField(CapabilityStatus.NOT_FOUND, reasons = listOf(reason))
        fun <T> notApplicable(reason: String): CatalogField<T> =
            CatalogField(CapabilityStatus.NOT_APPLICABLE, reasons = listOf(reason))
    }
}

data class RgbaSprite(
    val width: Int,
    val height: Int,
    val argb: IntArray,
) {
    init {
        require(width > 0 && height > 0) { "sprite dimensions must be positive" }
        require(argb.size == width * height) { "sprite pixel count must equal width * height" }
    }

    override fun equals(other: Any?): Boolean =
        other is RgbaSprite && width == other.width && height == other.height && argb.contentEquals(other.argb)

    override fun hashCode(): Int = 31 * (31 * width + height) + argb.contentHashCode()
}

data class BaseStats(
    val hp: Int,
    val attack: Int,
    val defense: Int,
    val speed: Int,
    val specialAttack: Int,
    val specialDefense: Int,
)

enum class MoveCategory { PHYSICAL, SPECIAL, STATUS, UNKNOWN }

data class SpeciesRecord(
    val id: Int,
    val formId: Int = 0,
    val dexNumber: CatalogField<Int>,
    val name: CatalogField<String>,
    val typeIds: CatalogField<List<Int>>,
    val baseStats: CatalogField<BaseStats>,
    val sprite: CatalogField<RgbaSprite>,
    val description: CatalogField<String> = CatalogField.notFound("description was not materialized"),
    val height: CatalogField<Int> = CatalogField.notFound("height was not materialized"),
    val weight: CatalogField<Int> = CatalogField.notFound("weight was not materialized"),
    val evolutionEdges: CatalogField<List<EvolutionEdge>> = CatalogField.notFound("evolutions were not materialized"),
    val learnset: CatalogField<List<LearnsetEntry>> = CatalogField.notFound("learnset was not materialized"),
    val moveAcquisitions: CatalogField<List<MoveAcquisition>> =
        CatalogField.notFound("non-level move acquisition was not materialized"),
    val abilityIds: CatalogField<List<Int>> = CatalogField.notApplicable("abilities are not part of this engine"),
    val growthRate: CatalogField<Int> = CatalogField.notFound("growth rate was not materialized"),
)

data class MoveRecord(
    val id: Int,
    val name: CatalogField<String>,
    val typeId: CatalogField<Int>,
    val category: CatalogField<MoveCategory>,
    val power: CatalogField<Int>,
    val accuracy: CatalogField<Int>,
    val pp: CatalogField<Int>,
    val priority: CatalogField<Int> = CatalogField.notFound("priority was not materialized"),
    val effectId: CatalogField<Int> = CatalogField.notFound("effect was not materialized"),
    val effectText: CatalogField<String> = CatalogField.notFound("effect text was not materialized"),
)

data class TypeRecord(
    val id: Int,
    val name: CatalogField<String>,
    val presentation: CatalogField<TypePresentation> = CatalogField.notFound("type presentation was not materialized"),
)

data class TypeMatchup(val attackingTypeId: Int, val defendingTypeId: Int, val multiplierPercent: Int)

data class LearnsetEntry(val level: Int, val moveId: Int, val methodId: Int = 0)

data class NormalizedLevelUpMove(
    val moveId: Int,
    val initial: Boolean,
    val levels: List<Int>,
)

data class LearnsetRuleset(
    val id: String,
    val label: String,
    val sourceOffset: Int,
    val confidence: Double,
    val entriesBySpecies: Map<Int, List<LearnsetEntry>>,
    val primary: Boolean = false,
    /** SaveBlock1 selector for this level-up table only; egg/TM mode selection is not implied. */
    val levelUpSelector: LevelUpRulesetSelector? = null,
)

data class LevelUpRulesetSelector(
    val saveBlock1ByteOffset: Int,
    val mask: Int,
    val expectedValue: Int,
)

enum class MoveAcquisitionMethod { EGG, MACHINE, TUTOR }

data class MoveAcquisition(
    val moveId: Int,
    val method: MoveAcquisitionMethod,
    val sourceId: Int? = null,
)

data class EvolutionEdge(
    val targetSpeciesId: Int,
    val methodId: Int,
    val parameter: Int,
    val raw: ByteArray = byteArrayOf(),
    val conditionValue: Int? = null,
) {
    override fun equals(other: Any?): Boolean =
        other is EvolutionEdge && targetSpeciesId == other.targetSpeciesId && methodId == other.methodId &&
            parameter == other.parameter && conditionValue == other.conditionValue && raw.contentEquals(other.raw)

    override fun hashCode(): Int =
        31 * (31 * (31 * (31 * targetSpeciesId + methodId) + parameter) + (conditionValue ?: 0)) +
            raw.contentHashCode()
}

data class AbilityRecord(
    val id: Int,
    val name: CatalogField<String>,
    val description: CatalogField<String> = CatalogField.notFound("ability description was not resolved from the ROM"),
    val mechanics: CatalogField<List<AbilityMechanic>> =
        CatalogField.notFound("ability mechanics were not resolved from ROM code"),
)

data class DescriptionRecord(
    val text: String,
    val height: Int? = null,
    val weight: Int? = null,
    val category: String? = null,
)

data class EncounterSlot(
    val speciesId: Int,
    val minimumLevel: Int,
    val maximumLevel: Int,
    val weight: Int?,
)

enum class EncounterWindow { ANY, MORNING, DAY, NIGHT }

data class EncounterArea(
    val id: Int,
    val name: CatalogField<String>,
    val methodId: Int,
    val slots: List<EncounterSlot>,
    val windows: Set<EncounterWindow> = setOf(EncounterWindow.ANY),
)

enum class PresentationSource { ROM_EXTRACTED, FAMILY_FALLBACK, ACCESSIBLE_FALLBACK, NEUTRAL }

data class TypePresentation(
    val source: PresentationSource,
    val foregroundArgb: Int,
    val backgroundArgb: Int,
    val borderArgb: Int,
)

data class CaptureBallRecord(
    val id: Int,
    val name: CatalogField<String>,
    val sprite: CatalogField<RgbaSprite>,
    val generic: Boolean = false,
)

enum class RuntimeMemoryEvidence { SOURCE_PROVEN_UNTESTED, LIVE_VALIDATED }

enum class CatalogGen3TextEncoding { ENGLISH }

enum class CatalogGen3BagPocket { ITEMS, KEY_ITEMS, BALLS, TM_HM, BERRIES }

data class CatalogGen3BitFlag(
    val byteOffset: Int,
    val mask: Int,
) {
    init {
        require(byteOffset >= 0)
        require(mask in 1..0x80 && mask.countOneBits() == 1)
    }
}

data class CatalogGen3TrainerCardAbi(
    val playerNameOffset: Int,
    val playerNameLength: Int,
    val genderOffset: Int,
    val trainerIdOffset: Int,
    val playTimeHoursOffset: Int,
    val playTimeMinutesOffset: Int,
    val encryptionKeyOffset: Int,
    val moneyOffset: Int,
    val maximumMoney: Long,
    val badgeFlags: List<CatalogGen3BitFlag>,
) {
    init {
        require(playerNameLength > 0)
        require(maximumMoney >= 0)
        require(badgeFlags.size <= 8 && badgeFlags.distinct().size == badgeFlags.size)
    }
}

data class CatalogGen3BagPocketAbi(
    val pocket: CatalogGen3BagPocket,
    val byteOffset: Int,
    val capacity: Int,
    val slotSize: Int = 4,
) {
    init {
        require(byteOffset >= 0)
        require(capacity > 0)
        require(slotSize >= 4)
    }
}

data class CatalogGen3BagAbi(val pockets: List<CatalogGen3BagPocketAbi>) {
    init {
        require(pockets.isNotEmpty())
        require(pockets.map(CatalogGen3BagPocketAbi::pocket).distinct().size == pockets.size)
    }
}

data class CatalogGen3SaveRuntimeAbi(
    val saveBlock1Size: Int,
    val saveBlock2Size: Int,
    val textEncoding: CatalogGen3TextEncoding,
    val trainer: CatalogGen3TrainerCardAbi,
    val bag: CatalogGen3BagAbi,
) {
    init {
        require(saveBlock1Size > 0 && saveBlock2Size > 0)
        requireRange(trainer.playerNameOffset, trainer.playerNameLength, saveBlock2Size)
        requireRange(trainer.genderOffset, 1, saveBlock2Size)
        requireRange(trainer.trainerIdOffset, 4, saveBlock2Size)
        requireRange(trainer.playTimeHoursOffset, 2, saveBlock2Size)
        requireRange(trainer.playTimeMinutesOffset, 1, saveBlock2Size)
        requireRange(trainer.encryptionKeyOffset, 4, saveBlock2Size)
        requireRange(trainer.moneyOffset, 4, saveBlock1Size)
        trainer.badgeFlags.forEach { requireRange(it.byteOffset, 1, saveBlock1Size) }
        bag.pockets.forEach { requireRange(it.byteOffset, it.capacity * it.slotSize, saveBlock1Size) }
    }

    private fun requireRange(offset: Int, length: Int, limit: Int) {
        require(offset >= 0 && length > 0 && offset.toLong() + length <= limit.toLong())
    }
}

data class CatalogGen3PartyAbi(
    val countAddress: Long,
    val partyAddress: Long,
    val capacity: Int,
    val recordSize: Int,
) {
    init {
        require(countAddress in 0x02000000L..0x0203FFFFL)
        require(capacity > 0 && recordSize >= 80)
        require(partyAddress in 0x02000000L..0x0203FFFFL)
        require(partyAddress + capacity.toLong() * recordSize <= 0x02040000L)
    }
}

data class CatalogGen3BattleUiAbi(
    val activeBattlerAddress: Long,
    val actionCursorAddress: Long,
    val moveCursorAddress: Long,
    val targetCursorAddress: Long,
) {
    init {
        listOf(activeBattlerAddress, actionCursorAddress, moveCursorAddress, targetCursorAddress).forEach {
            require(it in 0x02000000L..0x0203FFFFL)
        }
    }
}

data class CatalogGen3RuntimeMemoryLayout(
    val mainAddress: Long,
    val inBattleAddress: Long,
    val inBattleMask: Int,
    val saveBlock1MapGroupOffset: Int,
    val saveBlock1MapNumberOffset: Int,
    val saveBlock1PositionXOffset: Int = 0,
    val saveBlock1PositionYOffset: Int = 2,
    val multiUsePlayerCursorAddress: Long? = null,
    val multiUsePlayerCursorEvidence: RuntimeMemoryEvidence? = null,
    val playerPartyCountAddress: Long? = null,
    val playerPartyAddress: Long? = null,
    val battleMonsAddress: Long? = null,
    val battleTypeFlagsAddress: Long? = null,
    val trainerBattleMask: Int? = null,
    val nonWildBattleMask: Int? = null,
    val saveBlock1PointerAddress: Long? = null,
    val saveBlock2PointerAddress: Long? = null,
    val saveRuntimeAbi: CatalogGen3SaveRuntimeAbi? = null,
    val partyAbi: CatalogGen3PartyAbi? = null,
    val battleUiAbi: CatalogGen3BattleUiAbi? = null,
) {
    init {
        require(saveBlock1PositionXOffset >= 0 && saveBlock1PositionYOffset == saveBlock1PositionXOffset + 2) {
            "SaveBlock1 position must be two adjacent signed 16-bit coordinates"
        }
        require((playerPartyCountAddress == null) == (playerPartyAddress == null)) {
            "live party count and record addresses must be present together"
        }
        require(playerPartyCountAddress == null || playerPartyCountAddress in 0x02000000L..0x0203FFFFL)
        require(playerPartyAddress == null || playerPartyAddress in 0x02000000L..0x0203FFFFL)
        require(battleMonsAddress == null || battleMonsAddress in 0x02000000L..0x0203FBBFL) {
            "battle-mon window must fit in EWRAM"
        }
        require(
            listOf(battleTypeFlagsAddress, trainerBattleMask, nonWildBattleMask).all { it == null } ||
                listOf(battleTypeFlagsAddress, trainerBattleMask, nonWildBattleMask).all { it != null },
        ) { "battle type descriptor must be complete" }
        require(battleTypeFlagsAddress == null || battleTypeFlagsAddress in 0x02000000L..0x0203FFFCL)
        require(trainerBattleMask == null || trainerBattleMask.countOneBits() == 1)
        require(
            listOf(saveBlock1PointerAddress, saveBlock2PointerAddress, saveRuntimeAbi).all { it == null } ||
                listOf(saveBlock1PointerAddress, saveBlock2PointerAddress, saveRuntimeAbi).all { it != null },
        ) { "save-block pointer and ABI descriptor must be complete" }
        require(saveBlock1PointerAddress == null || saveBlock1PointerAddress in 0x02000000L..0x03007FFCL)
        require(saveBlock2PointerAddress == null || saveBlock2PointerAddress in 0x02000000L..0x03007FFCL)
        require(
            partyAbi == null ||
                (playerPartyCountAddress == null && playerPartyAddress == null) ||
                (
                    partyAbi.countAddress == playerPartyCountAddress &&
                        partyAbi.partyAddress == playerPartyAddress
                    ),
        ) { "legacy and typed party descriptors disagree" }
    }
}

data class CatalogRuntimeMetadata(
    val gen3SaveBlock1PointerAddress: Long? = null,
    val gen3RuntimeMemoryLayout: CatalogGen3RuntimeMemoryLayout? = null,
    val areaNamesByBaseId: Map<Int, String> = emptyMap(),
)

data class PngMapAsset(
    val bytes: ByteArray,
) {
    init {
        require(bytes.size >= PNG_SIGNATURE.size && bytes.copyOfRange(0, PNG_SIGNATURE.size).contentEquals(PNG_SIGNATURE)) {
            "local-map assets must be PNG images"
        }
    }

    override fun equals(other: Any?): Boolean = other is PngMapAsset && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = bytes.contentHashCode()

    private companion object {
        val PNG_SIGNATURE = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
    }
}

data class LocalMapCatalog(
    val maps: List<LocalMap> = emptyList(),
    val assets: Map<String, PngMapAsset> = emptyMap(),
) {
    init {
        validate()
    }

    fun validate(): LocalMapCatalog = apply {
        require(maps.map(LocalMap::key).toSet().size == maps.size) {
            "local-map keys must be unique"
        }
        require(maps.map(LocalMap::baseAreaId).toSet().size == maps.size) {
            "local maps must bind unique base-area IDs"
        }
        val referencedAssetKeys = maps.map(LocalMap::imageAssetKey).toSet()
        require(assets.keys == referencedAssetKeys) {
            "local-map assets must exactly match map asset keys"
        }
        maps.forEach { map ->
            require(map.key.isNotBlank()) { "local-map keys must not be blank" }
            require(map.baseAreaId in 0..0xFFFF) { "local-map base-area IDs must fit group/map identity" }
            require(map.pixelWidth > 0 && map.pixelHeight > 0) {
                "local-map pixel dimensions must be positive"
            }
            require(map.gridWidth > 0 && map.gridHeight > 0) {
                "local-map grid dimensions must be positive"
            }
            require(
                map.pixelWidth.toLong() == map.gridWidth.toLong() * LOCAL_METATILE_PIXELS &&
                    map.pixelHeight.toLong() == map.gridHeight.toLong() * LOCAL_METATILE_PIXELS,
            ) { "local-map pixel dimensions must match the metatile grid" }
            require(assets.containsKey(map.imageAssetKey)) {
                "local map ${map.key} has no raster"
            }
        }
    }

    private companion object {
        const val LOCAL_METATILE_PIXELS = 16
    }
}

data class LocalMap(
    val key: String,
    val displayName: String?,
    val baseAreaId: Int,
    val pixelWidth: Int,
    val pixelHeight: Int,
    val gridWidth: Int,
    val gridHeight: Int,
    val imageAssetKey: String,
)

data class WorldMapCatalog(
    val regions: List<WorldMapRegion> = emptyList(),
    val assets: Map<String, RgbaSprite> = emptyMap(),
) {
    init {
        validate()
    }

    fun validate(): WorldMapCatalog = apply {
        require(regions.map(WorldMapRegion::key).toSet().size == regions.size) {
            "world-map region keys must be unique"
        }
        val referencedAssetKeys = regions.map(WorldMapRegion::imageAssetKey).toSet()
        require(assets.keys == referencedAssetKeys) {
            "world-map assets must exactly match region asset keys"
        }
        regions.forEach { region ->
            require(region.key.isNotBlank()) { "world-map region keys must not be blank" }
            require(region.pixelWidth > 0 && region.pixelHeight > 0) {
                "world-map pixel dimensions must be positive"
            }
            require(region.gridWidth > 0 && region.gridHeight > 0) {
                "world-map grid dimensions must be positive"
            }
            val raster = requireNotNull(assets[region.imageAssetKey]) {
                "world-map region ${region.key} has no raster"
            }
            require(raster.width == region.pixelWidth && raster.height == region.pixelHeight) {
                "world-map region ${region.key} raster dimensions do not match metadata"
            }
            require(region.locations.isNotEmpty()) { "world-map regions must contain resolved locations" }
            require(region.locations.map(WorldMapLocation::key).toSet().size == region.locations.size) {
                "world-map location keys must be unique within a region"
            }
            region.locations.forEach { location ->
                require(location.key.isNotBlank() && location.displayName.isNotBlank()) {
                    "world-map location identity must not be blank"
                }
                require(location.baseAreaIds.isNotEmpty()) {
                    "world-map locations must bind at least one base area"
                }
                require(location.geometry.isNotEmpty()) {
                    "world-map locations must contain geometry"
                }
                location.geometry.forEach { cell ->
                    require(cell.x >= 0 && cell.y >= 0 && cell.width > 0 && cell.height > 0) {
                        "world-map cells must have non-negative origins and positive dimensions"
                    }
                    require(
                        cell.x.toLong() + cell.width <= region.gridWidth.toLong() &&
                            cell.y.toLong() + cell.height <= region.gridHeight.toLong(),
                    ) { "world-map cells must remain inside the region grid" }
                }
            }
        }
    }
}

data class WorldMapRegion(
    val key: String,
    val displayName: String?,
    val pixelWidth: Int,
    val pixelHeight: Int,
    val gridWidth: Int,
    val gridHeight: Int,
    val imageAssetKey: String,
    val locations: List<WorldMapLocation>,
)

data class WorldMapLocation(
    val key: String,
    val displayName: String,
    val baseAreaIds: Set<Int>,
    val geometry: List<WorldMapCell>,
)

data class WorldMapCell(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

data class ParsedCatalog(
    val romSha256: String,
    val family: EngineFamily,
    val platform: Platform,
    val romCrc32: String = "",
    val speciesById: Map<Int, SpeciesRecord> = emptyMap(),
    val movesById: Map<Int, MoveRecord> = emptyMap(),
    val typesById: Map<Int, TypeRecord> = emptyMap(),
    val abilitiesById: Map<Int, AbilityRecord> = emptyMap(),
    val typeChart: List<TypeMatchup> = emptyList(),
    val encounterAreas: List<EncounterArea> = emptyList(),
    val captureBallsById: Map<Int, CaptureBallRecord> = emptyMap(),
    val learnsetRulesets: List<LearnsetRuleset> = emptyList(),
    val runtimeMetadata: CatalogRuntimeMetadata = CatalogRuntimeMetadata(),
    val worldMaps: WorldMapCatalog = WorldMapCatalog(),
    val trainerAssets: TrainerAssetCatalog = TrainerAssetCatalog(),
    val localMaps: LocalMapCatalog = LocalMapCatalog(),
    val capabilities: Map<RomCapability, CapabilityEvidence> = emptyMap(),
    val diagnostics: List<String> = emptyList(),
) {
    fun navigableSpecies(): List<SpeciesRecord> = speciesById.values.filter { species ->
        (species.dexNumber.value ?: 0) > 0 && species.name.value?.any(Char::isLetterOrDigit) == true
    }
}
