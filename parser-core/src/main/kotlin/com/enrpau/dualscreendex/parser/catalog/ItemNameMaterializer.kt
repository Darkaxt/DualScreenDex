package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.language.defaultTextCodec
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import com.enrpau.dualscreendex.parser.model.GbaItemRootNomination
import com.enrpau.dualscreendex.parser.parse.Gen3CompiledItemNameResolver

/** One catalog-session producer; only the exact resolved projection may interpret native name bytes. */
class ItemNameMaterializer(private val session: RomAnalysisSession) {
    private val resolver = Gen3CompiledItemNameResolver(session)

    @Synchronized
    fun materialize(layout: ResolvedRomLayout, referencedIds: Set<Int>): Map<Int, CatalogField<String>> {
        session.cancellation.throwIfCancellationRequested()
        fun unavailable(reason: String): Map<Int, CatalogField<String>> = referencedIds.associateWith { CatalogField.notFound(reason) }
        if (referencedIds.isEmpty()) return emptyMap()
        if (layout.generation != 3 || layout.platform != Platform.GBA) return unavailable("compiled item-name ABI unavailable")
        val codec = layout.defaultTextCodec() ?: return unavailable("exact item-name projection unavailable")
        if (codec.terminator != 0xFF) return unavailable("item copier terminator disagrees with projection")
        val root = when (val nomination = layout.itemRootNomination) {
            GbaItemRootNomination.Absent -> return unavailable("original published item nomination absent")
            GbaItemRootNomination.Ambiguous -> return unavailable("original published item nomination ambiguous")
            is GbaItemRootNomination.Nominated -> nomination.offset
        }
        val proof = resolver.resolve(root)
        val table = proof.table ?: return unavailable(proof.reason)
        return referencedIds.sorted().associateWith { id ->
            session.cancellation.throwIfCancellationRequested()
            when {
                id !in 0..0xFFFF || id >= table.count -> CatalogField.notFound("item ID outside compiled u16 domain")
                id in table.excludedIds -> CatalogField.notFound("compiled item name requires unproved dynamic state")
                else -> {
                    val text = codec.decodeDetailed(session.rom, table.root + table.stride * id, table.nameBytes, session.cancellation)
                    if (text.terminated && text.invalidUnits == 0 && text.controlUnits == 0 && text.substitutionUnits == 0 && text.text.isNotBlank()) {
                        CatalogField.available(text.text)
                    } else CatalogField.notFound("item name lacks exact tokens and termination before numeric field")
                }
            }
        }
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
