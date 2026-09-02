package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.dataset.natures.NatureRecord

import com.enrpau.dualscreendex.parser.model.CapabilityEvidence
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomCapability
import com.enrpau.dualscreendex.parser.language.RomLanguageManifest

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
    val text: String?,
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

enum class CatalogGen3BagDataSource { SAVE_BLOCK1, EXTENDED_SAVE }

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
    /** Null for source families, such as Ruby/Sapphire, whose save values are not XOR-obfuscated. */
    val encryptionKeyOffset: Int?,
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
    val dataSource: CatalogGen3BagDataSource = CatalogGen3BagDataSource.SAVE_BLOCK1,
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

data class CatalogGen3EventFlagAbi(
    val byteOffset: Int,
    val byteCount: Int,
) {
    init {
        require(byteOffset >= 0)
        require(byteCount > 0)
    }
}

data class CatalogGen3SaveRuntimeAbi(
    val saveBlock1Size: Int,
    val saveBlock2Size: Int,
    val extendedSaveDataSize: Int = 0,
    val textEncoding: CatalogGen3TextEncoding,
    val trainer: CatalogGen3TrainerCardAbi,
    val bag: CatalogGen3BagAbi,
    val eventFlags: CatalogGen3EventFlagAbi? = null,
) {
    init {
        require(saveBlock1Size > 0 && saveBlock2Size > 0 && extendedSaveDataSize >= 0)
        requireRange(trainer.playerNameOffset, trainer.playerNameLength, saveBlock2Size)
        requireRange(trainer.genderOffset, 1, saveBlock2Size)
        requireRange(trainer.trainerIdOffset, 4, saveBlock2Size)
        requireRange(trainer.playTimeHoursOffset, 2, saveBlock2Size)
        requireRange(trainer.playTimeMinutesOffset, 1, saveBlock2Size)
        trainer.encryptionKeyOffset?.let { requireRange(it, 4, saveBlock2Size) }
        requireRange(trainer.moneyOffset, 4, saveBlock1Size)
        trainer.badgeFlags.forEach { requireRange(it.byteOffset, 1, saveBlock1Size) }
        bag.pockets.forEach { pocket ->
            val limit = when (pocket.dataSource) {
                CatalogGen3BagDataSource.SAVE_BLOCK1 -> saveBlock1Size
                CatalogGen3BagDataSource.EXTENDED_SAVE -> extendedSaveDataSize
            }
            requireRange(pocket.byteOffset, pocket.capacity * pocket.slotSize, limit)
        }
        eventFlags?.let { requireRange(it.byteOffset, it.byteCount, saveBlock1Size) }
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

data class CatalogGameClockSchedule(
    val dayStartHour: Int,
    val nightStartHour: Int,
) {
    init {
        require(dayStartHour in 0..23)
        require(nightStartHour in 0..23)
        require(dayStartHour != nightStartHour)
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
    val liveClockAddress: Long? = null,
    val liveClockSchedule: CatalogGameClockSchedule? = null,
    val multiUsePlayerCursorAddress: Long? = null,
    val multiUsePlayerCursorEvidence: RuntimeMemoryEvidence? = null,
    val playerPartyCountAddress: Long? = null,
    val playerPartyAddress: Long? = null,
    val battleMonsAddress: Long? = null,
    val battleTypeFlagsAddress: Long? = null,
    val trainerBattleMask: Int? = null,
    val nonWildBattleMask: Int? = null,
    val saveBlock1Address: Long? = null,
    val saveBlock2Address: Long? = null,
    val saveBlock1PointerAddress: Long? = null,
    val saveBlock2PointerAddress: Long? = null,
    val pokemonStorageAddress: Long? = null,
    val pokemonStoragePointerAddress: Long? = null,
    val pokemonStorageBoxCount: Int? = null,
    val pokemonStorageBoxCapacity: Int? = null,
    val pokemonStorageRecordSize: Int? = null,
    val pokemonStorageRecordsOffset: Int? = null,
    val extendedSaveAddress: Long? = null,
    val saveRuntimeAbi: CatalogGen3SaveRuntimeAbi? = null,
    val partyAbi: CatalogGen3PartyAbi? = null,
    val battleUiAbi: CatalogGen3BattleUiAbi? = null,
) {
    init {
        require(saveBlock1PositionXOffset >= 0 && saveBlock1PositionYOffset == saveBlock1PositionXOffset + 2) {
            "SaveBlock1 position must be two adjacent signed 16-bit coordinates"
        }
        require(liveClockAddress == null || liveClockAddress in 0x03000000L..0x03007FFAL) {
            "live clock window must fit in IWRAM"
        }
        require(liveClockSchedule == null || liveClockAddress != null) {
            "live clock schedule requires a validated clock address"
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
        val directSaveFields = listOf(saveBlock1Address, saveBlock2Address)
        val pointerSaveFields = listOf(saveBlock1PointerAddress, saveBlock2PointerAddress)
        require(directSaveFields.all { it == null } || directSaveFields.all { it != null }) {
            "direct save-block descriptor must be complete"
        }
        require(pointerSaveFields.all { it == null } || pointerSaveFields.all { it != null }) {
            "save-block pointer descriptor must be complete"
        }
        require(directSaveFields.all { it == null } || pointerSaveFields.all { it == null }) {
            "save blocks must use either direct addresses or pointer globals, never both"
        }
        require((saveRuntimeAbi == null) == (directSaveFields.all { it == null } && pointerSaveFields.all { it == null })) {
            "save-block addressing and ABI descriptor must be present together"
        }
        require(saveBlock1Address == null || saveBlock1Address in 0x02000000L..0x0203FFFFL)
        require(saveBlock2Address == null || saveBlock2Address in 0x02000000L..0x0203FFFFL)
        require(
            saveBlock1Address == null || saveBlock1Address + requireNotNull(saveRuntimeAbi).saveBlock1Size <= 0x02040000L,
        ) { "direct SaveBlock1 window must fit in EWRAM" }
        require(
            saveBlock2Address == null || saveBlock2Address + requireNotNull(saveRuntimeAbi).saveBlock2Size <= 0x02040000L,
        ) { "direct SaveBlock2 window must fit in EWRAM" }
        require(saveBlock1PointerAddress == null || saveBlock1PointerAddress in 0x02000000L..0x03007FFCL)
        require(saveBlock2PointerAddress == null || saveBlock2PointerAddress in 0x02000000L..0x03007FFCL)
        val storageShape = listOf(
            pokemonStorageBoxCount,
            pokemonStorageBoxCapacity,
            pokemonStorageRecordSize,
            pokemonStorageRecordsOffset,
        )
        require(storageShape.all { it == null } || storageShape.all { it != null }) {
            "Pokemon storage shape descriptor must be complete"
        }
        require((pokemonStorageAddress == null) || pokemonStoragePointerAddress == null) {
            "Pokemon storage must use either a direct address or a pointer global"
        }
        require((pokemonStorageAddress != null || pokemonStoragePointerAddress != null) == storageShape.all { it != null }) {
            "Pokemon storage addressing and shape must be present together"
        }
        require(pokemonStorageAddress == null || pokemonStorageAddress in 0x02000000L..0x0203FFFFL)
        require(pokemonStoragePointerAddress == null || pokemonStoragePointerAddress in 0x02000000L..0x03007FFCL)
        require(pokemonStorageBoxCount == null || pokemonStorageBoxCount > 0)
        require(pokemonStorageBoxCapacity == null || pokemonStorageBoxCapacity > 0)
        require(pokemonStorageRecordSize == null || pokemonStorageRecordSize >= 80)
        require(pokemonStorageRecordsOffset == null || pokemonStorageRecordsOffset >= 0)
        require(
            pokemonStorageAddress == null || pokemonStorageAddress + requireNotNull(pokemonStorageRecordsOffset) +
                requireNotNull(pokemonStorageBoxCount).toLong() * requireNotNull(pokemonStorageBoxCapacity) *
                requireNotNull(pokemonStorageRecordSize) <= 0x02040000L,
        ) { "direct Pokemon storage record window must fit in EWRAM" }
        require(extendedSaveAddress == null || extendedSaveAddress in 0x02000000L..0x0203FFFFL)
        require(
            saveRuntimeAbi?.extendedSaveDataSize?.let { size ->
                (size == 0 && extendedSaveAddress == null) ||
                    (size > 0 && extendedSaveAddress != null && extendedSaveAddress + size <= 0x02040000L)
            } ?: (extendedSaveAddress == null),
        ) { "extended-save runtime address and ABI size must be present together" }
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
    val gen2TimeOfDayWramOffset: Int? = null,
    val gen3SaveBlock1PointerAddress: Long? = null,
    val gen3RuntimeMemoryLayout: CatalogGen3RuntimeMemoryLayout? = null,
    val areaNamesByBaseId: Map<Int, String> = emptyMap(),
) {
    fun validate(): CatalogRuntimeMetadata = apply {
        require(gen2TimeOfDayWramOffset == null || gen2TimeOfDayWramOffset in 0 until GEN2_WRAM_BYTES) {
            "Gen II time-of-day offset must remain inside WRAM"
        }
    }

    private companion object {
        const val GEN2_WRAM_BYTES = 0x2000
    }
}

enum class MapLighting { MORNING, DAY, NIGHT, DARK }

enum class LocalMapLightingPolicy {
    AUTO, MORNING, DAY, NIGHT, DARK;

    fun resolve(requested: MapLighting): MapLighting = when (this) {
        AUTO -> requested
        MORNING -> MapLighting.MORNING
        DAY -> MapLighting.DAY
        NIGHT -> MapLighting.NIGHT
        DARK -> MapLighting.DARK
    }
}

data class MapLightingPalettes(
    val morning: IntArray,
    val day: IntArray,
    val night: IntArray,
    val dark: IntArray,
) {
    fun validate(): MapLightingPalettes = apply {
        listOf(morning, day, night, dark).forEach { colors ->
            require(colors.size == COLORS_PER_LIGHTING) {
                "indexed map lighting palettes must contain 32 colors"
            }
        }
    }

    operator fun get(lighting: MapLighting): IntArray = when (lighting) {
        MapLighting.MORNING -> morning
        MapLighting.DAY -> day
        MapLighting.NIGHT -> night
        MapLighting.DARK -> dark
    }

    override fun equals(other: Any?): Boolean = other is MapLightingPalettes &&
        morning.contentEquals(other.morning) && day.contentEquals(other.day) &&
        night.contentEquals(other.night) && dark.contentEquals(other.dark)

    override fun hashCode(): Int = listOf(
        morning.contentHashCode(),
        day.contentHashCode(),
        night.contentHashCode(),
        dark.contentHashCode(),
    ).fold(1) { result, value -> 31 * result + value }

    private companion object {
        const val COLORS_PER_LIGHTING = 32
    }
}

data class IndexedMapAsset(
    val pixelWidth: Int,
    val pixelHeight: Int,
    val compressedIndices: ByteArray,
    val lightingPolicy: LocalMapLightingPolicy,
    val palettes: MapLightingPalettes,
) {
    val pixelCount: Int
        get() = (pixelWidth.toLong() * pixelHeight).also {
            require(it in 1..Int.MAX_VALUE.toLong()) { "indexed map pixel count is invalid" }
        }.toInt()

    fun validate(): IndexedMapAsset = apply {
        require(pixelWidth > 0 && pixelHeight > 0) { "indexed map dimensions must be positive" }
        require(compressedIndices.isNotEmpty()) { "indexed map data must not be empty" }
        palettes.validate()
        LocalMapRasterCodec.inflate(this).forEach { value ->
            require((value.toInt() and 0xff) in 0..31) {
                "indexed map pixels must fit the 32-color domain"
            }
        }
    }

    override fun equals(other: Any?): Boolean = other is IndexedMapAsset &&
        pixelWidth == other.pixelWidth && pixelHeight == other.pixelHeight &&
        lightingPolicy == other.lightingPolicy && compressedIndices.contentEquals(other.compressedIndices) &&
        palettes == other.palettes

    override fun hashCode(): Int = 31 * (
        31 * (31 * (31 * pixelWidth + pixelHeight) + lightingPolicy.hashCode()) +
            compressedIndices.contentHashCode()
        ) + palettes.hashCode()
}

data class MapTimeOfDay(val hours: Int, val minutes: Int) {
    init {
        require(hours in 0..23) { "map-render hour must be in 0..23" }
        require(minutes in 0..59) { "map-render minute must be in 0..59" }
    }

    val minuteOfDay: Int = hours * 60 + minutes
}

data class MapTimeBlend(
    val blendColor: Int,
    val tint: Boolean,
    val coefficient: Int,
) {
    fun validate(): MapTimeBlend = apply {
        require(blendColor in 0..0xFFFFFF) { "map time blend color must fit 24 bits" }
        require(coefficient in 0..31) { "map time blend coefficient must fit five bits" }
    }
}

data class MapTimePaletteModel(
    val night: MapTimeBlend,
    val twilight: MapTimeBlend,
    val day: MapTimeBlend,
) {
    fun validate(): MapTimePaletteModel = apply {
        night.validate()
        twilight.validate()
        day.validate()
    }
}

data class TimedIndexedMapAsset(
    val pixelWidth: Int,
    val pixelHeight: Int,
    val compressedIndices: ByteArray,
    val baseColors: IntArray,
    val alternateColors: IntArray,
    val alternatePaletteMask: Int,
    val paletteModel: MapTimePaletteModel,
) {
    val pixelCount: Int
        get() = (pixelWidth.toLong() * pixelHeight).also {
            require(it in 1..Int.MAX_VALUE.toLong()) { "timed map pixel count is invalid" }
        }.toInt()

    fun validate(): TimedIndexedMapAsset = apply {
        require(pixelWidth > 0 && pixelHeight > 0) { "timed map dimensions must be positive" }
        require(compressedIndices.isNotEmpty()) { "timed map data must not be empty" }
        require(baseColors.size == COLORS_PER_MAP && alternateColors.size == COLORS_PER_MAP) {
            "timed map palettes must contain 256 colors"
        }
        require(baseColors.all { it in 0..0xFFFF } && alternateColors.all { it in 0..0xFFFF }) {
            "timed map palette colors must fit BGR555 plus the light marker bit"
        }
        require(alternatePaletteMask and PALETTE_MASK.inv() == 0) {
            "timed map alternate palette mask exceeds the map palette domain"
        }
        paletteModel.validate()
        LocalMapRasterCodec.inflate(this)
    }

    override fun equals(other: Any?): Boolean = other is TimedIndexedMapAsset &&
        pixelWidth == other.pixelWidth && pixelHeight == other.pixelHeight &&
        compressedIndices.contentEquals(other.compressedIndices) &&
        baseColors.contentEquals(other.baseColors) && alternateColors.contentEquals(other.alternateColors) &&
        alternatePaletteMask == other.alternatePaletteMask && paletteModel == other.paletteModel

    override fun hashCode(): Int = listOf(
        pixelWidth,
        pixelHeight,
        compressedIndices.contentHashCode(),
        baseColors.contentHashCode(),
        alternateColors.contentHashCode(),
        alternatePaletteMask,
        paletteModel.hashCode(),
    ).fold(1) { result, value -> 31 * result + value }

    private companion object {
        const val COLORS_PER_MAP = 256
        const val PALETTE_MASK = 0x1FFF
    }
}

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
    val indexedAssets: Map<String, IndexedMapAsset> = emptyMap(),
    val timedAssets: Map<String, TimedIndexedMapAsset> = emptyMap(),
    val scenes: List<LocalMapScene> = emptyList(),
    val pois: List<LocalMapPoi> = emptyList(),
    val poiAssets: Map<String, PngMapAsset> = emptyMap(),
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
        require(
            assets.keys.intersect(indexedAssets.keys).isEmpty() &&
                assets.keys.intersect(timedAssets.keys).isEmpty() &&
                indexedAssets.keys.intersect(timedAssets.keys).isEmpty()
        ) {
            "local-map asset keys must belong to exactly one raster store"
        }
        val referencedAssetKeys = maps.map(LocalMap::imageAssetKey).toSet()
        require(assets.keys + indexedAssets.keys + timedAssets.keys == referencedAssetKeys) {
            "local-map assets must exactly match map asset keys"
        }
        indexedAssets.values.forEach(IndexedMapAsset::validate)
        timedAssets.values.forEach(TimedIndexedMapAsset::validate)
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
            val indexed = indexedAssets[map.imageAssetKey]
            require(indexed == null || indexed.pixelWidth == map.pixelWidth && indexed.pixelHeight == map.pixelHeight) {
                "local map ${map.key} indexed raster dimensions do not match metadata"
            }
            val timed = timedAssets[map.imageAssetKey]
            require(timed == null || timed.pixelWidth == map.pixelWidth && timed.pixelHeight == map.pixelHeight) {
                "local map ${map.key} timed raster dimensions do not match metadata"
            }
        }
        val mapsByKey = maps.associateBy(LocalMap::key)
        require(pois.map(LocalMapPoi::key).toSet().size == pois.size) {
            "local-map POI keys must be unique"
        }
        val referencedPoiAssets = pois.mapNotNull { it.item?.iconAssetKey }.toSet()
        require(poiAssets.keys == referencedPoiAssets) {
            "local-map POI assets must exactly match referenced icon keys"
        }
        pois.forEach { poi ->
            require(poi.key.isNotBlank()) { "local-map POI keys must not be blank" }
            val map = requireNotNull(mapsByKey[poi.localMapKey]) {
                "local-map POI ${poi.key} references an unknown map"
            }
            require(poi.baseAreaId == map.baseAreaId) {
                "local-map POI ${poi.key} base-area identity does not match its map"
            }
            require(poi.tileX in 0 until map.gridWidth && poi.tileY in 0 until map.gridHeight) {
                "local-map POI ${poi.key} lies outside its map"
            }
            require(poi.displayName == null || poi.displayName.isNotBlank()) {
                "local-map POI display names must not be blank"
            }
            require(poi.destinationBaseAreaId == null || poi.destinationBaseAreaId in 0..0xFFFF) {
                "local-map POI destination base-area IDs must fit group/map identity"
            }
            when (poi.kind) {
                LocalMapPoiKind.SERVICE -> require(poi.service != null && poi.item == null) {
                    "service POIs require a service role and cannot carry item metadata"
                }
                LocalMapPoiKind.VISIBLE_ITEM -> require(poi.item != null && poi.service == null) {
                    "visible-item POIs require item metadata and cannot carry a service role"
                }
                LocalMapPoiKind.HIDDEN_ITEM -> require(
                    poi.item != null && poi.service == null &&
                        poi.organicVisibility == LocalMapPoiOrganicVisibility.PROXIMITY_SILHOUETTE,
                ) {
                    "hidden-item POIs require item metadata and proximity discovery"
                }
                LocalMapPoiKind.PLACE,
                LocalMapPoiKind.UNKNOWN,
                -> require(poi.service == null && poi.item == null) {
                    "place and unknown POIs cannot carry service or item metadata"
                }
            }
        }
        require(scenes.map(LocalMapScene::key).toSet().size == scenes.size) {
            "local-map scene keys must be unique"
        }
        val placedMapKeys = mutableSetOf<String>()
        scenes.forEach { scene ->
            require(scene.key.isNotBlank()) { "local-map scene keys must not be blank" }
            require(scene.gridWidth > 0 && scene.gridHeight > 0) { "local-map scene dimensions must be positive" }
            require(scene.placements.size >= 2) { "local-map scenes must connect at least two maps" }
            require(scene.placements.map(LocalMapScenePlacement::localMapKey).toSet().size == scene.placements.size) {
                "local-map scene placements must be unique"
            }
            scene.placements.forEach { placement ->
                val map = requireNotNull(mapsByKey[placement.localMapKey]) {
                    "local-map scene ${scene.key} references an unknown map"
                }
                require(map.baseAreaId == placement.baseAreaId) {
                    "local-map scene placement identity does not match its map"
                }
                require(placedMapKeys.add(placement.localMapKey)) {
                    "local maps may belong to only one generated scene"
                }
                require(
                    placement.gridX >= 0 && placement.gridY >= 0 &&
                        placement.gridX.toLong() + map.gridWidth <= scene.gridWidth.toLong() &&
                        placement.gridY.toLong() + map.gridHeight <= scene.gridHeight.toLong(),
                ) { "local-map scene placement lies outside its bounds" }
            }
            scene.placements.forEachIndexed { index, placement ->
                val map = mapsByKey.getValue(placement.localMapKey)
                scene.placements.drop(index + 1).forEach { other ->
                    val otherMap = mapsByKey.getValue(other.localMapKey)
                    require(
                        placement.gridX + map.gridWidth <= other.gridX ||
                            other.gridX + otherMap.gridWidth <= placement.gridX ||
                            placement.gridY + map.gridHeight <= other.gridY ||
                            other.gridY + otherMap.gridHeight <= placement.gridY,
                    ) { "local-map scene placements must not overlap" }
                }
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

enum class LocalMapPoiKind {
    PLACE,
    SERVICE,
    VISIBLE_ITEM,
    HIDDEN_ITEM,
    UNKNOWN,
}

enum class LocalMapPoiService {
    MART,
    POKEMON_CENTER,
    GYM,
    BUILDING,
}

enum class LocalMapPoiOrganicVisibility {
    VISIBLE,
    ENTRANCE_PROXIMITY,
    PROXIMITY_SILHOUETTE,
}

data class LocalMapPoiItem(
    val itemId: Int? = null,
    val displayName: String? = null,
    val collectionFlagId: Int? = null,
    val iconAssetKey: String? = null,
) {
    init {
        require(itemId == null || itemId in 0..0xFFFF) { "local-map POI item IDs must fit u16" }
        require(displayName == null || displayName.isNotBlank()) { "local-map POI item names must not be blank" }
        require(collectionFlagId == null || collectionFlagId in 0..0xFFFF) {
            "local-map POI item collection flags must fit u16"
        }
        require(iconAssetKey == null || iconAssetKey.isNotBlank()) {
            "local-map POI item icon keys must not be blank"
        }
    }
}

data class LocalMapPoi(
    val key: String,
    val localMapKey: String,
    val baseAreaId: Int,
    val tileX: Int,
    val tileY: Int,
    val kind: LocalMapPoiKind,
    val organicVisibility: LocalMapPoiOrganicVisibility = LocalMapPoiOrganicVisibility.VISIBLE,
    val displayName: String? = null,
    val service: LocalMapPoiService? = null,
    val item: LocalMapPoiItem? = null,
    val destinationBaseAreaId: Int? = null,
    val displayNamesByTrainerGender: Map<Int, String> = emptyMap(),
) {
    init {
        require(displayNamesByTrainerGender.keys.all { it in 0..1 }) {
            "local-map POI trainer-gender names must use male/female keys"
        }
        require(displayNamesByTrainerGender.values.all { it.isNotBlank() }) {
            "local-map POI trainer-gender names must not be blank"
        }
    }
}

data class LocalMapScene(
    val key: String,
    val gridWidth: Int,
    val gridHeight: Int,
    val placements: List<LocalMapScenePlacement>,
) {
    val pixelWidth: Int get() = gridWidth * 16
    val pixelHeight: Int get() = gridHeight * 16
}

data class LocalMapScenePlacement(
    val localMapKey: String,
    val baseAreaId: Int,
    val gridX: Int,
    val gridY: Int,
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
                require(location.key.isNotBlank()) {
                    "world-map location keys must not be blank"
                }
                require(location.displayName == null || location.displayName.isNotBlank()) {
                    "world-map location display names must not be blank"
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
    val displayName: String?,
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
    val naturesById: Map<Int, NatureRecord> = emptyMap(),
    val typeChart: List<TypeMatchup> = emptyList(),
    val encounterAreas: List<EncounterArea> = emptyList(),
    val captureBallsById: Map<Int, CaptureBallRecord> = emptyMap(),
    val learnsetRulesets: List<LearnsetRuleset> = emptyList(),
    val runtimeMetadata: CatalogRuntimeMetadata = CatalogRuntimeMetadata(),
    val worldMaps: WorldMapCatalog = WorldMapCatalog(),
    val trainerAssets: TrainerAssetCatalog = TrainerAssetCatalog(),
    val localMaps: LocalMapCatalog = LocalMapCatalog(),
    val theme: CatalogTheme = CatalogTheme.neutral(),
    val capabilities: Map<RomCapability, CapabilityEvidence> = emptyMap(),
    val diagnostics: List<String> = emptyList(),
    val languageManifest: RomLanguageManifest = RomLanguageManifest.UNKNOWN,
) {
    fun navigableSpecies(): List<SpeciesRecord> = speciesById.values.filter { species ->
        (species.dexNumber.value ?: 0) > 0 &&
            (species.name.value == null || species.name.value.any(Char::isLetterOrDigit))
    }
}
