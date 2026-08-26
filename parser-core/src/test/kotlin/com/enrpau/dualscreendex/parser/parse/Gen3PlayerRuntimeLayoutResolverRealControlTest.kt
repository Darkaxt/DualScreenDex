package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.catalog.CatalogGen3BagPocket
import com.enrpau.dualscreendex.parser.catalog.CatalogGen3TextEncoding
import com.enrpau.dualscreendex.parser.catalog.CatalogParser
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.EngineFamily
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test

class Gen3PlayerRuntimeLayoutResolverRealControlTest {
    @Test
    fun officialRubyAndSapphirePublishTheirDirectSourceDefinedSaveRuntimeDescriptor() {
        val controls = listOf(
            Triple(
                "Ruby",
                System.getenv("DUALDEX_OFFICIAL_RUBY_ROM")
                    ?: "D:/Temp/PokemonHacks/roms/official/Gen III/Pokemon - Ruby Version (USA, Europe) (Rev 2).gba",
                OFFICIAL_RUBY_SHA256,
            ),
            Triple(
                "Sapphire",
                System.getenv("DUALDEX_OFFICIAL_SAPPHIRE_ROM")
                    ?: "D:/Temp/PokemonHacks/roms/official/Gen III/Pokemon - Sapphire Version (USA, Europe) (Rev 2).gba",
                OFFICIAL_SAPPHIRE_SHA256,
            ),
        )

        controls.forEach { (label, configuredPath, expectedSha) ->
            val path = Path.of(configuredPath)
            assumeTrue("real official $label ROM does not exist: $path", Files.isRegularFile(path))

            val parsed = CatalogParser.parse(RomImage(Files.readAllBytes(path)))
            assertEquals(expectedSha, parsed.analysis.sha256)
            val runtime = requireNotNull(requireNotNull(parsed.catalog).runtimeMetadata.gen3RuntimeMemoryLayout)

            assertEquals(0x02025734L, runtime.saveBlock1Address)
            assertEquals(0x02024EA4L, runtime.saveBlock2Address)
            assertNull(runtime.saveBlock1PointerAddress)
            assertNull(runtime.saveBlock2PointerAddress)
            requireNotNull(runtime.pokemonStorageAddress)
            assertNull(runtime.pokemonStoragePointerAddress)
            assertEquals(14, runtime.pokemonStorageBoxCount)
            requireNotNull(runtime.liveClockAddress)
            assertNull(runtime.liveClockSchedule)
            val save = requireNotNull(runtime.saveRuntimeAbi)
            assertEquals(0x3AC0, save.saveBlock1Size)
            assertEquals(0x0890, save.saveBlock2Size)
            assertNull(save.trainer.encryptionKeyOffset)
            assertEquals(0x490, save.trainer.moneyOffset)
            assertEquals(0x1220, save.eventFlags?.byteOffset)
            assertEquals(0x0120, save.eventFlags?.byteCount)
            assertEquals(
                listOf(20, 20, 16, 64, 46),
                save.bag.pockets.map { it.capacity },
            )
            assertEquals(
                listOf(
                    0x1320 to 0x80,
                    0x1321 to 0x01,
                    0x1321 to 0x02,
                    0x1321 to 0x04,
                    0x1321 to 0x08,
                    0x1321 to 0x10,
                    0x1321 to 0x20,
                    0x1321 to 0x40,
                ),
                save.trainer.badgeFlags.map { it.byteOffset to it.mask },
            )
        }
    }

    @Test
    fun modernEmeraldPublishesItsSourceDefinedTrainerRuntimeDescriptor() {
        val path = Path.of(
            System.getenv("DUALDEX_MODERN_EMERALD_ROM")
                ?: "D:/Temp/PokemonHacks/corpus/expanded/roms/0116-a0b4e5e9c0c4/Modern Emerald (v3.5).gba",
        )
        assumeTrue("real Modern Emerald ROM does not exist: $path", Files.isRegularFile(path))

        val rom = RomImage(Files.readAllBytes(path))
        assertEquals(MODERN_EMERALD_SHA256, rom.sha256)
        val runtime = requireNotNull(Gen3RuntimeMemoryLayoutResolver.resolve(rom, EngineFamily.EMERALD))

        assertEquals(0x030036F0L, runtime.saveBlock1PointerAddress)
        assertEquals(0x030036F4L, runtime.saveBlock2PointerAddress)
        requireNotNull(runtime.pokemonStoragePointerAddress)
        assertEquals(15, runtime.pokemonStorageBoxCount)
        val save = requireNotNull(runtime.saveRuntimeAbi)
        assertEquals(0x00, save.trainer.playerNameOffset)
        assertEquals(0x08, save.trainer.genderOffset)
        assertEquals(0x0A, save.trainer.trainerIdOffset)
        assertEquals(0x0E, save.trainer.playTimeHoursOffset)
        assertEquals(0x10, save.trainer.playTimeMinutesOffset)
        assertEquals(0xBC, save.trainer.encryptionKeyOffset)
        assertEquals(0x490, save.trainer.moneyOffset)
    }

