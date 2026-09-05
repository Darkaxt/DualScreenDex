package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import com.enrpau.dualscreendex.parser.io.RomImage

/** Static declared operands, NOT execution/noninterference of graphics or sound descendants. */
internal object Gen2DeclaredSignAbi {
    enum class Status { RESOLVED, ABSENT, INCOMPLETE, CONFLICT, BUDGET }
    data class Resolution(val status: Status, val abi: Declaration? = null, val reason: String? = null)
    data class Grammar(val start: Int, val line: Int, val done: Int, val leadLimit: Int)

    fun resolve(rom: RomImage, sources: List<Gen2LocalMapPoiResolver.Source>, limits: ResolutionLimits, cancellation: ParserCancellationToken): Resolution {
        if (sources.isEmpty()) return Resolution(Status.ABSENT)
        val reader = Reader(rom, limits, cancellation)
        return try {
            // Nomination cannot affect a legacy ROM until its compiled field accessor's actual
            // selected map rows agree with the already accepted attribute bank AND address.
            val seeds = reader.find("CD @partial CD @switchAttrs CD @attrsPointer CD @copyAttrs CD @connections C9", home = true)
                .mapNotNull { (site, env) ->
                    try {
                        reader.needHome(env, "attrsPointer", "C5 D5 11 03 00 CD @mapField 69 60 D1 C1 C9")
                        reader.needHome(env, "mapField", "FA @mapGroup 47 FA @mapNumber 4F F0 %hram F5 3E %mapBank D7 CD @anyPointer 19 4E 23 46 F1 D7 C9")
                        reader.needHome(env, "anyPointer", "C5 05 48 06 00 21 @mapTable 09 09 2A 66 6F C1 0D 06 00 3E 09 CD @addNTimes C9")
                        val table = reader.pointer(env.getValue("mapBank"), env.getValue("mapTable"))
                        val agrees = sources.all { source ->
                            val group = source.baseAreaId ushr 8; val number = source.baseAreaId and 255
                            if (group == 0 || number == 0) false else {
                                val entry = table + (group - 1) * 2
                                val rows = reader.pointer(env.getValue("mapBank"), reader.word(entry))
                                val row = rows + (number - 1) * 9
                                reader.span(row, 9)
                                rom.u8(row) == source.attributesBank && reader.pointer(source.attributesBank, reader.word(row + 3)) == source.attributes
                            }
                        }
                        if (agrees) env.apply { put("copyChain", site) } else null
                    } catch (_: Invalid) { null }
                }
            when {
                seeds.isEmpty() -> Resolution(Status.ABSENT)
                seeds.size != 1 -> Resolution(Status.CONFLICT, reason = "multiple selected map declaration roots")
                else -> {
                    val env = seeds.single()
                    bindEventSeed(reader, env)
                    val dispatches = reader.find("CD @getByte 21 @scriptTable EF C9").mapNotNull { (site, candidate) ->
                        try {
                            candidate.putAll(env)
                            reader.needHome(candidate, "getByte", BYTE_READER)
                            candidate["commandBank"] = site / BANK
                            candidate
                        } catch (_: Invalid) { null }
                    }
                    if (dispatches.size > 1) Resolution(Status.CONFLICT, reason = "multiple script dispatchers for selected state")
                    else if (dispatches.isEmpty()) Resolution(Status.INCOMPLETE, reason = "selected script dispatcher missing")
                    else Resolution(Status.RESOLVED, Declaration(reader, dispatches.single()))
                }
            }
        } catch (failure: Ambiguous) {
            Resolution(Status.CONFLICT, reason = failure.message)
        } catch (failure: Exhausted) {
            Resolution(Status.BUDGET, reason = failure.message)
        } catch (failure: Invalid) {
            Resolution(Status.INCOMPLETE, reason = failure.message)
        }
    }

