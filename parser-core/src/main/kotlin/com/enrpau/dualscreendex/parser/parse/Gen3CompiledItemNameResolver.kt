package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ExtentCheck
import com.enrpau.dualscreendex.parser.analysis.GbaReferenceIndex
import com.enrpau.dualscreendex.parser.analysis.GbaTargetReferenceEvidence
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.model.GbaItemNameAuthority
import com.enrpau.dualscreendex.parser.model.GbaItemNameProvenance
import com.enrpau.dualscreendex.parser.model.GbaItemPublishedRoute
import com.enrpau.dualscreendex.parser.model.GbaItemRootNomination

/** Static inline names only. Every accepted instruction path is complete; unsupported ABIs stay absent. */
internal class Gen3CompiledItemNameResolver(private val session: RomAnalysisSession) {
    data class Table(val root: Int, val stride: Int, val count: Int, val nameBytes: Int, val excludedIds: Set<Int>) {
        init { require(excludedIds.size <= 1) }
    }
    data class Result(val table: Table? = null, val reason: String, val scannedBytes: Long = 0, val probeWords: Int = 0)

    private val originalResults = mutableMapOf<GbaItemPublishedRoute, GbaItemNameAuthority>()
    private var originalProof: Result? = null

    /** Composed once in identity/root resolution; defaults and invoked absence never grant discovery. */
    @Synchronized
    fun original(route: GbaItemPublishedRoute): GbaItemNameAuthority {
        session.cancellation.throwIfCancellationRequested()
        originalResults[route]?.let { return it }
        val terminal = when (route) {
            GbaItemPublishedRoute.NotEvaluated -> "original item route not evaluated"
            is GbaItemPublishedRoute.Invoked -> when (route.nomination) {
                GbaItemRootNomination.Absent -> "original published item nomination absent"
                GbaItemRootNomination.Ambiguous -> "original published item nomination ambiguous"
                is GbaItemRootNomination.Nominated -> null
            }
            GbaItemPublishedRoute.NotInvoked -> null
        }
        val authority = if (terminal != null) GbaItemNameAuthority.Unavailable(terminal) else {
            val proof = originalProof ?: proveOriginal().also {
                session.cancellation.throwIfCancellationRequested()
                originalProof = it.copy(scannedBytes = scannedBytes, probeWords = probeWords)
            }
            val table = proof.table
            val published = (route as? GbaItemPublishedRoute.Invoked)?.nomination as? GbaItemRootNomination.Nominated
            when {
                table == null -> GbaItemNameAuthority.Unavailable(proof.reason)
                published != null && published.offset != table.root ->
                    GbaItemNameAuthority.Unavailable("compiled item authority conflicts with original published root")
                else -> GbaItemNameAuthority.Available(table.root, table.stride, table.count, table.nameBytes,
                    table.excludedIds.singleOrNull(), if (published == null) GbaItemNameProvenance.COMPILED_CONSUMER
                    else GbaItemNameProvenance.PUBLISHED_ROOT)
            }
        }
        session.cancellation.throwIfCancellationRequested()
        originalResults[route] = authority
        return authority
    }

