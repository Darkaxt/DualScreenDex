package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.EngineFamily
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

class Gen3TrainerAssetResolverRealControlTest {
    @Test
    fun fireRedFamilyControlsProduceGenderSelectedOverworldSprites() {
        val controls = listOf(
            RealControl(
                environmentVariable = "DUALDEX_FIRERED_ROM",
                sha256 = "729041b940afe031302d630fdbe57c0c145f3f7b6d9b8eca5e98678d0ca4d059",
            ),
            RealControl(
                environmentVariable = "DUALDEX_LEAFGREEN_ROM",
                sha256 = "2f978f635b9593f6ca26ec42481c53a6b39f6cddd894ad5c062c1419fac58825",
            ),
            RealControl(
                environmentVariable = "DUALDEX_ODYSSEY_ROM",
                sha256 = "44c7e3eafab19c39df7c39d54bafb78a1d9caf7c371244b6f5efb12cfd98d0d0",
            ),
            RealControl(
                environmentVariable = "DUALDEX_UNBOUND_ROM",
                sha256 = "7aa25bbf568f7cfcf6ee1cf2e9e6ff637350b3d0705c2375cabb6baa7d9739f7",
                spriteWidth = 32,
            ),
        )
        controls.forEach { control ->
            val configured = System.getenv(control.environmentVariable)
            assumeTrue("set ${control.environmentVariable} to run this live-ROM regression", !configured.isNullOrBlank())
            val path = Path.of(requireNotNull(configured))
            assumeTrue("live ROM does not exist: $path", Files.isRegularFile(path))
            val rom = RomImage(Files.readAllBytes(path))
            assertEquals(control.sha256, rom.sha256.lowercase())

            val catalog = requireNotNull(Gen3TrainerAssetResolver.resolve(rom, EngineFamily.FIRERED_LEAFGREEN))
            assertEquals(
                control.environmentVariable,
                mapOf(0 to "trainer/overworld/male", 1 to "trainer/overworld/female"),
                catalog.overworldAssetKeys,
            )
            catalog.overworldAssetKeys.values.forEach { key ->
                val sprite = catalog.assets.getValue(key)
                assertEquals(control.spriteWidth, sprite.width)
                assertEquals(32, sprite.height)
                assertEquals(true, sprite.argb.any { it ushr 24 != 0 })
            }
        }
    }

    @Test
    fun officialEmeraldProducesTwoPlayerPortraitsAndEightHoennBadges() {
        val configured = System.getenv("DUALDEX_OFFICIAL_EMERALD_ROM")
        assumeTrue("set DUALDEX_OFFICIAL_EMERALD_ROM to run this live-ROM regression", !configured.isNullOrBlank())
        val path = Path.of(requireNotNull(configured))
        assumeTrue("live ROM does not exist: $path", Files.isRegularFile(path))

        val catalog = requireNotNull(
            Gen3TrainerAssetResolver.resolve(
                RomImage(Files.readAllBytes(path)),
                EngineFamily.EMERALD,
            ),
        )
        assertEquals(
            mapOf(0 to "trainer/avatar/male", 1 to "trainer/avatar/female"),
            catalog.avatarAssetKeys,
        )
        assertEquals(
            mapOf(0 to "trainer/overworld/male", 1 to "trainer/overworld/female"),
            catalog.overworldAssetKeys,
        )
        assertEquals((1..8).map { "trainer/badge/$it" }, catalog.badgeAssetKeys)
        assertEquals(
            catalog.avatarAssetKeys.values.toSet() + catalog.overworldAssetKeys.values + catalog.badgeAssetKeys,
            catalog.assets.keys,
        )
        catalog.avatarAssetKeys.values.forEach { key ->
            assertEquals(64, catalog.assets.getValue(key).width)
            assertEquals(64, catalog.assets.getValue(key).height)
        }
        catalog.badgeAssetKeys.forEach { key ->
            assertEquals(16, catalog.assets.getValue(key).width)
            assertEquals(16, catalog.assets.getValue(key).height)
        }
        catalog.overworldAssetKeys.values.forEach { key ->
            assertEquals(16, catalog.assets.getValue(key).width)
            assertEquals(32, catalog.assets.getValue(key).height)
        }
        assertEquals(EXPECTED_ARGB_HASHES, catalog.assets.mapValues { argbHash(it.value.argb) })
    }

