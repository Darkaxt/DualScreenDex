package com.enrpau.dualscreendex.parser.dataset.evolutions

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.io.RomBoundsException
import com.enrpau.dualscreendex.parser.model.HeaderlessUnifiedSpeciesMetadata
import com.enrpau.dualscreendex.parser.model.ValidationEvidence

/**
 * Resolves expansion-derived evolution lists embedded as a pointer field in each unified species
 * record. The already-proven species root, stride, active predicate, and count are the authority;
 * content can select only a unique pointer field and record ABI within that record.
 */
internal object EmbeddedEvolutionPointerResolver {
    private val supportedRecordSizes = intArrayOf(6, 8, 12)
    private const val maximumEntriesPerSpecies = 64
    private const val maximumMethod = 0x0FFF
    private const val noneMethod = 0xFFFE
    private const val listTerminator = 0xFFFF

    data class Resolution(
        val evidence: ValidationEvidence,
        val resolved: ResolvedEvolutionLayout,
        val pointerFieldOffset: Int,
    )

    private data class Candidate(
        val fieldOffset: Int,
        val recordSize: Int,
        val rows: List<EvolutionRowOutcome>,
        val edgeCount: Int,
    )

    fun resolve(
        session: RomAnalysisSession,
        metadata: HeaderlessUnifiedSpeciesMetadata,
        speciesCount: Int,
    ): Resolution? {
        if (speciesCount <= 1 || metadata.speciesRecordSize < 8) return null
        val candidates = buildList {
            for (fieldOffset in 0..metadata.speciesRecordSize - 4 step 4) {
                for (recordSize in supportedRecordSizes) {
                    decodeCandidate(session, metadata, speciesCount, fieldOffset, recordSize)?.let(::add)
                }
            }
        }
        val selected = candidates.singleOrNull() ?: return null
        val pointerTableOffset = metadata.speciesTableOffset + selected.fieldOffset
        // EvolutionTableLayout remains the stable typed edge ABI descriptor. The selected physical
        // pointer stride is retained in the published TableLayout and evidence below.
        val descriptor = EvolutionTableLayout(
            offset = pointerTableOffset.toLong(),
            count = speciesCount.toLong(),
            slotsPerSpecies = 1,
            recordSize = selected.recordSize,
        )
        return Resolution(
            evidence = ValidationEvidence(
                compatible = true,
                validRecords = speciesCount,
                totalRecords = speciesCount,
                confidence = 1.0,
                reasons = listOf(
                    "decoded every active unified-species evolution pointer through one unique terminated-list ABI",
                ),
                offset = pointerTableOffset,
                recordSize = selected.recordSize,
                elementSize = selected.recordSize,
                coveredRecords = speciesCount,
                expectedRecords = speciesCount,
                incompleteRecords = 0,
            ),
            resolved = ResolvedEvolutionLayout(descriptor, selected.rows),
            pointerFieldOffset = selected.fieldOffset,
        )
    }

    private fun decodeCandidate(
        session: RomAnalysisSession,
        metadata: HeaderlessUnifiedSpeciesMetadata,
        speciesCount: Int,
        fieldOffset: Int,
        recordSize: Int,
    ): Candidate? {
        val rows = ArrayList<EvolutionRowOutcome>(speciesCount)
        var pointerCount = 0
        var edgeCount = 0
        try {
            for (speciesId in 0 until speciesCount) {
                val speciesRecord = metadata.speciesTableOffset + speciesId * metadata.speciesRecordSize
                val active = speciesId != 0 && session.rom.u8(
                    speciesRecord + metadata.activePredicateOffset,
                ) != 0
                if (!active) {
                    rows += EvolutionRowOutcome.StructuralEmpty(speciesId)
                    continue
                }
                val pointerField = speciesRecord + fieldOffset
                val rawPointer = session.rom.u32le(pointerField)
                if (rawPointer == 0L) {
                    rows += EvolutionRowOutcome.StructuralEmpty(speciesId)
                    continue
                }
                val listOffset = session.rom.gbaPointer(pointerField) ?: return null
                pointerCount++
                val edges = ArrayList<EvolutionEdgeValue>()
                var terminated = false
                for (entryIndex in 0 until maximumEntriesPerSpecies) {
                    val entry = listOffset + entryIndex * recordSize
                    val method = session.rom.u16le(entry)
                    if (method == listTerminator) {
                        terminated = true
                        break
                    }
                    if (method == noneMethod) continue
                    if (method > maximumMethod) return null
                    val target = session.rom.u16le(entry + 4)
                    // Expansion consumers sanitize SPECIES_NONE and inactive IDs before using them.
                    if (target != 0) {
                        if (target !in 1 until speciesCount) return null
                        val targetRecord = metadata.speciesTableOffset + target * metadata.speciesRecordSize
                        if (session.rom.u8(targetRecord + metadata.activePredicateOffset) != 0) {
                            edges += EvolutionEdgeValue(
                                targetSpeciesId = target,
                                methodId = method,
                                parameter = session.rom.u16le(entry + 2),
                                conditionValue = if (recordSize >= 8) session.rom.u16le(entry + 6) else null,
                                raw = session.rom.slice(entry, recordSize),
                            )
                        }
                    }
                }
                if (!terminated) return null
                edgeCount += edges.size
                rows += if (edges.isEmpty()) {
                    EvolutionRowOutcome.StructuralEmpty(speciesId)
                } else {
                    EvolutionRowOutcome.Decoded(speciesId, edges)
                }
            }
        } catch (_: RomBoundsException) {
            return null
        } catch (_: ArithmeticException) {
            return null
        }
        if (pointerCount == 0 || edgeCount == 0) return null
        return Candidate(fieldOffset, recordSize, rows, edgeCount)
    }
}