    private fun proveOriginal(): Result {
        if (!scan(rom.size)) return unavailable("original item reference nomination budget")
        val index = session.gbaReferenceIndex ?: return unavailable("original item references unavailable")
        if (index.overflowed) return unavailable("original item reference inventory overflow")
        val hints = index.itemConsumerHints ?: return unavailable("original item candidate hints incomplete")
        if (!hints.complete || hints.observedSites > minOf(64, session.limits.maxCandidatesPerDataset)) {
            return unavailable("original item candidate inventory overflow")
        }
        if (hints.sites.isEmpty()) return unavailable("no original compiled item candidates")
        val roots = linkedSetOf<Int>()
        val targets = linkedSetOf<Int>()
        for (site in hints.sites) {
            session.cancellation.throwIfCancellationRequested()
            val root = literal(site) ?: return unavailable("incomplete original item candidate literal")
            val getter = pointerGetter(site, root) ?: return unavailable("incomplete original item getter candidate")
            roots += root
            targets += getter.entry
        }
        if (roots.size > minOf(8, session.limits.maxProbeRootsPerDataset)) return unavailable("original item root budget")
        var siteCount = 0L
        val evidence = linkedMapOf<Int, GbaTargetReferenceEvidence>()
        for (root in roots) {
            for (field in 1..MAX_FIELD_NOMINATION) {
                session.cancellation.throwIfCancellationRequested()
                if (index.referenceCount(root + field) > 0) return unavailable("unreconciled original item field-root witness")
            }
            val references = index.target(root) ?: return unavailable("missing original item root inventory")
            siteCount += references.count
            evidence[root] = references
        }
        if (siteCount > minOf(128, session.limits.maxNominatedGbaReferenceSites)) return unavailable("aggregate original item site budget")
        if (evidence.values.any { !it.siteEvidenceAvailable }) {
            if (evidence.values.any { !it.siteEvidenceAvailable && it.instructionSites.isNotEmpty() }) {
                return unavailable("partial original item root inventory")
            }
            val recovered = recoverOriginalSites(evidence) ?: return unavailable("original item batch recovery unavailable")
            evidence.putAll(recovered)
        }
        val calls = callers(targets) ?: return unavailable("original item caller inventory unavailable")
        val tables = mutableListOf<Table>()
        for (root in roots) {
            session.cancellation.throwIfCancellationRequested()
            val result = prove(root, index, evidence.getValue(root), calls)
            tables += result.table ?: return result // unsupported witnesses never vanish via mapNotNull
        }
        if (tables.size != 1) return unavailable("competing complete original item roots")
        return Result(tables.single(), "complete original compiled item authority")
    }

    /** One bounded target-set recovery, shared by every original root and family probe. */
    private fun recoverOriginalSites(expected: Map<Int, GbaTargetReferenceEvidence>): Map<Int, GbaTargetReferenceEvidence>? {
        if (!scan(rom.size)) return null
        val sites = expected.keys.associateWith { mutableListOf<Int>() }
        val observed = expected.keys.associateWith { 0 }.toMutableMap()
        var total = 0
        for (at in 0..rom.size - 2 step 2) {
            if (at and 4095 == 0) session.cancellation.throwIfCancellationRequested()
            val op = rom.u16le(at)
            if (op and 0xF800 != 0x4800) continue
            val slot = ((at + 4) and -4).toLong() + (op and 255) * 4L
            if (slot !in 0..rom.size.toLong() - 4) continue
            val root = rom.gbaPointer(slot.toInt()) ?: continue
            val list = sites[root] ?: continue
            observed[root] = observed.getValue(root) + 1
            total++
            if (total <= minOf(128, session.limits.maxNominatedGbaReferenceSites)) list += at
        }
        if (total > minOf(128, session.limits.maxNominatedGbaReferenceSites) ||
            expected.any { (root, value) -> observed.getValue(root) != value.count }) return null
        return expected.mapValues { (root, value) -> GbaTargetReferenceEvidence(value.count,
            sites.getValue(root), value.count, session.limits.maxNominatedGbaReferenceSites, null) }
    }

    private val results = mutableMapOf<Int, Result>()
    private val rom = session.rom
    private var scannedBytes = 0L
    private var probeWords = 0
    private var exhausted = false

    @Synchronized
    fun resolve(root: Int): Result {
        session.cancellation.throwIfCancellationRequested()
        results[root]?.let { return it }
        val result = prove(root)
        session.cancellation.throwIfCancellationRequested()
        return result.copy(scannedBytes = scannedBytes, probeWords = probeWords).also { results[root] = it }
    }

    private fun unavailable(reason: String) = Result(reason = if (exhausted) "item consumer work budget exhausted" else reason)

