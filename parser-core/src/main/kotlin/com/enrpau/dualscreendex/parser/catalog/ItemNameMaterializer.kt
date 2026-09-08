package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.language.defaultTextCodec
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import com.enrpau.dualscreendex.parser.model.GbaItemNameAuthority
import com.enrpau.dualscreendex.parser.text.PokemonTextToken
import com.enrpau.dualscreendex.parser.text.StaticLabelUse

/** One catalog-session producer; only the exact resolved projection may interpret native name bytes. */
class ItemNameMaterializer(private val session: RomAnalysisSession) {
    @Synchronized
    fun materialize(layout: ResolvedRomLayout, referencedIds: Set<Int>): Map<Int, CatalogField<String>> {
        session.cancellation.throwIfCancellationRequested()
        fun unavailable(reason: String): Map<Int, CatalogField<String>> = referencedIds.associateWith { CatalogField.notFound(reason) }
        if (referencedIds.isEmpty()) return emptyMap()
        if (layout.generation == 1 && layout.platform in setOf(Platform.GB, Platform.GBC)) {
            return materializeGenOne(layout, referencedIds)
        }
        if (layout.generation == 2 && layout.platform in setOf(Platform.GB, Platform.GBC)) {
            return materializeGenTwo(layout, referencedIds)
        }
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
                table.excludedId != null && id == table.excludedId -> CatalogField.notFound("compiled item name requires unproved dynamic state")
                else -> {
                    val text = codec.decodeDetailed(session.rom, table.root + table.stride * id, table.nameBytes, session.cancellation)
                    if (text.terminated && text.invalidUnits == 0 && text.controlUnits == 0 && text.substitutionUnits == 0 && text.text.isNotBlank()) {
                        CatalogField.available(text.text)
                    } else CatalogField.notFound("item name lacks exact tokens and termination before numeric field")
                }
            }
        }.also { session.cancellation.throwIfCancellationRequested() }
    }

    private fun materializeGenOne(layout: ResolvedRomLayout, requested: Set<Int>): Map<Int, CatalogField<String>> {
        val result = requested.associateWith { CatalogField.notFound<String>("original consumer or current structural reference unavailable") }.toMutableMap()
        val authority = session.gen1ItemNameAuthority as? com.enrpau.dualscreendex.parser.model.GbItemNameAuthority.Available
            ?: return result
        val codec = layout.defaultTextCodec() ?: return result
        if (!codec.supports(1, layout.platform) || codec.terminator != authority.terminator) return result
        val authorized = session.gen1ItemReferences.mapNotNull { reference ->
            session.cancellation.throwIfCancellationRequested()
            reference.itemId.takeIf { it in 1..255 && it in requested }
        }.toSet()
        if (authorized.isEmpty()) return result
        fun decode(bytes: ByteArray): CatalogField<String> {
            val text = codec.decodeDetailed(com.enrpau.dualscreendex.parser.io.RomImage(bytes), 0, bytes.size, session.cancellation)
            return if (text.terminated && text.invalidUnits == 0 && text.controlUnits == 0 &&
                text.substitutionUnits == 0 && text.text.isNotBlank()) CatalogField.available(text.text)
            else CatalogField.notFound("item name lacks exact tokens and bounded termination")
        }

        // A single shared walk; traversed but unrequested ordinals gain no semantic authority.
        // The buffer is also the copy source, so no packed byte is read twice from the ROM.
        val ordinary = authorized.filter { it < authority.machineThreshold }.toSet()
        if (ordinary.isNotEmpty()) {
            val capacity = minOf(4096L, session.limits.maxDatasetExtentBytes,
                (authority.bankEnd - authority.root).toLong()).coerceAtLeast(0).toInt()
            val packed = ByteArray(capacity)
            var loaded = 0
            fun ensure(end: Int): Boolean {
                if (end !in 0..capacity) return false
                while (loaded < end) {
                    session.cancellation.throwIfCancellationRequested()
                    packed[loaded] = session.rom.u8(authority.root + loaded).toByte()
                    loaded++
                }
                return true
            }
            var cursor = 0
            for (id in 1..ordinary.max()) {
                val start = cursor
                var terminated = false
                while (ensure(cursor + 1)) {
                    if ((packed[cursor++].toInt() and 255) == authority.terminator) { terminated = true; break }
                }
                if (!terminated) break
                if (id in ordinary && cursor - start <= authority.copyBytes && ensure(start + authority.copyBytes)) {
                    result[id] = decode(packed.copyOfRange(start, start + authority.copyBytes))
                }
            }
        }

        // Generated labels use an evaluator-owned output, never ambient RAM or a global item domain.
        val prefixes = mutableMapOf<Boolean, ByteArray?>()
        for (id in authorized.filter { it >= authority.machineThreshold }.sorted()) {
            session.cancellation.throwIfCancellationRequested()
            val lower = id < authority.machineSplit
            if (!prefixes.containsKey(lower)) prefixes[lower] = run {
                val start = if (lower) authority.lowerPrefix else authority.upperPrefix
                val count = if (lower) authority.lowerPrefixBytes else authority.upperPrefixBytes
                if (count.toLong() + 3 > session.limits.maxDatasetExtentBytes) null
                else ByteArray(count) { index ->
                    session.cancellation.throwIfCancellationRequested()
                    session.rom.u8(start + index).toByte()
                }.takeUnless { bytes -> bytes.any { (it.toInt() and 255) == authority.terminator } }
            }
            val prefix = prefixes[lower] ?: continue
            val number = ((id + if (lower) authority.lowerAdjustment else 0) and 255) - authority.numberSubtract
            if (number !in 0..99) continue
            val output = prefix + byteArrayOf((authority.digitOrigin + number / 10).toByte(),
                (authority.digitOrigin + number % 10).toByte(), authority.terminator.toByte())
            result[id] = decode(output)
        }
        session.cancellation.throwIfCancellationRequested()
        return result
    }

    private fun materializeGenTwo(layout: ResolvedRomLayout, requested: Set<Int>): Map<Int, CatalogField<String>> {
        val result = requested.associateWith { CatalogField.notFound<String>("original consumer or current structural reference unavailable") }.toMutableMap()
        val authority = session.gen2ItemNameAuthority as? com.enrpau.dualscreendex.parser.model.Gen2ItemNameAuthority.Available
            ?: return result
        val codec = layout.defaultTextCodec() ?: return result
        if (!codec.supports(2, layout.platform) || codec.terminator != authority.terminator) return result
        val authorized = session.gen2ItemReferences.mapNotNull { reference ->
            session.cancellation.throwIfCancellationRequested()
            reference.itemId.takeIf { it in 1..255 && it in requested &&
                reference.mapGroupTable != null && reference.mapGroupBank != null && reference.mapHeader != null }
        }.toSet()
        if (authorized.isEmpty()) return result
        fun decode(bytes: ByteArray, use: StaticLabelUse? = null): CatalogField<String> {
            val image = com.enrpau.dualscreendex.parser.io.RomImage(bytes)
            val label = use?.let { codec.decodeStaticLabel(image, 0, bytes.size, session.cancellation, it) }
            val text = label?.decoded ?: codec.decodeDetailed(image, 0, bytes.size, session.cancellation)
            val unratified = label?.unratifiedSubstitutionUnits ?: text.substitutionUnits
            return if (text.terminated && text.invalidUnits == 0 && text.controlUnits == 0 &&
                unratified == 0 && text.text.isNotBlank()) CatalogField.available(text.text)
            else CatalogField.notFound("item name lacks exact tokens and bounded termination")
        }

        // Walk each packed byte once, including the compiled full-copy lookahead. Unrequested rows
        // are only delimiters; their undecodable tokens do not invalidate a later requested row.
        val ordinary = authorized.filter { it < authority.machineThreshold }.toSet()
        if (ordinary.isNotEmpty()) {
            val capacity = minOf(4096L, session.limits.maxDatasetExtentBytes,
                (authority.bankEnd - authority.root).toLong()).coerceAtLeast(0).toInt()
            val packed = ByteArray(capacity)
            var loaded = 0
            fun ensure(end: Int): Boolean {
                if (end !in 0..capacity) return false
                while (loaded < end) {
                    session.cancellation.throwIfCancellationRequested()
                    val at = authority.root + loaded
                    if (at in authority.codeOffsets) return false
                    packed[loaded++] = session.rom.u8(at).toByte()
                }
                return true
            }
            var cursor = 0
            for (id in 1..ordinary.max()) {
                val start = cursor
                var terminated = false
                while (ensure(cursor + 1)) {
                    if ((packed[cursor++].toInt() and 255) == authority.terminator) { terminated = true; break }
                }
                if (!terminated) break
                if (id in ordinary && cursor - start <= authority.copyBytes && ensure(start + authority.copyBytes)) {
                    result[id] = decode(packed.copyOfRange(start, start + authority.copyBytes), StaticLabelUse.GEN2_ORDINARY_ITEM_NAME)
                }
            }
        }

        // Gen II owns separate TM-first/HM-later classification and two skipped-ID adjustments.
        // Hole IDs are not rejected or invented here: only the current typed references authorize IDs.
        val prefixes = mutableMapOf<Boolean, ByteArray?>()
        for (id in authorized.filter { it >= authority.machineThreshold }.sorted()) {
            session.cancellation.throwIfCancellationRequested()
            val tm = id < authority.machineSplit
            if (!prefixes.containsKey(tm)) prefixes[tm] = run {
                val start = if (tm) authority.tmPrefix else authority.hmPrefix
                val count = if (tm) authority.tmPrefixBytes else authority.hmPrefixBytes
                if (count.toLong() + 3 > session.limits.maxDatasetExtentBytes) null
                else ByteArray(count) { index ->
                    session.cancellation.throwIfCancellationRequested()
                    session.rom.u8(start + index).toByte()
                }.takeIf { bytes ->
                    // The literal span must be complete before generated digits can become input.
                    session.cancellation.throwIfCancellationRequested()
                    val literal = com.enrpau.dualscreendex.parser.io.RomImage(bytes)
                    var cursor = 0
                    while (cursor < bytes.size) {
                        session.cancellation.throwIfCancellationRequested()
                        val token = codec.decodeToken(literal, cursor, bytes.size)
                        if (token !is PokemonTextToken.Glyph && token !is PokemonTextToken.Whitespace) return@takeIf false
                        cursor += token.byteCount
                    }
                    true
                }
            }
            val prefix = prefixes[tm] ?: continue
            var number = (id - (if (id >= authority.skipFirst) 1 else 0) -
                (if (id >= authority.skipSecond) 1 else 0) - authority.numberSubtract + 1) and 255
            if (!tm) number = (number - authority.hmSubtract) and 255
            if (number !in 0..99) continue
            val output = prefix + byteArrayOf((authority.digitOrigin + number / 10).toByte(),
                (authority.digitOrigin + number % 10).toByte(), authority.terminator.toByte())
            result[id] = decode(output)
        }
        session.cancellation.throwIfCancellationRequested()
        return result
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