    private fun bindEventSeed(r: Reader, e: MutableMap<String, Int>) {
        r.needHome(e, "switchAttrs", "FA @mapGroup 47 FA @mapNumber 4F CD @attrsBank D7 C9")
        e["anyField"] = e.getValue("mapField") + 8
        r.needHome(e, "attrsBank", "E5 D5 11 00 00 CD @anyField 79 D1 E1 C9")
        r.needHome(e, "partial", "F0 %hram F5 3E %mapBank D7 CD @mapPointer 11 @partialState 01 05 00 CD @copyBytes F1 D7 C9")
        // These copy/stride leaves transfer and locate the selected records themselves;
        // unlike graphics boundaries, correlated call addresses alone are not sufficient.
        r.needHome(e, "copyBytes", "04 0C 18 03 2A 12 13 0D 20 FA 05 20 F7 C9")
        r.needHome(e, "addNTimes", "A7 C8 09 3D 20 FC C9")
        r.needHome(e, "mapPointer", "FA @mapGroup 47 FA @mapNumber 4F")
        r.check(e.getValue("mapPointer") + 8 == e.getValue("anyPointer"), "map pointer entry")
        r.needHome(e, "copyAttrs", "11 @attrsState 0E 0C 2A 12 13 0D 20 FA C9")
        e["scriptsBankState"] = e.getValue("attrsState") + 6
        e["eventsState"] = e.getValue("attrsState") + 9
        val loaders = r.find("CD @copyChain CD @switchScripts CD @readScripts AF CD @readEvents C9", e, true)
        r.unique(loaders.size, "selected loader")
        e.putAll(loaders.single().second)
        r.needHome(e, "switchScripts", "FA @scriptsBankState D7 C9")
        r.needHome(e, "readEvents", "F5 21 @eventsState 2A 66 6F 23 23 CD @warps CD @coords CD @backgrounds F1 A7 C0 CD @objects C9")
        for ((name, prefix, stride) in listOf(Triple("warps", "warp", "05"), Triple("coords", "coord", "08"), Triple("backgrounds", "bg", "05"))) {
            r.needHome(e, name, "2A 4F EA @${prefix}Count 7D EA @${prefix}Pointer 7C EA @${prefix}Hi 79 A7 C8 01 $stride 00 CD @addNTimes C9")
            r.check(e.getValue(prefix + "Hi") == e.getValue(prefix + "Pointer") + 1, "event pointer width")
        }
        r.need(0x10, "E0 %hram EA 00 20 C9", e)
        r.need(0x28, "D5 5F 16 00 19 19 2A 66 6F D1 E9", e)
        val candidates = r.find("CD @facing 38 02 AF C9 FA @bgKind 21 @bgTable EF C9").mapNotNull { (site, c) ->
            try {
                c.putAll(e)
                r.needHome(c, "facing", "CD @coordinates 47 7A D6 04 57 7B D6 04 5F FA @bgCount A7 C8 4F F0 %hram F5 CD @switchScripts CD @facingRows E1 7C D7 C9")
                r.needHome(c, "facingRows", "21 @bgPointer 2A 66 6F E5 2A BB 20 06 2A BA 20 02 18 0D E1 3E 05 85 6F 30 01 24 0D 20 EA AF C9 E1 11 @bgBuffer 01 05 00 CD @copyBytes 37 C9")
                r.check(c.getValue("bgKind") == c.getValue("bgBuffer") + 2, "event kind offset")
                c["bgScript"] = c.getValue("bgBuffer") + 3
                val table = r.pointer(site / BANK, c.getValue("bgTable"))
                val read = r.pointer(site / BANK, r.word(table))
                r.need(read, "CD @talk 21 @bgScript 2A 66 6F CD @getScripts CD @callScript 37 C9", c)
                r.needHome(c, "getScripts", "FA @scriptsBankState C9")
                r.needHome(c, "callScript", "EA @scriptBankState 7D EA @scriptPointerState 7C EA @scriptPointerHi 3E FF EA @scriptMode 37 C9")
                r.check(c.getValue("scriptPointerHi") == c.getValue("scriptPointerState") + 1, "script state width")
                c
            } catch (_: Invalid) { null }
        }
        r.unique(candidates.size, "selected background declaration dispatcher")
        e.putAll(candidates.single())
    }