    private fun prove(root: Int): Result {
        if (root !in 0 until rom.size) return unavailable("item root outside ROM")
        // Account for the shared index nomination pass even when another family phase built it first.
        if (!scan(rom.size)) return unavailable("reference nomination scan budget")
        val index = session.gbaReferenceIndex ?: return unavailable("compiled item references unavailable")
        if (index.overflowed) return unavailable("compiled item reference inventory overflow")
        if ((1..MAX_FIELD_NOMINATION).any { index.referenceCount(root + it) > 0 }) {
            return unavailable("unreconciled field-root item witnesses")
        }
        val nominated = index.target(root) ?: return unavailable("no compiled item-root references")
        val references = if (nominated.siteEvidenceAvailable) nominated else {
            if (nominated.instructionSites.isNotEmpty() || nominated.count > session.limits.maxNominatedGbaReferenceSites ||
                !scan(rom.size)) return unavailable("incomplete item reference inventory")
            session.nominatedGbaReferenceSites(root) ?: return unavailable("item reference recovery unavailable")
        }
        return prove(root, index, references, null)
    }

    private fun prove(root: Int, index: GbaReferenceIndex, references: GbaTargetReferenceEvidence,
                      batchCalls: Map<Int, List<Int>>?): Result {
        if (root !in 0 until rom.size || index.overflowed) return unavailable("invalid item root inventory")
        val sites = references.instructionSites
        if (!references.siteEvidenceAvailable || references.siteBudgetExceeded || sites.isEmpty() ||
            sites.size != references.count || sites.distinct().size != sites.size ||
            references.observedSites != references.count || sites.size > session.limits.maxNominatedGbaReferenceSites) {
            return unavailable("incomplete item reference inventory")
        }
        val getters = mutableListOf<Getter>()
        for (site in sites) {
            session.cancellation.throwIfCancellationRequested()
            if (literal(site) != root) return unavailable("item reference site does not load nominated root")
            val getter = pointerGetter(site, root) ?: scalarGetter(site, root)
                ?: return unavailable("unreconciled item getter at 0x${site.toString(16)}")
            getters += getter
        }
        if (getters.map { it.stride to it.count }.distinct().size != 1) return unavailable("conflicting item geometry")
        val pointer = getters.singleOrNull { it.field == null } ?: return unavailable("conflicting or missing inline-name getter")
        val boundary = getters.filter { it.field != null }.minByOrNull { requireNotNull(it.field) }
            ?: return unavailable("no independently bounded numeric field")
        val width = requireNotNull(boundary.field)
        // Reconcile the complete source-shaped scalar ABI. Otherwise losing the first getter
        // could silently widen the name field to the next numeric field's offset.
        val description = (width + 9) and -4
        val expectedFields = listOf(width to 2, width + 2 to 2, width + 4 to 1, width + 5 to 1,
            description to 4, description + 4 to 1, description + 5 to 1, description + 6 to 1,
            description + 7 to 1, description + 8 to 4, description + 12 to 1,
            description + 16 to 4, description + 20 to 1)
        val fields = getters.filter { it.field != null }.map { requireNotNull(it.field) to it.width }
        if (fields.size != expectedFields.size || fields.toSet() != expectedFields.toSet()) {
            return unavailable("incomplete or conflicting item scalar field inventory")
        }
        if (boundary.width != 2 || width !in 1 until pointer.stride || getters.any {
                it.field != null && it.field + it.width > pointer.stride
            }) return unavailable("invalid item numeric field boundary")
        when (session.limits.checkTableExtent(root.toLong(), pointer.count.toLong(), pointer.stride.toLong(), rom.size.toLong())) {
            is ExtentCheck.Valid -> Unit
            else -> return unavailable("item record extent unavailable")
        }
        val calls = (if (batchCalls == null) callers(setOf(pointer.entry))?.get(pointer.entry)
            else batchCalls[pointer.entry]) ?: return unavailable("item copier caller scan unavailable")
        val contracts = mutableListOf<CopyContract>()
        for (call in calls) {
            if (matches(call - 40, 0xB510, 0x1C0C, 0x0400, 0x0C00) &&
                word(call - 32) and 0xFF00 == 0x2800) {
                contracts += ordinaryWrapper(call, pointer.entry)
                    ?: return unavailable("incomplete static item copy contract")
            } else if (matches(call - 8, 0xB510, 0x1C0C, 0x0400, 0x0C00)) {
                contracts += simpleWrapper(call, pointer.entry)
                    ?: return unavailable("incomplete simple item copy contract")
            }
        }
        if (exhausted) return unavailable("item wrapper budget")
        if (contracts.distinct().size != 1) return unavailable("missing or conflicting static item copy contracts")
        return Result(Table(root, pointer.stride, pointer.count, width, setOfNotNull(contracts.single().excludedId)), "complete compiled static item-name consumer")
    }

