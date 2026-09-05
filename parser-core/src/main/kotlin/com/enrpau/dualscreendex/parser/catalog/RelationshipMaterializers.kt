package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.text.GbInlineDescriptions
import com.enrpau.dualscreendex.parser.dataset.descriptions.DescriptionRowOutcome
import com.enrpau.dualscreendex.parser.dataset.evolutions.EvolutionRowOutcome
import com.enrpau.dualscreendex.parser.dataset.learnsets.LearnsetRowOutcome
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.language.defaultTextCodec
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.text.Gen1DescriptionTextCodec
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec

internal data class RecordMaterialization<T>(
    val records: Map<Int, T>,
    val failures: Map<Int, String>,
) {
    init {
        require(records.keys.intersect(failures.keys).isEmpty()) {
            "materialized and failed record identities must be disjoint"
        }
    }
}

internal data class RelationshipMaterialization(
    val evolutions: RecordMaterialization<List<EvolutionEdge>>,
    val learnsets: RecordMaterialization<List<LearnsetEntry>>,
)

object RelationshipMaterializers {
    fun descriptions(
        rom: RomImage,
        layout: ResolvedRomLayout,
    ): Map<Int, DescriptionRecord> = descriptionsWithEvidence(rom, layout).records

    internal fun descriptionsWithEvidence(
        rom: RomImage,
        layout: ResolvedRomLayout,
    ): RecordMaterialization<DescriptionRecord> {
        val codec = layout.defaultTextCodec()
        if (layout.generation == 3) {
            val unified = layout.headerlessUnifiedSpecies
            val descriptionOffset = unified?.descriptionPointerOffset
            if (unified != null && descriptionOffset != null) {
                val table = layout.tables.descriptions ?: return emptyMaterialization()
                val categoryOffset = unified.categoryOffset ?: return emptyMaterialization()
                val heightOffset = unified.heightOffset ?: return emptyMaterialization()
                val weightOffset = unified.weightOffset ?: return emptyMaterialization()
                val categoryWidth = unified.speciesNameOffset - categoryOffset
                return materializeRecords(table.count) { id ->
                    val base = unified.speciesTableOffset + id * unified.speciesRecordSize
                    DescriptionRecord(
                        text = codec?.let {
                            val pointer = rom.gbaPointer(base + descriptionOffset)
                                ?: error("invalid embedded description pointer")
                            decodeTerminated(
                                rom,
                                pointer,
                                MAX_DESCRIPTION_BYTES,
                                it,
                            )
                        },
                        height = rom.u16le(base + heightOffset),
                        weight = rom.u16le(base + weightOffset),
                        category = codec?.let {
                            decodeTerminated(
                                rom,
                                base + categoryOffset,
                                categoryWidth,
                                it,
                            )
                        },
                    ).requireTextWhen(codec != null)
                }
            }
            if (layout.pokeemeraldExpansion == null) {
                return typedDescriptions(layout, includeText = codec != null)
            }
        }
        val table = layout.tables.descriptions ?: return emptyMaterialization()
        if (layout.generation < 3) {
            return codec?.let { gen12Descriptions(rom, layout, it) } ?: emptyMaterialization()
        }
        val expansion = layout.pokeemeraldExpansion ?: return emptyMaterialization()
        val stride = table.stride ?: expansion.speciesRecordSize
        return materializeRecords(table.count) { id ->
            val base = table.offset + id * stride
            DescriptionRecord(
                text = codec?.let {
                    val pointer = rom.gbaPointer(base + expansion.descriptionPointerOffset)
                        ?: error("invalid expansion description pointer")
                    decodeTerminated(
                        rom,
                        pointer,
                        MAX_DESCRIPTION_BYTES,
                        it,
                    )
                },
                height = rom.u16le(base + expansion.heightOffset),
                weight = rom.u16le(base + expansion.weightOffset),
                category = codec?.let {
                    decodeTerminated(
                        rom,
                        base + expansion.categoryOffset,
                        expansion.speciesNameOffset - expansion.categoryOffset,
                        it,
                    )
                },
            ).requireTextWhen(codec != null)
        }
    }

    fun evolutions(
        rom: RomImage,
        layout: ResolvedRomLayout,
        validSpeciesIds: Set<Int>? = null,
    ): Map<Int, List<EvolutionEdge>> = evolutionsWithEvidence(
        rom,
        layout,
        validSpeciesIds,
    ).records

