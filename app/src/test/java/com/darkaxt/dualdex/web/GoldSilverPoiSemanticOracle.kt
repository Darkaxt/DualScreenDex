package com.darkaxt.dualdex.web

import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiService

/** TEST ONLY. Source-ratified Gen II POI semantics; never production routing or decoded output. */
internal object GoldSilverPoiSemanticOracle {
    enum class ContextReason {
        CONTEXT_DEPENDENT_DESTINATION,
        CONDITIONAL_EVENT,
        STATE_SELECTED_TEXT,
        RUNTIME_STANDARD_TEXT,
        RUNTIME_SPECIAL_OR_MENU,
        WRAM_SUBSTITUTED_HEADLINE,
    }

    // pret/pokegold a0dad0957ac8a9ffa67e950ee3ab6715a212ded5:
    // constants/map_constants.asm, maps/*.asm and engine/events/std_scripts.asm.
    // The compiled controls independently prove the event rows, command handlers and standard table.
    val contextualKeysByReason = linkedMapOf(
        ContextReason.CONTEXT_DEPENDENT_DESTINATION to keys("""
            local/0101/warp/2 local/0203/warp/2 local/0403/warp/2 local/0506/warp/2
            local/0601/warp/2 local/0704/warp/2 local/0708/warp/2 local/0801/warp/2
            local/0a0a/warp/2 local/0a0d/warp/2 local/0b09/warp/2 local/0c05/warp/2
            local/0e06/warp/2 local/1002/warp/2 local/110a/warp/2 local/1205/warp/2
            local/1303/warp/2 local/1401/warp/0 local/1401/warp/1 local/1401/warp/2
            local/1401/warp/3 local/1511/warp/2 local/1606/warp/2 local/1709/warp/2
            local/1906/warp/2 local/1a05/warp/2
        """),
        ContextReason.CONDITIONAL_EVENT to keys("""
            local/032a/bg/0 local/032a/bg/1 local/032b/bg/0 local/032b/bg/1
            local/1807/bg/3
        """),
        ContextReason.STATE_SELECTED_TEXT to keys("""
            local/0102/bg/0 local/0102/bg/1 local/0202/bg/0 local/0202/bg/1
            local/031c/bg/1 local/031c/bg/2 local/0329/bg/0 local/032a/bg/10
            local/032a/bg/11 local/032a/bg/12 local/032a/bg/13 local/032a/bg/14
            local/032a/bg/15 local/032a/bg/16 local/032a/bg/17 local/032a/bg/18
            local/032a/bg/19 local/032a/bg/2 local/032a/bg/20 local/032a/bg/21
            local/032a/bg/3 local/032a/bg/4 local/032a/bg/5 local/032a/bg/6
            local/032a/bg/7 local/032a/bg/8 local/032a/bg/9 local/032d/bg/0
            local/0407/bg/0 local/0407/bg/1 local/0501/bg/0 local/0501/bg/1
            local/0706/bg/1 local/0706/bg/2 local/0805/bg/0 local/0805/bg/1
            local/0a07/bg/0 local/0a07/bg/1 local/0b03/bg/0 local/0b03/bg/1
            local/0c0b/bg/15 local/0c0b/bg/16 local/0e04/bg/0 local/0e04/bg/1
            local/1108/bg/0 local/1108/bg/1 local/1515/bg/0 local/1515/bg/1
            local/1516/bg/1 local/1605/bg/0 local/1605/bg/1 local/1704/bg/0
            local/1704/bg/1 local/1805/bg/0 local/1805/bg/14 local/1807/bg/1
            local/1808/bg/2 local/1904/bg/0
        """),
        ContextReason.RUNTIME_STANDARD_TEXT to keys("""
            local/0404/bg/0 local/0408/bg/0 local/0802/bg/2 local/0804/bg/0
            local/0b05/bg/2 local/0b06/bg/2 local/0b0a/bg/2 local/0b0b/bg/2
        """),
        ContextReason.RUNTIME_SPECIAL_OR_MENU to keys("""
            local/0317/bg/2 local/0318/bg/2 local/0319/bg/2 local/031a/bg/2
            local/0b12/bg/0 local/0b13/bg/0 local/0b13/bg/1 local/0b13/bg/10
            local/0b13/bg/11 local/0b13/bg/12 local/0b13/bg/13 local/0b13/bg/14
            local/0b13/bg/15 local/0b13/bg/16 local/0b13/bg/17 local/0b13/bg/18
            local/0b13/bg/19 local/0b13/bg/2 local/0b13/bg/20 local/0b13/bg/21
            local/0b13/bg/22 local/0b13/bg/23 local/0b13/bg/24 local/0b13/bg/25
            local/0b13/bg/26 local/0b13/bg/27 local/0b13/bg/28 local/0b13/bg/29
            local/0b13/bg/3 local/0b13/bg/4 local/0b13/bg/5 local/0b13/bg/6
            local/0b13/bg/7 local/0b13/bg/8 local/0b13/bg/9 local/1401/bg/0
            local/1402/bg/0 local/1402/bg/1 local/1403/bg/0 local/1403/bg/1
            local/1404/bg/0 local/1404/bg/1 local/150b/bg/0 local/1513/bg/0
            local/1513/bg/1 local/1513/bg/10 local/1513/bg/11 local/1513/bg/12
            local/1513/bg/13 local/1513/bg/14 local/1513/bg/15 local/1513/bg/16
            local/1513/bg/17 local/1513/bg/18 local/1513/bg/19 local/1513/bg/2
            local/1513/bg/20 local/1513/bg/21 local/1513/bg/22 local/1513/bg/23
            local/1513/bg/24 local/1513/bg/25 local/1513/bg/26 local/1513/bg/27
            local/1513/bg/28 local/1513/bg/29 local/1513/bg/3 local/1513/bg/30
            local/1513/bg/31 local/1513/bg/33 local/1513/bg/34 local/1513/bg/35
            local/1513/bg/4 local/1513/bg/5 local/1513/bg/6 local/1513/bg/7
            local/1513/bg/8 local/1807/bg/0
        """),
        ContextReason.WRAM_SUBSTITUTED_HEADLINE to keys("""
            local/0d04/bg/0 local/150d/bg/0 local/1804/bg/1
            local/1805/bg/5 local/1805/bg/6 local/1805/bg/7 local/1805/bg/8
        """),
    )