    private data class Getter(val entry: Int, val stride: Int, val count: Int, val field: Int?, val width: Int = 0)

    private fun pointerGetter(site: Int, root: Int): Getter? =
        shiftPointerGetter(site, root) ?: mulPointerGetter(site, root)

    private fun shiftPointerGetter(site: Int, root: Int): Getter? {
        val entry = site - 22
        if (!matches(entry, 0xB500, 0x0400, 0x0C00) ||
            !matches(entry + 10, 0x1C01, 0x0409, 0x0C09) || word(site) and 0xFF00 != 0x4900 ||
            !matches(site + 2, 0x1840, 0xBC02, 0x4708) || literal(site) != root) return null
        val count = sanitize(bl(entry + 6) ?: return null) ?: return null
        val first = shift(word(entry + 16), 1, 0) ?: return null
        if (word(entry + 18) != 0x1840) return null
        val last = shift(word(entry + 20), 0, 0) ?: return null
        return Getter(entry, stride(first, last) ?: return null, count, null)
    }

    private fun mulPointerGetter(site: Int, root: Int): Getter? {
        val entry = site - 18
        if (!matches(entry, 0xB500, 0x0400, 0x0C00) || !matches(entry + 10, 0x0400, 0x0C00) ||
            word(site) and 0xFF00 != 0x4900 || !matches(site + 2, 0x1840, 0xBC02, 0x4708) ||
            literal(site) != root) return null
        val count = sanitize(bl(entry + 6) ?: return null) ?: return null
        val stride = immediateMulStride(entry + 14) ?: return null
        return Getter(entry, stride, count, null)
    }

    private fun scalarGetter(site: Int, root: Int): Getter? =
        shiftScalarGetter(site, root) ?: mulScalarGetter(site, root)

    private fun shiftScalarGetter(site: Int, root: Int): Getter? {
        val entry = site - 6
        if (!matches(entry, 0xB510, 0x0400, 0x0C00) || word(site) and 0xFF00 != 0x4C00 || literal(site) != root) return null
        val count = sanitize(bl(entry + 8) ?: return null) ?: return null
        if (!matches(entry + 12, 0x0400, 0x0C00) || word(entry + 18) != 0x1809) return null
        val first = shift(word(entry + 16), 0, 1) ?: return null
        val last = shift(word(entry + 20), 1, 1) ?: return null
        var cursor = entry + 22
        var adjustment = 0
        if (word(cursor) and 0xFF00 == 0x3400) { adjustment = word(cursor) and 255; cursor += 2 }
        if (word(cursor) != 0x1909) return null
        cursor += 2
        if (word(cursor) and 0xFF00 == 0x3100) { adjustment += word(cursor) and 255; cursor += 2 }
        val load = word(cursor)
        val width = when (load and 0xF800) { 0x7800 -> 1; 0x8800 -> 2; 0x6800 -> 4; else -> return null }
        if (load and 0x3F != 8 || !matches(cursor + 2, 0xBC10, 0xBC02, 0x4708)) return null
        val field = adjustment + ((load ushr 6) and 31) * width
        return Getter(entry, stride(first, last) ?: return null, count, field, width)
    }

