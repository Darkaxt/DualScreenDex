package com.enrpau.dualscreendex.server

import com.enrpau.dualscreendex.parser.catalog.LearnsetRuleset
import com.enrpau.dualscreendex.parser.catalog.ParsedCatalog
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DualDexRuntimeTest {
    @Test
    fun autoUsesResidentPrimaryAndManualSelectionOnlyChangesActiveId() {
        val runtime = DualDexRuntime()
        runtime.loadCatalog(
            "fixture.gba",
            ParsedCatalog(
                "sha",
                EngineFamily.EMERALD,
                Platform.GBA,
                learnsetRulesets = listOf(
                    LearnsetRuleset("base", "Base", 100, 1.0, emptyMap(), primary = true),
                    LearnsetRuleset("modern", "Expanded", 200, 1.0, emptyMap()),
                ),
            ),
        )

        assertEquals("base", runtime.stateView().activeRulesetId)
        assertTrue(runtime.stateView().rulesetAssumed)

        val selected = runtime.action("SETTINGS", mapOf("ruleset" to "modern"))
        assertEquals("modern", selected.activeRulesetId)
        assertEquals(false, selected.rulesetAssumed)

        assertThrows(IllegalArgumentException::class.java) {
            runtime.action("SETTINGS", mapOf("ruleset" to "missing"))
        }
        assertEquals("modern", runtime.stateView().activeRulesetId)
    }
}