    val contextualKeys = contextualKeysByReason.values.flatten().toSet().also {
        require(it.size == contextualKeysByReason.values.sumOf(Set<String>::size))
        require(it.size == 182)
    }

    // Every row calls zero-based StdScripts index 20 (ElevatorButtonScript), whose
    // compiled path is playsound, pause, playsound, end and has no text operand.
    val noTextKeys = keys("""
        local/0b0c/bg/1 local/0b0d/bg/1 local/0b0e/bg/1 local/0b0f/bg/1
        local/0b10/bg/1 local/0b11/bg/1 local/1505/bg/1 local/1506/bg/1
        local/1507/bg/1 local/1508/bg/1 local/1509/bg/1 local/150a/bg/1
    """)

    // Gold/Silver has no player-gender selector. No retained POI is gender-conditioned.
    val genderedDirectTextKeys = emptySet<String>()

    // Every row calls the compiled StdScripts table: index 16 is PokecenterSignScript
    // and index 17 is MartSignScript. Both paths end in a direct ROM-native jumptext.
    val serviceKeys = linkedMapOf(
        LocalMapPoiService.POKEMON_CENTER to keys("""
            local/010e/bg/4 local/0207/bg/3 local/0409/bg/5 local/050a/bg/6
            local/0608/bg/0 local/070e/bg/1 local/0711/bg/6 local/0807/bg/5
            local/0a01/bg/3 local/0a05/bg/4 local/0b02/bg/10 local/0c03/bg/5
            local/0e02/bg/5 local/1105/bg/6 local/1204/bg/4 local/1302/bg/0
            local/1504/bg/6 local/1603/bg/2 local/1703/bg/4 local/1902/bg/6
            local/1a03/bg/3
        """),
        LocalMapPoiService.MART to keys("""
            local/010e/bg/5 local/0409/bg/6 local/050a/bg/5 local/0711/bg/7
            local/0807/bg/6 local/0a05/bg/5 local/0c03/bg/6 local/0e02/bg/6
            local/1105/bg/7 local/1204/bg/5 local/1703/bg/5 local/1902/bg/7
            local/1a03/bg/2
        """),
    )

    private fun keys(source: String): Set<String> {
        val values = source.trimIndent().split(Regex("\\s+")).filter(String::isNotBlank)
        require(values.size == values.toSet().size)
        return values.toSet()
    }
}
