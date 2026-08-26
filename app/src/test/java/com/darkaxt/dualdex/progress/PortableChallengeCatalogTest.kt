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
    }
}
