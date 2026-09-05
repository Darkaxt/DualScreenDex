package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.catalog.LocalMap

/** Independent assembler for the pinned, finite declaration ABI; not a runtime emulator. */
internal class Gen2DeclaredSignFixture(val shift: Int = 0, val bankShift: Int = 0) {
    val bytes = ByteArray(0x200000) { 0xFF.toByte() }
    private val symbols = linkedMapOf<String, Int>()
    private val romSymbols = mutableSetOf<String>()
    val codeBank = 3 + bankShift
    val dataBank = 2 + bankShift
    val mapBank = 4 + bankShift
    val attributes = dataBank * 0x4000 + 0x800 + shift
    val events = attributes + 0x100
    val script = attributes + 0x200
    val text = attributes + 0x300
    val source get() = Gen2LocalMapPoiResolver.Source(0x101, dataBank, attributes)
    val map = LocalMap("local/257", null, 0x101, 160, 160, 10, 10, "asset/257")

    init {
        val home = listOf("loader", "copyAttrsChain", "copyPartial", "switchAttrs", "attrsBank", "attrsPointer", "mapPointer", "mapField", "copyAttrs", "readEvents", "readWarp", "readCoord", "readBG", "switchScripts", "getScripts", "callScript", "getByte", "mapTextbox", "printText", "printAt", "textDispatch", "textStart", "placeString", "line", "done", "setup", "speech", "openText", "clearWindow", "farCall", "hdma", "facing", "facingRows", "readScripts", "addNTimes", "copyBytes", "doubleByte")
        home.forEachIndexed { i, name -> symbols[name] = 0x400 + shift + i * 0x80 }
        listOf("bgDispatch", "bgTable", "bgRead", "scriptDispatch", "scriptTable", "direct", "template", "scriptJump", "repeat", "openHandler", "talk", "textTable").forEachIndexed { i, name -> symbols[name] = codeBank * 0x4000 + 0x1000 + shift + i * 0x100 }
        symbols["textTable"] = 0x3800 + shift
        romSymbols += symbols.keys
        romSymbols += listOf("anyPointer", "anyField", "mapTable", "mapHeader", "nextChar", "nextPlace", "stopByte", "textDispatchStep", "textCommands", "graphicsA", "graphicsB", "graphicsC", "graphicsD", "graphicsE")
        symbols["anyPointer"] = at("mapPointer") + 8
        symbols["anyField"] = at("mapField") + 8
        symbols["mapTable"] = mapBank * 0x4000 + 0x100 + shift
        symbols["mapHeader"] = at("mapTable") + 0x20
        symbols["mapBank"] = mapBank
        symbols["codeBank"] = codeBank
        symbols["attrState"] = 0xD143 + shift
        symbols["scriptBankState"] = 0xD21D + shift
        symbols["scriptPointerState"] = 0xD21E + shift
        symbols["scriptPointerHi"] = 0xD21F + shift
        symbols["scriptMode"] = 0xD21C + shift
        symbols["textBankState"] = 0xD232 + shift
        symbols["textPointerState"] = 0xD233 + shift
        symbols["textPointerHi"] = 0xD234 + shift
        symbols["mapGroup"] = 0xDAFD + shift
        symbols["mapNumber"] = 0xDAFE + shift
        symbols["mapScriptsBank"] = at("attrState") + 6
        symbols["mapEventsPointer"] = at("attrState") + 9
        symbols["warpCount"] = 0xDA48 + shift
        symbols["warpPointer"] = 0xDA49 + shift
        symbols["warpPointerHi"] = 0xDA4A + shift
        symbols["coordCount"] = 0xDA4B + shift
        symbols["coordPointer"] = 0xDA4C + shift
        symbols["coordPointerHi"] = 0xDA4D + shift
        symbols["bgCount"] = 0xDA4E + shift
        symbols["bgPointer"] = 0xDA4F + shift
        symbols["bgPointerHi"] = 0xDA50 + shift
        symbols["bgBuffer"] = 0xCF11 + shift
        symbols["bgKind"] = 0xCF13 + shift
        symbols["bgScript"] = 0xCF14 + shift
        symbols["partialState"] = 0xD13E + shift
        symbols["flags"] = 0xD257 + shift
        symbols["tileOrigin"] = 0xC4B9 + shift
        symbols["lineOrigin"] = 0xC4E1 + shift
        symbols["nextChar"] = at("placeString") + 11
        symbols["nextPlace"] = at("placeString") + 1
        symbols["stopByte"] = at("done") + 6
        symbols["textDispatchStep"] = at("textDispatch") + 9
        symbols["textCommands"] = at("textTable")
        symbols["farReturnHi"] = (at("farCall") + 33) ushr 8
        symbols["farReturnLo"] = (at("farCall") + 33) and 255
        symbols["graphicsA"] = 0x6600
        symbols["graphicsB"] = 0x6700
        symbols["graphicsC"] = 0x3000
        symbols["graphicsD"] = 0x3100
        symbols["graphicsE"] = 0x3200
        word(at("mapTable"), at("mapHeader"))
        bytes[at("mapHeader")] = dataBank.toByte()
        word(at("mapHeader") + 3, attributes)
        bytes[attributes + 1] = 5; bytes[attributes + 2] = 5
        bytes[attributes + 6] = dataBank.toByte(); word(attributes + 9, events)
        raw(events, "00 00 00 00 01 01 02 00")
        word(events + 8, script); bytes[events + 10] = 0
        raw(script, "53"); word(script + 1, text)
        // Independent source sample and a second record: LINE is not DONE and neighbors are not prose.
        raw(text, "00 07 9c 01 67 07 8a 7f 07 0c 03 2e 04 46 07 8b 5a 06 63 07 ab 07 8b 5e 00 01 68 04 da 05 b7 5e")
        raw(0x10, "e0 9f ea 00 20 c9")
        raw(0x28, "d5 5f 16 00 19 19 2a 66 6f d1 e9")
        emit("loader", "cd @copyAttrsChain cd @switchScripts cd @readScripts af cd @readEvents c9")
        emit("copyAttrsChain", "cd @copyPartial cd @switchAttrs cd @attrsPointer cd @copyAttrs cd @graphicsC c9")
        emit("copyPartial", "f0 9f f5 3e #mapBank d7 cd @mapPointer 11 @partialState 01 05 00 cd @copyBytes f1 d7 c9")
        emit("switchAttrs", "fa @mapGroup 47 fa @mapNumber 4f cd @attrsBank d7 c9")
        emit("attrsBank", "e5 d5 11 00 00 cd @anyField 79 d1 e1 c9")
        emit("attrsPointer", "c5 d5 11 03 00 cd @mapField 69 60 d1 c1 c9")
        emit("mapPointer", "fa @mapGroup 47 fa @mapNumber 4f c5 05 48 06 00 21 @mapTable 09 09 2a 66 6f c1 0d 06 00 3e 09 cd @addNTimes c9")
        emit("mapField", "fa @mapGroup 47 fa @mapNumber 4f f0 9f f5 3e #mapBank d7 cd @anyPointer 19 4e 23 46 f1 d7 c9")
        // Complete pinned direct interpretation leaves, independently assembled from source.
        emit("copyBytes", "04 0c 18 03 2a 12 13 0d 20 fa 05 20 f7 c9")
        emit("addNTimes", "a7 c8 09 3d 20 fc c9")
        emit("copyAttrs", "11 @attrState 0e 0c 2a 12 13 0d 20 fa c9")
        emit("readEvents", "f5 21 @mapEventsPointer 2a 66 6f 23 23 cd @readWarp cd @readCoord cd @readBG f1 a7 c0 cd @graphicsD c9")
        for ((name, prefix, width) in listOf(Triple("readWarp", "warp", "05"), Triple("readCoord", "coord", "08"), Triple("readBG", "bg", "05"))) {
            emit(name, "2a 4f ea @${prefix}Count 7d ea @${prefix}Pointer 7c ea @${prefix}PointerHi 79 a7 c8 01 $width 00 cd @addNTimes c9")
        }
        emit("switchScripts", "fa @mapScriptsBank d7 c9")
        emit("getScripts", "fa @mapScriptsBank c9")
        emit("facing", "cd @graphicsE 47 7a d6 04 57 7b d6 04 5f fa @bgCount a7 c8 4f f0 9f f5 cd @switchScripts cd @facingRows e1 7c d7 c9")
        emit("facingRows", "21 @bgPointer 2a 66 6f e5 2a bb 20 06 2a ba 20 02 18 0d e1 3e 05 85 6f 30 01 24 0d 20 ea af c9 e1 11 @bgBuffer 01 05 00 cd @copyBytes 37 c9")
        emit("bgDispatch", "cd @facing 38 02 af c9 fa @bgKind 21 @bgTable ef c9")
        word(at("bgTable"), at("bgRead"))
        emit("bgRead", "cd @talk 21 @bgScript 2a 66 6f cd @getScripts cd @callScript 37 c9")
        emit("callScript", "ea @scriptBankState 7d ea @scriptPointerState 7c ea @scriptPointerHi 3e ff ea @scriptMode 37 c9")
        emit("scriptDispatch", "cd @getByte 21 @scriptTable ef c9")
        word(at("scriptTable") + 0x53 * 2, at("direct"))
        word(at("scriptTable") + 0x48 * 2, at("openHandler"))
        word(at("scriptTable") + 0x4E * 2, at("repeat"))
        emit("getByte", "e5 c5 f0 9f f5 fa @scriptBankState d7 21 @scriptPointerState 4e 23 46 0a 03 70 2b 71 47 f1 d7 78 c1 e1 c9")
        emit("direct", "fa @scriptBankState ea @textBankState cd @getByte ea @textPointerState cd @getByte ea @textPointerHi 06 #codeBank 21 @template c3 @scriptJump")
        emit("template", "48 4e ff ff 54 4a 91")
        emit("scriptJump", "78 ea @scriptBankState 7d ea @scriptPointerState 7c ea @scriptPointerHi c9")
        emit("repeat", "cd @getByte 6f cd @getByte 67 fe ff 20 11 7d fe ff 20 0c 21 @textBankState 2a 47 2a 66 6f cd @mapTextbox c9 c9")
        emit("openHandler", "cd @openText c9")
        emit("openText", "cd @clearWindow f0 9f f5 3e 01 d7 cd @graphicsA cd @speech cd @hdma cd @graphicsB f1 d7 c9")
        emit("clearWindow", "3b e5 f5 e5 f8 06 36 7f 2b 36 46 2b 36 1e e1 f1 cd @farCall 33 33 33 c9")
        emit("farCall", "e5 e5 f5 c5 e5 f8 0e f0 9f 47 7e d7 70 2b 46 2b 4e 2b 2b 2b 36 #farReturnHi 2b 36 #farReturnLo 2b 70 2b 71 e1 c1 f1 c9")
        emit("hdma", "f0 da f5 3e 01 e0 da cd @graphicsD f1 e0 da c9")
        emit("mapTextbox", "f0 9f f5 78 d7 cd @setup 3e 01 e0 da cd @printText af e0 da f1 d7 c9")
        emit("setup", "e5 cd @speech cd @graphicsC cd @graphicsE e1 c9")
        emit("speech", "21 90 c4 06 04 0e 12 c3 @graphicsD")
        emit("printText", "01 @tileOrigin cd @printAt c9")
        emit("printAt", "fa @flags f5 cb cf ea @flags cd @textDispatch f1 ea @flags c9")
        emit("textDispatch", "2a fe 50 c8 cd @textDispatchStep 18 f7 e5 c5 4f 06 00 21 @textCommands 09 09 5e 23 56 c1 e1 d5 c9")
        word(at("textCommands"), at("textStart"))
        emit("textStart", "54 5d 60 69 cd @placeString 62 6b 23 c9")
        emit("placeString", "e5 1a fe 50 20 09 44 4d e1 c9 d1 13 c3 @nextPlace fe 0c da @doubleByte fe 5a ca @line fe 5e ca @done")
        emit("line", "e1 21 @lineOrigin e5 c3 @nextChar")
        emit("done", "e1 11 @stopByte 1b c9 50")
        emit("doubleByte", "47 13 1a 4f 3b e5 f5 e5 f8 06 36 7f 2b 36 46 2b 36 1e e1 f1 cd @farCall 33 33 33 cd @graphicsE c3 @nextChar")
    }

    fun at(name: String): Int = requireNotNull(symbols[name]) { name }
    fun word(offset: Int, target: Int, romAddress: Boolean = true) {
        val value = if (romAddress && target in 0x4000 until bytes.size) target % 0x4000 + 0x4000 else target
        bytes[offset] = value.toByte(); bytes[offset + 1] = (value ushr 8).toByte()
    }
    fun emit(name: String, assembly: String) {
        var cursor = at(name)
        assembly.split(' ').forEach { token ->
            when {
                token.startsWith('@') -> { val name = token.drop(1); word(cursor, at(name), name in romSymbols); cursor += 2 }
                token.startsWith('#') -> bytes[cursor++] = at(token.drop(1)).toByte()
                else -> bytes[cursor++] = token.toInt(16).toByte()
            }
        }
    }
    fun raw(offset: Int, hex: String) = hex.split(' ').map { it.toInt(16).toByte() }.toByteArray().copyInto(bytes, offset)
}
