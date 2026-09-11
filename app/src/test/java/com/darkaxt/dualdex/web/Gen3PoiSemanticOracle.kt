package com.darkaxt.dualdex.web

import com.enrpau.dualscreendex.parser.model.EngineFamily

/** TEST ONLY. Source-ratified official Japanese Gen III POI semantics. */
internal object Gen3PoiSemanticOracle {
    data class Expectation(
        val contextualKeys: Set<String>,
        val noTextKeys: Set<String>,
        val genderedDirectTextKeys: Set<String>,
        val unresolvedKeys: Set<String>,
    )

    // pret/pokeruby 63a8cbf0016b351a4e68f7036fa0b77e23d2f2c1, pret/pokeemerald
    // 9a83a2bbe8e097e62c00f1dbd56849766775d7b6 and pret/pokefirered
    // c75f352304d529f6ba92d4f74b9cf8b5c3810788 supply the structural event oracle.
    // Exact compiled controls independently prove every retained record, MAP_DYNAMIC value 0x7f7f,
    // background kind 8, the lockall/checkplayergender command prefix, and warp targets whose
    // section 0x57 names are selected through the MAPSEC_DYNAMIC runtime consumer.
    fun expectation(family: EngineFamily): Expectation = when (family) {
        EngineFamily.RUBY_SAPPHIRE -> Expectation(
            contextualKeys = (indexedKeys("warp", """
                0d17:0-1 1900:0 1901:0 1902:0 1903:0 1904:0 1905:0 1906:0 1907:0
                1908:0 1909:0 190a:0 190b:0 190c:0 190d:0 190e:0 190f:0 1910:0 1911:0
                1912:0 1913:0 1914:0 1915:0 1916:0 1917:0 1918:0-1 1919:0-1 191a:0-3
                191b:0-3 1928:0-2 0203:1-4 0302:1-4 0406:1-4 0504:1-4 0604:1-4
                0701:1-4 0805:1-4 090b:1-4 0a06:1-4 0b06:1-4 0c03:1-4 0d07:1-4
                0e04:1-4 0f03:1-4 100d:1-4 1929:5,7 192a:0 192b:0-11
            """) + indexedKeys("bg", "0009:2-3 0101:0-1 0103:1,3 0304:0"))
                .also { require(it.size == 123) },
            noTextKeys = hoennSecretBases,
            genderedDirectTextKeys = emptySet(),
            unresolvedKeys = indexedKeys("bg", """
                0001:4,11 0005:0,7 0019:11 0101:2 0103:2 0303:0-3 0304:1 0401:0-1
                0501:0-3 0600:0-3 0801:0-13 0902:0-3 0a00:0-1 0a03:0-23
                0b03:0-1 0c01:0-1 0d03:0-9 0d04:0-5 0e00:0-5
                0f00:0-1 1806:0 180c:0 182f:0-5 1830:0 1831:0
                1835:0-7 1839:0 1841:0-3 1843:0-2 1844:0 1847:10
                1929:0-7 1a05:0 1a0a:0 1d00:0 1d03:0 1d04:0
                1d05:0 1d06:0 1d07:0 1d08:0 1d09:0-5 1d0a:0
            """).also { require(it.size == 146) },
        )
        EngineFamily.EMERALD -> Expectation(
            contextualKeys = (indexedKeys("warp", """
                0d16:0-1 1865:0 1868:0 1900:0 1901:0 1902:0 1903:0 1904:0 1905:0
                1906:0 1907:0 1908:0 1909:0 190a:0 190b:0 190c:0 190d:0 190e:0 190f:0
                1910:0 1911:0 1912:0 1913:0 1914:0 1915:0 1916:0 1917:0 1918:0-1
                1919:0-1 191a:0-3 191b:0-3 1928:0-2 193c:0-1 0203:1-2 0302:1-2
                0406:1-2 0505:1-2 0605:1-2 0701:1-2 0805:1-2 090c:1-2 0a06:1-2
                0b06:1-2 0c03:1-2 0d07:1-2 0e04:1-2 0f03:1-2 100d:1-2 100e:1-2
                1929:5,7 192a:0 192b:0-11 1a36:1-2
            """) + indexedKeys("bg", "0009:2-3 0101:0-1 0103:1,3 0304:0"))
                .also { require(it.size == 101) },
            noTextKeys = hoennSecretBases,
            genderedDirectTextKeys = emptySet(),
            unresolvedKeys = indexedKeys("bg", """
                0001:4,11-12 0005:0,7 0019:11 0101:2 0103:2 0303:0-3 0304:1 0401:0-1
                0801:0-13 0a00:0-1 0a03:0-23 0b03:0-1
                0c01:0-1 0d03:0-9 0d04:0,2-9,11 0e00:0-1 0e0b:1-2
                0f00:0-1 1806:0 180c:0 182f:0-5 1830:0 1831:0
                1835:0-7 1839:0 1841:0-3 1843:0-2 1844:0 1847:10
                1929:0-7 1a05:0-3 1a0a:0 1a12:0-2 1a16:0-1
                1a19:0 1a1c:0 1a1f:0-1 1a22:0 1a28:0-9 1a3c:0 1d00:0
                1d03:0 1d04:0 1d05:0 1d06:0 1d07:0 1d08:0 1d09:0 1d0a:0
            """).also { require(it.size == 156) },
        )
        EngineFamily.FIRERED_LEAFGREEN -> Expectation(
            contextualKeys = (indexedKeys("warp", """
                0000:0-1 0001:0-1 0002:0-3 0003:0-3 0004:0 012e:0-1 013a:0 020b:0
                0a06:0-1
            """) + indexedKeys("bg", "0300:1-2 0401:0")).also { require(it.size == 22) },
            noTextKeys = emptySet(),
            genderedDirectTextKeys = emptySet(),
            unresolvedKeys = indexedKeys("bg", """
                012e:0 0130:0-7 0131:0-7 0132:0-7 0133:1-12 0134:0-3 0135:0-11
                0136:0-3 0137:1-16 0138:0-3 0139:0-3 013a:0 013b:0 013c:0 013d:1
                013e:1-2 0166:0 0172:0,4-24 0178:0 020a:0 020b:0 0307:8 0338:0 0339:0 033d:0 0400:0 0401:1 0403:3
                0501:0-1 0602:0-1 0705:0-1 0709:0 0906:0-16
                0a06:0-1 0a0e:12-15,17-26,28-29,31-33 0a10:0-1 0b03:0-1
                0c00:0-13 0e03:0-1 1601:0 1702:0 1e00:0 2000:0-8 2100:0-1
                2900:0
            """).also { require(it.size == 200) },
        )
        else -> error("not an official Gen III family: $family")
    }

    private val hoennSecretBases = indexedKeys("bg", """
        0019:6-7 001a:4-6,8-9,11,13-14 001c:4 001d:2-7,10 001e:0-1,4-12
        001f:3-4,7-8 0021:0,3-6 0022:2-9,12-16 0023:2-8,13 0024:1-4
        0026:1-2,6 0028:0-3 002a:0-4
    """).also { require(it.size == 75) }

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
}
