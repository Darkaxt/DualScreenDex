package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.language.defaultTextCodec
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import com.enrpau.dualscreendex.parser.model.GbaItemNameAuthority

/** One catalog-session producer; only the exact resolved projection may interpret native name bytes. */
class ItemNameMaterializer(private val session: RomAnalysisSession) {
    @Synchronized
    fun materialize(layout: ResolvedRomLayout, referencedIds: Set<Int>): Map<Int, CatalogField<String>> {
        session.cancellation.throwIfCancellationRequested()
        fun unavailable(reason: String): Map<Int, CatalogField<String>> = referencedIds.associateWith { CatalogField.notFound(reason) }
        if (referencedIds.isEmpty()) return emptyMap()
        if (layout.generation != 3 || layout.platform != Platform.GBA) return unavailable("compiled item-name ABI unavailable")
        val codec = layout.defaultTextCodec() ?: return unavailable("exact item-name projection unavailable")
        if (codec.terminator != 0xFF) return unavailable("item copier terminator disagrees with projection")
        val table = when (val authority = layout.itemNameAuthority) {
            is GbaItemNameAuthority.Unavailable -> return unavailable(authority.reason)
            is GbaItemNameAuthority.Available -> authority
        }
        return referencedIds.sorted().associateWith { id ->
            session.cancellation.throwIfCancellationRequested()
            when {
                id !in 0..0xFFFF || id >= table.count -> CatalogField.notFound("item ID outside compiled u16 domain")
                id == table.excludedId -> CatalogField.notFound("compiled item name requires unproved dynamic state")
                else -> {
                    val text = codec.decodeDetailed(session.rom, table.root + table.stride * id, table.nameBytes, session.cancellation)
                    if (text.terminated && text.invalidUnits == 0 && text.controlUnits == 0 && text.substitutionUnits == 0 && text.text.isNotBlank()) {
                        CatalogField.available(text.text)
                    } else CatalogField.notFound("item name lacks exact tokens and termination before numeric field")
                }
            }
        }.also { session.cancellation.throwIfCancellationRequested() }
    }

    internal data class Joined(val balls: Map<Int, CaptureBallRecord>, val localMaps: LocalMapCatalog)
    companion object {
        internal fun join(names: Map<Int, CatalogField<String>>, balls: Map<Int, CaptureBallRecord>, localMaps: LocalMapCatalog) = Joined(
            balls.mapValues { (id, ball) -> names[id]?.let { ball.copy(name = it) } ?: ball },
            localMaps.copy(pois = localMaps.pois.map { poi ->
                val id = poi.item?.itemId
                if (id != null && id in names) poi.copy(item = poi.item.copy(displayName = names.getValue(id).value)) else poi
            }),
        )
    }
}
