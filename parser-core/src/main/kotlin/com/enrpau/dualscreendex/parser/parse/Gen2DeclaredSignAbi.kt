package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.analysis.ResolutionLimits
import com.enrpau.dualscreendex.parser.io.RomImage

/** Static declared operands, NOT execution/noninterference of graphics or sound descendants. */
internal object Gen2DeclaredSignAbi {
    enum class Status { RESOLVED, ABSENT, INCOMPLETE, CONFLICT, BUDGET }
    data class Resolution(val status: Status, val abi: Declaration? = null, val reason: String? = null)
    sealed interface TokenWidth {
        data object Scalar : TokenWidth
        data class LeadBytePair(val leadLimit: Int) : TokenWidth
        fun byteCount(value: Int): Int = if (this is LeadBytePair && value in 1 until leadLimit) 2 else 1
    }
    data class Grammar(val start: Int, val line: Int, val done: Int, val endCommand: Int, val width: TokenWidth, val dictionary: ScalarDictionary? = null)
    private enum class TextOperand { LOCAL, FAR }

    /** Only positively compiled ROM0 literals, layouts, and WRAM substitutions are classified. */
    class ScalarDictionary internal constructor(private val r: Reader, private val env: Map<String, Int>, private val targets: Map<Int, Int>) {
        val controls: Set<Int> = targets.keys
        private val literals = mutableMapOf<Int, IntRange?>()
        private val layouts = mutableMapOf<Int, Boolean>()
        private val runtimeValues = mutableMapOf<Int, Boolean>()
        fun literal(value: Int): IntRange? {
            r.cancel()
            if (value !in controls) return null
            if (literals.containsKey(value)) return literals[value]
            val result = try {
                val e = env.toMutableMap()
                r.need(r.home(targets[value] ?: throw Invalid("undeclared literal control")), "D5 11 @literal C3 @literalJoin", e)
                r.needHome(e, "literalJoin", "CD @placeString 60 69 D1 C3 @nextChar")
                val start = r.home(e.getValue("literal"))
                var cursor = start
                while (true) {
                    r.spend()
                    if (cursor - start >= 32) throw Exhausted("literal extent limit")
                    r.span(start, cursor - start + 1)
                    r.home(cursor)
                    val byte = r.byte(cursor)
                    if (byte == e.getValue("endCommand")) break
                    r.check(byte !in controls, "nested or runtime literal control")
                    cursor++
                }
                start until cursor
            } catch (_: Invalid) {
                null
            }
              catch (_: Exhausted) { null }
            literals[value] = result
            return result
        }

        fun isLayout(value: Int): Boolean {
            r.cancel()
            if (value !in controls) return false
            layouts[value]?.let { return it }
            layouts[value] = false // Fail closed on recursive dictionary controls.
            val result = try {
                val target = r.home(targets[value] ?: throw Invalid("undeclared layout control"))
                val candidates = mutableListOf<Unit>()
                fun accept(pattern: String, validate: (Map<String, Int>) -> Unit = {}) {
                    r.match(target, pattern, env.toMutableMap())?.let { matched ->
                        validate(matched)
                        candidates += Unit
                    }
                }
                accept("E1 01 28 00 09 E5 C3 @nextChar")
                accept("E1 01 14 00 09 E5 C3 @nextChar")
                accept("D5 CD @textScroll CD @textScroll 21 @layoutOrigin D1 C3 @nextChar") { matched ->
                    r.home(matched.getValue("textScroll"))
                    r.check(matched.getValue("layoutOrigin") in WRAM, "layout origin")
                }
                accept(
                    "FA @linkMode FE %linkModeValue 28 %communicationJump CD @loadCursor CD @waitBg " +
                        "D5 CD @prompt D1 CD @unloadCursor D5 CD @textScroll CD @textScroll " +
                        "21 @layoutOrigin D1 C3 @nextChar",
                ) { matched ->
                    r.check(matched.getValue("linkMode") in WRAM, "continuation link state")
                    r.check(target + 7 + matched.getValue("communicationJump").toByte().toInt() == target + 10,
                        "continuation communication branch")
                    for (name in listOf("loadCursor", "waitBg", "prompt", "unloadCursor", "textScroll")) {
                        r.home(matched.getValue(name))
                    }
                    r.check(matched.getValue("layoutOrigin") in WRAM, "continuation origin")
                }
                accept(
                    "FA @linkMode B7 20 %communicationJump CD @loadCursor CD @waitBg D5 CD @prompt D1 " +
                        "FA @linkMode B7 C4 @unloadCursor D5 CD @textScroll CD @textScroll " +
                        "21 @layoutOrigin D1 C3 @nextChar",
                ) { matched ->
                    r.check(matched.getValue("linkMode") in WRAM, "continuation link state")
                    r.check(target + 6 + matched.getValue("communicationJump").toByte().toInt() == target + 9,
                        "continuation communication branch")
                    for (name in listOf("loadCursor", "waitBg", "prompt", "unloadCursor", "textScroll")) {
                        r.home(matched.getValue(name))
                    }
                    r.check(matched.getValue("layoutOrigin") in WRAM, "continuation origin")
                }
                accept(
                    "D5 FA @linkMode FE %linkModeValue 28 %linkJump CD @loadCursor CD @waitBg " +
                        "CD @prompt 21 @layoutOrigin 01 12 04 CD @clearBox CD @unloadCursor " +
                        "0E 14 CD @delayFrames 21 @layoutOrigin2 D1 C3 @nextChar",
                ) { matched ->
                    r.check(matched.getValue("linkMode") in WRAM, "paragraph link state")
                    r.check(target + 8 + matched.getValue("linkJump").toByte().toInt() == target + 11,
                        "paragraph link branch")
                    for (name in listOf("loadCursor", "waitBg", "prompt", "clearBox", "unloadCursor", "delayFrames")) {
                        r.home(matched.getValue(name))
                    }
                    r.check(matched.getValue("layoutOrigin") in WRAM && matched.getValue("layoutOrigin2") in WRAM,
                        "paragraph origins")
                }
                accept("D5 11 @layoutMarker 44 4D CD @placeString 60 69 D1 C3 @nextChar") { matched ->
                    val marker = r.home(matched.getValue("layoutMarker"))
                    r.span(marker, 2)
                    val nested = r.byte(marker)
                    r.check(r.byte(marker + 1) == matched.getValue("endCommand") && nested != value,
                        "layout marker")
                    r.check(isLayout(nested), "nested layout control")
                }
                r.unique(candidates.size, "static layout control")
                true
            } catch (_: Invalid) {
                false
            } catch (_: Ambiguous) {
                false
            } catch (_: Exhausted) {
                false
            }
            layouts[value] = result
            return result
        }

