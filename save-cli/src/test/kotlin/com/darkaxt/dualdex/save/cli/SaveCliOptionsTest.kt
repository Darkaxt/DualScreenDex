package com.darkaxt.dualdex.save.cli

import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SaveCliOptionsTest {
    @Test
    fun acceptsExplicitRomSavePairsAndOutputPaths() {
        val options = SaveCliOptions.parse(
            arrayOf(
                "--pair", "game.zip", "game.srm",
                "--pair", "other.gba", "other.sav",
                "--probe", "derived.srm",
                "--json", "report.json",
                "--markdown", "report.md",
            ),
        )

        assertEquals(
            listOf(
                SaveInputPair(Path.of("game.zip"), Path.of("game.srm")),
                SaveInputPair(Path.of("other.gba"), Path.of("other.sav")),
            ),
            options.pairs,
        )
        assertEquals(Path.of("report.json"), options.json)
        assertEquals(Path.of("report.md"), options.markdown)
        assertEquals(listOf(Path.of("derived.srm")), options.probes)
    }

    @Test
    fun rejectsAnUnpairedOrMissingOutputArgument() {
        assertThrows(IllegalArgumentException::class.java) {
            SaveCliOptions.parse(arrayOf("--pair", "game.gba", "--json", "report.json"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            SaveCliOptions.parse(arrayOf("--pair", "game.gba", "game.srm", "--json", "report.json"))
        }
    }
}