    @Test
    fun unboundPublishesItsSourceDefinedCfruClock() {
        val path = Path.of(
            System.getenv("DUALDEX_UNBOUND_ROM")
                ?: "D:/Temp/PokemonHacks/corpus/expanded/roms/0199-a275be0f927e/Unbound (v2.1.1.1).gba",
        )
        assumeTrue("real Unbound ROM does not exist: $path", Files.isRegularFile(path))

        val rom = RomImage(Files.readAllBytes(path))
        assertEquals(UNBOUND_SHA256, rom.sha256)
        val runtime = requireNotNull(Gen3RuntimeMemoryLayoutResolver.resolve(rom, EngineFamily.FIRERED_LEAFGREEN))

        assertEquals(0x03005EA4L, runtime.liveClockAddress)
        assertNull(runtime.liveClockSchedule)
        assertEquals(0x03005008L, runtime.saveBlock1PointerAddress)
        assertEquals(0x0300500CL, runtime.saveBlock2PointerAddress)
        requireNotNull(runtime.pokemonStorageAddress)
        assertEquals(14, runtime.pokemonStorageBoxCount)
        val save = requireNotNull(runtime.saveRuntimeAbi)
        assertEquals(0x3D68, save.saveBlock1Size)
        assertEquals(0x0F24, save.saveBlock2Size)
        assertEquals(0x290, save.trainer.moneyOffset)
        assertEquals(0xF20, save.trainer.encryptionKeyOffset)
    }

    @Test
    fun officialFireRedPublishesItsSourceDefinedSaveRuntimeDescriptor() {
        val path = Path.of(
            System.getenv("DUALDEX_OFFICIAL_FIRERED_ROM")
                ?: "D:/Temp/PokemonHacks/roms/official/Gen III/Pokemon - FireRed Version (USA, Europe) (Rev 1).gba",
        )
        assumeTrue("live ROM does not exist: $path", Files.isRegularFile(path))

        val parsed = CatalogParser.parse(RomImage(Files.readAllBytes(path)))
        assertEquals(OFFICIAL_FIRERED_SHA256, parsed.analysis.sha256)
        val runtime = requireNotNull(requireNotNull(parsed.catalog).runtimeMetadata.gen3RuntimeMemoryLayout)

        assertEquals(0x03005008L, runtime.saveBlock1PointerAddress)
        assertEquals(0x0300500CL, runtime.saveBlock2PointerAddress)
        requireNotNull(runtime.pokemonStoragePointerAddress)
        assertEquals(14, runtime.pokemonStorageBoxCount)
        assertEquals(null, runtime.extendedSaveAddress)
        assertNull(runtime.liveClockAddress)
        assertNull(runtime.liveClockSchedule)
        val save = requireNotNull(runtime.saveRuntimeAbi)
        assertEquals(0x3D68, save.saveBlock1Size)
        assertEquals(0x0F24, save.saveBlock2Size)
        assertEquals(0, save.extendedSaveDataSize)
        assertEquals(0x0EE0, save.eventFlags?.byteOffset)
        assertEquals(0x0120, save.eventFlags?.byteCount)
        assertEquals(0x290, save.trainer.moneyOffset)
        assertEquals(0xF20, save.trainer.encryptionKeyOffset)
        assertEquals(
            listOf(42, 30, 13, 58, 43),
            save.bag.pockets.map { it.capacity },
        )
    }

