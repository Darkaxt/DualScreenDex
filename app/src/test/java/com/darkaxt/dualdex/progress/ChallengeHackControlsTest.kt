package com.darkaxt.dualdex.progress

import com.enrpau.dualscreendex.parser.catalog.CatalogParser
import com.enrpau.dualscreendex.parser.io.RomSourceLoader
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class ChallengeHackControlsTest {
    @Test
    fun `source backed hacks bind from structures and reject unavailable leader and adapter roles`() {
        val templates = PortableChallengeCatalog.decodeTemplates(
            File("src/main/assets/challenges/portable-extended.json").readBytes(),
        )
        val expectedById = expectedInventories().associateBy(ExpectedInventory::id)
        CONTROLS.forEach { control ->
            val configured = System.getenv(control.environment)?.takeIf(String::isNotBlank)
            val path = Path.of(configured ?: control.fallback)
            if (configured == null) assumeTrue("${control.id} ROM does not exist: $path", Files.isRegularFile(path))
            else assertTrue("configured ${control.id} ROM does not exist: $path", Files.isRegularFile(path))
            val loaded = RomSourceLoader.load(path)
            assertEquals(control.sha256, loaded.rom.sha256)
            val catalog = requireNotNull(CatalogParser.parse(loaded.rom).catalog)
            val bindings = ChallengeCatalogRoleResolver.resolve(catalog)
            val inventory = ChallengeCatalogBinder.bind(templates, bindings)
            val expected = requireNotNull(expectedById[control.id])

            assertTrue("${control.id} lost its regional group", bindings.regionalSpeciesIds.isNotEmpty())
            assertEquals("${control.id} badge sequence", 8, bindings.badgeCount)
            assertTrue(inventory.none { it.key.startsWith("battle-leader") })
            assertTrue(inventory.none { it.key.startsWith("special-minigame") })
            assertEquals(inventory, ChallengeCatalogBinder.bind(templates, ChallengeCatalogRoleResolver.resolve(catalog)))
            assertEquals(expected.sha256, loaded.rom.sha256)
            assertEquals(expected.inventory, inventory.size)
            assertEquals(expected.badgeCount, bindings.badgeCount)
            assertEquals(expected.regionalSpecies, bindings.regionalSpeciesIds.size)
            assertEquals(expected.collectibleAreas, bindings.areaCollectibles.size)
            assertEquals(expected.gymLeaders, bindings.gymLeaders.size)
            assertEquals(expected.adapters, bindings.provenAdapters.size)

            val withoutBadges = ChallengeCatalogBinder.bind(templates, bindings.copy(badgeCount = null))
            assertEquals(
                inventory.map { it.key }.filterNot { it.startsWith("progress-") },
                withoutBadges.map { it.key },
            )
            println(
                "CHALLENGE_CONTROL ${control.id} inventory=${inventory.size} " +
                    "badges=${bindings.badgeCount} regional=${bindings.regionalSpeciesIds.size} " +
                    "areas=${bindings.areaCollectibles.size} leaders=0 adapters=0",
            )
        }
    }

    private data class Control(
        val id: String,
        val environment: String,
        val fallback: String,
        val sha256: String,
    )

    private fun expectedInventories(): List<ExpectedInventory> {
        val resource = requireNotNull(javaClass.getResourceAsStream("/challenges/real-control-inventory.json"))
        return resource.reader().use { Gson().fromJson(it, ExpectedInventoryManifest::class.java).controls }
    }

    private data class ExpectedInventoryManifest(val controls: List<ExpectedInventory>)
    private data class ExpectedInventory(
        val id: String,
        val sha256: String,
        val inventory: Int,
        val badgeCount: Int,
        val regionalSpecies: Int,
        val collectibleAreas: Int,
        val gymLeaders: Int,
        val adapters: Int,
    )

    private companion object {
        val CONTROLS = listOf(
            Control(
                "modern-emerald",
                "DUALDEX_MODERN_EMERALD_ROM",
                "D:/Temp/PokemonHacks/corpus/expanded/roms/0116-a0b4e5e9c0c4/Modern Emerald (v3.5).gba",
                "21a0306c4e5b5dc15ca70b74e713e3140612c1045aa298072993a6c5dd8d6895",
            ),
            Control(
                "unbound",
                "DUALDEX_UNBOUND_ROM",
                "D:/Temp/PokemonHacks/corpus/expanded/roms/0199-a275be0f927e/Unbound (v2.1.1.1).gba",
                "7aa25bbf568f7cfcf6ee1cf2e9e6ff637350b3d0705c2375cabb6baa7d9739f7",
            ),
            Control(
                "odyssey",
                "DUALDEX_ODYSSEY_ROM",
                "D:/Temp/PokemonHacks/corpus/expanded/roms/0123-5e7ce46db2ce/Odyssey (v4.1.1).gba",
                "44c7e3eafab19c39df7c39d54bafb78a1d9caf7c371244b6f5efb12cfd98d0d0",
            ),
        )
    }
}
