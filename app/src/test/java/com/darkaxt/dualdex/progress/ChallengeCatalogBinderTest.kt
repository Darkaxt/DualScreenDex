package com.darkaxt.dualdex.progress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ChallengeCatalogBinderTest {
    @Test
    fun `templates bind to supplied semantic roles instead of game identity`() {
        val templates = PortableChallengeCatalog.decodeTemplates(
            java.io.File("src/main/assets/challenges/portable-extended.json").readBytes(),
        )
        val bindings = ChallengeCatalogBindings(
            badgeCount = 8,
            regionalSpeciesIds = setOf(1, 4, 7),
            areaCollectibles = listOf(
                AreaCollectibleBinding("area-7", "Green Path", setOf("item-a", "item-b")),
            ),
            gymLeaders = listOf(GymLeaderBinding("leader-2", "River Leader")),
            provenAdapters = setOf("MINIGAME:contest"),
            provenTemporalWindows = setOf("PLAYTHROUGH", "BATTLE_EPOCH", "GAME_SPECIFIC"),
        )

        val bound = ChallengeCatalogBinder.bind(templates, bindings)

        assertEquals(6, bound.size)
        assertTrue(bound.any { it.key == "progress-first-badge" })
        assertTrue(bound.any { it.key == "progress-all-badges" && it.predicate.evaluate(ChallengeContext(metrics = mapOf("trainer.badges" to 8))).complete })
        assertTrue(bound.any { it.key == "collection-regional-record" })
        assertTrue(bound.any { it.key == "exploration-area-items-area-7" && "Green Path" in it.description })
        assertTrue(bound.any { it.key == "battle-leader-no-items-leader-2" && "River Leader" in it.description })
        assertTrue(bound.any { it.key == "special-minigame-contest" })
        assertFalse(bound.any { "rom" in it.key.lowercase() })
    }

    @Test
    fun `removing one role or adapter removes only dependent definitions`() {
        val templates = PortableChallengeCatalog.decodeTemplates(
            java.io.File("src/main/assets/challenges/portable-extended.json").readBytes(),
        )
        val complete = ChallengeCatalogBindings(
            badgeCount = 8,
            regionalSpeciesIds = setOf(1, 2),
            areaCollectibles = listOf(AreaCollectibleBinding("area-1", "First Area", setOf("item-1"))),
            gymLeaders = listOf(GymLeaderBinding("leader-1", "First Leader")),
            provenAdapters = setOf("MINIGAME:contest"),
            provenTemporalWindows = setOf("PLAYTHROUGH", "BATTLE_EPOCH", "GAME_SPECIFIC"),
        )

        val all = ChallengeCatalogBinder.bind(templates, complete)
        val withoutLeader = ChallengeCatalogBinder.bind(templates, complete.copy(gymLeaders = emptyList()))
        val withoutAdapter = ChallengeCatalogBinder.bind(templates, complete.copy(provenAdapters = emptySet()))
        val withoutBattleWindow = ChallengeCatalogBinder.bind(
            templates,
            complete.copy(provenTemporalWindows = setOf("PLAYTHROUGH", "GAME_SPECIFIC")),
        )

        assertEquals(all.map { it.key }.filterNot { it.startsWith("battle-leader") }, withoutLeader.map { it.key })
        assertEquals(all.map { it.key }.filterNot { it.startsWith("special-minigame") }, withoutAdapter.map { it.key })
        assertEquals(all.map { it.key }.filterNot { it.startsWith("battle-leader") }, withoutBattleWindow.map { it.key })
    }

    @Test
    fun `reordered bindings preserve identifiers and ambiguous identifiers are rejected`() {
        val templates = PortableChallengeCatalog.decodeTemplates(
            java.io.File("src/main/assets/challenges/portable-extended.json").readBytes(),
        )
        val first = AreaCollectibleBinding("area-1", "First Area", setOf("item-1"))
        val second = AreaCollectibleBinding("area-2", "Second Area", setOf("item-2"))
        val forward = ChallengeCatalogBinder.bind(
            templates,
            ChallengeCatalogBindings(areaCollectibles = listOf(first, second)),
        )
        val reversed = ChallengeCatalogBinder.bind(
            templates,
            ChallengeCatalogBindings(areaCollectibles = listOf(second, first)),
        )

        assertEquals(forward.map { it.key }, reversed.map { it.key })
        try {
            ChallengeCatalogBindings(areaCollectibles = listOf(first, first.copy(displayName = "Ambiguous")))
            fail("duplicate semantic identifiers must fail closed")
        } catch (_: IllegalArgumentException) {
            // Expected: an ambiguous identifier must never silently select one candidate.
        }
        try {
            ChallengeCatalogBindings(provenAdapters = setOf("MINIGAME:../../retail-offset"))
            fail("invalid adapter identifiers must fail closed")
        } catch (_: IllegalArgumentException) {
            // Expected: adapters are explicit proven semantic bindings, not locator strings.
        }
    }
}