    internal fun evolutionsWithEvidence(
        rom: RomImage,
        layout: ResolvedRomLayout,
        validSpeciesIds: Set<Int>? = null,
    ): RecordMaterialization<List<EvolutionEdge>> {
        val table = layout.tables.evolutions ?: return emptyMaterialization()
        if (table.count <= 0) return emptyMaterialization()
        if (layout.generation < 3) {
            return relationshipsWithEvidence(rom, layout).evolutions
        }
        if (layout.pokeemeraldExpansion != null) {
            val stride = table.stride ?: layout.pokeemeraldExpansion.speciesRecordSize
            val elementSize = table.elementSize ?: layout.pokeemeraldExpansion.evolutionRecordSize
                ?: return emptyMaterialization()
            if (elementSize !in setOf(6, 8, 12)) return emptyMaterialization()
            return materializeRecords(table.count) { speciesId ->
                val pointer = rom.gbaPointer(table.offset + speciesId * stride)
                    ?: error("invalid expansion evolution pointer")
                val decoded = readExpansionEvolutions(rom, pointer, elementSize)
                    ?: error("unterminated expansion evolution row")
                validSpeciesIds?.let { valid ->
                    decoded.filter { edge -> edge.targetSpeciesId in valid }
                } ?: decoded
            }
        }
        return typedEvolutions(layout, validSpeciesIds)
    }

    fun learnsets(
        rom: RomImage,
        layout: ResolvedRomLayout,
    ): Map<Int, List<LearnsetEntry>> = learnsetsWithEvidence(rom, layout).records

    internal fun learnsetsWithEvidence(
        rom: RomImage,
        layout: ResolvedRomLayout,
    ): RecordMaterialization<List<LearnsetEntry>> {
        val table = layout.tables.learnsets ?: return emptyMaterialization()
        if (table.count <= 0) return emptyMaterialization()
        if (layout.generation < 3) {
            return relationshipsWithEvidence(rom, layout).learnsets
        }
        if (layout.pokeemeraldExpansion != null) {
            val stride = table.stride ?: layout.pokeemeraldExpansion.speciesRecordSize
            return materializeRecords(table.count) { speciesId ->
                val pointer = rom.gbaPointer(table.offset + speciesId * stride)
                    ?: error("invalid expansion learnset pointer")
                readExpansionLearnset(rom, pointer)
                    ?: error("unterminated expansion learnset row")
            }
        }
        return typedLearnsets(layout)
    }

    internal fun relationshipsWithEvidence(
        rom: RomImage,
        layout: ResolvedRomLayout,
        validSpeciesIds: Set<Int>? = null,
    ): RelationshipMaterialization {
        if (layout.generation < 3) {
            val table = layout.tables.evolutions
                ?: layout.tables.learnsets
                ?: return emptyRelationships()
            return gen12Relationships(rom, layout, table)
        }
        return RelationshipMaterialization(
            evolutions = evolutionsWithEvidence(rom, layout, validSpeciesIds),
            learnsets = learnsetsWithEvidence(rom, layout),
        )
    }

    private fun typedDescriptions(
        layout: ResolvedRomLayout,
        includeText: Boolean,
    ): RecordMaterialization<DescriptionRecord> {
        val decodedRecords = layout.resolvedDatasets.descriptions?.catalogDescriptions()
            ?: return emptyMaterialization()
        val records = if (includeText) {
            decodedRecords
        } else {
            decodedRecords.mapValues { (_, record) -> record.copy(text = null, category = null) }
        }
        val resolved = requireNotNull(layout.resolvedDatasets.descriptions)
        val failures = resolved.rows.mapNotNull { row ->
            when (row) {
                is DescriptionRowOutcome.Decoded -> null
                is DescriptionRowOutcome.StructuralEmpty ->
                    row.rowIndex to "description row is structurally empty"
                is DescriptionRowOutcome.Malformed ->
                    row.rowIndex to boundedReason(row.reasons)
            }
        }.toMap()
        return RecordMaterialization(records, failures)
    }

    private fun typedEvolutions(
        layout: ResolvedRomLayout,
        validSpeciesIds: Set<Int>?,
    ): RecordMaterialization<List<EvolutionEdge>> {
        val projected = layout.resolvedDatasets.evolutions?.catalogEvolutions()
            ?: return emptyMaterialization()
        val resolved = requireNotNull(layout.resolvedDatasets.evolutions)
        val records = validSpeciesIds?.let { valid ->
            projected.mapValues { (_, edges) ->
                edges.filter { edge -> edge.targetSpeciesId in valid }
            }
        } ?: projected
        val failures = resolved.rows.mapNotNull { row ->
            (row as? EvolutionRowOutcome.Malformed)?.let {
                row.rowIndex to boundedReason(row.reasons)
            }
        }.toMap()
        return RecordMaterialization(records, failures)
    }

