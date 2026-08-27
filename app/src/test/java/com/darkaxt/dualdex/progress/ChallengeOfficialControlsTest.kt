package com.darkaxt.dualdex.progress

import com.enrpau.dualscreendex.parser.catalog.CatalogParser
import com.enrpau.dualscreendex.parser.io.RomSourceLoader
import com.google.gson.Gson
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class ChallengeOfficialControlsTest {
    @Test
    fun `all eleven official controls produce deterministic role bound inventories and results`() {
        val manifest = manifest()
        val root = Path.of(System.getenv("DUALDEX_OFFICIAL_ROM_ROOT") ?: OFFICIAL_ROOT)
        assumeTrue("official ROM root does not exist: $root", Files.isDirectory(root))
        val templates = templates()
        val expectedById = expectedInventories().associateBy(ExpectedInventory::id)

        manifest.official.forEach { control ->
            val loaded = RomSourceLoader.load(root.resolve(control.relativePath))
            assertEquals(control.sha256, loaded.rom.sha256)
            val catalog = requireNotNull(CatalogParser.parse(loaded.rom).catalog)
            val firstBindings = ChallengeCatalogRoleResolver.resolve(catalog)
            val secondBindings = ChallengeCatalogRoleResolver.resolve(catalog)
            assertEquals(firstBindings, secondBindings)
            val firstInventory = ChallengeCatalogBinder.bind(templates, firstBindings)
            val secondInventory = ChallengeCatalogBinder.bind(templates, secondBindings)
            assertEquals(firstInventory, secondInventory)
            assertTrue("${control.id} lost its regional group", firstBindings.regionalSpeciesIds.isNotEmpty())
            if (control.generation == 3) assertEquals("${control.id} badge sequence", 8, firstBindings.badgeCount)
            else assertEquals("${control.id} must not guess badge roles", null, firstBindings.badgeCount)
            assertTrue(firstBindings.gymLeaders.isEmpty())
            assertTrue(firstBindings.provenAdapters.isEmpty())
            assertInventory(requireNotNull(expectedById[control.id]), loaded.rom.sha256, firstInventory, firstBindings)
            assertDeterministicResults(firstInventory, firstBindings)
            println(
                "CHALLENGE_CONTROL ${control.id} inventory=${firstInventory.size} " +
                    "badges=${firstBindings.badgeCount ?: 0} regional=${firstBindings.regionalSpeciesIds.size} " +
                    "areas=${firstBindings.areaCollectibles.size} leaders=0 adapters=0",
            )
        }
    }

    private fun assertDeterministicResults(
        definitions: List<ChallengeDefinition>,
        bindings: ChallengeCatalogBindings,
    ) {
        val capabilities = definitions.flatMapTo(linkedSetOf()) { it.requiredCapabilities }
        val complete = ChallengeContext(
            metrics = mapOf("trainer.badges" to (bindings.badgeCount ?: 0)),
            sets = mapOf(
                "pokedex.caughtSpeciesIds" to bindings.regionalSpeciesIds.mapTo(sortedSetOf(), Int::toString),
                "map.collectedPoiKeys" to bindings.areaCollectibles.flatMapTo(sortedSetOf()) { it.poiKeys },
            ),
            capabilities = capabilities,
            resolvedCatalogEntities = bindings.resolvedCatalogEntities,
            knownCatalogEntities = bindings.areaCollectibles.mapTo(linkedSetOf()) { "AREA:${it.key}" },
            provenAdapters = bindings.provenAdapters,
        )
        val engine = ChallengeEngine()
        val first = engine.evaluate(definitions, complete, emptyMap(), nowEpochMs = 1, saveFingerprint = null)
        val second = engine.evaluate(definitions, complete, emptyMap(), nowEpochMs = 1, saveFingerprint = null)
        assertEquals(first, second)
        assertTrue(first.visible.all { it.complete })
        val empty = engine.evaluate(
            definitions,
            complete.copy(metrics = emptyMap(), sets = emptyMap()),
            emptyMap(),
            nowEpochMs = 1,
            saveFingerprint = null,
        )
        assertFalse(empty.visible.any { it.complete })
    }

    private fun templates() = PortableChallengeCatalog.decodeTemplates(
        File("src/main/assets/challenges/portable-extended.json").readBytes(),
    )

    private fun expectedInventories(): List<ExpectedInventory> {
        val resource = requireNotNull(javaClass.getResourceAsStream("/challenges/real-control-inventory.json"))
        return resource.reader().use { Gson().fromJson(it, ExpectedInventoryManifest::class.java).controls }
    }

    private fun assertInventory(
        expected: ExpectedInventory,
        sha256: String,
        definitions: List<ChallengeDefinition>,
        bindings: ChallengeCatalogBindings,
    ) {
        assertEquals(expected.sha256, sha256)
        assertEquals(expected.inventory, definitions.size)
        assertEquals(expected.badgeCount.takeIf { it > 0 }, bindings.badgeCount)
        assertEquals(expected.regionalSpecies, bindings.regionalSpeciesIds.size)
        assertEquals(expected.collectibleAreas, bindings.areaCollectibles.size)
        assertEquals(expected.gymLeaders, bindings.gymLeaders.size)
        assertEquals(expected.adapters, bindings.provenAdapters.size)
    }

    private fun manifest(): IdentityManifest {
        val resource = requireNotNull(javaClass.getResourceAsStream("/unified-state/official-rom-identities.json"))
        return resource.reader().use { Gson().fromJson(it, IdentityManifest::class.java) }
    }

    private data class IdentityManifest(val official: List<IdentityControl>)
    private data class IdentityControl(
        val id: String,
        val generation: Int,
        val relativePath: String,
        val sha256: String,
    )
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
        const val OFFICIAL_ROOT = "D:/Temp/PokemonHacks/roms/official"
    }
}
