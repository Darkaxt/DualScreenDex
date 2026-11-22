package com.darkaxt.dualdex.progress

import com.darkaxt.dualdex.catalog.CatalogReader
import com.darkaxt.dualdex.web.JdbcTestCatalogDatabaseFactory
import com.google.gson.Gson
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class ChallengeGeneratedCatalogControlsTest {
    @Test
    fun generatedCurrentCatalogsBindTheReviewedHackInventoryWithoutOpeningOriginals() {
        val configured = System.getenv("DUALDEX_TEST_GENERATED_CATALOG_ROOT")
        assumeTrue("set DUALDEX_TEST_GENERATED_CATALOG_ROOT to qualify already-generated catalogs", !configured.isNullOrBlank())
        val root = Path.of(requireNotNull(configured))
        val scratch = Path.of(requireNotNull(System.getenv("DUALDEX_TEST_TEMP_ROOT")))
        Files.createDirectories(scratch)
        val working = Files.createTempDirectory(scratch, "generated-challenge-catalogs-")
        val resource = requireNotNull(javaClass.getResourceAsStream("/challenges/real-control-inventory.json"))
        val controls = resource.reader().use { Gson().fromJson(it, Manifest::class.java).controls }
            .filter { it.id in setOf("modern-emerald", "unbound", "odyssey") }
        assertEquals(setOf("modern-emerald", "unbound", "odyssey"), controls.map { it.id }.toSet())
        assertEquals(3, controls.size)
        val templates = PortableChallengeCatalog.decodeTemplates(
            File("src/main/assets/challenges/portable-extended.json").readBytes(),
        )
        controls.forEach { expected ->
            assertTrue(expected.sha256.matches(Regex("[0-9a-f]{64}")))
            val original = root.resolve("${expected.sha256}.sqlite")
            assertTrue("${expected.id} generated catalog is missing", Files.isRegularFile(original))
            val before = digest(original)
            // Reader migrations operate only on a disposable generated-database copy.
            val copy = Files.copy(original, working.resolve("${expected.sha256}.sqlite"))
            val catalog = JdbcTestCatalogDatabaseFactory.open(copy.toFile()).use { database ->
                requireNotNull(CatalogReader(database).readComplete()) {
                    "${expected.id} catalog is incomplete or has a different parser/schema version"
                }.catalog
            }
            assertEquals(expected.id, before, digest(original))
            assertEquals(expected.sha256, catalog.romSha256)
            val bindings = ChallengeCatalogRoleResolver.resolve(catalog)
            val inventory = ChallengeCatalogBinder.bind(templates, bindings)
            assertEquals("${expected.id} inventory", expected.inventory, inventory.size)
            assertEquals("${expected.id} badges", expected.badgeCount, bindings.badgeCount)
            assertEquals("${expected.id} regional species", expected.regionalSpecies, bindings.regionalSpeciesIds.size)
            assertEquals("${expected.id} collectible areas", expected.collectibleAreas, bindings.areaCollectibles.size)
            assertEquals("${expected.id} leaders", expected.gymLeaders, bindings.gymLeaders.size)
            assertEquals("${expected.id} adapters", expected.adapters, bindings.provenAdapters.size)
            assertEquals(inventory, ChallengeCatalogBinder.bind(templates, ChallengeCatalogRoleResolver.resolve(catalog)))
            assertTrue(inventory.none { it.key.startsWith("battle-leader") || it.key.startsWith("special-minigame") })
            val withoutBadges = ChallengeCatalogBinder.bind(templates, bindings.copy(badgeCount = null))
            assertEquals(inventory.map { it.key }.filterNot { it.startsWith("progress-") }, withoutBadges.map { it.key })
            println("GENERATED_CHALLENGE_CONTROL ${expected.id} inventory=${inventory.size} " +
                "badges=${bindings.badgeCount} regional=${bindings.regionalSpeciesIds.size} " +
                "areas=${bindings.areaCollectibles.size} leaders=${bindings.gymLeaders.size} " +
                "adapters=${bindings.provenAdapters.size}")
        }
    }

    private fun digest(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val size = input.read(buffer)
                if (size < 0) break
                digest.update(buffer, 0, size)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private data class Manifest(val controls: List<ExpectedInventory>)
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
}