    private fun mulScalarGetter(site: Int, root: Int): Getter? {
        val entry = site - 6
        if (!matches(entry, 0xB510, 0x0400, 0x0C00) || word(site) and 0xFF00 != 0x4C00 ||
            literal(site) != root || !matches(entry + 12, 0x0400, 0x0C00)) return null
        val count = sanitize(bl(entry + 8) ?: return null) ?: return null
        val stride = immediateMulStride(entry + 16) ?: return null
        var cursor = entry + 20
        var adjustment = 0
        if (word(cursor) and 0xFF00 == 0x3400) { adjustment = word(cursor) and 255; cursor += 2 }
        if (word(cursor) != 0x1900) return null
        cursor += 2
        if (word(cursor) and 0xFF00 == 0x3000) { adjustment += word(cursor) and 255; cursor += 2 }
        val load = word(cursor)
        val width = when (load and 0xF800) { 0x7800 -> 1; 0x8800 -> 2; 0x6800 -> 4; else -> return null }
        if (load and 0x3F != 0 || !matches(cursor + 2, 0xBC10, 0xBC02, 0x4708)) return null
        val field = adjustment + ((load ushr 6) and 31) * width
        return Getter(entry, stride, count, field, width)
    }

    /** MOV r1,#stride; MUL r0,r1, with the same bounded geometry as the shift forms. */
    private fun immediateMulStride(at: Int): Int? {
        val move = word(at)
        if (move and 0xFF00 != 0x2100 || word(at + 2) != 0x4348) return null
        return (move and 255).takeIf { it.toLong() in 2..MAX_RECORD_BYTES }
    }

    /** This entire call-free leaf normalizes u16, compares an instruction-built upper bound and returns zero on BHI. */
    private fun sanitize(entry: Int): Int? {
        if (!matches(entry, 0xB500, 0x0400, 0x0C01) || word(entry + 6) and 0xFF00 != 0x2000 ||
            !matches(entry + 10, 0x4281, 0xD801, 0x1C08, 0xE000, 0x2000, 0xBC02, 0x4708)) return null
        val shift = shift(word(entry + 8), 0, 0) ?: return null
        val maximum = (word(entry + 6) and 255).toLong() shl shift
        return (maximum + 1).takeIf { it in 1..65536 }?.toInt()
    }

    private fun stride(first: Int, last: Int): Int? = (((1L shl first) + 1) shl last)
        .takeIf { it in 2..MAX_RECORD_BYTES }?.toInt()

    private fun shift(opcode: Int, source: Int, destination: Int): Int? =
        if (opcode >= 0 && opcode and 0xF83F == ((source shl 3) or destination)) (opcode ushr 6) and 31 else null

    /** One finite target-set BL nomination pass; overflow counts remain complete, never sampled. */
    private fun callers(targets: Set<Int>): Map<Int, List<Int>>? {
        if (!scan(rom.size)) return null
        val sites = targets.associateWith { mutableListOf<Int>() }
        var observed = 0
        val limit = minOf(session.limits.maxNominatedGbaReferenceSites, MAX_CALLERS)
        for (at in 0..rom.size - 4 step 2) {
            if (at and 4095 == 0) session.cancellation.throwIfCancellationRequested()
            val list = sites[rawBl(at)] ?: continue
            observed++
            if (observed <= limit) list += at
        }
        session.cancellation.throwIfCancellationRequested()
        if (observed > limit) { exhausted = true; return null }
        return sites
    }

    private data class CopyContract(val entry: Int, val getter: Int, val copier: Int, val excludedId: Int?)