        fun isRuntime(value: Int): Boolean {
            r.cancel()
            if (value !in controls) return false
            runtimeValues[value]?.let { return it }
            val result = try {
                val e = env.toMutableMap()
                val target = r.home(targets[value] ?: throw Invalid("undeclared runtime control"))
                r.need(target, "D5 11 @runtimeText C3 @literalJoin", e)
                r.needHome(e, "literalJoin", "CD @placeString 60 69 D1 C3 @nextChar")
                r.check(e.getValue("runtimeText") in WRAM, "runtime text state")
                true
            } catch (_: Invalid) {
                false
            } catch (_: Exhausted) {
                false
            }
            runtimeValues[value] = result
            return result
        }
    }

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
                val directionEntries = (1..4).map { index ->
                    val handler = r.pointer(site / BANK, r.word(table + index * 2))
                    val direction = r.byte(handler + 1)
                    val jump = r.byte(handler + 3).toByte().toInt()
                    r.need(handler, "06 %direction 18 %directionJump", c.toMutableMap())
                    direction to handler + 4 + jump
                }
                r.check(directionEntries.map { it.first }.toSet() == setOf(0x00, 0x04, 0x08, 0x0c),
                    "directional background event mask")
                val directionChecks = directionEntries.map { it.second }.distinct()
                r.unique(directionChecks.size, "directional background event check")
                val directionCheck = directionChecks.single()
                r.need(directionCheck, "FA @playerDirection E6 0C B8 C2 @dontRead", c)
                r.check(directionCheck + 9 == read, "directional background event continuation")
                c["staticSignKinds"] = 5
                r.needHome(c, "getScripts", "FA @scriptsBankState C9")
                r.needHome(c, "callScript", "EA @scriptBankState 7D EA @scriptPointerState 7C EA @scriptPointerHi 3E FF EA @scriptRunning 37 C9")
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
        private val textOperands = mutableMapOf<Int, TextOperand>()
        private val standardScripts = mutableMapOf<Int, Int?>()
        private var standardTable: Pair<Int, Int>? = null
        private var standardTableAttempted = false
        private var textRuntime: Pair<Grammar, Map<String, Int>>? = null
        private var textRuntimeAttempted = false
        private val writeOperands = mutableMapOf<Int, TextOperand?>()
        private val contextualOperands = mutableMapOf<Int, Int?>()
        private val prefixOperands = mutableMapOf<Int, Int?>()
        private val behaviorOperands = mutableMapOf<Int, Int?>()
        fun supportsSignKind(kind: Int): Boolean = kind in 0 until root.getValue("staticSignKinds")