    private fun typedLearnsets(
        layout: ResolvedRomLayout,
    ): RecordMaterialization<List<LearnsetEntry>> {
        val records = layout.resolvedDatasets.learnsets?.catalogPrimaryEntries()
            ?: return emptyMaterialization()
        val resolved = requireNotNull(layout.resolvedDatasets.learnsets)
        val rows = resolved.primary?.layout?.rows ?: return emptyMaterialization()
        val failures = rows.mapNotNull { row ->
            (row as? LearnsetRowOutcome.Malformed)?.let {
                row.rowIndex to boundedReason(row.reasons)
            }
        }.toMap()
        return RecordMaterialization(records, failures)
    }

    private fun readExpansionLearnset(
        rom: RomImage,
        offset: Int,
    ): List<LearnsetEntry>? {
        val entries = mutableListOf<LearnsetEntry>()
        var cursor = offset
        repeat(MAX_EXPANSION_LEARNSET_ENTRIES) {
            if (!rom.contains(cursor, 2)) return null
            val move = rom.u16le(cursor)
            if (move == EXPANSION_TERMINATOR) return entries
            if (!rom.contains(cursor, 4)) return null
            entries += LearnsetEntry(
                level = rom.u16le(cursor + 2),
                moveId = move,
            )
            cursor += 4
        }
        return null
    }

    private fun readExpansionEvolutions(
        rom: RomImage,
        offset: Int,
        recordSize: Int,
    ): List<EvolutionEdge>? {
        val edges = mutableListOf<EvolutionEdge>()
        var cursor = offset
        repeat(MAX_EXPANSION_EVOLUTION_ENTRIES) {
            if (!rom.contains(cursor, 2)) return null
            val method = rom.u16le(cursor)
            if (method == EXPANSION_TERMINATOR) return edges
            if (!rom.contains(cursor, recordSize) || method !in 0..1024) return null
            edges += EvolutionEdge(
                targetSpeciesId = rom.u16le(cursor + 4),
                methodId = method,
                parameter = rom.u16le(cursor + 2),
                raw = rom.slice(cursor, recordSize),
            )
            cursor += recordSize
        }
        return null
    }

    private fun gen12Descriptions(
        rom: RomImage,
        layout: ResolvedRomLayout,
        codec: PokemonTextCodec,
    ): RecordMaterialization<DescriptionRecord> {
        val table = layout.tables.descriptions ?: return emptyMaterialization()
        table.gbDescriptions?.let { inline ->
            val entries = GbInlineDescriptions.entries(rom, inline)
            val records = linkedMapOf<Int, DescriptionRecord>()
            val failures = linkedMapOf<Int, String>()
            if (entries.size != table.count) return emptyMaterialization()
            entries.forEachIndexed { index, entry ->
                val row = entry?.let { GbInlineDescriptions.decode(rom, it, codec) }
                if (row == null) failures[index + 1] = "native inline description is malformed"
                else records[index + 1] = DescriptionRecord(row.text, row.height, row.weight, row.category)
            }
            return RecordMaterialization(records, failures)
        }
        return materializeRecords(table.count, firstId = 1) { id ->
            val index = id - 1
            val pointerOffset = table.offset + index * table.recordSize
            require(rom.contains(pointerOffset, 2)) { "description pointer exceeds ROM" }
            val bank = if (layout.generation == 1) {
                table.bank
            } else {
                table.banks.getOrNull(index / 64)
            }
            val entry = bank?.let {
                rom.gbBankAddress(it, rom.u16le(pointerOffset))
            } ?: error("invalid description banked pointer")
            val categoryLength = terminatedLength(
                rom,
                entry,
                MAX_CATEGORY_BYTES,
                codec.terminator,
            ) ?: error("description category is not terminated")
            val category = codec.decode(rom.slice(entry, categoryLength))
            val metadata = entry + categoryLength
            if (layout.generation == 1) {
                require(rom.contains(metadata + 5, 3)) { "description metadata exceeds ROM" }
                val text = rom.gbBankAddress(
                    rom.u8(metadata + 7),
                    rom.u16le(metadata + 5),
                ) ?: error("invalid far-text pointer")
                val description = Gen1DescriptionTextCodec.decode(
                    rom,
                    text,
                    MAX_DESCRIPTION_BYTES,
                    codec,
                ) ?: error("Gen I description is not terminated")
                DescriptionRecord(description, category = category).requireText()
            } else {
                require(rom.contains(metadata, 4)) { "description metadata exceeds ROM" }
                DescriptionRecord(
                    text = decodeTerminated(
                        rom,
                        metadata + 4,
                        MAX_DESCRIPTION_BYTES,
                        codec,
                    ),
                    height = rom.u16le(metadata),
                    weight = rom.u16le(metadata + 2),
                    category = category,
                ).requireText()
            }
        }
    }

