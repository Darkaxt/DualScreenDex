package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.RomCapability
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

class Gen3CompactMapGroupRealControlTest {
    @Test
    fun focusedSemanticJoinClusterProducesExactCompleteCatalogs() {
        val manifest = System.getenv("DUALDEX_FIRST50_MANIFEST")?.takeIf(String::isNotBlank)
        assumeTrue("set DUALDEX_FIRST50_MANIFEST to run real compact-consumer controls", manifest != null)
        val rows = Files.readAllLines(Path.of(requireNotNull(manifest))).drop(1)
            .mapIndexed { index, line -> index + 1 to Path.of(csv(line).last()) }
            .toMap()

        controls.forEach { control ->
            val rom = RomImage(Files.readAllBytes(rows.getValue(control.index)))
            assertEquals(control.romSha256, rom.sha256)
            val catalog = requireNotNull(CatalogParser.parse(rom).catalog)
            assertEquals(CapabilityStatus.AVAILABLE, catalog.capabilities.getValue(RomCapability.WORLD_MAP).status)
            assertEquals(control.regionCount, catalog.worldMaps.regions.size)
            assertEquals(control.rasterArgbSha256, catalog.worldMaps.regions.map { region ->
                val raster = catalog.worldMaps.assets.getValue(region.imageAssetKey)
                val digest = MessageDigest.getInstance("SHA-256")
                raster.argb.forEach { digest.update(ByteBuffer.allocate(4).putInt(it).array()) }
                digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
            })
            assertEquals(control.locationCounts, catalog.worldMaps.regions.map { it.locations.size })
        }
    }

    private fun csv(line: String): List<String> {
        val values = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            val character = line[index]
            when {
                character == '"' && quoted && index + 1 < line.length && line[index + 1] == '"' -> {
                    current.append('"')
                    index++
                }
                character == '"' -> quoted = !quoted
                character == ',' && !quoted -> {
                    values += current.toString()
                    current.clear()
                }
                else -> current.append(character)
            }
            index++
        }
        values += current.toString()
        return values
    }

    private data class Control(
        val index: Int,
        val romSha256: String,
        val regionCount: Int,
        val rasterArgbSha256: List<String>,
        val locationCounts: List<Int>,
    )

    private companion object {
        val officialEmeraldRaster = listOf("1c3a1bf13c851dcc707f1f3f71c8f90e703a0faf0832917a0195618952a77aab")
        val officialFrlgRasters = listOf(
            "250195a226d642147bb594e30cb03596ef94dd88237204f761fb164286d53654",
            "8e1d6f588bf4bd24913a559e70f6af8f42c32d484f523ee197a09b73c03b4135",
            "eebdbb58c4d7fbbc875d6fbc465751625c26baf2a2c728c06fa8331d92fd7e4a",
            "b96065661b1848860cc69db7e9370194df740568e4352d7288e2b4ee17640a3b",
        )
        val darkVioletRasters = listOf(
            "117e4d9c854ec0b80ab942dcd7f65d8e52d8826589e93fa88532a8ce60422118",
            "da5db5e336b772d95b541a793b3d44a6dc6ce628e43f6077b65c430b024e4aa1",
            "17a547a2ecec1d3f93abfd74f569f250a92f16e303f93c12c1311566538db0bf",
            "fd9e4540d935e9756f5fe9c7c519a9c7cbc3920778e61a1df0c9737c511d6b3d",
        )
        val controls = listOf(
            Control(10, "333e4fcbf2b8039ad1848a84d0f6826e790109ed150243f6cf7c9934b22ae380", 1, officialEmeraldRaster, listOf(64)),
            Control(14, "fe428c3a45747c9d1466506b5f6d9245e2faf7337660664b6ba3ee28a86ca4ab", 1, officialEmeraldRaster, listOf(58)),
            Control(18, "2eb56e73fdba2b81c26596d19e80410fbd48de0586af5d342c25ec741eb59f57", 4, officialFrlgRasters, listOf(32, 6, 6, 8)),
            Control(33, "42f99abd548934d77999ac3eb563fb9bc70a34701d37a262b21b882a43a8bdd9", 4, listOf(
                "50a41e7a72bddfb8812a99ff01ac3e26170d5ab2ca5ed179b885c7ad21ed0ebb",
                "4cf81884ab3be1fd315555385dd719cab2b5d46880f17982a5fc3ceb5ba838da",
                "c79660d299bd1fb315c32cacfd21a41d10d76ef20fffacea67267609fb038bf2",
                "11fef4f3fdbc027b99034f2389238b5d4c88938292dac94fded4c7ee45fcd08e",
            ), listOf(45, 10, 4, 8)),
            Control(39, "71b44f3b4be1b17428dd3fcb1c37002268c7b832dc49626b9d57bf56de10f387", 4, officialFrlgRasters, listOf(33, 7, 6, 8)),
            Control(40, "81b97561b73d02a26ba52369d582ac5d8615078de2b202e0673f4e6512af120d", 4, officialFrlgRasters, listOf(32, 6, 6, 8)),
            Control(41, "c6440addb23d76f514d0ba4baf049a5c34a0d7c0938a5c6ee4fbfa3792f9daea", 4, officialFrlgRasters, listOf(32, 6, 6, 8)),
            Control(42, "712697aba9a0f2401bc0fb8677caa69d9d21beee26c7d9920226e52f02f76a4e", 4, officialFrlgRasters, listOf(35, 8, 6, 10)),
            Control(43, "6b7e6df19c974371a4f80ea5c0f1e8d68a2cfee248faf34080a48ae3f0135e21", 4, darkVioletRasters, listOf(44, 10, 6, 3)),
            Control(44, "d171d29b691ced98178b4370826f0627f9c2ed6e0313d813f909ba147031c717", 4, darkVioletRasters, listOf(44, 10, 6, 3)),
            Control(47, "7f4aa1aa68b1df783c3a44b38984640227a5eec22debffbf18db3713de2616bc", 1, officialEmeraldRaster, listOf(64)),
        )
    }
}
