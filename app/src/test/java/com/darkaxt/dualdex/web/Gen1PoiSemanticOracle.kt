package com.darkaxt.dualdex.web

import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiService
import com.enrpau.dualscreendex.parser.model.EngineFamily

/** TEST ONLY. Source-ratified official Japanese Gen I POI semantics. */
internal object Gen1PoiSemanticOracle {
    data class Expectation(
        val contextualKeys: Set<String>,
        val contextualDestinationKeys: Set<String>,
        val unresolvedKeys: Set<String>,
    )

    // pret/pokered 2ab2421410b764e4dfebeddf8d9249d2cba947c4 and pret/pokeyellow
    // e6ba56989b0f2694f393e6924820be11dcc1fbb8 supply the structural event oracle.
    // The exact compiled controls independently prove retained rows and the LAST_MAP consumer.
    private val sharedContextualDestinations = indexedKeys("warp", """
        0025:0-1 0027:0-1 0028:0-1 0029:0-1 002a:0-1 002b:0-1 002c:0-1 002d:0-1
        002e:0-1 002f:0-1 0030:0-1 0031:0-3 0032:2-3 0034:0-3 0036:0-1 0037:0-1
        0038:0-1 0039:0-1 003a:0-1 003b:0-1 003c:7 003e:0-1 003f:0-1 0040:0-1
        0041:0-1 0042:0-1 0043:0-1 0044:0-1 0045:0-1 0046:0-3 0047:0-1 0048:0-1
        0049:0-3 004a:0-1 004b:0-1 004c:0-3 004d:0-1 004e:0-1 004f:0-3 0050:0-1
        0051:0-1 0052:0-3 0053:0-2 0054:0-3 0055:0-1 0057:0-3 0058:0-1 0059:0-1
        005a:0-1 005b:0-1 005c:0-1 005d:0-1 005e:0 006c:0-1 007a:0-3 0080:1-2
        0085:0-1 0086:0-1 0087:0-1 0089:0-1 008a:0-1 008b:0-1 008c:0-1 008d:0-1
        008e:0-1 0095:0-1 0096:0-1 0097:0-1 0098:0-1 0099:0-1 009a:0-1 009b:0-1
        009c:0-1 009d:0-1 009e:0-1 00a3:0-1 00a4:0-2 00a5:0-3,6-7 00a6:0-1
        00a7:0-1 00ab:0-1 00ac:0-1 00ad:0-1 00ae:0-1 00af:0-1 00b1:0-1 00b2:0-1
        00b3:0-1 00b4:0-1 00b5:0-1 00b6:0-1 00b7:0-1 00b8:0-3 00ba:0-7 00bc:0-1
        00bd:0-1 00be:0-3 00c0:0-3 00c1:0-3 00c2:1-2 00c4:0-1 00e4:0-1 00e5:0-1
        00e6:0-2 00eb:2
    """).also { require(it.size == 248) }

    private val dynamicStandardScripts = indexedKeys("bg", "007e:0-2 0089:0-2")
        .also { require(it.size == 6) }

    private val redBlueUnresolved = indexedKeys("bg", """
        0007:8-13 0025:0 0056:0-1 007f:0 0087:0 009b:0-1 00b0:1 00b9:0
        00bb:0-1 00bf:0-1 00c0:0-1 00c3:0-1 00cb:0 00ec:0
    """).also { require(it.size == 25) }

    private val yellowUnresolved = indexedKeys("bg", """
        0001:0-2,5 0005:0-1,4-6 0006:0 0007:8-13 000c:0 0021:0 0025:0 0033:0-5
        0056:0-1 007f:0 0082:0-3 0087:0 009b:0-1 00b0:1 00b9:0 00bb:0-1
        00bf:0-1 00c3:0-1 00cb:0 00ec:0 00f8:0-3
    """).also { require(it.size == 49) }

    val serviceKeys = linkedMapOf(
        LocalMapPoiService.POKEMON_CENTER to keys("""
            local/0001/bg/4 local/0002/bg/3 local/0003/bg/3 local/0004/bg/3
            local/0005/bg/3 local/0006/bg/2 local/0007/bg/4 local/0008/bg/2
            local/000a/bg/7 local/000f/bg/0 local/0015/bg/1
        """),
        LocalMapPoiService.MART to keys("""
            local/0001/bg/3 local/0002/bg/2 local/0003/bg/2 local/0004/bg/2
            local/0005/bg/2 local/0007/bg/3 local/0008/bg/1 local/000a/bg/3
        """),
    )

    fun expectation(family: EngineFamily): Expectation = when (family) {
        EngineFamily.RED_BLUE -> Expectation(
            contextualKeys = sharedContextualDestinations + dynamicStandardScripts,
            contextualDestinationKeys = sharedContextualDestinations,
            unresolvedKeys = redBlueUnresolved,
        )
        EngineFamily.YELLOW -> {
            val destinations = sharedContextualDestinations + indexedKeys("warp", "00f8:0-1")
            Expectation(
                contextualKeys = destinations + dynamicStandardScripts,
                contextualDestinationKeys = destinations,
                unresolvedKeys = yellowUnresolved,
            )
        }
        else -> error("not an official Gen I family: $family")
    }

    private fun indexedKeys(kind: String, source: String): Set<String> = buildSet {
        source.trimIndent().split(Regex("\\s+")).filter(String::isNotBlank).forEach { token ->
            val (map, indexes) = token.split(':', limit = 2)
            indexes.split(',').forEach { range ->
                val bounds = range.split('-', limit = 2).map(String::toInt)
                val values = if (bounds.size == 1) bounds[0]..bounds[0] else bounds[0]..bounds[1]
                values.forEach { index -> add("local/$map/$kind/$index") }
            }
        }
    }

    private fun keys(source: String): Set<String> =
        source.trimIndent().split(Regex("\\s+")).filter(String::isNotBlank).toSet()
}