        fun grammar(command: Int): Grammar? {
            r.cancel()
            if (declarations.containsKey(command)) return grammars[command]
            try {
                val e = root.toMutableMap()
                val bank = e.getValue("commandBank")
                val table = e.getValue("scriptTable")
                val handler = r.slot(bank, table, command)
                val local = r.match(handler,
                    "FA @scriptBankState EA @textBankState CD @getByte EA @textPointerState CD @getByte EA @textPointerHi 06 %templateBank 21 @template C3 @scriptJump",
                    e.toMutableMap())
                val far = r.match(handler,
                    "CD @getByte EA @textBankState CD @getByte EA @textPointerState CD @getByte EA @textPointerHi 06 %templateBank 21 @template C3 @scriptJump",
                    e.toMutableMap())
                val operands = listOfNotNull(
                    local?.let { TextOperand.LOCAL to it },
                    far?.let { TextOperand.FAR to it },
                )
                r.unique(operands.size, "direct text operand")
                val (operand, matched) = operands.single()
                e.putAll(matched)
                r.check(e.getValue("textPointerState") == e.getValue("textBankState") + 1 && e.getValue("textPointerHi") == e.getValue("textBankState") + 2, "captured text state width")
                r.need(r.pointer(bank, e.getValue("scriptJump")), "78 EA @scriptBankState 7D EA @scriptPointerState 7C EA @scriptPointerHi C9", e)
                r.check(e.getValue("templateBank") == bank, "direct template changes command bank")
                val template = r.pointer(bank, e.getValue("template"))
                r.need(template, "%openCommand %repeatCommand FF FF %waitCommand %closeCommand %endScriptCommand", e)
                val roles = listOf("openCommand", "repeatCommand", "waitCommand", "closeCommand", "endScriptCommand")
                if (roles.map { e.getValue(it) }.distinct().size != roles.size) throw Ambiguous("aliased template roles")
                r.need(r.slot(bank, table, e.getValue("repeatCommand")), "CD @getByte 6F CD @getByte 67 FE FF 20 11 7D FE FF 20 0C 21 @textBankState 2A 47 2A 66 6F CD @mapTextbox C9 C9", e)
                r.need(r.slot(bank, table, e.getValue("openCommand")), "CD @openText C9", e)
                r.need(r.slot(bank, table, e.getValue("waitCommand")), "C3 @waitButton", e)
                bindCloseEnvelope(r, r.slot(bank, table, e.getValue("closeCommand")), e)
                r.need(r.slot(bank, table, e.getValue("endScriptCommand")), "CD @exitSubroutine 38 01 C9 AF EA @scriptRunning 3E 00 EA @scriptMode 21 @scriptFlags CB 86 CD @stopScript C9", e)
                r.check(e.getValue("scriptRunning") != e.getValue("scriptMode"), "aliased script running flag and mode")
                r.home(e.getValue("waitButton"))
                for (name in listOf("exitSubroutine", "stopScript")) r.pointer(bank, e.getValue(name))
                val inlineSetup = bindMapTextbox(r, e)
                bindSetupEnvelope(r, e, inlineSetup)
                r.needHome(e, "printText", "01 @origin CD @printAt C9")
                r.needHome(e, "printAt", "FA @textFlags F5 CB CF EA @textFlags CD @textDispatch F1 EA @textFlags C9")
                r.needHome(e, "textDispatch", "2A FE %endCommand C8 CD @textStep 18 F7 E5 C5 4F 06 00 21 @textCommands 09 09 5E 23 56 C1 E1 D5 C9")
                r.check(e.getValue("textStep") == e.getValue("textDispatch") + 9, "text dispatch step")
                // The selected START slot is zero in this supported table ABI, independently of codec.
                val start = r.home(r.word(r.home(e.getValue("textCommands"))))
                r.need(start, "54 5D 60 69 CD @placeString 62 6B 23 C9", e)
                val place = r.home(e.getValue("placeString"))
                r.need(place, "E5 1A FE %endCommand 20 09 44 4D E1 C9 D1 13 C3 @nextPlace", e)
                r.check(e.getValue("nextPlace") == place + 1 && e.getValue("endCommand") != 0, "token entry")
                e["nextChar"] = place + 11
                val paired = r.match(place + 15, "FE %leadLimit DA @doubleByte", e) != null
                val width = if (paired) {
                    // Do not broaden or weaken the existing pair/setup/farcall contract.
                    r.check(!inlineSetup && e.getValue("leadLimit") in 2..32, "token pair setup/width")
                    bindDoubleByte(r, e)
                    TokenWidth.LeadBytePair(e.getValue("leadLimit"))
                } else TokenWidth.Scalar
                var cursor = place + if (paired) 20 else 15
                var line: Int? = null; var done: Int? = null
                val controls = linkedMapOf<Int, Int>()
                // Scalar width requires the complete dictionary AND positive ordinary fallback.
                // The legacy pair grammar retains its established prefix-through-DONE scope.
                while (!paired || done == null) {
                    r.spend()
                    val size: Int
                    val value: Int
                    val target: Int
                    val opcode = r.byte(r.home(cursor))
                    if (opcode == 0xFE) r.span(cursor, 3) else if (opcode == 0xA7) r.span(cursor, 2)
                    if (opcode == 0xFE && r.byte(cursor + 2) == 0xCA) {
                        r.span(cursor, 5)
                        value = r.byte(cursor + 1); target = r.word(cursor + 3); size = 5
                    } else if (opcode == 0xA7 && r.byte(cursor + 1) == 0xCA) {
                        r.span(cursor, 4)
                        value = 0; target = r.word(cursor + 2); size = 4
                    } else {
                        if (paired) throw Invalid("unsupported text dictionary branch")
                        break
                    }
                    if (value in controls) throw Ambiguous("conflicting text dictionary control")
                    if (controls.size >= 40) throw Exhausted("text dictionary branch limit")
                    controls[value] = r.home(target)
                    val candidate = e.toMutableMap()
                    if (r.match(target, "E1 21 @lineOrigin E5 C3 @nextChar", candidate) != null) {
                        if (line != null) throw Ambiguous("multiple LINE handlers")
                        line = value
                    }
                    val stop = e.toMutableMap()
                    if (r.match(target, "E1 11 @stopByte 1B C9 %endCommand", stop) != null) {
                        r.check(stop.getValue("stopByte") == target + 6, "DONE stop pointer")
                        if (done != null) throw Ambiguous("multiple DONE handlers")
                        done = value
                    }
                    cursor += size
                }
                r.check(line != null && done != null && line != done && e.getValue("endCommand") !in controls, "LINE/DONE grammar incomplete")
                if (!paired) bindScalarFallback(r, cursor, e)
                val grammar = Grammar(0, requireNotNull(line), requireNotNull(done), e.getValue("endCommand"), width,
                    ScalarDictionary(r, e.toMap(), controls.toMap()))
                val priorRuntime = textRuntime
                if (priorRuntime != null) {
                    for (field in listOf("getByte", "scriptBankState", "mapTextbox", "openCommand")) {
                        r.check(priorRuntime.second.getValue(field) == e.getValue(field),
                            "competing text runtime $field")
                    }
                } else {
                    textRuntime = grammar to e.toMap()
                }
                grammars[command] = grammar
                textOperands[command] = operand
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
        fun directText(script: Int, scriptBank: Int): Pair<Int, Grammar>? = try {
            r.span(script, 1)
            r.check(script / BANK == scriptBank, "direct text script bank")
            val command = r.byte(script)
            val grammar = grammar(command) ?: return null
            val text = when (textOperands[command]) {
                TextOperand.LOCAL -> {
                    r.span(script, 3)
                    r.pointer(scriptBank, r.word(script + 1))
                }
                TextOperand.FAR -> {
                    r.span(script, 4)
                    r.pointer(r.byte(script + 1), r.word(script + 2))
                }
                null -> return null
            }
            text to grammar
        } catch (_: Invalid) { null }
          catch (_: Ambiguous) { null }
          catch (_: Exhausted) { null }

        fun isNoTextScript(script: Int, scriptBank: Int): Boolean {
            val runtime = resolveTextRuntime() ?: return false
            var cursor = script
            repeat(MAX_LINEAR_SCRIPT_COMMANDS) {
                r.cancel()
                try {
                    r.span(cursor, 1)
                    r.check(cursor / BANK == scriptBank, "textless script bank")
                } catch (_: Invalid) {
                    return false
                } catch (_: Exhausted) {
                    return false
                }
                val command = r.byte(cursor)
                if (command == runtime.second.getValue("endScriptCommand")) return true
                if (command in textOperands || writtenText(cursor, scriptBank, runtime) != null ||
                    contextualOperandBytes(command) != null) return false
                val operands = prefixOperandBytes(command, runtime)
                    ?: behaviorOperandBytes(command, runtime)
                    ?: return false
                cursor += 1 + operands
            }
            return false
        }

        private fun behaviorOperandBytes(
            command: Int,
            runtime: Pair<Grammar, Map<String, Int>>,
        ): Int? {
            if (behaviorOperands.containsKey(command)) return behaviorOperands[command]
            val result = try {
                val e = runtime.second.toMutableMap()
                val handler = r.slot(e.getValue("commandBank"), e.getValue("scriptTable"), command)
                val sound = r.match(handler,
                    "CD @getByte 5F CD @getByte 57 CD @playSound C9",
                    e.toMutableMap())?.also { r.home(it.getValue("playSound")) }
                val pause = r.match(handler,
                    "CD @getByte A7 28 %pauseLoop EA @scriptDelay 0E 02 CD @delayFrames " +
                        "21 @scriptDelay 35 20 %delayLoop C9",
                    e.toMutableMap())?.also {
                    r.check(it.getValue("scriptDelay") in WRAM, "pause delay state")
                    r.check(handler + 6 + it.getValue("pauseLoop").toByte().toInt() == handler + 9,
                        "pause zero branch")
                    r.check(handler + 20 + it.getValue("delayLoop").toByte().toInt() == handler + 9,
                        "pause delay branch")
                    r.home(it.getValue("delayFrames"))
                }
                val matches = listOfNotNull(sound?.let { 2 }, pause?.let { 1 })
                r.unique(matches.size, "textless behavior command")
                matches.single()
            } catch (_: Invalid) { null }
              catch (_: Ambiguous) { null }
              catch (_: Exhausted) { null }
            behaviorOperands[command] = result
            return result
        }

        fun isContextualScript(script: Int, scriptBank: Int): Boolean {
            val runtime = resolveTextRuntime() ?: return false
            var cursor = script
            repeat(MAX_LINEAR_SCRIPT_COMMANDS) {
                r.cancel()
                try {
                    r.span(cursor, 1)
                    r.check(cursor / BANK == scriptBank, "contextual script bank")
                } catch (_: Invalid) {
                    return false
                } catch (_: Exhausted) {
                    return false
                }
                val command = r.byte(cursor)
                contextualOperandBytes(command)?.let { return true }
                val operands = prefixOperandBytes(command, runtime) ?: return false
                cursor += 1 + operands
            }
            return false
        }

        private fun prefixOperandBytes(
            command: Int,
            runtime: Pair<Grammar, Map<String, Int>>,
        ): Int? {
            if (command == runtime.second.getValue("openCommand")) return 0
            if (prefixOperands.containsKey(command)) return prefixOperands[command]
            val result = try {
                val e = runtime.second.toMutableMap()
                val handler = r.slot(e.getValue("commandBank"), e.getValue("scriptTable"), command)
                val reanchor = r.match(handler, "CD @reanchorMap CD @getByte C9", e.toMutableMap())?.also {
                    r.home(it.getValue("reanchorMap"))
                }
                val setValue = r.match(handler, "CD @getByte EA @scriptVar C9", e.toMutableMap())?.also {
                    r.check(it.getValue("scriptVar") in WRAM, "script value state")
                }
                val facePlayer = r.match(handler,
                    "F0 %lastTalked A7 C8 16 00 F0 %lastTalked 5F 3E %facingBank " +
                        "21 @relativeFacing CF 7A 87 87 5F F0 %lastTalked 57 CD @applyFacing C9",
                    e.toMutableMap())?.also {
                    r.pointer(it.getValue("facingBank"), it.getValue("relativeFacing"))
                    r.pointer(it.getValue("commandBank"), it.getValue("applyFacing"))
                }
                val matches = listOfNotNull(
                    reanchor?.let { 1 },
                    setValue?.let { 1 },
                    facePlayer?.let { 0 },
                )
                r.unique(matches.size, "static text prefix command")
                matches.single()
            } catch (_: Invalid) { null }
              catch (_: Ambiguous) { null }
              catch (_: Exhausted) { null }
            prefixOperands[command] = result
            return result
        }

        private fun contextualOperandBytes(command: Int): Int? {
            if (contextualOperands.containsKey(command)) return contextualOperands[command]
            val result = try {
                val e = root.toMutableMap()
                val handler = r.slot(e.getValue("commandBank"), e.getValue("scriptTable"), command)
                val flag = r.match(handler,
                    "CD @getByte 5F CD @getByte 57 06 %flagAction CD @flagHandler 79 A7 " +
                        "28 %falseJump 3E %trueValue EA @scriptVar C9",
                    e.toMutableMap())?.also {
                    r.check(it.getValue("flagAction") == CHECK_FLAG_ACTION, "context selector flag action")
                    r.check(it.getValue("trueValue") == 1, "context selector true normalization")
                    r.check(it.getValue("scriptVar") in WRAM, "context selector state")
                    r.check(handler + 17 + it.getValue("falseJump").toByte().toInt() == handler + 19,
                        "context selector false branch")
                    r.pointer(it.getValue("commandBank"), it.getValue("flagHandler"))
                }
                val random = r.match(handler,
                    "CD @getByte EA @scriptVar A7 C8 4F CD @randomDivide A7 28 %noRestriction " +
                        "47 AF 90 47 C5 CD @random C1 F0 %randomAdd B8 30 %retryRandom " +
                        "18 %finishRandom C5 CD @random C1 F0 %randomAdd F5 FA @scriptVar " +
                        "4F F1 CD @simpleDivide EA @scriptVar C9",
                    e.toMutableMap())?.also {
                    r.check(it.getValue("scriptVar") in WRAM, "random selector state")
                    r.check(handler + 15 + it.getValue("noRestriction").toByte().toInt() == handler + 31,
                        "random unrestricted branch")
                    r.check(handler + 29 + it.getValue("retryRandom").toByte().toInt() == handler + 19,
                        "random retry branch")
                    r.check(handler + 31 + it.getValue("finishRandom").toByte().toInt() == handler + 38,
                        "random finish branch")
                    r.need(r.pointer(it.getValue("commandBank"), it.getValue("randomDivide")),
                        "AF 47 91 04 91 30 FC 05 81 C9", it)
                    r.home(it.getValue("random"))
                    r.home(it.getValue("simpleDivide"))
                }
                val special = r.match(handler,
                    "CD @getByte 5F CD @getByte 57 3E %specialBank 21 @specialDispatch CF C9",
                    e.toMutableMap())?.also {
                    r.pointer(it.getValue("specialBank"), it.getValue("specialDispatch"))
                }
                val dynamicMenu = r.match(handler,
                    "AF EA @scriptVar CD @getByte 5F CD @getByte 57 FA @scriptBankState 47 " +
                        "3E %dynamicBank 21 @dynamicTarget CF D8 3E %trueValue EA @scriptVar C9",
                    e.toMutableMap())?.also {
                    r.check(it.getValue("scriptVar") in WRAM, "dynamic menu state")
                    r.check(it.getValue("trueValue") == 1, "dynamic menu true result")
                    r.pointer(it.getValue("dynamicBank"), it.getValue("dynamicTarget"))
                }
                val matches = listOfNotNull(
                    flag?.let { 2 },
                    random?.let { 1 },
                    special?.let { 2 },
                    dynamicMenu?.let { 2 },
                )
                r.unique(matches.size, "context selector command")
                matches.single()
            } catch (_: Invalid) { null }
              catch (_: Ambiguous) { null }
              catch (_: Exhausted) { null }
            contextualOperands[command] = result
            return result
        }

        fun linearDirectText(script: Int, scriptBank: Int): Pair<Int, Grammar>? {
            val runtime = resolveTextRuntime() ?: return null
            var cursor = script
            repeat(MAX_LINEAR_SCRIPT_COMMANDS) {
                r.cancel()
                r.span(cursor, 1)
                r.check(cursor / BANK == scriptBank, "linear text script bank")
                val command = r.byte(cursor)
                if (command in textOperands) return directText(cursor, scriptBank)
                writtenText(cursor, scriptBank, runtime)?.let { return it }
                val operands = prefixOperandBytes(command, runtime) ?: return null
                cursor += 1 + operands
            }
            return null
        }

        private fun writtenText(
            script: Int,
            scriptBank: Int,
            runtime: Pair<Grammar, Map<String, Int>>,
        ): Pair<Int, Grammar>? = try {
            val command = r.byte(script)
            val operand = if (writeOperands.containsKey(command)) {
                writeOperands[command]
            } else {
                val e = runtime.second.toMutableMap()
                val handler = r.slot(e.getValue("commandBank"), e.getValue("scriptTable"), command)
                val local = r.match(handler,
                    "CD @getByte 6F CD @getByte 67 FA @scriptBankState 47 CD @mapTextbox C9",
                    e.toMutableMap())
                val far = r.match(handler,
                    "CD @getByte 47 CD @getByte 6F CD @getByte 67 CD @mapTextbox C9",
                    e.toMutableMap())
                val matches = listOfNotNull(
                    local?.let { TextOperand.LOCAL },
                    far?.let { TextOperand.FAR },
                )
                val resolved = matches.singleOrNull()
                writeOperands[command] = resolved
                resolved
            } ?: return null
            val text = when (operand) {
                TextOperand.LOCAL -> {
                    r.span(script, 3)
                    r.pointer(scriptBank, r.word(script + 1))
                }
                TextOperand.FAR -> {
                    r.span(script, 4)
                    r.pointer(r.byte(script + 1), r.word(script + 2))
                }
            }
            text to runtime.first
        } catch (_: Invalid) { null }
          catch (_: Ambiguous) { null }
          catch (_: Exhausted) { null }

        private fun resolveTextRuntime(): Pair<Grammar, Map<String, Int>>? {
            textRuntime?.let { return it }
            if (textRuntimeAttempted) return null
            textRuntimeAttempted = true
            try {
                val candidates = mutableListOf<Int>()
                for (command in 0..255) {
                    r.spend()
                    val e = root.toMutableMap()
                    val handler = try {
                        r.slot(e.getValue("commandBank"), e.getValue("scriptTable"), command)
                    } catch (_: Invalid) {
                        continue
                    }
                    val local = r.match(handler,
                        "FA @scriptBankState EA @textBankState CD @getByte EA @textPointerState " +
                            "CD @getByte EA @textPointerHi 06 %templateBank 21 @template C3 @scriptJump",
                        e.toMutableMap())
                    val far = r.match(handler,
                        "CD @getByte EA @textBankState CD @getByte EA @textPointerState " +
                            "CD @getByte EA @textPointerHi 06 %templateBank 21 @template C3 @scriptJump",
                        e.toMutableMap())
                    if ((if (local != null) 1 else 0) + (if (far != null) 1 else 0) == 1) {
                        candidates += command
                        if (candidates.size > MAX_DIRECT_COMMAND_CANDIDATES) {
                            throw Ambiguous("multiple direct text command candidates")
                        }
                    }
                }
                candidates.forEach(::grammar)
            } catch (_: Invalid) {
                return null
            } catch (_: Ambiguous) {
                return null
            } catch (_: Exhausted) {
                return null
            }
            return textRuntime
        }

        fun isNoTextStandardScript(index: Int): Boolean {
            val script = standardScript(index) ?: return false
            return isNoTextScript(script, script / BANK)
        }

        fun isContextualStandardScript(index: Int): Boolean {
            val script = standardScript(index) ?: return false
            return isContextualScript(script, script / BANK)
        }

        fun standardDirectText(index: Int): Pair<Int, Grammar>? {
            val script = standardScript(index) ?: return null
            return linearDirectText(script, script / BANK)
        }

        private fun standardScript(index: Int): Int? {
            r.cancel()
            if (index !in 0..255) return null
            if (standardScripts.containsKey(index)) return standardScripts[index]
            val table = resolveStandardTable()
            val result = try {
                if (table == null) null else {
                    val row = table.second + index * 3
                    r.span(row, 3)
                    r.pointer(r.byte(row), r.word(row + 1))
                }
            } catch (_: Invalid) { null }
              catch (_: Exhausted) { null }
            standardScripts[index] = result
            return result
        }

        private fun resolveStandardTable(): Pair<Int, Int>? {
            if (standardTableAttempted) return standardTable
            standardTableAttempted = true
            standardTable = try {
                val e = root.toMutableMap()
                val bank = e.getValue("commandBank")
                val handler = r.slot(bank, e.getValue("scriptTable"), JUMP_STD_COMMAND)
                r.need(handler, "CD @stdScript 18 %jumpOffset", e)
                val scriptJump = handler + 5 + e.getValue("jumpOffset").toByte().toInt()
                r.need(scriptJump,
                    "78 EA @scriptBankState 7D EA @scriptPointerState 7C EA @scriptPointerHi C9", e)
                val stdScript = r.pointer(bank, e.getValue("stdScript"))
                r.need(stdScript,
                    "CD @getByte 5F CD @getByte 57 21 @stdTable 19 19 19 3E %stdBank " +
                        "CD @farByte 47 23 3E %stdBank CD @farWord C9", e)
                r.home(e.getValue("farByte"))
                r.home(e.getValue("farWord"))
                e.getValue("stdBank") to r.pointer(e.getValue("stdBank"), e.getValue("stdTable"))
            } catch (_: Invalid) { null }
              catch (_: Ambiguous) { null }
              catch (_: Exhausted) { null }
            return standardTable
        }

        // Cached command outcomes only: reporting must not read ROM bytes or retry grammar.
        fun failureReasons(): List<String> = declarations.toSortedMap().mapNotNull { (command, result) ->
            if (result.status == Status.RESOLVED) null
            else "Gen II sign command 0x${command.toString(16).padStart(2, '0')} ${result.status}: ${result.reason}"
        }

        internal fun outcome(command: Int): Resolution { grammar(command); return declarations.getValue(command) }
    }

    private fun bindCloseEnvelope(r: Reader, offset: Int, e: MutableMap<String, Int>) {
        val variants = listOf(
            "hdma" to "CD @hdma CD @closeText C9",
            "closeTransfer" to "F0 %oam F5 3E 01 E0 %oam CD @closeTransfer F1 E0 %oam CD @closeText C9",
        )
        r.spend()
        val matches = variants.mapNotNull { (transfer, pattern) ->
            r.match(offset, pattern, e.toMutableMap())?.let { transfer to it }
        }
        r.unique(matches.size, "CloseText envelope")
        val (transfer, matched) = matches.single()
        e.putAll(matched)
        // Graphics remain opaque; inline save/restore must share the setup's OAM field.
        r.home(e.getValue(transfer)); r.home(e.getValue("closeText"))
    }

    private fun bindMapTextbox(r: Reader, e: MutableMap<String, Int>): Boolean {
        val variants = listOf(
            false to "F0 %hram F5 78 D7 CD @setup 3E 01 E0 %oam CD @printText AF E0 %oam F1 D7 C9",
            true to "F0 %hram F5 78 D7 E5 CD @speech CD @sprites 3E 01 E0 %oam CD @tilemap E1 CD @printText AF E0 %oam F1 D7 C9",
        )
        r.spend()
        val matches = variants.mapNotNull { (inline, pattern) ->
            r.match(r.home(e.getValue("mapTextbox")), pattern, e.toMutableMap())?.let { inline to it }
        }
        r.unique(matches.size, "MapTextbox envelope")
        e.putAll(matches.single().second)
        return matches.single().first
    }

    /** Closed top-level setup envelope. Its graphics descendants are opaque declaration boundaries,
     * not interpreted calls and emphatically not certified side-effect-free/returned leaves. */
    private fun bindSetupEnvelope(r: Reader, e: MutableMap<String, Int>, inline: Boolean) {
        if (!inline) r.needHome(e, "setup", "E5 CD @speech CD @sprites CD @tilemap E1 C9")
        r.needHome(e, "speech", "21 @boxOrigin 06 04 0E 12 C3 @drawBox")
        r.needHome(e, "openText", "CD @clearWindow F0 %hram F5 3E %graphicsBank D7 CD @reanchor CD @speech CD @hdma CD @fonts F1 D7 C9")
        if (!inline) {
            // Preserve every previously required setup/pair dispatcher declaration.
            r.needHome(e, "hdma", "F0 %oam F5 3E 01 E0 %oam CD @transfer F1 E0 %oam C9")
            val clearWindow = r.home(e.getValue("clearWindow"))
            val stackFarCall = r.match(
                clearWindow,
                "3B E5 F5 E5 F8 06 36 %clearBank 2B 36 %clearHi 2B 36 %clearLo E1 F1 CD @farCall 33 33 33 C9",
                e.toMutableMap(),
            )
            val clearRows = r.match(
                clearWindow,
                "21 @clearRow0 CD @clearRow 21 @clearRow1 CD @clearRow " +
                    "21 @clearRow2 CD @clearRow 21 @clearRow3 CD @clearRow AF CD @clearSprites " +
                    "AF 21 @windowEnd 32 32 7D EA @windowLo 7C EA @windowHi CD @updateWindow C9",
                e.toMutableMap(),
            )
            r.unique(listOfNotNull(stackFarCall, clearRows).size, "ClearWindow envelope")
            if (stackFarCall != null) {
                e.putAll(stackFarCall)
                bindFarCallEnvelope(r, e)
                r.pointer(e.getValue("clearBank"), e.getValue("clearHi") * 256 + e.getValue("clearLo"))
            } else {
                e.putAll(requireNotNull(clearRows))
                r.check(e.getValue("clearRow1") == e.getValue("clearRow0") + 16 &&
                    e.getValue("clearRow2") == e.getValue("clearRow1") + 16 &&
                    e.getValue("clearRow3") == e.getValue("clearRow2") + 16,
                    "ClearWindow row stride")
                r.check(e.getValue("windowHi") == e.getValue("windowLo") + 1,
                    "ClearWindow pointer state width")
                r.home(e.getValue("clearRow"))
                r.home(e.getValue("clearSprites"))
                r.home(e.getValue("updateWindow"))
            }
            r.home(e.getValue("transfer"))
        }
        for (name in listOf("sprites", "tilemap", "drawBox", "hdma", "clearWindow")) r.home(e.getValue(name))
        for (name in listOf("reanchor", "fonts")) {
            r.check(e.getValue(name) in BANK until 2 * BANK, "graphics boundary banked target")
            r.pointer(e.getValue("graphicsBank"), e.getValue(name))
        }
    }

    private fun bindScalarFallback(r: Reader, offset: Int, e: MutableMap<String, Int>) {
        // Every JR and shared continuation is part of this finite source-shaped declaration.
        // Diacritic/letter-delay callees are address-checked opaque boundaries, not executed.
        r.need(r.home(offset), "FE %handMark 28 04 FE %dakMark 20 07 47 CD @diacritic C3 @nextChar " +
            "FE %firstRegular 30 24 FE %firstHand 30 11 FE %firstHiraDak 30 04 C6 %katDakDelta 18 02 C6 %hiraDakDelta " +
            "06 %dakMark CD @diacritic 18 0F FE %firstHiraHand 30 04 C6 %katHandDelta 18 02 C6 %hiraHandDelta " +
            "06 %handMark CD @diacritic 22 CD @letterDelay C3 @nextChar", e)
        r.check(0 < e.getValue("firstHiraDak") && e.getValue("firstHiraDak") < e.getValue("firstHand") &&
            e.getValue("firstHand") < e.getValue("firstHiraHand") && e.getValue("firstHiraHand") < e.getValue("firstRegular"), "scalar range order")
        if (e.getValue("handMark") == e.getValue("dakMark")) throw Ambiguous("aliased diacritic marks")
        r.home(e.getValue("diacritic")); r.home(e.getValue("letterDelay"))
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
        fun spend() { cancel(); if (++work > limits.maxProbeWorkPerDataset) throw Exhausted("declaration work limit") }
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
        fun slot(bank: Int, table: Int, command: Int): Int {
            check(command in 0..255 && table in 0 until 2 * BANK &&
                table + command * 2 + 2 <= if (table < BANK) BANK else 2 * BANK, "command table bank boundary")
            return pointer(bank, word(pointer(bank, table) + command * 2))
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
    private val WRAM = 0xc000..0xdfff
    private const val BANK = 0x4000
    private const val JUMP_STD_COMMAND = 0x0c
    private const val MAX_LINEAR_SCRIPT_COMMANDS = 8
    private const val MAX_DIRECT_COMMAND_CANDIDATES = 4
    private const val CHECK_FLAG_ACTION = 2
    private const val BYTE_READER = "E5 C5 F0 %hram F5 FA @scriptBankState D7 21 @scriptPointerState 4E 23 46 0A 03 70 2B 71 47 F1 D7 78 C1 E1 C9"
}
