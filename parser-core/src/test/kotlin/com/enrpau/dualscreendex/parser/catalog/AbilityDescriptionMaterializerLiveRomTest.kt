package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

class AbilityDescriptionMaterializerLiveRomTest {
    @Test
    fun `published source-backed ability tables survive dense unrelated pointer candidates`() {
        listOf(
            Control(
                path = "D:/Temp/dualdex-expanded-corpus/roms/0066-7c6425766ed0/Emerald Rogue (Vanilla) (v2.1.2).gba",
                sha256 = "bc41411bec0b89c37f8514bae6fe8b7472093fe6badcd503ed2c466929f1e93e",
                descriptionCount = 77,
            ),
            Control(
                path = "D:/Temp/dualdex-expanded-corpus/roms/0092-d4c7fecad0af/Heart & Soul (v1.2.1).gba",
                sha256 = "7c4f90c8b68b64d4639a37306d5302a11cd4d7c44005114ec2faa2b28b210c3d",
                descriptionCount = 81,
            ),
            Control(
                path = "D:/Temp/dualdex-expanded-corpus/roms/0116-a0b4e5e9c0c4/Modern Emerald (v3.5).gba",
                sha256 = "21a0306c4e5b5dc15ca70b74e713e3140612c1045aa298072993a6c5dd8d6895",
                descriptionCount = 81,
            ),
        ).forEach { control ->
            val path = Path.of(control.path)
            assumeTrue("live ROM does not exist: $path", Files.isRegularFile(path))
            val rom = RomImage(Files.readAllBytes(path))
            assertEquals(control.sha256, rom.sha256)

            val catalog = requireNotNull(CatalogParser.parse(rom).catalog)
            val text = catalog.defaultTextProjection()
            val evidence = text.localizedCapabilities.getValue(LocalizedTextCapability.ABILITY_DESCRIPTIONS)
            assertEquals(CapabilityStatus.AVAILABLE, evidence.status)
            assertEquals(control.descriptionCount, evidence.coveredRecords)
            assertEquals(control.descriptionCount, evidence.expectedRecords)
            assertEquals(
                control.descriptionCount,
                catalog.abilitiesById.keys.count { text.abilityDescription(it) != null },
            )
        }
    }

    private data class Control(
        val path: String,
        val sha256: String,
        val descriptionCount: Int,
    )
}