    private fun simpleWrapper(call: Int, getter: Int): CopyContract? {
        val entry = call - 8
        if (!matches(entry, 0xB510, 0x1C0C, 0x0400, 0x0C00) || bl(call) != getter ||
            !matches(call + 4, 0x1C01, 0x1C20) || !matches(call + 12, 0xBC10, 0xBC01, 0x4700)) return null
        val copier = bl(call + 8) ?: return null
        if (!completeCopier(copier)) return null
        return CopyContract(entry, getter, copier, null)
    }

    private fun completeCopier(entry: Int): Boolean = matches(entry,
        0xB500, 0x1C03, 0xE002, 0x701A, 0x3301, 0x3101, 0x780A,
        0x1C10, 0x28FF, 0xD1F8, 0x20FF, 0x7018, 0x1C18, 0xBC02, 0x4708)

    private fun ordinaryWrapper(call: Int, getter: Int): CopyContract? {
        val entry = call - 40
        if (!matches(entry, 0xB510, 0x1C0C, 0x0400, 0x0C00) ||
            word(entry + 8) and 0xFF00 != 0x2800 || word(entry + 10) != 0xD10D || bl(call) != getter ||
            !matches(call + 4, 0x1C01, 0x1C20) || !matches(call + 12, 0xBC10, 0xBC01, 0x4700)) return null
        // Only the unequal branch reaches this path. Dynamic descendants have no claimed effects.
        val copier = bl(call + 8) ?: return null
        if (!completeCopier(copier)) return null
        // The equal arm must leave across its literal pool and the entire ordinary path.
        // Its helper bodies remain opaque; only the wrapper's data/control flow is claimed.
        if (word(entry + 12) and 0xFF00 != 0x2000 || bl(entry + 14) == null ||
            !matches(entry + 18, 0x1C01, 0x1C20) || bl(entry + 22) != copier ||
            !matches(entry + 26, 0x4902, 0x1C20) || bl(entry + 30) == null ||
            word(entry + 34) != 0xE007) return null
        return CopyContract(entry, getter, copier, word(entry + 8) and 255)
    }

    private fun scan(bytes: Int): Boolean {
        session.cancellation.throwIfCancellationRequested()
        if (exhausted || scannedBytes + bytes > session.limits.maxDatasetExtentBytes) { exhausted = true; return false }
        scannedBytes += bytes
        return true
    }

    private fun word(at: Int): Int {
        session.cancellation.throwIfCancellationRequested()
        if (exhausted || ++probeWords > session.limits.maxProbeWorkPerDataset) { exhausted = true; return -1 }
        return if (at >= 0 && at and 1 == 0 && at <= rom.size - 2) rom.u16le(at) else -1
    }
    private fun matches(at: Int, vararg words: Int): Boolean = words.indices.all { word(at + 2 * it) == words[it] }
    private fun literal(at: Int): Int? {
        val instruction = word(at)
        if (instruction < 0 || instruction and 0xF800 != 0x4800) return null
        val slot = ((at + 4) and -4).toLong() + (instruction and 255) * 4L
        return if (slot in 0..rom.size.toLong() - 4) rom.gbaPointer(slot.toInt()) else null
    }
    private fun bl(at: Int): Int? {
        val high = word(at); val low = word(at + 2)
        return blTarget(at, high, low)
    }
    private fun rawBl(at: Int): Int? = blTarget(at, rom.u16le(at), rom.u16le(at + 2))
    private fun blTarget(at: Int, high: Int, low: Int): Int? {
        if (high < 0 || low < 0 || high and 0xF800 != 0xF000 || low and 0xF800 != 0xF800) return null
        val displacement = (((high and 0x7FF) shl 12) or ((low and 0x7FF) shl 1)) shl 9 shr 9
        return (at.toLong() + 4 + displacement).takeIf { it in 0 until rom.size.toLong() }?.toInt()
    }
    private companion object {
        const val MAX_FIELD_NOMINATION = 63
        const val MAX_RECORD_BYTES = 256L
        const val MAX_CALLERS = 128
    }
}