    class Declaration internal constructor(private val r: Reader, private val root: Map<String, Int>) {
        private val declarations = mutableMapOf<Int, Resolution>()
        private val grammars = mutableMapOf<Int, Grammar>()
        fun grammar(command: Int): Grammar? {
            r.cancel()
            if (declarations.containsKey(command)) return grammars[command]
            try {
                val e = root.toMutableMap()
                val bank = e.getValue("commandBank")
                val table = r.pointer(bank, e.getValue("scriptTable"))
                val handler = r.pointer(bank, r.word(table + command * 2))
                r.need(handler, "FA @scriptBankState EA @textBankState CD @getByte EA @textPointerState CD @getByte EA @textPointerHi 06 %templateBank 21 @template C3 @scriptJump", e)
                r.check(e.getValue("textPointerState") == e.getValue("textBankState") + 1 && e.getValue("textPointerHi") == e.getValue("textBankState") + 2, "captured text state width")
                r.need(r.pointer(bank, e.getValue("scriptJump")), "78 EA @scriptBankState 7D EA @scriptPointerState 7C EA @scriptPointerHi C9", e)
                r.check(e.getValue("templateBank") == bank, "direct template changes command bank")
                val template = r.pointer(bank, e.getValue("template"))
                r.need(template, "%openCommand %repeatCommand FF FF 54 4A 91", e)
                val repeat = r.pointer(bank, r.word(table + e.getValue("repeatCommand") * 2))
                r.need(repeat, "CD @getByte 6F CD @getByte 67 FE FF 20 11 7D FE FF 20 0C 21 @textBankState 2A 47 2A 66 6F CD @mapTextbox C9 C9", e)
                val open = r.pointer(bank, r.word(table + e.getValue("openCommand") * 2))
                r.need(open, "CD @openText C9", e)
                r.needHome(e, "mapTextbox", "F0 %hram F5 78 D7 CD @setup 3E 01 E0 %oam CD @printText AF E0 %oam F1 D7 C9")
                bindSetupEnvelope(r, e)
                r.needHome(e, "printText", "01 @origin CD @printAt C9")
                r.needHome(e, "printAt", "FA @textFlags F5 CB CF EA @textFlags CD @textDispatch F1 EA @textFlags C9")
                r.needHome(e, "textDispatch", "2A FE %endCommand C8 CD @textStep 18 F7 E5 C5 4F 06 00 21 @textCommands 09 09 5E 23 56 C1 E1 D5 C9")
                r.check(e.getValue("textStep") == e.getValue("textDispatch") + 9, "text dispatch step")
                // The selected START slot is zero in this supported table ABI, independently of codec.
                val start = r.home(r.word(r.home(e.getValue("textCommands"))))
                r.need(start, "54 5D 60 69 CD @placeString 62 6B 23 C9", e)
                val place = r.home(e.getValue("placeString"))
                r.need(place, "E5 1A FE %endCommand 20 09 44 4D E1 C9 D1 13 C3 @nextPlace FE %leadLimit DA @doubleByte", e)
                r.check(e.getValue("nextPlace") == place + 1 && e.getValue("leadLimit") in 2..32, "token entry/width")
                e["nextChar"] = place + 11
                bindDoubleByte(r, e)
                var cursor = place + 20
                var line: Int? = null; var done: Int? = null
                val controls = mutableSetOf<Int>()
                // Only the finite CP/JP dictionary prefix is supported; never search arbitrary bytes.
                var branches = 0
                while (done == null && branches++ < 40) {
                    val size: Int
                    val value: Int
                    val target: Int
                    if (r.byte(cursor) == 0xFE && r.byte(cursor + 2) == 0xCA) {
                        value = r.byte(cursor + 1); target = r.word(cursor + 3); size = 5
                    } else if (r.byte(cursor) == 0xA7 && r.byte(cursor + 1) == 0xCA) {
                        value = 0; target = r.word(cursor + 2); size = 4
                    } else throw Invalid("unsupported text dictionary branch")
                    if (!controls.add(value)) throw Ambiguous("conflicting text dictionary control")
                    val candidate = e.toMutableMap()
                    if (r.match(r.home(target), "E1 21 @lineOrigin E5 C3 @nextChar", candidate) != null) {
                        if (line != null) throw Ambiguous("multiple LINE handlers")
                        line = value
                    }
                    val stop = e.toMutableMap()
                    if (r.match(r.home(target), "E1 11 @stopByte 1B C9 %endCommand", stop) != null) {
                        r.check(stop.getValue("stopByte") == target + 6, "DONE stop pointer")
                        done = value
                    }
                    cursor += size
                }
                r.check(line != null && done != null && line != done, "LINE/DONE grammar incomplete")
                val grammar = Grammar(0, requireNotNull(line), requireNotNull(done), e.getValue("leadLimit"))
                grammars[command] = grammar
                declarations[command] = Resolution(Status.RESOLVED)
                return grammar
            } catch (failure: Ambiguous) {
                declarations[command] = Resolution(Status.CONFLICT, reason = failure.message)
            } catch (failure: Exhausted) {
                declarations[command] = Resolution(Status.BUDGET, reason = failure.message)
            } catch (failure: Invalid) {
                declarations[command] = Resolution(Status.INCOMPLETE, reason = failure.message)
            }
            return null
        }
        internal fun outcome(command: Int): Resolution { grammar(command); return declarations.getValue(command) }
    }

