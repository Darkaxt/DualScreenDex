package com.darkaxt.dualdex.progress

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PortableChallengeCatalogTest {
    @Test
    fun `bundled baseline is independently worded portable and parseable`() {
        val file = File("src/main/assets/challenges/portable-baseline.json")
        val definitions = PortableChallengeCatalog.decode(file.readBytes())

        assertEquals(6, definitions.size)
        assertEquals(definitions.size, definitions.map { it.key }.distinct().size)
        assertTrue(definitions.all { it.title.isNotBlank() && it.description.isNotBlank() })
        assertTrue(definitions.all { it.requiredCapabilities.isNotEmpty() && it.organicSafe })
        assertTrue(definitions.all { it.sourceInspiration == "portable-pattern" })
        assertEquals(
            listOf(1, 2),
            definitions.filter { it.progressionGroup == "captured-species" }.map { it.progressionRank },
        )
    }

    @Test
    fun `bundled extension declares role bound tier two and adapter gated tier three templates`() {
        val file = File("src/main/assets/challenges/portable-extended.json")
        val templates = PortableChallengeCatalog.decodeTemplates(file.readBytes())

        assertEquals(6, templates.size)
        assertEquals(setOf(2, 3), templates.mapTo(sortedSetOf()) { it.portabilityTier })
        assertTrue(templates.filter { it.portabilityTier == 2 }.all { it.requiredCatalogRoles.isNotEmpty() })
        assertTrue(templates.filter { it.portabilityTier == 3 }.all { it.requiredAdapters.isNotEmpty() })
        assertTrue(templates.all { it.sourceInspiration == "classified-portable-pattern" })
        assertEquals(
            listOf(1, 2),
            templates.filter { it.progressionGroup == "badge-progression" }.map { it.progressionRank },
        )
    }

    @Test
    fun `unsupported temporal window rejects only its dependent template`() {
        val file = File("src/main/assets/challenges/portable-extended.json")
        val mutated = file.readText().replaceFirst("\"PLAYTHROUGH\"", "\"FRAME_EXACT\"")

        val templates = PortableChallengeCatalog.decodeTemplates(mutated.toByteArray())

        assertEquals(5, templates.size)
        assertTrue(templates.none { it.key == "progress-first-badge" })
    }

    @Test
    fun `incomplete progression metadata rejects only its dependent definition`() {
        val file = File("src/main/assets/challenges/portable-baseline.json")
        val mutated = file.readText().replaceFirst(
            "\"progressionGroup\": \"captured-species\",",
            "\"progressionGroup\": \"\",",
        )

        val definitions = PortableChallengeCatalog.decode(mutated.toByteArray())

        assertEquals(5, definitions.size)
        assertTrue(definitions.none { it.key == "collection-first-partner" })
    }
}