    private fun gen12Relationships(
        rom: RomImage,
        layout: ResolvedRomLayout,
        table: TableLayout,
    ): RelationshipMaterialization {
        val evolutions = linkedMapOf<Int, List<EvolutionEdge>>()
        val evolutionFailures = linkedMapOf<Int, String>()
        val learnsets = linkedMapOf<Int, List<LearnsetEntry>>()
        val learnsetFailures = linkedMapOf<Int, String>()
        val bank = table.bank
        repeat(table.count.coerceAtLeast(0)) { index ->
            val speciesId = index + 1
            val pointerOffset = table.offset + index * table.recordSize
            val start = runCatching {
                require(bank != null) { "combined relationship bank is unavailable" }
                require(rom.contains(pointerOffset, 2)) { "relationship pointer exceeds ROM" }
                rom.gbBankAddress(bank, rom.u16le(pointerOffset))
                    ?: error("invalid combined relationship pointer")
            }.getOrElse { failure ->
                val reason = failureReason("combined relationship row", failure)
                evolutionFailures[speciesId] = reason
                learnsetFailures[speciesId] = reason
                return@repeat
            }
            val evolution = decodeGen12EvolutionSection(
                rom,
                start,
                layout.generation,
            )
            if (evolution.edges != null) {
                evolutions[speciesId] = evolution.edges
            } else {
                evolutionFailures[speciesId] = requireNotNull(evolution.failure)
            }
            val learnsetOffset = evolution.learnsetOffset
            if (learnsetOffset == null) {
                learnsetFailures[speciesId] = "learnset boundary was not resolved"
                return@repeat
            }
            val learnset = decodeGen12Learnset(rom, learnsetOffset)
            if (learnset.entries != null) {
                learnsets[speciesId] = learnset.entries
            } else {
                learnsetFailures[speciesId] = requireNotNull(learnset.failure)
            }
        }
        return RelationshipMaterialization(
            RecordMaterialization(evolutions, evolutionFailures),
            RecordMaterialization(learnsets, learnsetFailures),
        )
    }

    private fun decodeGen12EvolutionSection(
        rom: RomImage,
        start: Int,
        generation: Int,
    ): Gen12EvolutionSection {
        val edges = mutableListOf<EvolutionEdge>()
        var cursor = start
        repeat(MAX_GEN12_EVOLUTION_ENTRIES) {
            if (!rom.contains(cursor, 1)) {
                return Gen12EvolutionSection(null, null, "evolution row reaches EOF")
            }
            val method = rom.u8(cursor)
            if (method == 0) {
                return Gen12EvolutionSection(edges, cursor + 1, null)
            }
            val width = evolutionWidth(generation, method)
            if (width == null) {
                return Gen12EvolutionSection(
                    edges = null,
                    learnsetOffset = null,
                    failure = "evolution row contains an unsupported method",
                )
            }
            if (!rom.contains(cursor, width)) {
                return Gen12EvolutionSection(null, null, "evolution record reaches EOF")
            }
            val raw = rom.slice(cursor, width)
            edges += EvolutionEdge(
                targetSpeciesId = raw.last().toInt() and 0xFF,
                methodId = method,
                parameter = raw[1].toInt() and 0xFF,
                raw = raw,
            )
            cursor += width
        }
        return if (rom.contains(cursor, 1) && rom.u8(cursor) == 0) {
            Gen12EvolutionSection(edges, cursor + 1, null)
        } else {
            Gen12EvolutionSection(null, null, "evolution row exceeds the entry budget")
        }
    }