    /** Closed top-level setup envelope. Its graphics descendants are opaque declaration boundaries,
     * not interpreted calls and emphatically not certified side-effect-free/returned leaves. */
    private fun bindSetupEnvelope(r: Reader, e: MutableMap<String, Int>) {
        r.needHome(e, "setup", "E5 CD @speech CD @sprites CD @tilemap E1 C9")
        r.needHome(e, "speech", "21 @boxOrigin 06 04 0E 12 C3 @drawBox")
        r.needHome(e, "openText", "CD @clearWindow F0 %hram F5 3E %graphicsBank D7 CD @reanchor CD @speech CD @hdma CD @fonts F1 D7 C9")
        r.needHome(e, "hdma", "F0 %oam F5 3E 01 E0 %oam CD @transfer F1 E0 %oam C9")
        r.needHome(e, "clearWindow", "3B E5 F5 E5 F8 06 36 %clearBank 2B 36 %clearHi 2B 36 %clearLo E1 F1 CD @farCall 33 33 33 C9")
        bindFarCallEnvelope(r, e)
        for (name in listOf("sprites", "tilemap", "drawBox", "transfer")) r.home(e.getValue(name))
        for (name in listOf("reanchor", "fonts")) {
            r.check(e.getValue(name) in BANK until 2 * BANK, "graphics boundary banked target")
            r.pointer(e.getValue("graphicsBank"), e.getValue(name))
        }
        r.pointer(e.getValue("clearBank"), e.getValue("clearHi") * 256 + e.getValue("clearLo"))
    }

    private fun bindFarCallEnvelope(r: Reader, e: MutableMap<String, Int>) {
        r.needHome(e, "farCall", "E5 E5 F5 C5 E5 F8 0E F0 %hram 47 7E D7 70 2B 46 2B 4E 2B 2B 2B 36 %returnHi 2B 36 %returnLo 2B 70 2B 71 E1 C1 F1 C9")
        val continuation = r.home(e.getValue("returnHi") * 256 + e.getValue("returnLo"))
        r.check(continuation == e.getValue("farCall") + 33, "setup dispatcher continuation")
        // RET is a stack-dispatched call to an opaque setup/glyph target, not a returned leaf.
    }

    private fun bindDoubleByte(r: Reader, e: MutableMap<String, Int>) {
        r.needHome(e, "doubleByte", "47 13 1A 4F 3B E5 F5 E5 F8 06 36 %glyphBank 2B 36 %glyphHi 2B 36 %glyphLo E1 F1 CD @farCall 33 33 33 CD @letterDelay C3 @nextChar")
        r.pointer(e.getValue("glyphBank"), e.getValue("glyphHi") * 256 + e.getValue("glyphLo"))
        r.home(e.getValue("letterDelay"))
    }