    @Test
    fun officialEmeraldPublishesTheCompleteSourceVerifiedPlayerRuntimeDescriptor() {
        val path = Path.of(
            System.getenv("DUALDEX_OFFICIAL_EMERALD_ROM")
                ?: "D:/Temp/PokemonHacks/roms/official/Gen III/Pokemon - Emerald Version (USA, Europe).gba",
        )
        assumeTrue("live ROM does not exist: $path", Files.isRegularFile(path))

        val parsed = CatalogParser.parse(RomImage(Files.readAllBytes(path)))
        assertEquals(OFFICIAL_EMERALD_SHA256, parsed.analysis.sha256)
        val runtime = requireNotNull(requireNotNull(parsed.catalog).runtimeMetadata.gen3RuntimeMemoryLayout)

        assertEquals(0x03005D8CL, runtime.saveBlock1PointerAddress)
        assertEquals(0x03005D90L, runtime.saveBlock2PointerAddress)
        requireNotNull(runtime.pokemonStoragePointerAddress)
        assertEquals(14, runtime.pokemonStorageBoxCount)
        requireNotNull(runtime.liveClockAddress)
        assertNull(runtime.liveClockSchedule)
        val save = requireNotNull(runtime.saveRuntimeAbi)
        assertEquals(0x3D88, save.saveBlock1Size)
        assertEquals(0x0F2C, save.saveBlock2Size)
        assertEquals(CatalogGen3TextEncoding.ENGLISH, save.textEncoding)
        assertEquals(0x1270, save.eventFlags?.byteOffset)
        assertEquals(0x012C, save.eventFlags?.byteCount)
        assertEquals(0x00, save.trainer.playerNameOffset)
        assertEquals(8, save.trainer.playerNameLength)
        assertEquals(0x08, save.trainer.genderOffset)
        assertEquals(0x0A, save.trainer.trainerIdOffset)
        assertEquals(0x0E, save.trainer.playTimeHoursOffset)
        assertEquals(0x10, save.trainer.playTimeMinutesOffset)
        assertEquals(0xAC, save.trainer.encryptionKeyOffset)
        assertEquals(0x490, save.trainer.moneyOffset)
        assertEquals(999_999L, save.trainer.maximumMoney)
        assertEquals(
            listOf(
                0x137C to 0x80,
                0x137D to 0x01,
                0x137D to 0x02,
                0x137D to 0x04,
                0x137D to 0x08,
                0x137D to 0x10,
                0x137D to 0x20,
                0x137D to 0x40,
            ),
            save.trainer.badgeFlags.map { it.byteOffset to it.mask },
        )
        assertEquals(
            listOf(
                Triple(CatalogGen3BagPocket.ITEMS, 0x560, 30),
                Triple(CatalogGen3BagPocket.KEY_ITEMS, 0x5D8, 30),
                Triple(CatalogGen3BagPocket.BALLS, 0x650, 16),
                Triple(CatalogGen3BagPocket.TM_HM, 0x690, 64),
                Triple(CatalogGen3BagPocket.BERRIES, 0x790, 46),
            ),
            save.bag.pockets.map { Triple(it.pocket, it.byteOffset, it.capacity) },
        )

        val party = requireNotNull(runtime.partyAbi)
        assertEquals(0x020244E9L, party.countAddress)
        assertEquals(0x020244ECL, party.partyAddress)
        assertEquals(6, party.capacity)
        assertEquals(100, party.recordSize)

        val battleUi = requireNotNull(runtime.battleUiAbi) { runtime.toString() }
        assertEquals(0x02024064L, battleUi.activeBattlerAddress)
        assertEquals(0x020244ACL, battleUi.actionCursorAddress)
        assertEquals(0x020244B0L, battleUi.moveCursorAddress)
        assertEquals(0x0202420CL, battleUi.targetCursorAddress)
    }

    @Test
    fun odysseyPublishesSourceDerivedLiveStorageWithoutARomProfile() {
        val path = Path.of(
            System.getenv("DUALDEX_ODYSSEY_ROM")
                ?: "D:/Temp/PokemonHacks/corpus/expanded/roms/0123-5e7ce46db2ce/Odyssey (v4.1.1).gba",
        )
        assumeTrue("real Odyssey ROM does not exist: $path", Files.isRegularFile(path))

        val parsed = CatalogParser.parse(RomImage(Files.readAllBytes(path)))
        assertEquals(ODYSSEY_SHA256, parsed.analysis.sha256)
        val runtime = requireNotNull(requireNotNull(parsed.catalog).runtimeMetadata.gen3RuntimeMemoryLayout)

        requireNotNull(runtime.pokemonStoragePointerAddress)
        assertEquals(14, runtime.pokemonStorageBoxCount)
        assertEquals(30, runtime.pokemonStorageBoxCapacity)
        assertEquals(80, runtime.pokemonStorageRecordSize)
        assertEquals(4, runtime.pokemonStorageRecordsOffset)
    }

    private companion object {
        const val MODERN_EMERALD_SHA256 =
            "21a0306c4e5b5dc15ca70b74e713e3140612c1045aa298072993a6c5dd8d6895"
        const val OFFICIAL_EMERALD_SHA256 =
            "a9dec84dfe7f62ab2220bafaef7479da0929d066ece16a6885f6226db19085af"
        const val OFFICIAL_FIRERED_SHA256 =
            "729041b940afe031302d630fdbe57c0c145f3f7b6d9b8eca5e98678d0ca4d059"
        const val OFFICIAL_RUBY_SHA256 =
            "0fdd36e92b75bed65d09df4635ab0b707b288c2bf1dc4c6e7a4a4f0eebe9d64c"
        const val OFFICIAL_SAPPHIRE_SHA256 =
            "02ca41513580a8b780989dee428df747b52a0b1a55bec617886b4059eb1152fb"
        const val UNBOUND_SHA256 =
            "7aa25bbf568f7cfcf6ee1cf2e9e6ff637350b3d0705c2375cabb6baa7d9739f7"
        const val ODYSSEY_SHA256 =
            "44c7e3eafab19c39df7c39d54bafb78a1d9caf7c371244b6f5efb12cfd98d0d0"
    }
}
