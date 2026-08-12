package com.enrpau.dualscreendex.parser.dataset.encounters

import com.enrpau.dualscreendex.parser.catalog.CatalogField
import com.enrpau.dualscreendex.parser.catalog.EncounterArea
import com.enrpau.dualscreendex.parser.catalog.EncounterSlot
import com.enrpau.dualscreendex.parser.catalog.EncounterWindow
import java.util.Collections

/** Pure projection over already-selected codec rows; it performs no discovery or ABI inference. */
object EncounterMaterializationProjection {
    fun materialize(layout: ResolvedEncounterLayout): List<EncounterArea> {
        val decoded = layout.rows
            .filterIsInstance<EncounterHeaderOutcome.Decoded>()
            .flatMap { row ->
                row.methods.map { method ->
                    EncounterArea(
                        id = groupMapId(row.mapGroup, row.mapNumber) * 10 + method.methodId,
                        name = CatalogField.available(
                            "Map ${row.mapGroup}-${row.mapNumber} - ${methodLabel(method)}",
                        ),
                        methodId = method.methodId,
                        slots = immutableList(
                            method.slots.map { slot ->
                                EncounterSlot(
                                    speciesId = slot.speciesId,
                                    minimumLevel = slot.minimumLevel,
                                    maximumLevel = slot.maximumLevel,
                                    weight = slot.weight,
                                )
                            },
                        ),
                        windows = immutableSet(method.windows.map(::catalogWindow)),
                    )
                }
            }
        val merged = decoded.groupBy(EncounterArea::id).values.map { variants ->
            val first = variants.first()
            first.copy(
                slots = immutableList(variants.flatMap(EncounterArea::slots).distinct()),
                windows = immutableSet(variants.flatMap(EncounterArea::windows)),
            )
        }
        return immutableList(merged)
    }

    private fun methodLabel(method: DecodedEncounterMethod): String = method.environment
        ?.let { environment -> "${method.label} (${environment.name.lowercase()})" }
        ?: method.label

    private fun catalogWindow(window: EncounterTimeWindow): EncounterWindow = when (window) {
        EncounterTimeWindow.ANY -> EncounterWindow.ANY
        EncounterTimeWindow.MORNING -> EncounterWindow.MORNING
        EncounterTimeWindow.DAY -> EncounterWindow.DAY
        EncounterTimeWindow.NIGHT -> EncounterWindow.NIGHT
    }

    private fun groupMapId(group: Int, map: Int): Int = (group shl 8) or map

    private fun <T> immutableList(values: Collection<T>): List<T> =
        Collections.unmodifiableList(values.toList())

    private fun <T> immutableSet(values: Collection<T>): Set<T> =
        Collections.unmodifiableSet(LinkedHashSet(values))
}