    internal class Reader(val rom: RomImage, private val limits: ResolutionLimits, private val cancellation: ParserCancellationToken) {
        private var work = 0
        private var nominations = 0
        private val patterns = mutableMapOf<String, Pattern>()
        fun cancel() = cancellation.throwIfCancellationRequested()
        private fun spend() { cancel(); if (++work > limits.maxProbeWorkPerDataset) throw Exhausted("declaration work limit") }
        fun unique(count: Int, reason: String) {
            if (count > 1) throw Ambiguous("$reason is conflicting")
            check(count == 1, "$reason is missing")
        }
        fun check(value: Boolean, reason: String) { if (!value) throw Invalid(reason) }
        fun span(offset: Int, length: Int) {
            if (length.toLong() > limits.maxDatasetExtentBytes) throw Exhausted("declaration extent limit")
            check(offset >= 0 && length >= 0 && offset.toLong() + length <= rom.size && offset % BANK + length <= BANK, "declaration bank/extent")
        }
        fun byte(offset: Int): Int { span(offset, 1); return rom.u8(offset) }
        fun word(offset: Int): Int { span(offset, 2); return rom.u16le(offset) }
        fun home(address: Int): Int { check(address in 0 until BANK, "interpretation target is not home code"); span(address, 1); return address }
        fun pointer(bank: Int, address: Int): Int {
            check(address in 0 until 2 * BANK, "invalid LE banked address")
            val offset = if (address < BANK) address else bank * BANK + address - BANK
            span(offset, 1); return offset
        }
        fun needHome(e: MutableMap<String, Int>, name: String, pattern: String) = need(home(e.getValue(name)), pattern, e)
        fun need(offset: Int, pattern: String, e: MutableMap<String, Int>) {
            spend(); check(match(offset, pattern, e) != null, "unsupported declaration at ${offset.toString(16)} ($pattern)")
        }
        fun match(offset: Int, source: String, e: MutableMap<String, Int>): MutableMap<String, Int>? {
            val pattern = patterns.getOrPut(source) { Pattern(source) }
            if (offset < 0 || offset.toLong() + pattern.size > rom.size || offset % BANK + pattern.size > BANK) return null
            if (pattern.fixed.any { (i, value) -> rom.u8(offset + i) != value }) return null
            // Charge only complete fixed-byte nominations, not unrelated byte prefixes.
            if (pattern.size.toLong() > limits.maxDatasetExtentBytes) throw Exhausted("declaration instruction extent limit")
            val additions = mutableMapOf<String, Int>()
            for ((i, name, width) in pattern.fields) {
                val value = if (width == 2) rom.u16le(offset + i) else rom.u8(offset + i)
                if ((e[name] ?: additions[name])?.let { it != value } == true) return null
                additions[name] = value
            }
            e.putAll(additions); return e
        }
        fun find(pattern: String, seed: Map<String, Int> = emptyMap(), home: Boolean = false): List<Pair<Int, MutableMap<String, Int>>> {
            val result = mutableListOf<Pair<Int, MutableMap<String, Int>>>()
            val compiled = patterns.getOrPut(pattern) { Pattern(pattern) }
            val limit = if (home) minOf(rom.size, BANK) else rom.size
            for (offset in 0 until limit) {
                if (offset % 4096 == 0) spend()
                if (rom.u8(offset) != compiled.fixed.first().second) continue
                val env = seed.toMutableMap()
                if (match(offset, pattern, env) != null) {
                    if (++nominations > limits.maxProbeRootsPerDataset || result.size >= limits.maxCandidatesPerDataset) throw Exhausted("declaration candidate limit")
                    result += offset to env
                }
            }
            return result
        }
    }
    private class Pattern(source: String) {
        val fixed = mutableListOf<Pair<Int, Int>>()
        val fields = mutableListOf<Triple<Int, String, Int>>()
        var size = 0
        init {
            source.split(' ').forEach { token ->
                when (token[0]) {
                    '@', '%' -> { val width = if (token[0] == '@') 2 else 1; fields += Triple(size, token.drop(1), width); size += width }
                    else -> { fixed += size to token.toInt(16); size++ }
                }
            }
        }
    }
    private class Ambiguous(message: String) : RuntimeException(message)
    private class Invalid(message: String) : RuntimeException(message)
    private class Exhausted(message: String) : RuntimeException(message)
    private const val BANK = 0x4000
    private const val BYTE_READER = "E5 C5 F0 %hram F5 FA @scriptBankState D7 21 @scriptPointerState 4E 23 46 0A 03 70 2B 71 47 F1 D7 78 C1 E1 C9"
}