    @Test
    fun modernEmeraldPreservesPlayerPortraitsWhenBadgeArtworkDoesNotResolve() {
        val configured = System.getenv("DUALDEX_MODERN_EMERALD_ROM")
        assumeTrue("set DUALDEX_MODERN_EMERALD_ROM to run this live-ROM regression", !configured.isNullOrBlank())
        val path = Path.of(requireNotNull(configured))
        assumeTrue("live ROM does not exist: $path", Files.isRegularFile(path))

        val rom = RomImage(Files.readAllBytes(path))
        assertEquals(MODERN_EMERALD_SHA256, rom.sha256.lowercase())
        val catalog = requireNotNull(Gen3TrainerAssetResolver.resolve(rom, EngineFamily.EMERALD))

        assertEquals(mapOf(0 to "trainer/avatar/male", 1 to "trainer/avatar/female"), catalog.avatarAssetKeys)
        assertEquals(
            mapOf(0 to "trainer/overworld/male", 1 to "trainer/overworld/female"),
            catalog.overworldAssetKeys,
        )
        assertEquals(emptyList<String>(), catalog.badgeAssetKeys)
        assertEquals(catalog.avatarAssetKeys.values.toSet() + catalog.overworldAssetKeys.values, catalog.assets.keys)
        assertEquals(
            MODERN_AVATAR_ARGB_HASHES,
            catalog.assets.filterKeys(catalog.avatarAssetKeys.values::contains).mapValues { argbHash(it.value.argb) },
        )
        catalog.overworldAssetKeys.values.forEach { key ->
            val sprite = catalog.assets.getValue(key)
            assertEquals(16, sprite.width)
            assertEquals(32, sprite.height)
            assertEquals(true, sprite.argb.any { it ushr 24 != 0 })
        }
    }

    private fun argbHash(argb: IntArray): String {
        val bytes = ByteBuffer.allocate(argb.size * 4).order(ByteOrder.BIG_ENDIAN)
        argb.forEach(bytes::putInt)
        return MessageDigest.getInstance("SHA-256").digest(bytes.array()).joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val MODERN_EMERALD_SHA256 = "21a0306c4e5b5dc15ca70b74e713e3140612c1045aa298072993a6c5dd8d6895"
        val MODERN_AVATAR_ARGB_HASHES = mapOf(
            "trainer/avatar/male" to "86a27bea1436640e331f6faedbcad9a6b1af097d54c07b5fe489565dd62e8235",
            "trainer/avatar/female" to "88ef40596e677e75ba4dc105d6ea8df727232fdb806f6ee5611611230b196593",
        )
        val EXPECTED_ARGB_HASHES = mapOf(
            "trainer/avatar/male" to "86a27bea1436640e331f6faedbcad9a6b1af097d54c07b5fe489565dd62e8235",
            "trainer/avatar/female" to "88ef40596e677e75ba4dc105d6ea8df727232fdb806f6ee5611611230b196593",
            "trainer/overworld/male" to "e0d5136d38565e02c5eeb8f6fce3e52065e078e90f0ac950f1f83c107d5331ab",
            "trainer/overworld/female" to "882dde8e939dda521f81e381cc3f257be48ccf2dd21af07bbfba360500eb4615",
            "trainer/badge/1" to "ee1b49619687b62d478a92054b3e75f9028cb7901f8bfd0bdd788f9dfe9d8429",
            "trainer/badge/2" to "107fa5d2a6f380e99d9c5141a6fed52ce540f32579fddcf0dffa62f3c07bf138",
            "trainer/badge/3" to "2e16817fc400e69c768e67dff1da551d0145729ecc3c1c80811873c433317330",
            "trainer/badge/4" to "a48ffbc2b0be04d200d378f37c2ffa3ef47ad1cd9ecaf7620bb0f08b1eb8c351",
            "trainer/badge/5" to "c8a979194068163caf855d5cbe7c97735690759a4f41b0feca5322f7dde828f7",
            "trainer/badge/6" to "2e408e4a5dff3bda654a06ac3c56f150e764f2ca2d6a7739937587fe17fe3cde",
            "trainer/badge/7" to "1338f8d00dc2b77552955e2436398b66825aa5c807103a0d1576e9ee27941d83",
            "trainer/badge/8" to "c5f84d82e64d66d1fd477f47a6f3fbedb358400ca10889c3d802c6351563e529",
        )
    }

    private data class RealControl(
        val environmentVariable: String,
        val sha256: String,
        val spriteWidth: Int = 16,
    )
}