    private fun decodeGen12Learnset(
        rom: RomImage,
        start: Int,
    ): Gen12LearnsetSection {
        val entries = mutableListOf<LearnsetEntry>()
        var cursor = start
        repeat(MAX_GEN12_LEARNSET_ENTRIES) {
            if (!rom.contains(cursor, 1)) {
                return Gen12LearnsetSection(null, "learnset row reaches EOF")
            }
            val level = rom.u8(cursor)
            if (level == 0) return Gen12LearnsetSection(entries, null)
            if (!rom.contains(cursor, 2)) {
                return Gen12LearnsetSection(null, "learnset entry reaches EOF")
            }
            val move = rom.u8(cursor + 1)
            // Gen I/II consumers use move 0 as NO_MOVE, matching empty party move slots.
            // It is a no-op relationship, not a catalog move identity.
            if (move != 0) entries += LearnsetEntry(level, move)
            cursor += 2
        }
        return if (rom.contains(cursor, 1) && rom.u8(cursor) == 0) {
            Gen12LearnsetSection(entries, null)
        } else {
            Gen12LearnsetSection(null, "learnset row exceeds the entry budget")
        }
    }

    private fun evolutionWidth(generation: Int, method: Int): Int? = when (generation) {
        1 -> when (method) {
            1, 3 -> 3
            2 -> 4
            else -> null
        }
        2 -> when (method) {
            in 1..4 -> 3
            5 -> 4
            else -> null
        }
        else -> null
    }

    private fun decodeTerminated(
        rom: RomImage,
        offset: Int,
        maximumLength: Int,
        codec: PokemonTextCodec,
    ): String {
        val length = terminatedLength(
            rom,
            offset,
            maximumLength,
            codec.terminator,
        ) ?: error("text is not explicitly terminated within its bound")
        return codec.decode(rom.slice(offset, length))
    }

    private fun terminatedLength(
        rom: RomImage,
        offset: Int,
        maximumLength: Int,
        terminator: Int,
    ): Int? {
        if (maximumLength <= 0 || offset !in 0 until rom.size) return null
        val length = minOf(maximumLength, rom.size - offset)
        return (0 until length).firstOrNull {
            rom.u8(offset + it) == terminator
        }?.plus(1)
    }

    private fun DescriptionRecord.requireText(): DescriptionRecord = requireTextWhen(true)

    private fun DescriptionRecord.requireTextWhen(required: Boolean): DescriptionRecord = also {
        require(!required || !text.isNullOrBlank()) { "description text is blank" }
    }

    private fun RomImage.contains(offset: Int, length: Int): Boolean =
        offset >= 0 && length >= 0 && offset.toLong() + length <= size.toLong()

    private fun <T> materializeRecords(
        count: Int,
        firstId: Int = 0,
        decoder: (Int) -> T,
    ): RecordMaterialization<T> {
        val records = linkedMapOf<Int, T>()
        val failures = linkedMapOf<Int, String>()
        repeat(count.coerceAtLeast(0)) { index ->
            val id = firstId + index
            runCatching { decoder(id) }
                .onSuccess { records[id] = it }
                .onFailure { failure ->
                    failures[id] = failureReason("optional record", failure)
                }
        }
        return RecordMaterialization(records, failures)
    }

    private fun boundedReason(reasons: Collection<String>): String = reasons
        .asSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .take(MAX_FAILURE_REASONS)
        .joinToString("; ")
        .take(MAX_FAILURE_REASON_CHARS)
        .ifBlank { "optional record is malformed" }

    private fun failureReason(label: String, failure: Throwable): String =
        "$label failed (${failure.javaClass.simpleName})"

    private fun <T> emptyMaterialization(): RecordMaterialization<T> =
        RecordMaterialization(emptyMap(), emptyMap())

    private fun emptyRelationships() = RelationshipMaterialization(
        emptyMaterialization(),
        emptyMaterialization(),
    )

    private data class Gen12EvolutionSection(
        val edges: List<EvolutionEdge>?,
        val learnsetOffset: Int?,
        val failure: String?,
    )

    private data class Gen12LearnsetSection(
        val entries: List<LearnsetEntry>?,
        val failure: String?,
    )

    private const val MAX_DESCRIPTION_BYTES = 512
    private const val MAX_CATEGORY_BYTES = 24
    private const val MAX_EXPANSION_LEARNSET_ENTRIES = 256
    private const val MAX_EXPANSION_EVOLUTION_ENTRIES = 32
    private const val MAX_GEN12_EVOLUTION_ENTRIES = 16
    private const val MAX_GEN12_LEARNSET_ENTRIES = 128
    private const val MAX_FAILURE_REASONS = 2
    private const val MAX_FAILURE_REASON_CHARS = 160
    private const val EXPANSION_TERMINATOR = 0xFFFF
}
